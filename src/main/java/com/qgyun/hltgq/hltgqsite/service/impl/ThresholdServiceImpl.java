package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.entity.WaterThreshold;
import com.qgyun.hltgq.hltgqsite.mapper.StStinfoMapper;
import com.qgyun.hltgq.hltgqsite.mapper.WaterThresholdMapper;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.service.StationSortService;
import com.qgyun.hltgq.hltgqsite.service.ThresholdService;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.ThresholdMetaVO;
import com.qgyun.hltgq.hltgqsite.vo.ThresholdSaveVO;
import com.qgyun.hltgq.hltgqsite.vo.ThresholdSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.ThresholdVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阈值设置实现：单级警戒值维护（保证值/设计值不渲染，保存时不改写既有值）。
 * <p>类型字典与站点支持关系：类型编码与站点档案监测类型（epjutj）同一套编号，
 * 站点候选 = 档案中 epjutj 含该编码的站点；类型字典在 {@link #TYPES} 单一维护，
 * 新增类型只改此处（界面通过 /threshold/meta 自动获得）。
 * <p>站点顺序：候选站点与监测页站点下拉同源，按站点排序配置（/station-sort）中该类型序列的顺序排列
 * （见 {@link #sortScope}），未配置过顺序时保持按站名；列表筛选下拉只列已配置阈值的站点。
 * <p>唯一性：表无数据库唯一约束，判重由本类按「站点 + 类型 + 指标」在应用层完成（多值行如 #1#|#3# 也算已配置），
 * 与全库监测类型 LIKE 子串匹配规范一致；雨量/流量/开度指标恒为空（「站点 + 类型」唯一），
 * 水位普通站指标为空、闸站上下游合并为一条 gate_z 记录。
 * <p>监测指标（{@code zb}）：墒情 #7#、水质 #8# 一个类型下含多个指标，按「站点 + 类型 + 指标」各存一行；
 * 指标清单**按监测页实际展示口径开放**（墒情三层、水质七项，见 {@link #TYPES}），
 * 指标编码即监测数据表字段名，告警比对接该列到数据表取数。
 * <p>水位闸门上下游（逻辑指标 {@code gate_z}）：外部告警引擎按 {@code zb} 当闸门表列名逐列取数比对，
 * 库内**必须保持 up_z / down_z 两行**，不能合并存储；本类在接口层把同站点两行合并为一条
 * {@code gate_z} 记录（threshold=上游/闸前、thresholdDown=下游/闸后），页面一次设置两侧；
 * 保存时按侧 upsert（留空的一侧删除，至少保留一侧），删除整组一起删。
 * <p>字段口径：类型写 {@code zvieyb}（历史列 {@code type} 仅读取兜底），指标写 {@code zb}，方向写 {@code alarmdir}，
 * 警戒值写 {@code threshold}，描述写 {@code remark}；{@code guarantee}/{@code num} 不写。
 * <p>告警方向：类型/指标级默认值只作新增时的初始选中与 {@code alarmdir} 空值兜底，**业主可自行切换**；
 * 水位/雨量/流量/开度一律默认「高于」（不预设业务方向）；墒情「低于」= 旱情预警、水质仅溶解氧「低于」= 缺氧预警。
 */
@Service
public class ThresholdServiceImpl implements ThresholdService {

    private static final Logger log = LoggerFactory.getLogger(ThresholdServiceImpl.class);

    /** 告警方向编码：高于警戒值触发（仅作新增初始值与空值兜底，业主可切换） */
    private static final String DIR_ABOVE = "#1#";

    /** 告警方向编码：低于警戒值触发 */
    private static final String DIR_BELOW = "#2#";

    /** 水位类型编码 */
    private static final String TYPE_WATER_LEVEL = "#1#";

    /** 水位指标：通用水位（普通水位站，zb 落空，与存量口径一致） */
    private static final String IND_GENERAL = "general";

    /**
     * 水位指标：闸门上下游水位（逻辑指标，不落库）。
     * <p>库内仍按闸门表列名拆成 up_z / down_z 两行（外围告警引擎按 zb 取列比对），
     * 接口层合并为一条记录：threshold=上游（闸前）、thresholdDown=下游（闸后）。
     */
    private static final String IND_GATE = "gate_z";

    /** 水位指标：闸前水位（上游，落库编码 = 闸门表 up_z） */
    private static final String IND_UP_Z = "up_z";

    /** 水位指标：闸后水位（下游，落库编码 = 闸门表 down_z） */
    private static final String IND_DOWN_Z = "down_z";

    /** 类型编码提取（多值形如 #1#|#3#） */
    private static final Pattern CODE_PATTERN = Pattern.compile("#\\d+#");

    /** 阈值类型字典（键序即界面下拉顺序）：编码 → 名称 / 单位 / 默认方向 / 适用站点说明 / 监测指标清单 */
    private static final Map<String, ThresholdMetaVO.TypeItem> TYPES = new LinkedHashMap<>();

    static {
        // 水位：普通水位站用「通用水位」（zb 空，与存量「站点+类型」判重口径一致）；
        // 闸门站用「闸门上下游水位」一条记录同时设置上游（闸前）/下游（闸后），
        // 保存时拆成 up_z/down_z 两行入库，供告警引擎按列取数比对
        put(TYPE_WATER_LEVEL, "水位", "m", DIR_ABOVE, "河道站、水库站（通用水位）；闸站（闸门上下游水位）",
                indicator(IND_GENERAL, "通用水位", "m", DIR_ABOVE),
                indicator(IND_GATE, "闸门上下游水位", "m", DIR_ABOVE));
        put("#2#", "雨量", "mm", DIR_ABOVE, "雨量站");
        put("#3#", "流量", "m³/s", DIR_ABOVE, "测流闸、河道站");
        put("#4#", "开度", "m", DIR_ABOVE, "泄洪闸、节制闸、进水闸");
        // 墒情：按墒情页实际展示口径开放 10/20/30cm 三层（数据表另有 40~100cm，页面不展示故不配置）
        put("#7#", "墒情", "%", DIR_BELOW, "墒情站",
                indicator("mten", "10cm含水量", "%", DIR_BELOW),
                indicator("mtwenty", "20cm含水量", "%", DIR_BELOW),
                indicator("mthirty", "30cm含水量", "%", DIR_BELOW));
        // 水质：按水质页实际展示口径开放七项（氨氮/CODMn/BOD₅/总磷/总氮/溶解氧/水温），
        // 浓度类越高越差（高于），溶解氧越低越危险（低于）
        put("#8#", "水质", "mg/L", DIR_ABOVE, "水质监测站",
                indicator("nh3n", "氨氮", "mg/L", DIR_ABOVE),
                indicator("codmn", "高锰酸盐指数", "mg/L", DIR_ABOVE),
                indicator("bod5", "BOD₅", "mg/L", DIR_ABOVE),
                indicator("tp", "总磷", "mg/L", DIR_ABOVE),
                indicator("tn", "总氮", "mg/L", DIR_ABOVE),
                indicator("dox", "溶解氧", "mg/L", DIR_BELOW),
                indicator("wt", "水温", "℃", DIR_ABOVE));
    }

    /** 描述最大长度（与界面字数统计一致） */
    private static final int DESC_MAX = 500;

    private static final int PAGE_SIZE_DEFAULT = 20;

    private static final int PAGE_SIZE_MAX = 200;

    @Autowired
    private WaterThresholdMapper thresholdMapper;

    @Autowired
    private StStinfoMapper stStinfoMapper;

    /** 站点展示顺序（站点排序配置，与各监测页站点下拉同源） */
    @Autowired
    private StationSortService stationSortService;

    /** 企业编码兜底（会话缺失时使用，列 NOT NULL） */
    @Value("${hltgq.corp-code}")
    private String corpCode;

    /** 操作人兜底（会话缺失时使用，列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    @Override
    public ThresholdMetaVO meta() {
        ThresholdMetaVO vo = new ThresholdMetaVO();
        vo.setTypes(new ArrayList<>(TYPES.values()));
        List<ThresholdMetaVO.OptionItem> dirs = new ArrayList<>();
        dirs.add(option(DIR_ABOVE, "高于警戒值触发"));
        dirs.add(option(DIR_BELOW, "低于警戒值触发"));
        vo.setAlarmDirs(dirs);
        return vo;
    }

    @Override
    public List<ThresholdSiteVO> sites(String type, String keyword, String indicator) {
        String typeCode = requireType(type);
        // 指标可选：多指标类型下传了指标，「已配置」标记就按该指标判定（未传则按类型）
        List<ThresholdSiteVO> rows = thresholdMapper.selectCandidateSites(typeCode, trim(keyword), trim(indicator));
        // 站点顺序与监测页站点下拉同源：按该类型对应序列的站点排序配置排列（未配置过顺序时保持按站名）
        return stationSortService.applyOrder(sortScope(typeCode), rows, ThresholdSiteVO::getSiteId);
    }

    @Override
    public List<ThresholdSiteVO> configuredSites() {
        return thresholdMapper.selectConfiguredSites();
    }

    @Override
    public Map<String, Object> page(int page, int size, String siteId, String type, String keyword) {
        int current = page < 1 ? 1 : page;
        int pageSize = normalizeSize(size);
        // 先查全量行并合并闸门上下游（同站点 up_z/down_z 两行 → 一条 gate_z 记录），再内存分页，
        // 避免两行被拆到相邻两页、也保证 total 与列表条数一致
        List<ThresholdVO> merged = mergeRows(thresholdMapper.selectFilteredRows(
                trim(siteId), optionalType(type), trim(keyword)));
        int total = merged.size();
        int pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int from = Math.min((current - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("page", current);
        out.put("size", pageSize);
        out.put("pages", pages);
        out.put("records", new ArrayList<>(merged.subList(from, to)));
        return out;
    }

    @Override
    public ThresholdVO detail(String id) {
        WaterThreshold row = requireRow(id);
        if (isGateMember(row)) {
            return gatePairVO(gateMembers(row.getSite()));
        }
        return toVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThresholdVO create(ThresholdSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String type = requireType(req.getType());
        String indicator = normalizeIndicator(type, req.getIndicator());
        StStinfo site = requireSupportedSite(req.getSite(), type, indicator);
        String alarmDir = requireAlarmDir(req.getAlarmDir(), type,
                IND_GATE.equals(indicator) ? IND_GATE : indicator);
        String desc = normalizeDesc(req.getDescription());

        if (IND_GATE.equals(indicator)) {
            // 闸门上下游：一条记录同时设置两侧，至少填一侧；落库仍拆成 up_z/down_z 两行
            BigDecimal up = optionalThreshold(req.getThreshold());
            BigDecimal down = optionalThreshold(req.getThresholdDown());
            if (up == null && down == null) {
                throw new IllegalArgumentException("请至少填写上游（闸前）或下游（闸后）警戒值");
            }
            if (countBySiteAndType(site.getId(), type, indicator) > 0) {
                throw new IllegalArgumentException("该站点已配置【" + displayName(type, indicator)
                        + "】阈值，请直接编辑");
            }
            WaterThreshold upRow = insertRow(site.getId(), type, IND_UP_Z, up, alarmDir, desc);
            WaterThreshold downRow = insertRow(site.getId(), type, IND_DOWN_Z, down, alarmDir, desc);
            log.info("[阈值设置] 新增（闸门上下游）：站点={}（{}），上游={}，下游={}，方向={}，操作人={}",
                    trim(site.getStnm()), site.getId(), up, down, alarmDir,
                    upRow != null ? upRow.getUpdatedBy() : downRow.getUpdatedBy());
            return gatePairVO(gateMembers(site.getId()));
        }

        BigDecimal value = requireThreshold(req.getThreshold());

        if (countBySiteAndType(site.getId(), type, indicator) > 0) {
            throw new IllegalArgumentException("该站点已配置【" + displayName(type, indicator) + "】阈值，请直接编辑");
        }

        WaterThreshold entity = new WaterThreshold();
        entity.setSite(site.getId());
        entity.setZvieyb(type);
        entity.setZb(indicator);
        entity.setAlarmdir(alarmDir);
        entity.setThreshold(value);
        entity.setRemark(desc);
        applyCreateAudit(entity);
        thresholdMapper.insert(entity);
        log.info("[阈值设置] 新增：站点={}（{}），类型={}，指标={}，方向={}，警戒值={}，操作人={}",
                trim(site.getStnm()), site.getId(), displayName(type, indicator), indicator, alarmDir, value,
                entity.getUpdatedBy());
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThresholdVO update(String id, ThresholdSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        WaterThreshold row = requireRow(id);
        // 站点、类型与指标锁定：换类型 / 换站点 / 换指标 = 删除后重新新增，故只取库中值
        String type = effectiveType(row.getZvieyb(), row.getType());
        String indicator = trim(row.getZb());
        String operator = currentOperatorId();

        if (isGateMember(row)) {
            String alarmDir = requireAlarmDir(req.getAlarmDir(), type, IND_GATE);
            String desc = normalizeDesc(req.getDescription());
            return updateGatePair(row, type, req, alarmDir, desc, operator);
        }

        BigDecimal value = requireThreshold(req.getThreshold());
        String alarmDir = requireAlarmDir(req.getAlarmDir(), type, indicator);
        String desc = normalizeDesc(req.getDescription());

        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<WaterThreshold> wrapper = new UpdateWrapper<>();
        wrapper.eq("\"id\"", row.getId());
        wrapper.set("\"alarmdir\"", alarmDir);
        wrapper.set("\"threshold\"", value);
        // 描述允许清空，显式 set null（MyBatis-Plus 默认忽略 null 字段）
        wrapper.set("\"remark\"", desc);
        wrapper.set("\"updated_at\"", now);
        wrapper.set("\"updated_by\"", operator);
        // 类型仍在旧列 type 的历史行：保存时把类型规范化到 zvieyb（值不变），
        // 保证告警引擎与下游查询只按 zvieyb 一个口径读取
        boolean migrateType = trim(row.getZvieyb()) == null && type != null;
        if (migrateType) {
            wrapper.set("\"zvieyb\"", type);
        }
        thresholdMapper.update(null, wrapper);

        WaterThreshold updated = thresholdMapper.selectById(row.getId());
        log.info("[阈值设置] 编辑：id={}，站点={}，类型={}，指标={}，方向={}，警戒值={}，类型列规范化={}，操作人={}",
                row.getId(), row.getSite(), type, indicator, alarmDir, value, migrateType, operator);
        return toVO(updated != null ? updated : row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        WaterThreshold row = requireRow(id);
        if (isGateMember(row)) {
            // 闸门上下游是一条逻辑记录：上游/下游两行一起删，避免留下只有一侧的“半条”配置
            QueryWrapper<WaterThreshold> wrapper = new QueryWrapper<>();
            wrapper.eq("\"site\"", row.getSite());
            wrapper.apply("COALESCE(NULLIF(\"zvieyb\", ''), \"type\", '') LIKE {0}", "%" + TYPE_WATER_LEVEL + "%");
            wrapper.in("\"zb\"", Arrays.asList(IND_UP_Z, IND_DOWN_Z));
            int removed = thresholdMapper.delete(wrapper);
            log.info("[阈值设置] 删除（闸门上下游）：站点={}，删除行数={}，操作人={}",
                    row.getSite(), removed, currentOperatorId());
            return;
        }
        thresholdMapper.deleteById(row.getId());
        log.info("[阈值设置] 删除：id={}，站点={}，类型={}，指标={}，操作人={}",
                row.getId(), row.getSite(), effectiveType(row.getZvieyb(), row.getType()), trim(row.getZb()),
                currentOperatorId());
    }

    // ==================== 校验与组装 ====================

    /**
     * 闸门上下游单侧保存：有值→更新已有行（无行则补插）；为空→删除该侧行。
     * <p>两侧共用同一告警方向与描述；类型仍在旧列 type 的历史行一并规范化到 zvieyb。
     */
    private void saveGateSide(String siteId, String type, String indicator, WaterThreshold existing,
                              BigDecimal value, String alarmDir, String desc, String operator) {
        if (value == null) {
            if (existing != null) {
                thresholdMapper.deleteById(existing.getId());
            }
            return;
        }
        if (existing == null) {
            insertRow(siteId, type, indicator, value, alarmDir, desc);
            return;
        }
        UpdateWrapper<WaterThreshold> wrapper = new UpdateWrapper<>();
        wrapper.eq("\"id\"", existing.getId());
        wrapper.set("\"alarmdir\"", alarmDir);
        wrapper.set("\"threshold\"", value);
        // 描述允许清空，显式 set null（MyBatis-Plus 默认忽略 null 字段）
        wrapper.set("\"remark\"", desc);
        wrapper.set("\"updated_at\"", LocalDateTime.now());
        wrapper.set("\"updated_by\"", operator);
        if (trim(existing.getZvieyb()) == null) {
            wrapper.set("\"zvieyb\"", type);
        }
        thresholdMapper.update(null, wrapper);
    }

    /**
     * 编辑闸门上下游记录：请求的 threshold=上游（闸前）、thresholdDown=下游（闸后）。
     * <p>按侧 upsert：有值则更新（无行则补插），留空则删除该侧，两侧不能同时留空。
     */
    private ThresholdVO updateGatePair(WaterThreshold row, String type, ThresholdSaveVO req,
                                       String alarmDir, String desc, String operator) {
        BigDecimal up = optionalThreshold(req.getThreshold());
        BigDecimal down = optionalThreshold(req.getThresholdDown());
        if (up == null && down == null) {
            throw new IllegalArgumentException("请至少填写上游（闸前）或下游（闸后）警戒值");
        }
        WaterThreshold upRow = null;
        WaterThreshold downRow = null;
        for (WaterThreshold member : gateMembers(row.getSite())) {
            if (IND_UP_Z.equals(trim(member.getZb()))) {
                upRow = member;
            } else if (IND_DOWN_Z.equals(trim(member.getZb()))) {
                downRow = member;
            }
        }
        saveGateSide(row.getSite(), type, IND_UP_Z, upRow, up, alarmDir, desc, operator);
        saveGateSide(row.getSite(), type, IND_DOWN_Z, downRow, down, alarmDir, desc, operator);
        log.info("[阈值设置] 编辑（闸门上下游）：站点={}，上游={}，下游={}，方向={}，操作人={}",
                row.getSite(), up, down, alarmDir, operator);
        return gatePairVO(gateMembers(row.getSite()));
    }

    /** 插入一行阈值；value 为空表示该侧不配置，直接返回 null */
    private WaterThreshold insertRow(String siteId, String type, String indicator,
                                     BigDecimal value, String alarmDir, String desc) {
        if (value == null) {
            return null;
        }
        WaterThreshold entity = new WaterThreshold();
        entity.setSite(siteId);
        entity.setZvieyb(type);
        entity.setZb(indicator);
        entity.setAlarmdir(alarmDir);
        entity.setThreshold(value);
        entity.setRemark(desc);
        applyCreateAudit(entity);
        thresholdMapper.insert(entity);
        return entity;
    }

    /** 该站点的闸门上下游成员行（up_z/down_z，按更新时间倒序，首行为最新）；可能只有一侧 */
    private List<WaterThreshold> gateMembers(String siteId) {
        QueryWrapper<WaterThreshold> wrapper = new QueryWrapper<>();
        wrapper.eq("\"site\"", siteId);
        wrapper.apply("COALESCE(NULLIF(\"zvieyb\", ''), \"type\", '') LIKE {0}", "%" + TYPE_WATER_LEVEL + "%");
        wrapper.in("\"zb\"", Arrays.asList(IND_UP_Z, IND_DOWN_Z));
        wrapper.orderByDesc("\"updated_at\"").orderByAsc("\"id\"");
        return thresholdMapper.selectList(wrapper);
    }

    /** 是否闸门上下游成员行（水位类型且 zb=up_z/down_z）：库内是两行，接口层按一条记录处理 */
    private static boolean isGateMember(WaterThreshold row) {
        if (row == null || parseCodes(effectiveType(row.getZvieyb(), row.getType()))
                .indexOf(TYPE_WATER_LEVEL) < 0) {
            return false;
        }
        String zb = trim(row.getZb());
        return IND_UP_Z.equals(zb) || IND_DOWN_Z.equals(zb);
    }

    /** 列表行是否闸门上下游成员（口径同 {@link #isGateMember(WaterThreshold)}） */
    private static boolean isGateMember(ThresholdVO row) {
        if (row == null || parseCodes(row.getThresholdType()).indexOf(TYPE_WATER_LEVEL) < 0) {
            return false;
        }
        String zb = trim(row.getIndicator());
        return IND_UP_Z.equals(zb) || IND_DOWN_Z.equals(zb);
    }

    /**
     * 列表行合并：同站点的闸门上下游两行合并为一条 gate_z 记录，其余行原样翻译。
     * <p>入参按 updated_at 倒序（组内首行为最新行），合并记录位置即组内最新行的位置；
     * id 取下游行优先（列表与详情同一规则，保存后高亮能命中）。
     */
    private List<ThresholdVO> mergeRows(List<ThresholdVO> rows) {
        Map<String, ThresholdVO> grouped = new LinkedHashMap<>();
        for (ThresholdVO row : rows) {
            if (!isGateMember(row)) {
                decorate(row);
                grouped.put("id:" + row.getId(), row);
                continue;
            }
            String key = "gate:" + trim(row.getSite());
            ThresholdVO pair = grouped.get(key);
            if (pair == null) {
                // 组内首行为最新行：描述/方向/更新时间以它为准，警戒值随后按侧填充
                pair = new ThresholdVO();
                pair.setId(row.getId());
                pair.setSite(row.getSite());
                pair.setSiteName(row.getSiteName());
                pair.setSiteCode(row.getSiteCode());
                pair.setThresholdType(row.getThresholdType());
                pair.setDescription(row.getDescription());
                pair.setAlarmDir(row.getAlarmDir());
                pair.setUpdatedAt(row.getUpdatedAt());
                pair.setIndicator(IND_GATE);
                grouped.put(key, pair);
            }
            String zb = trim(row.getIndicator());
            if (IND_DOWN_Z.equals(zb)) {
                pair.setThresholdDown(row.getThreshold());
                pair.setId(row.getId());
            } else {
                pair.setThreshold(row.getThreshold());
            }
        }
        for (ThresholdVO vo : grouped.values()) {
            if (IND_GATE.equals(vo.getIndicator())) {
                decorate(vo);
            }
        }
        return new ArrayList<>(grouped.values());
    }

    /**
     * 闸门上下游成员行 → 合并视图（一条记录）：指标统一为 gate_z，
     * threshold=上游（闸前）、thresholdDown=下游（闸后）；
     * id 取下游行优先（无下游取上游），描述/方向/更新时间取组内最新成员（入参按 updated_at 倒序）。
     */
    private ThresholdVO gatePairVO(List<WaterThreshold> members) {
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("阈值记录不存在或已删除");
        }
        ThresholdVO vo = toVO(members.get(0));
        vo.setIndicator(IND_GATE);
        vo.setThreshold(null);
        vo.setThresholdDown(null);
        for (WaterThreshold member : members) {
            String zb = trim(member.getZb());
            if (IND_DOWN_Z.equals(zb)) {
                vo.setThresholdDown(member.getThreshold());
                vo.setId(member.getId());
            } else if (IND_UP_Z.equals(zb)) {
                vo.setThreshold(member.getThreshold());
            }
        }
        decorate(vo);
        return vo;
    }

    /** 阈值行存在性校验 */
    private WaterThreshold requireRow(String id) {
        String key = trim(id);
        if (key == null) {
            throw new IllegalArgumentException("阈值记录 id 不能为空");
        }
        WaterThreshold row = thresholdMapper.selectById(key);
        if (row == null) {
            throw new IllegalArgumentException("阈值记录不存在或已删除");
        }
        return row;
    }

    /**
     * 同站点同类型同指标是否已配置（多值行如 #1#|#3# 同样视为已配置）。
     * <p>雨量/流量/开度与水位通用水位指标为空，判重退化为「站点 + 类型」；
     * 水位闸门上下游（gate_z）任一侧已落库（up_z 或 down_z）即算已配置，保证一条逻辑记录。
     */
    private long countBySiteAndType(String siteId, String type, String indicator) {
        QueryWrapper<WaterThreshold> wrapper = new QueryWrapper<>();
        wrapper.eq("\"site\"", siteId);
        wrapper.apply("COALESCE(NULLIF(\"zvieyb\", ''), \"type\", '') LIKE {0}", "%" + type + "%");
        if (IND_GATE.equals(indicator)) {
            wrapper.in("\"zb\"", Arrays.asList(IND_UP_Z, IND_DOWN_Z));
        } else if (indicator == null) {
            wrapper.apply("COALESCE(\"zb\", '') = ''");
        } else {
            wrapper.eq("\"zb\"", indicator);
        }
        Long count = thresholdMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /**
     * 站点校验：必须存在于站点档案且档案监测类型含该阈值类型。
     * <p>水位闸门上下游（gate_z）例外：闸门站档案多不含 #1#（多为 #3#|#4#|#5#），
     * 以闸门表 up_z/down_z 是否存在有效数据为准；通用水位仍要求档案含 #1# 且闸门表无有效水位数据，
     * 避免同一闸站出现「通用」与「闸门上下游」两套取数口径（含 #5# 但无闸门水位的支渠不受影响）。
     */
    private StStinfo requireSupportedSite(String siteId, String type, String indicator) {
        String key = trim(siteId);
        if (key == null) {
            throw new IllegalArgumentException("请选择站点");
        }
        QueryWrapper<StStinfo> wrapper = new QueryWrapper<>();
        wrapper.eq("\"id\"", key);
        wrapper.last("LIMIT 1");
        StStinfo site = stStinfoMapper.selectOne(wrapper);
        if (site == null) {
            throw new IllegalArgumentException("站点不存在或未在站点档案登记");
        }
        if (TYPE_WATER_LEVEL.equals(type)) {
            if (IND_GATE.equals(indicator)) {
                if (thresholdMapper.countEffectiveUpZ(key) <= 0 && thresholdMapper.countEffectiveDownZ(key) <= 0) {
                    throw new IllegalArgumentException("该站点无【闸门上下游水位】监测数据，不能配置该指标");
                }
                return site;
            }
            if (site.getEpjutj() == null || !site.getEpjutj().contains(TYPE_WATER_LEVEL)) {
                throw new IllegalArgumentException("该站点不支持【水位】阈值");
            }
            // 以闸门表实际水位数据判定闸门站：含 #5# 但无闸门水位的支渠/渡槽仍可配通用水位
            if (thresholdMapper.countEffectiveUpZ(key) > 0 || thresholdMapper.countEffectiveDownZ(key) > 0) {
                throw new IllegalArgumentException("闸门站请选择【闸门上下游水位】配置");
            }
            return site;
        }
        if (site.getEpjutj() == null || !site.getEpjutj().contains(type)) {
            throw new IllegalArgumentException("该站点不支持【" + TYPES.get(type).getName() + "】阈值");
        }
        return site;
    }

    /** 类型必填校验（字典内） */
    private static String requireType(String type) {
        String code = trim(type);
        if (code == null) {
            throw new IllegalArgumentException("请选择阈值类型");
        }
        if (!TYPES.containsKey(code)) {
            throw new IllegalArgumentException("阈值类型无效：" + type);
        }
        return code;
    }

    /** 类型可选校验（列表筛选用）：空值返回 null，非空必须在字典内 */
    private static String optionalType(String type) {
        String code = trim(type);
        return code == null ? null : requireType(code);
    }

    /**
     * 监测指标校验：多指标类型（墒情 #7#、水质 #8#）必填且必须在字典内；
     * 水位 #1# 的「通用水位」（空/general）落空，闸门上下游统一归一化为逻辑指标 gate_z
     * （兼容旧参数 up_z/down_z，落库时再拆回两行）；
     * 雨量/流量/开度类型本身已唯一确定指标，请求带值也归一化为空。
     *
     * @return 落库/接口使用的指标编码（通用水位与单指标类型为 null）
     */
    private static String normalizeIndicator(String typeCode, String indicator) {
        ThresholdMetaVO.TypeItem item = TYPES.get(typeCode);
        if (TYPE_WATER_LEVEL.equals(typeCode)) {
            String code = trim(indicator);
            if (code == null || IND_GENERAL.equals(code)) {
                return null;
            }
            if (IND_GATE.equals(code) || IND_UP_Z.equals(code) || IND_DOWN_Z.equals(code)) {
                return IND_GATE;
            }
            throw new IllegalArgumentException("【" + (item == null ? "水位" : item.getName())
                    + "】不支持监测指标：" + indicator);
        }
        if (item == null || item.getIndicators() == null || item.getIndicators().isEmpty()) {
            return null;
        }
        String code = trim(indicator);
        if (code == null) {
            throw new IllegalArgumentException("请选择监测指标");
        }
        if (findIndicator(typeCode, code) == null) {
            throw new IllegalArgumentException("【" + item.getName() + "】不支持监测指标：" + indicator);
        }
        return code;
    }

    /** 类型下的指标项（类型无指标或不匹配返回 null） */
    private static ThresholdMetaVO.IndicatorItem findIndicator(String typeCode, String indicator) {
        String key = trim(indicator);
        ThresholdMetaVO.TypeItem item = TYPES.get(typeCode);
        if (key == null || item == null || item.getIndicators() == null) {
            return null;
        }
        for (ThresholdMetaVO.IndicatorItem ind : item.getIndicators()) {
            if (key.equals(ind.getCode())) {
                return ind;
            }
        }
        return null;
    }

    /** 多指标类型下的指标项：类型串可多值，逐编码匹配（命中即返回） */
    private static ThresholdMetaVO.IndicatorItem matchIndicator(String typeRaw, String indicator) {
        if (trim(indicator) == null) {
            return null;
        }
        for (String code : parseCodes(typeRaw)) {
            ThresholdMetaVO.IndicatorItem ind = findIndicator(code, indicator.trim());
            if (ind != null) {
                return ind;
            }
        }
        return null;
    }

    /** 类型与指标的组合名（单指标类型即类型名；列表翻译与冲突提示共用） */
    private static String displayName(String typeCode, String indicator) {
        ThresholdMetaVO.TypeItem item = TYPES.get(typeCode);
        String typeName = item == null ? typeCode : item.getName();
        ThresholdMetaVO.IndicatorItem ind = findIndicator(typeCode, indicator);
        // 水位未落指标即通用水位：冲突提示给出明确口径，避免与闸前/闸后混淆
        if (ind == null && indicator == null && TYPE_WATER_LEVEL.equals(typeCode)) {
            ind = findIndicator(typeCode, IND_GENERAL);
        }
        return ind == null ? typeName : typeName + "·" + ind.getName();
    }

    /**
     * 阈值类型 → 站点排序序列（/station-sort 的 type 值域）：与监测页站点下拉共用同一套顺序。
     * <p>编码对应关系：开度阈值即闸站（#4# 与闸门序列同编码）；墒情阈值编码为 #7#，
     * 站点排序序列编码为 #5#，语义相同故在此映射。
     * <p>未在映射内的类型（如 #8# 水质，站点排序侧无对应序列）返回 null，
     * applyOrder 按未配置处理（保持接口默认顺序，即按站名）。
     */
    private static String sortScope(String typeCode) {
        if (typeCode == null) return null;
        switch (typeCode) {
            case "#1#": return "waterLevel";
            case "#2#": return "rainfall";
            case "#3#": return "flow";
            case "#4#": return "gate";
            case "#7#": return "moisture";
            default: return null;
        }
    }

    /**
     * 告警方向：未选时按指标默认方向落库（无指标或指标无默认方向时按类型默认方向，与界面初始值一致）。
     */
    private static String requireAlarmDir(String alarmDir, String type, String indicator) {
        String dir = trim(alarmDir);
        if (dir == null) {
            ThresholdMetaVO.IndicatorItem ind = findIndicator(type, indicator);
            if (ind != null && trim(ind.getDefaultAlarmDir()) != null) {
                return ind.getDefaultAlarmDir();
            }
            ThresholdMetaVO.TypeItem item = TYPES.get(type);
            return item != null ? item.getDefaultAlarmDir() : DIR_ABOVE;
        }
        if (!DIR_ABOVE.equals(dir) && !DIR_BELOW.equals(dir)) {
            throw new IllegalArgumentException("告警方向无效：" + alarmDir);
        }
        return dir;
    }

    /** 警戒值：必填且大于 0；统一保留两位小数，避免与界面展示不一致 */
    private static BigDecimal requireThreshold(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("请输入大于 0 的数字");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** 可选警戒值（闸门上下游按侧选填）：为空返回 null；非空按 {@link #requireThreshold} 校验 */
    private static BigDecimal optionalThreshold(BigDecimal value) {
        return value == null ? null : requireThreshold(value);
    }

    /** 描述：去首尾空白，空串按 null 落库，超长报错 */
    private static String normalizeDesc(String desc) {
        String text = desc == null ? null : desc.trim();
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.length() > DESC_MAX) {
            throw new IllegalArgumentException("描述最多 " + DESC_MAX + " 字");
        }
        return text;
    }

    /** 生效类型：zvieyb 优先，历史列 type 兜底（多值取首个编码） */
    private static String effectiveType(String zvieyb, String type) {
        String primary = trim(zvieyb);
        if (primary != null) {
            return primary;
        }
        List<String> codes = parseCodes(type);
        if (!codes.isEmpty()) {
            return codes.get(0);
        }
        return trim(type);
    }

    /** 新增审计字段（列均 NOT NULL，会话缺失时用配置兜底） */
    private void applyCreateAudit(WaterThreshold entity) {
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
            log.warn("[阈值设置] 当前请求无用户上下文，审计字段使用兜底值：operator={}, corp={}", operator, corp);
        }
    }

    /** 当前操作人（更新审计字段用） */
    private String currentOperatorId() {
        UserContext user = UserContextHolder.currentUser();
        if (user != null && trim(user.getUserId()) != null) {
            return user.getUserId();
        }
        log.warn("[阈值设置] 当前请求无用户上下文，updated_by 使用兜底值：{}", fallbackOperator);
        return fallbackOperator;
    }

    // ==================== 视图翻译 ====================

    /** 实体 → 视图（含站点名称/编号、类型名、方向名） */
    private ThresholdVO toVO(WaterThreshold row) {
        ThresholdVO vo = new ThresholdVO();
        vo.setId(row.getId());
        vo.setSite(row.getSite());
        vo.setThresholdType(effectiveType(row.getZvieyb(), row.getType()));
        vo.setIndicator(trim(row.getZb()));
        vo.setAlarmDir(trim(row.getAlarmdir()));
        vo.setThreshold(row.getThreshold());
        vo.setDescription(row.getRemark());
        vo.setUpdatedAt(row.getUpdatedAt());
        fillSite(vo);
        decorate(vo);
        return vo;
    }

    /** 补齐站点名称与编号（档案缺失时站名回退站点主键） */
    private void fillSite(ThresholdVO vo) {
        String siteId = trim(vo.getSite());
        if (siteId == null) {
            return;
        }
        List<StationSiteVO> archives = stStinfoMapper.selectArchiveSites(Collections.singletonList(siteId));
        for (StationSiteVO archive : archives) {
            if (!siteId.equals(trim(archive.getSiteId()))) {
                continue;
            }
            String name = trim(archive.getName());
            vo.setSiteName(name != null ? name : siteId);
            vo.setSiteCode(trim(archive.getStcd()));
            return;
        }
        vo.setSiteName(siteId);
    }

    /** 编码 → 中文名 / 单位（多值类型以「、」连接；多指标类型按指标取单位与默认方向；字典外编码名称回退原编码） */
    private static void decorate(ThresholdVO vo) {
        List<String> names = new ArrayList<>();
        String unit = null;
        String defaultDir = null;
        for (String code : parseCodes(vo.getThresholdType())) {
            ThresholdMetaVO.TypeItem item = TYPES.get(code);
            if (item == null) {
                continue;
            }
            names.add(item.getName());
            if (unit == null) {
                unit = item.getUnit();
            }
            if (defaultDir == null) {
                defaultDir = item.getDefaultAlarmDir();
            }
        }
        // 多指标类型：指标名与单位/默认方向按指标取值（指标为空时保持类型级，兼容单指标存量行）
        ThresholdMetaVO.IndicatorItem ind = matchIndicator(vo.getThresholdType(), vo.getIndicator());
        // 水位类型未落指标 = 通用水位（存量普通水位站口径），补中文名供列表/详情展示
        if (ind == null && trim(vo.getIndicator()) == null
                && parseCodes(vo.getThresholdType()).contains(TYPE_WATER_LEVEL)) {
            ind = findIndicator(TYPE_WATER_LEVEL, IND_GENERAL);
        }
        if (ind != null) {
            vo.setIndicatorName(ind.getName());
            if (trim(ind.getUnit()) != null) {
                unit = ind.getUnit();
            }
            if (trim(ind.getDefaultAlarmDir()) != null) {
                defaultDir = ind.getDefaultAlarmDir();
            }
        }
        if (!names.isEmpty()) {
            vo.setTypeName(String.join("、", names));
            vo.setUnit(unit);
        } else if (trim(vo.getThresholdType()) != null) {
            vo.setTypeName(vo.getThresholdType());
        }
        // 存量行未落方向（alarmdir 为空）：按类型默认方向展示（与告警引擎口径一致），界面加「默认」标记
        String dir = trim(vo.getAlarmDir());
        if (dir == null) {
            dir = defaultDir;
            vo.setAlarmDir(dir);
            vo.setAlarmDirDefault(dir != null);
        }
        vo.setAlarmDirName(alarmDirName(dir));
    }

    private static String alarmDirName(String code) {
        if (DIR_ABOVE.equals(code)) {
            return "高于警戒值触发";
        }
        if (DIR_BELOW.equals(code)) {
            return "低于警戒值触发";
        }
        return null;
    }

    // ==================== 工具方法 ====================

    /** 提取多值编码（形如 #1#|#3# → [#1#, #3#]，去重保序） */
    private static List<String> parseCodes(String raw) {
        List<String> codes = new ArrayList<>();
        if (raw == null) {
            return codes;
        }
        Matcher matcher = CODE_PATTERN.matcher(raw);
        while (matcher.find()) {
            if (!codes.contains(matcher.group())) {
                codes.add(matcher.group());
            }
        }
        return codes;
    }

    private static ThresholdMetaVO.OptionItem option(String code, String name) {
        ThresholdMetaVO.OptionItem item = new ThresholdMetaVO.OptionItem();
        item.setCode(code);
        item.setName(name);
        return item;
    }

    private static void put(String code, String name, String unit, String defaultAlarmDir, String siteScopeDesc,
                            ThresholdMetaVO.IndicatorItem... indicators) {
        ThresholdMetaVO.TypeItem item = new ThresholdMetaVO.TypeItem();
        item.setCode(code);
        item.setName(name);
        item.setUnit(unit);
        item.setDefaultAlarmDir(defaultAlarmDir);
        item.setSiteScopeDesc(siteScopeDesc);
        List<ThresholdMetaVO.IndicatorItem> list = new ArrayList<>();
        if (indicators != null) {
            Collections.addAll(list, indicators);
        }
        item.setIndicators(list);
        TYPES.put(code, item);
    }

    /** 指标字典项（编码 = 监测数据表字段名，告警比对接该列取数） */
    private static ThresholdMetaVO.IndicatorItem indicator(String code, String name, String unit, String defaultAlarmDir) {
        ThresholdMetaVO.IndicatorItem item = new ThresholdMetaVO.IndicatorItem();
        item.setCode(code);
        item.setName(name);
        item.setUnit(unit);
        item.setDefaultAlarmDir(defaultAlarmDir);
        return item;
    }

    private static int normalizeSize(int size) {
        if (size < 1) {
            return PAGE_SIZE_DEFAULT;
        }
        return Math.min(size, PAGE_SIZE_MAX);
    }

    /** 去首尾空白，空串按 null 处理（委托 CheckUtils） */
    private static String trim(String value) {
        return CheckUtils.trim(value);
    }
}
