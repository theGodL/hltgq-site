package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.entity.RainAdjust;
import com.qgyun.hltgq.hltgqsite.mapper.RainAdjustMapper;
import com.qgyun.hltgq.hltgqsite.service.RainAdjustService;
import com.qgyun.hltgq.hltgqsite.vo.RainAdjustSaveVO;
import com.qgyun.hltgq.hltgqsite.vo.RainAdjustVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 雨量补偿配置实现（监测数据删除方案 §5.1）。
 * <p>维护入口＝站点+设备联动：先选站点档案，再联动该站设备下拉；保存 site/device/stcd 三 id。
 * <p>唯一性：device 为唯一/匹配键（mq 入库按 device.id 匹配补偿，兼容 MQTT 链路无 stcd 报文），
 * 已在应用层按 device 判重（表另有 uq_rain_adjust_device 兜底）；site/device/stcd 为匹配键与识别列，
 * 编辑时锁定不可改（换设备=新增新配置后停用/删除旧配置）。
 * <p>生效口径：保存后 ≤5 分钟（mq 缓存 TTL）自动生效，无需重启 mq；停用（enabled=false）＝按原值入库；
 * 删除出厂默认站配置将触发 mq 预置自检 WARN（前端确认已提示，见方案附十二）。
 */
@Service
public class RainAdjustServiceImpl implements RainAdjustService {

    private static final Logger log = LoggerFactory.getLogger(RainAdjustServiceImpl.class);

    /** 补偿值上限（±；offset_value 数值列口径） */
    private static final BigDecimal OFFSET_LIMIT = new BigDecimal("99999999.99");

    /** 备注最大长度（与界面字数统计一致） */
    private static final int REMARK_MAX = 255;

    private static final int PAGE_SIZE_DEFAULT = 20;

    private static final int PAGE_SIZE_MAX = 200;

    /** 设备类型编码提取（多值形如 #1#|#2#） */
    private static final Pattern CODE_PATTERN = Pattern.compile("#\\d+#");

    /** 设备监测类型字典（与设备总览页 CATEGORY_DEFS 同源；设备下拉类型列翻译用） */
    private static final Map<String, String> DEVICE_TYPES = new LinkedHashMap<>();

    static {
        DEVICE_TYPES.put("#1#", "水位");
        DEVICE_TYPES.put("#2#", "雨量");
        DEVICE_TYPES.put("#3#", "流量");
        DEVICE_TYPES.put("#4#", "闸门");
        DEVICE_TYPES.put("#5#", "视频");
        DEVICE_TYPES.put("#7#", "墒情");
        DEVICE_TYPES.put("#8#", "水质");
    }

    @Autowired
    private RainAdjustMapper rainAdjustMapper;

    /** 企业编码兜底（会话缺失时使用，列 NOT NULL） */
    @Value("${hltgq.corp-code}")
    private String corpCode;

    /** 操作人兜底（会话缺失时使用，列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    @Override
    public IPage<RainAdjustVO> page(long page, long size, String siteId, String keyword) {
        Page<RainAdjustVO> pager = new Page<>(page < 1 ? 1 : page, normalizeSize(size));
        return rainAdjustMapper.selectPageWithNames(pager, trim(siteId), trim(keyword));
    }

    @Override
    public List<RainAdjustVO.SiteOption> sites() {
        return rainAdjustMapper.selectSiteOptions();
    }

    @Override
    public List<RainAdjustVO.DeviceOption> devices(String siteId) {
        String siteKey = requireSiteId(siteId);
        List<RainAdjustVO.DeviceOption> list = rainAdjustMapper.selectDeviceOptions(siteKey);
        for (RainAdjustVO.DeviceOption option : list) {
            option.setTypeName(deviceTypeName(option.getType()));
        }
        return list;
    }

    @Override
    public RainAdjustVO create(RainAdjustSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        RainAdjustVO.SiteOption site = requireSite(req.getSite());
        RainAdjustVO.DeviceOption device = requireDevice(req.getDevice());
        if (!site.getId().equals(trim(device.getSite()))) {
            throw new IllegalArgumentException("所选设备不属于该站点，请重新选择");
        }
        if (countByDevice(device.getId()) > 0) {
            throw new IllegalArgumentException("该设备已配置雨量补偿，请直接编辑");
        }
        BigDecimal offset = requireOffset(req.getOffsetValue());
        String remark = normalizeRemark(req.getRemark());

        RainAdjust entity = new RainAdjust();
        entity.setSite(site.getId());
        entity.setDevice(device.getId());
        // 识别辅助列（不参与匹配）：取站点档案 RTU 站号，可空
        entity.setStcd(trim(site.getStcd()));
        entity.setOffsetValue(offset);
        entity.setEnabled(req.getEnabled() == null ? Boolean.TRUE : req.getEnabled());
        entity.setRemark(remark);
        applyCreateAudit(entity);
        rainAdjustMapper.insert(entity);
        log.info("[雨量补偿] 新增：站点={}（{}），设备={}（{}），补偿值={}，启用={}，操作人={}",
                site.getStnm(), site.getId(), device.getName(), device.getId(), offset, entity.getEnabled(),
                entity.getUpdatedBy());

        RainAdjustVO vo = toVO(entity);
        fillNames(vo, site, device);
        return vo;
    }

    @Override
    public RainAdjustVO update(String id, RainAdjustSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        RainAdjust row = requireRow(id);
        BigDecimal offset = requireOffset(req.getOffsetValue());
        // 启用开关缺省时保持库中原值（表单总是带值；null 不改变）
        Boolean enabled = req.getEnabled() != null ? req.getEnabled() : row.getEnabled();
        String remark = normalizeRemark(req.getRemark());

        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperatorId();
        UpdateWrapper<RainAdjust> wrapper = new UpdateWrapper<>();
        wrapper.eq("\"id\"", row.getId());
        wrapper.set("\"offset_value\"", offset);
        wrapper.set("\"enabled\"", enabled);
        // 备注允许清空，显式 set null（MyBatis-Plus 默认忽略 null 字段）
        wrapper.set("\"remark\"", remark);
        wrapper.set("\"updated_at\"", now);
        wrapper.set("\"updated_by\"", operator);
        rainAdjustMapper.update(null, wrapper);

        RainAdjust updated = rainAdjustMapper.selectById(row.getId());
        RainAdjustVO vo = toVO(updated != null ? updated : row);
        fillNames(vo, rainAdjustMapper.selectSiteById(row.getSite()),
                rainAdjustMapper.selectDeviceById(row.getDevice()));
        log.info("[雨量补偿] 编辑：id={}，站点={}，设备={}，补偿值={}，启用={}，操作人={}",
                row.getId(), row.getSite(), row.getDevice(), offset, enabled, operator);
        return vo;
    }

    @Override
    public void delete(String id) {
        RainAdjust row = requireRow(id);
        rainAdjustMapper.deleteById(row.getId());
        log.info("[雨量补偿] 删除：id={}，站点={}，设备={}，操作人={}",
                row.getId(), row.getSite(), row.getDevice(), currentOperatorId());
    }

    // ==================== 数据维护删除联动（删除必须同时明确补偿：0=设备复位） ====================

    @Override
    public Map<String, Object> adjustContext(String stcd) {
        String key = requireStcd(stcd);
        RainAdjustVO.SiteOption site = rainAdjustMapper.selectSiteByStcd(key);
        RainAdjust existing = site != null ? selectBySite(site.getId()) : null;
        RainAdjustVO.DeviceOption device = null;
        if (existing != null) {
            device = rainAdjustMapper.selectDeviceById(existing.getDevice());
        } else if (site != null) {
            device = rainAdjustMapper.selectRainDeviceBySite(site.getId());
        }
        boolean configured = existing != null;
        // 已有配置即视为设备可定位：配置行的 device 就是 mq 入库匹配键
        boolean deviceFound = configured || device != null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deviceFound", deviceFound);
        out.put("deviceId", device != null ? device.getId() : (configured ? existing.getDevice() : null));
        out.put("deviceName", device != null && trim(device.getName()) != null
                ? device.getName() : (configured ? existing.getDevice() : null));
        out.put("configured", configured);
        out.put("offsetValue", configured ? existing.getOffsetValue() : null);
        out.put("enabled", configured ? existing.getEnabled() : null);
        return out;
    }

    @Override
    public Map<String, Object> applyByDataDelete(String stcd, String tmText, BigDecimal adjustValue) {
        String key = requireStcd(stcd);
        if (adjustValue == null) {
            throw new IllegalArgumentException("设备偏差值不能为空");
        }
        BigDecimal value = adjustValue.setScale(2, RoundingMode.HALF_UP);
        if (value.abs().compareTo(OFFSET_LIMIT) > 0) {
            throw new IllegalArgumentException("设备偏差值超出允许范围（±" + OFFSET_LIMIT + "）");
        }
        RainAdjustVO.SiteOption site = rainAdjustMapper.selectSiteByStcd(key);
        if (site == null) {
            throw new IllegalArgumentException("站点未在站点档案登记，无法配置补偿");
        }
        RainAdjust existing = selectBySite(site.getId());
        String operator = currentOperatorId();
        String tmKey = trim(tmText);
        // 设备复位（偏差 0）：停用现有启用中的配置（保留行与审计；mq 侧停用＝按原值入库）
        if (value.signum() == 0) {
            if (existing == null || !Boolean.TRUE.equals(existing.getEnabled())) {
                return resultOf("none", existing == null ? null : existing.getOffsetValue(), Boolean.FALSE,
                        existing == null ? null : deviceName(existing.getDevice()),
                        "该设备无启用中的补偿配置，未做变更");
            }
            updateRow(existing, existing.getOffsetValue(), Boolean.FALSE,
                    appendRemark(existing.getRemark(), "随数据删除标记设备复位（TM=" + tmKey + "）"), operator);
            log.info("[雨量补偿] 随数据删除停用（设备复位）：站点={}（{}），设备={}，补偿值={}，操作人={}",
                    site.getStnm(), site.getId(), existing.getDevice(), existing.getOffsetValue(), operator);
            return resultOf("disabled", existing.getOffsetValue(), Boolean.FALSE, deviceName(existing.getDevice()),
                    "该设备补偿已停用（" + fmtOffset(existing.getOffsetValue()) + "，新入库按原值）");
        }
        // 偏差非 0：写入补偿值 = -偏差（新建或覆盖），并确保启用
        BigDecimal offset = value.negate();
        String suffix = "随数据删除更新补偿（TM=" + tmKey + "，设备偏差 " + fmtSigned(value) + "）";
        if (existing == null) {
            RainAdjustVO.DeviceOption device = rainAdjustMapper.selectRainDeviceBySite(site.getId());
            if (device == null) {
                throw new IllegalArgumentException("站点未登记雨量设备，无法配置补偿");
            }
            RainAdjust entity = new RainAdjust();
            entity.setSite(site.getId());
            entity.setDevice(device.getId());
            // 识别辅助列（不参与匹配）：取站点档案 RTU 站号
            entity.setStcd(trim(site.getStcd()));
            entity.setOffsetValue(offset);
            entity.setEnabled(Boolean.TRUE);
            entity.setRemark(appendRemark(null, suffix));
            applyCreateAudit(entity);
            rainAdjustMapper.insert(entity);
            log.info("[雨量补偿] 随数据删除新增：站点={}（{}），设备={}（{}），补偿值={}，操作人={}",
                    site.getStnm(), site.getId(), device.getName(), device.getId(), offset, entity.getUpdatedBy());
            return resultOf("created", offset, Boolean.TRUE,
                    trim(device.getName()) == null ? device.getId() : device.getName(),
                    "已为该设备配置补偿 " + fmtOffset(offset) + "（≤5 分钟生效）");
        }
        updateRow(existing, offset, Boolean.TRUE, appendRemark(existing.getRemark(), suffix), operator);
        log.info("[雨量补偿] 随数据删除更新：站点={}（{}），设备={}，补偿值={}，操作人={}",
                site.getStnm(), site.getId(), existing.getDevice(), offset, operator);
        return resultOf("updated", offset, Boolean.TRUE, deviceName(existing.getDevice()),
                "该设备补偿已更新为 " + fmtOffset(offset) + "（≤5 分钟生效）");
    }

    /** 数据维护联动：按站点查单条配置（一站一条的运维口径） */
    private RainAdjust selectBySite(String siteId) {
        QueryWrapper<RainAdjust> wrapper = new QueryWrapper<>();
        wrapper.eq("\"site\"", siteId);
        wrapper.last("LIMIT 1");
        return rainAdjustMapper.selectOne(wrapper);
    }

    /** 数据维护联动：更新 offset/enabled/remark + 审计（与编辑同列口径） */
    private void updateRow(RainAdjust row, BigDecimal offset, Boolean enabled, String remark, String operator) {
        UpdateWrapper<RainAdjust> wrapper = new UpdateWrapper<>();
        wrapper.eq("\"id\"", row.getId());
        wrapper.set("\"offset_value\"", offset);
        wrapper.set("\"enabled\"", enabled);
        wrapper.set("\"remark\"", remark);
        wrapper.set("\"updated_at\"", LocalDateTime.now());
        wrapper.set("\"updated_by\"", operator);
        rainAdjustMapper.update(null, wrapper);
    }

    /** 设备展示名（档案缺失时回退设备 id） */
    private String deviceName(String deviceId) {
        RainAdjustVO.DeviceOption device = rainAdjustMapper.selectDeviceById(deviceId);
        return device != null && trim(device.getName()) != null ? device.getName() : deviceId;
    }

    /** 备注追加（旧备注为空取新句；超长截尾保审计） */
    private static String appendRemark(String old, String suffix) {
        String base = trim(old);
        String text = base == null ? suffix : base + "；" + suffix;
        return text.length() > REMARK_MAX ? text.substring(0, REMARK_MAX) : text;
    }

    /** 测站编号必填校验（删除行 stcd，去首尾空白） */
    private static String requireStcd(String stcd) {
        String key = trim(stcd);
        if (key == null) {
            throw new IllegalArgumentException("站点编号不能为空");
        }
        return key;
    }

    private static String fmtOffset(BigDecimal value) {
        return value == null ? "--" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String fmtSigned(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + fmtOffset(value);
    }

    private static Map<String, Object> resultOf(String action, BigDecimal offset, Boolean enabled,
                                                String deviceName, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("action", action);
        out.put("offsetValue", offset);
        out.put("enabled", enabled);
        out.put("deviceName", deviceName);
        out.put("message", message);
        return out;
    }

    // ==================== 校验与审计 ====================

    /** 配置行存在性校验 */
    private RainAdjust requireRow(String id) {
        String key = trim(id);
        if (key == null) {
            throw new IllegalArgumentException("补偿配置 id 不能为空");
        }
        RainAdjust row = rainAdjustMapper.selectById(key);
        if (row == null) {
            throw new IllegalArgumentException("补偿配置不存在或已删除");
        }
        return row;
    }

    /** 站点必填校验（须在站点档案登记；stcd 识别列取自档案 iofhpi） */
    private RainAdjustVO.SiteOption requireSite(String siteId) {
        String key = trim(siteId);
        if (key == null) {
            throw new IllegalArgumentException("请选择站点");
        }
        RainAdjustVO.SiteOption site = rainAdjustMapper.selectSiteById(key);
        if (site == null) {
            throw new IllegalArgumentException("站点不存在或未在站点档案登记");
        }
        return site;
    }

    /** 设备必填校验 */
    private RainAdjustVO.DeviceOption requireDevice(String deviceId) {
        String key = trim(deviceId);
        if (key == null) {
            throw new IllegalArgumentException("请选择设备");
        }
        RainAdjustVO.DeviceOption device = rainAdjustMapper.selectDeviceById(key);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在或已删除");
        }
        return device;
    }

    /** 站点参数必填校验（下拉联动查询用） */
    private static String requireSiteId(String siteId) {
        String key = trim(siteId);
        if (key == null) {
            throw new IllegalArgumentException("请选择站点");
        }
        return key;
    }

    /** 补偿值必填 + 两位小数规范化 + 量程校验 */
    private static BigDecimal requireOffset(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("请输入补偿值");
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        if (normalized.abs().compareTo(OFFSET_LIMIT) > 0) {
            throw new IllegalArgumentException("补偿值超出允许范围（±" + OFFSET_LIMIT + "）");
        }
        return normalized;
    }

    /** 备注规范化（空串按 null；长度上限 255） */
    private static String normalizeRemark(String remark) {
        String text = trim(remark);
        if (text == null) {
            return null;
        }
        if (text.length() > REMARK_MAX) {
            throw new IllegalArgumentException("备注不能超过 " + REMARK_MAX + " 字");
        }
        return text;
    }

    /** 设备唯一校验（device 为匹配键，全局唯一） */
    private long countByDevice(String deviceId) {
        QueryWrapper<RainAdjust> wrapper = new QueryWrapper<>();
        wrapper.eq("\"device\"", deviceId);
        Long count = rainAdjustMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /** 新增审计字段（列均 NOT NULL，会话缺失时用配置兜底） */
    private void applyCreateAudit(RainAdjust entity) {
        UserContext user = UserContextHolder.currentUser();
        String operator = user != null && trim(user.getUserId()) != null
                ? user.getUserId() : fallbackOperator;
        String corp = user != null && trim(user.getCorpCode()) != null
                ? user.getCorpCode() : corpCode;
        LocalDateTime now = LocalDateTime.now();
        entity.setCorpCode(corp);
        entity.setCreatedAt(now);
        entity.setCreatedBy(operator);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operator);
        if (user == null) {
            log.warn("[雨量补偿] 当前请求无用户上下文，审计字段使用兜底值：operator={}, corp={}", operator, corp);
        }
    }

    /** 当前操作人（更新审计字段用） */
    private String currentOperatorId() {
        UserContext user = UserContextHolder.currentUser();
        if (user != null && trim(user.getUserId()) != null) {
            return user.getUserId();
        }
        log.warn("[雨量补偿] 当前请求无用户上下文，updated_by 使用兜底值：{}", fallbackOperator);
        return fallbackOperator;
    }

    // ==================== 视图翻译 ====================

    private static RainAdjustVO toVO(RainAdjust row) {
        RainAdjustVO vo = new RainAdjustVO();
        vo.setId(row.getId());
        vo.setSite(row.getSite());
        vo.setDevice(row.getDevice());
        vo.setStcd(row.getStcd());
        vo.setOffsetValue(row.getOffsetValue());
        vo.setEnabled(row.getEnabled());
        vo.setRemark(row.getRemark());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setCreatedBy(row.getCreatedBy());
        vo.setUpdatedAt(row.getUpdatedAt());
        vo.setUpdatedBy(row.getUpdatedBy());
        return vo;
    }

    /** 回填站点/设备展示名（档案缺失时不填，列表 SQL 侧已回退主键） */
    private static void fillNames(RainAdjustVO vo, RainAdjustVO.SiteOption site, RainAdjustVO.DeviceOption device) {
        if (site != null) {
            String name = trim(site.getStnm());
            vo.setSiteName(name != null ? name : vo.getSite());
            vo.setSiteCode(trim(site.getStcd()));
        }
        if (device != null) {
            String name = trim(device.getName());
            vo.setDeviceName(name != null ? name : vo.getDevice());
            vo.setDeviceCode(trim(device.getCode()));
        }
    }

    /** 设备类型编码 → 中文名（多值以「、」连接；字典外回退原编码） */
    private static String deviceTypeName(String raw) {
        List<String> names = new ArrayList<>();
        if (raw != null) {
            Matcher matcher = CODE_PATTERN.matcher(raw);
            while (matcher.find()) {
                String name = DEVICE_TYPES.get(matcher.group());
                if (name != null && !names.contains(name)) {
                    names.add(name);
                }
            }
        }
        if (!names.isEmpty()) {
            return String.join("、", names);
        }
        return trim(raw);
    }

    // ==================== 工具方法 ====================

    private static int normalizeSize(long size) {
        if (size < 1) {
            return PAGE_SIZE_DEFAULT;
        }
        return (int) Math.min(size, PAGE_SIZE_MAX);
    }

    /** 去首尾空白，空串按 null 处理 */
    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }
}
