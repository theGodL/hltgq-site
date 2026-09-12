package com.qgyun.hltgq.hltgqsite.watersaving.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qgyun.hltgq.hltgqsite.auth.SessionContextService;
import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.entity.WaterSavingPotential;
import com.qgyun.hltgq.hltgqsite.stationdetail.client.FileClient;
import com.qgyun.hltgq.hltgqsite.watersaving.mapper.WaterSavingPotentialMapper;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.WaterSavingPotentialSaveRequest;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.WaterSavingPotentialVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节水潜力测算服务（t_auto_hltgq_water_saving_potential）。
 *
 * <p>一行 = 一个「年度 + 分析范围」的测算（页面：static/water-saving-potential.html）。
 * 结果指标（节水总量/率/供需缺口/占比）由前端按记录数值实时计算，不在本服务口径内。
 *
 * <p>关键口径：
 * <ul>
 *   <li>年度匹配：year 列按区间 [该年 1-1, 次年 1-1) 查询，兼容平台日期控件写入的任意日期值；
 *       本服务写入时规范化为该年 1 月 1 日 00:00:00</li>
 *   <li>保存为 upsert：同「年度 + 分析范围」已存在即整行覆盖（含空值清空），否则新增；
 *       同键多行（平台人工重复录入）时取 updated_at 最新一行</li>
 *   <li>附件回显为尽力模式：basis_file 为单个文件引用时经文件服务换签名地址（basisFileUrl），
 *       引用为空 / 复合引用 / 无会话 / 服务失败降级为 null（前端保留引用原文），不阻断读取</li>
 *   <li>status 缺省 #2# 已测算</li>
 * </ul>
 */
@Service
public class WaterSavingPotentialService {

    private static final Logger log = LoggerFactory.getLogger(WaterSavingPotentialService.class);

    /** 分析范围编码 → 名称（页面下拉 #1#~#5#） */
    private static final Map<String, String> SCOPE_NAMES = new LinkedHashMap<>();

    static {
        SCOPE_NAMES.put("#1#", "全灌区");
        SCOPE_NAMES.put("#2#", "太湖县");
        SCOPE_NAMES.put("#3#", "望江县");
        SCOPE_NAMES.put("#4#", "宿松县");
        SCOPE_NAMES.put("#5#", "怀宁县");
    }

    /** 状态编码 → 名称 */
    private static final Map<String, String> STATUS_NAMES = new LinkedHashMap<>();

    static {
        STATUS_NAMES.put("#1#", "草稿");
        STATUS_NAMES.put("#2#", "已测算");
    }

    /** 状态缺省值：已测算 */
    private static final String DEFAULT_STATUS = "#2#";

    @Autowired
    private WaterSavingPotentialMapper waterSavingPotentialMapper;

    @Autowired
    private FileClient fileClient;

    @Autowired
    private SessionContextService sessionContextService;

    @Value("${hltgq.corp-code}")
    private String corpCode;

    /**
     * 读取指定「年度 + 分析范围」的测算记录。
     *
     * @param year    年度（2000~2100）
     * @param scope   分析范围：#1# 全灌区 / #2# 太湖县 / #3# 望江县 / #4# 宿松县 / #5# 怀宁县
     * @param request 当前请求（提取会话透传文件服务，解析附件回显地址）
     * @return 记录（无记录返回 null）
     */
    public WaterSavingPotentialVO getRecord(Integer year, String scope, HttpServletRequest request) {
        int resolvedYear = validateYear(year);
        String resolvedScope = validateScope(scope);
        WaterSavingPotential entity = findRecord(resolvedYear, resolvedScope);
        if (entity == null) {
            log.info("[节水潜力] 查询无记录 year={}, scope={}", resolvedYear, resolvedScope);
            return null;
        }
        log.info("[节水潜力] 查询命中 id={}, year={}, scope={}, status={}",
                entity.getId(), resolvedYear, resolvedScope, entity.getStatus());
        return toVO(entity, request);
    }

    /**
     * 保存测算记录（upsert）：同「年度 + 分析范围」存在即整行覆盖，不存在即新增。
     * <p>数值留空传 null，覆盖保存时同样把已存值清空。
     *
     * @return 记录 id
     */
    public String save(WaterSavingPotentialSaveRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        int year = validateYear(req.getYear());
        String scope = validateScope(req.getScope());
        String status = resolveStatus(req.getStatus());
        String userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();

        WaterSavingPotential existing = findRecord(year, scope);
        if (existing != null) {
            fillValues(existing, req, status);
            existing.setUpdatedAt(now);
            existing.setUpdatedBy(userId);
            waterSavingPotentialMapper.updateRecord(existing);
            log.info("[节水潜力] 覆盖保存 id={}, year={}, scope={}, status={}, 操作人={}",
                    existing.getId(), year, scope, status, userId);
            return existing.getId();
        }

        WaterSavingPotential entity = new WaterSavingPotential();
        entity.setYear(LocalDateTime.of(year, 1, 1, 0, 0));
        entity.setScope(scope);
        fillValues(entity, req, status);
        entity.setCorpCode(corpCode);
        entity.setCreatedAt(now);
        entity.setCreatedBy(userId);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(userId);
        waterSavingPotentialMapper.insert(entity);
        log.info("[节水潜力] 新增保存 id={}, year={}, scope={}, status={}, 操作人={}",
                entity.getId(), year, scope, status, userId);
        return entity.getId();
    }

    // ==================== 私有工具 ====================

    /** 按「年度 + 分析范围」查询最新一行：year 区间匹配（兼容平台控件任意日期值），同键多行取 updated_at 最新 */
    private WaterSavingPotential findRecord(int year, String scope) {
        LocalDateTime start = LocalDateTime.of(year, 1, 1, 0, 0);
        QueryWrapper<WaterSavingPotential> wrapper = new QueryWrapper<>();
        wrapper.ge("\"year\"", start)
                .lt("\"year\"", start.plusYears(1))
                .eq("\"scope\"", scope)
                .orderByDesc("\"updated_at\"")
                .last("LIMIT 1");
        List<WaterSavingPotential> list = waterSavingPotentialMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 请求数值与文本落到实体（含 null 覆盖，保证整行覆盖语义） */
    private void fillValues(WaterSavingPotential entity, WaterSavingPotentialSaveRequest req, String status) {
        entity.setTotalResources(req.getTotalResources());
        entity.setSupplyCapacity(req.getSupplyCapacity());
        entity.setAvailableSupply(req.getAvailableSupply());
        entity.setBasisFile(req.getBasisFile());
        entity.setAgriBaseline(req.getAgriBaseline());
        entity.setAgriTarget(req.getAgriTarget());
        entity.setIndBaseline(req.getIndBaseline());
        entity.setIndTarget(req.getIndTarget());
        entity.setLifeBaseline(req.getLifeBaseline());
        entity.setLifeTarget(req.getLifeTarget());
        entity.setCalcBasis(req.getCalcBasis());
        entity.setMeasures(req.getMeasures());
        entity.setStatus(status);
    }

    /** 实体 → 响应 VO：编码附中文名，附件引用附解析地址 */
    private WaterSavingPotentialVO toVO(WaterSavingPotential entity, HttpServletRequest request) {
        WaterSavingPotentialVO vo = new WaterSavingPotentialVO();
        vo.setYear(entity.getYear() == null ? null : entity.getYear().getYear());
        vo.setScope(entity.getScope());
        vo.setScopeName(SCOPE_NAMES.getOrDefault(entity.getScope(), entity.getScope()));
        vo.setTotalResources(entity.getTotalResources());
        vo.setSupplyCapacity(entity.getSupplyCapacity());
        vo.setAvailableSupply(entity.getAvailableSupply());
        vo.setBasisFile(entity.getBasisFile());
        vo.setBasisFileUrl(resolveBasisFileUrl(entity.getBasisFile(), request));
        vo.setAgriBaseline(entity.getAgriBaseline());
        vo.setAgriTarget(entity.getAgriTarget());
        vo.setIndBaseline(entity.getIndBaseline());
        vo.setIndTarget(entity.getIndTarget());
        vo.setLifeBaseline(entity.getLifeBaseline());
        vo.setLifeTarget(entity.getLifeTarget());
        vo.setCalcBasis(entity.getCalcBasis());
        vo.setMeasures(entity.getMeasures());
        vo.setStatus(entity.getStatus());
        vo.setStatusName(STATUS_NAMES.getOrDefault(entity.getStatus(), entity.getStatus()));
        return vo;
    }

    /**
     * 基础数据依据附件回显地址（尽力模式）：引用值为单个文件 id 时经文件服务换签名地址；
     * 已是完整 URL 直接返回；复合引用（多附件/JSON 数组）暂不解析；无会话或服务失败返回 null。
     * <p>附件属增强信息，解析失败仅日志告警，不阻断读取。
     */
    private String resolveBasisFileUrl(String basisFile, HttpServletRequest request) {
        if (basisFile == null || basisFile.trim().isEmpty()) {
            return null;
        }
        String ref = basisFile.trim();
        if (ref.startsWith("http://") || ref.startsWith("https://")) {
            return ref;
        }
        if (ref.contains(",") || ref.startsWith("[") || ref.startsWith("{")) {
            log.warn("[节水潜力] 附件引用为复合格式（多附件/JSON），暂不解析回显地址: {}", ref);
            return null;
        }
        String sessionId = sessionContextService.extractSessionId(request);
        if (sessionId == null) {
            log.warn("[节水潜力] 无会话，附件回显地址解析跳过 fileId={}", ref);
            return null;
        }
        try {
            FileClient.FileInfo info = fileClient.getFile(ref, sessionId);
            return info.url;
        } catch (Exception e) {
            log.warn("[节水潜力] 附件回显地址解析失败 fileId={}: {}", ref, e.getMessage());
            return null;
        }
    }

    /** 年度校验：必填且 2000~2100 */
    private int validateYear(Integer year) {
        if (year == null) {
            throw new IllegalArgumentException("年度不能为空");
        }
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("年度超出允许范围（2000~2100）：" + year);
        }
        return year;
    }

    /** 分析范围校验：必填且为页面下拉取值 #1#~#5# */
    private String validateScope(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            throw new IllegalArgumentException("分析范围不能为空");
        }
        String value = scope.trim();
        if (!SCOPE_NAMES.containsKey(value)) {
            throw new IllegalArgumentException("分析范围不合法（#1# 全灌区 / #2# 太湖县 / #3# 望江县 / #4# 宿松县 / #5# 怀宁县）：" + value);
        }
        return value;
    }

    /** 状态规范化：空默认已测算，非空须为 #1#/#2# */
    private String resolveStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return DEFAULT_STATUS;
        }
        String value = status.trim();
        if (!STATUS_NAMES.containsKey(value)) {
            throw new IllegalArgumentException("状态不合法（#1# 草稿 / #2# 已测算）：" + value);
        }
        return value;
    }

    /** 当前登录人 userId（鉴权已由拦截器保证；异常场景容错为 null，仅审计字段留空） */
    private String currentUserId() {
        UserContext user = UserContextHolder.currentUser();
        if (user == null || user.getUserId() == null) {
            log.warn("[节水潜力] 当前请求无用户上下文，created_by/updated_by 置空");
            return null;
        }
        return user.getUserId();
    }
}
