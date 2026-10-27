package com.qgyun.hltgq.hltgqsite.waterusage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageDict;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageRules;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport.GateConfig;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterPlanMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterUserMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.GateOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterUserSaveVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterUserVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用水户服务（列表/详情/新增/编辑/删除保护 + 表单元数据）。
 * <p>闸口配置由后端按站档回查固化名称快照（不信任前端传入的 name）；
 * 差额策略保存时校验最新读数：主闸口读数 − 扣减闸口读数合计 ≤ 0 禁止保存（读数缺失跳过）；
 * 删除保护：存在四页业务引用（需水 / 调度方案 / 调度记录 / 账单）→ 禁止删除；
 * 否则按计划引用分级——被非编制中计划引用 → 禁止；仅被编制中计划引用 → 二次确认（force=true）
 * 后连带清理对应明细并重算计划总水量。
 * <p>编号 YH + 3 位序号由服务端自动生成、不可填写（新增取最大值 +1，编辑保持不变），
 * 唯一索引兜底并发（冲突重试在 Controller 层）。
 * <p>联系人从平台用户（t_apaas_uc_user）下拉选择：保存时按 contactUserId 回查固化姓名/手机号快照
 * （不信任前端传入）；编辑未换人时回传原快照保持（平台侧改名/离职后仍可编辑其他字段）。
 */
@Service
public class WaterUserService {

    private static final Logger log = LoggerFactory.getLogger(WaterUserService.class);

    private final WaterUserMapper userMapper;

    private final WaterPlanMapper planMapper;

    private final WaterBillService billService;

    private final ShortIdGenerator shortIdGenerator;

    private final ObjectMapper objectMapper;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public WaterUserService(WaterUserMapper userMapper,
                            WaterPlanMapper planMapper,
                            WaterBillService billService,
                            ShortIdGenerator shortIdGenerator,
                            ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.planMapper = planMapper;
        this.billService = billService;
        this.shortIdGenerator = shortIdGenerator;
        this.objectMapper = objectMapper;
    }

    // ==================== 列表 / 详情 / 元数据 ====================

    /** 表单元数据（页面加载一次：字典 + 闸口下拉 + 联系人候选 + 规则版本） */
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userTypes", WaterUsageDict.userTypeOptions());
        out.put("userStatuses", WaterUsageDict.userStatusOptions());
        out.put("calcModes", WaterUsageDict.calcModeOptions());
        out.put("settleCycles", WaterUsageDict.settleCycleOptions());
        out.put("gates", userMapper.selectGates(null));
        out.put("contacts", userMapper.selectContactOptions());
        out.put("today", LocalDate.now().toString());
        out.put("rulesVersion", WaterUsageRules.version());
        return out;
    }

    /** 列表分页（年度计划水量/年度已调度为当年派生列；待出账为账单口径实时装配） */
    public Map<String, Object> page(int page, int size, String keyword,
                                    String calcMode, String settleCycle, String status) {
        int p = WaterUsageSupport.clampPage(page);
        int s = WaterUsageSupport.clampSize(size);
        String kw = WaterUsageSupport.trim(keyword);
        String cm = WaterUsageSupport.trim(calcMode);
        String sc = WaterUsageSupport.trim(settleCycle);
        String st = WaterUsageSupport.trim(status);
        int year = LocalDate.now().getYear();
        long total = userMapper.countUsers(kw, cm, sc, st);
        List<WaterUserVO> records = userMapper.selectUserPage(kw, cm, sc, st, year, s, (long) (p - 1) * s);
        for (WaterUserVO row : records) {
            decorate(row);
        }
        billService.fillPending(records);
        return WaterUsageSupport.pageResult(total, p, s, records);
    }

    /** 详情 */
    public WaterUserVO detail(String id) {
        String key = WaterUsageSupport.requireText(id, "用水户 id 不能为空");
        WaterUserVO vo = userMapper.selectUserById(key);
        if (vo == null) {
            throw new IllegalArgumentException("用水户不存在");
        }
        decorate(vo);
        return vo;
    }

    /**
     * 该用水户关联闸口选项（调度方案编制「调度闸口」下拉收敛：仅该户配置的闸口组）。
     * <p>按配置顺序输出（差额模式主闸口在前、扣减闸口随后），与保存校验的 GateConfig.contains 同口径；
     * 未配置 / 配置解析失败返回空数组（前端据此提示「请先在用水户管理中配置关联闸口」）。
     */
    public List<GateOptionVO> userGates(String userId) {
        String key = WaterUsageSupport.requireText(userId, "用水户 id 不能为空");
        WaterUserVO user = userMapper.selectUserById(key);
        if (user == null) {
            throw new IllegalArgumentException("用水户不存在");
        }
        GateConfig config = WaterUsageSupport.gateConfigOf(user.getGateConfigRaw(), objectMapper);
        if (config == null || config.allIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, GateOptionVO> byId = new LinkedHashMap<>();
        for (GateOptionVO row : userMapper.selectGateNamesByIds(config.allIds)) {
            byId.put(row.getId(), row);
        }
        List<GateOptionVO> out = new ArrayList<>();
        for (String gateId : config.allIds) {
            GateOptionVO hit = byId.get(gateId);
            if (hit != null) {
                out.add(hit);
            }
        }
        return out;
    }

    // ==================== 新增 / 编辑 ====================

    /** 新增（编号由服务端自动生成 YH + 3 位序号，不可填写） */
    @Transactional(rollbackFor = Exception.class)
    public WaterUserVO create(WaterUserSaveVO req) {
        Map<String, Object> row = checkAndBuild(req, null);
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        row.put("id", shortIdGenerator.nextUUID(null));
        row.put("user_no", String.format("YH%03d", userMapper.selectMaxUserSeq() + 1));
        row.put("now", now);
        row.put("operator", operator);
        userMapper.insertUser(row);
        return detail((String) row.get("id"));
    }

    /** 编辑（编号不可改，保持原值；状态可改） */
    @Transactional(rollbackFor = Exception.class)
    public WaterUserVO update(String id, WaterUserSaveVO req) {
        String key = WaterUsageSupport.requireText(id, "用水户 id 不能为空");
        WaterUserVO current = userMapper.selectUserById(key);
        if (current == null) {
            throw new IllegalArgumentException("用水户不存在");
        }
        Map<String, Object> row = checkAndBuild(req, key);
        // 编号不可修改，恒保持库内原值
        row.put("user_no", current.getUserNo());
        row.put("id", key);
        row.put("now", LocalDateTime.now());
        row.put("operator", WaterUsageSupport.currentOperatorId(fallbackOperator));
        userMapper.updateUser(row);
        return detail(key);
    }

    // ==================== 删除保护（业务引用硬拦 + 计划引用两级） ====================

    /** 删除前置检查（前端据此渲染：业务引用硬拦 / 计划 blocked / 计划 pending 三级提示） */
    public Map<String, Object> deleteCheck(String id) {
        String key = WaterUsageSupport.requireText(id, "用水户 id 不能为空");
        if (userMapper.selectUserById(key) == null) {
            throw new IllegalArgumentException("用水户不存在");
        }
        Map<String, Object> out = WaterUsageSupport.splitPlanRefs(userMapper.selectUserRefs(key));
        out.put("refs", userMapper.countBusinessRefs(key));
        return out;
    }

    /**
     * 删除：存在四页业务引用（需水/调度/记录/账单）→ 禁止；
     * 被非编制中计划引用 → 禁止；
     * 仅被编制中计划引用且 force=true → 连带清理明细并重算计划总水量。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, boolean force) {
        String key = WaterUsageSupport.requireText(id, "用水户 id 不能为空");
        if (userMapper.selectUserById(key) == null) {
            throw new IllegalArgumentException("用水户不存在");
        }
        Map<String, Object> bizRefs = userMapper.countBusinessRefs(key);
        long bizTotal = refCount(bizRefs, "demands") + refCount(bizRefs, "dispatches")
                + refCount(bizRefs, "records") + refCount(bizRefs, "bills");
        if (bizTotal > 0) {
            throw new IllegalArgumentException("该用水户已存在业务数据（需水上报 " + refCount(bizRefs, "demands")
                    + " 条 / 调度指令 " + refCount(bizRefs, "dispatches") + " 条 / 调度记录 "
                    + refCount(bizRefs, "records") + " 条 / 账单 " + refCount(bizRefs, "bills")
                    + " 张），删除会使业务与财务记录悬空，不可删除");
        }
        Map<String, Object> refs = WaterUsageSupport.splitPlanRefs(userMapper.selectUserRefs(key));
        List<String> planIds = WaterUsageSupport.resolvePlanRefsDelete(refs, force, "该用水户");
        if (!planIds.isEmpty()) {
            userMapper.deleteUserItemsInPlans(key, planIds);
            planMapper.recalcPlanTotals(planIds, LocalDateTime.now(),
                    WaterUsageSupport.currentOperatorId(fallbackOperator));
        }
        userMapper.deleteUser(key);
    }

    /** 计数取值（null / 非数字按 0） */
    private static long refCount(Map<String, Object> refs, String key) {
        Object value = refs == null ? null : refs.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    // ==================== 校验 / 组装 ====================

    /** 全字段校验并组装写库 Map（row key 为 snake_case 列名）；编辑时排除自身查重 */
    private Map<String, Object> checkAndBuild(WaterUserSaveVO req, String excludeId) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String name = WaterUsageSupport.requireText(req.getName(), "请填写用水户名称");
        WaterUsageSupport.maxLength(name, 100, "用水户名称");
        if (userMapper.countByName(name, excludeId) > 0) {
            throw new IllegalArgumentException("用水户名称已存在：" + name);
        }
        String userType = WaterUsageSupport.requireText(req.getUserType(), "请选择用水类型");
        if (!WaterUsageDict.isValidUserType(userType)) {
            throw new IllegalArgumentException("用水类型无效");
        }
        String contactUserId = WaterUsageSupport.trim(req.getContactUserId());
        String contactName;
        String contactPhone;
        if (contactUserId != null) {
            // 联系人下拉选中：按 id 回查平台用户，固化姓名/手机号快照（不信任前端传入）
            Map<String, Object> contact = userMapper.selectPlatformUserById(contactUserId);
            if (contact == null) {
                throw new IllegalArgumentException("联系人不存在，请重新选择");
            }
            contactName = WaterUsageSupport.requireText((String) contact.get("name"), "该联系人姓名为空，请重新选择");
            WaterUsageSupport.maxLength(contactName, 50, "联系人");
            contactPhone = WaterUsageSupport.trim((String) contact.get("mobile"));
            if (contactPhone != null && !contactPhone.matches("\\d{11}")) {
                // 平台手机号异常时快照置空（列表可空），不阻塞保存
                contactPhone = null;
            }
        } else if (excludeId == null) {
            // 新增必须从下拉选择联系人
            throw new IllegalArgumentException("请选择联系人");
        } else {
            // 编辑未换人：保持原快照（平台侧改名/离职后下拉匹配不上时回传原值）
            contactName = WaterUsageSupport.requireText(req.getContactName(), "请选择联系人");
            WaterUsageSupport.maxLength(contactName, 50, "联系人");
            contactPhone = WaterUsageSupport.trim(req.getContactPhone());
            if (contactPhone != null && !contactPhone.matches("\\d{11}")) {
                // 存量快照异常时置空（前端电话框只读，不能因历史值阻塞档案编辑）；口径与回查分支一致
                contactPhone = null;
            }
        }
        BigDecimal area = WaterUsageSupport.optionalPositive(req.getIrrigationArea(), "灌溉面积", 2);
        if (WaterUsageDict.requireIrrigationArea(userType) && area == null) {
            throw new IllegalArgumentException("农业 / 生态用水户必须填写灌溉面积");
        }
        BigDecimal waterPrice = WaterUsageSupport.requirePositive(req.getWaterPrice(), "水价", 3);
        String calcMode = WaterUsageSupport.requireText(req.getCalcMode(), "请选择计算策略");
        if (!WaterUsageDict.isValidCalcMode(calcMode)) {
            throw new IllegalArgumentException("计算策略无效");
        }
        String settleCycle = WaterUsageSupport.requireText(req.getSettleCycle(), "请选择统计周期");
        if (!WaterUsageDict.isValidSettleCycle(settleCycle)) {
            throw new IllegalArgumentException("统计周期无效");
        }
        // 状态（不传默认正常）
        String status = WaterUsageSupport.trim(req.getStatus());
        if (status == null) {
            status = WaterUsageDict.USER_STATUS_NORMAL;
        }
        if (!WaterUsageDict.isValidUserStatus(status)) {
            throw new IllegalArgumentException("用水户状态无效");
        }
        BigDecimal lng = optionalRange(req.getLng(), "经度",
                new BigDecimal("-180"), new BigDecimal("180"), 6);
        BigDecimal lat = optionalRange(req.getLat(), "纬度",
                new BigDecimal("-90"), new BigDecimal("90"), 6);
        BigDecimal annualPlanWater = annualPlanWaterOf(req.getAnnualPlanWater());
        String remark = WaterUsageSupport.trim(req.getRemark());
        WaterUsageSupport.maxLength(remark, 200, "备注");
        String gateConfigJson = buildGateConfig(req.getGateConfig(), calcMode);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("user_type", userType);
        row.put("status", status);
        row.put("contact_name", contactName);
        row.put("contact_phone", contactPhone);
        row.put("irrigation_area", area);
        row.put("lng", lng);
        row.put("lat", lat);
        row.put("annual_plan_water", annualPlanWater);
        row.put("water_price", waterPrice);
        row.put("calc_mode", calcMode);
        row.put("gate_config", gateConfigJson);
        row.put("settle_cycle", settleCycle);
        row.put("remark", remark);
        return row;
    }

    /**
     * 闸口配置校验并重建 JSON 快照：结构须与计算策略一致
     * （#1# → SUM：gates 至少 1 个；#2# → DIFF：mains 至少 1 个、deduct 至少 1 个、主闸口不得出现在扣减列表）；
     * 名称一律按 id 回查站档固化，不信任前端传入。
     * <p>差额主闸口可多选（过闸水量相加后减扣减闸口合计）：快照写 mains 数组，
     * 同时保留首个主闸口的 main 单对象（兼容旧版只读 main 的解析方）。
     */
    private String buildGateConfig(JsonNode node, String calcMode) {
        String expectedMode = WaterUsageDict.gateModeOf(calcMode);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("请配置关联闸口");
        }
        if (!expectedMode.equals(node.path("mode").asText(""))) {
            throw new IllegalArgumentException("关联闸口结构与计算策略不一致，请重新选择闸口");
        }
        ObjectNode root = objectMapper.createObjectNode();
        if (WaterUsageDict.GATE_MODE_SUM.equals(expectedMode)) {
            List<String> gateIds = collectGateIds(node.get("gates"), "请至少选择一个关联闸口");
            Map<String, String> names = queryGateNames(gateIds);
            root.put("mode", WaterUsageDict.GATE_MODE_SUM);
            ArrayNode gates = root.putArray("gates");
            for (String gateId : gateIds) {
                ObjectNode gate = gates.addObject();
                gate.put("id", gateId);
                gate.put("name", names.get(gateId));
            }
        } else {
            List<String> mainIds = collectDiffMainIds(node);
            List<String> deductIds = collectGateIds(node.get("deduct"), "请至少选择一个扣减闸口");
            for (String mainId : mainIds) {
                if (deductIds.contains(mainId)) {
                    throw new IllegalArgumentException("主闸口不能同时作为扣减闸口");
                }
            }
            List<String> allIds = new ArrayList<>(mainIds);
            allIds.addAll(deductIds);
            Map<String, String> names = queryGateNames(allIds);
            root.put("mode", WaterUsageDict.GATE_MODE_DIFF);
            ArrayNode mains = root.putArray("mains");
            for (String gateId : mainIds) {
                ObjectNode gate = mains.addObject();
                gate.put("id", gateId);
                gate.put("name", names.get(gateId));
            }
            // 旧结构兼容：首个主闸口另存 main 单对象
            ObjectNode main = root.putObject("main");
            main.put("id", mainIds.get(0));
            main.put("name", names.get(mainIds.get(0)));
            ArrayNode deduct = root.putArray("deduct");
            for (String gateId : deductIds) {
                ObjectNode gate = deduct.addObject();
                gate.put("id", gateId);
                gate.put("name", names.get(gateId));
            }
            checkDiffReadings(mainIds, deductIds);
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("关联闸口数据格式错误");
        }
    }

    /** 收集差额主闸口 id（优先 mains 数组，兼容旧版 main 单对象；去重保序） */
    private List<String> collectDiffMainIds(JsonNode node) {
        JsonNode mains = node.get("mains");
        if (mains != null && mains.isArray() && mains.size() > 0) {
            return collectGateIds(mains, "请至少选择一个主闸口");
        }
        JsonNode main = node.get("main");
        String mainId = main == null || !main.isObject()
                ? null : WaterUsageSupport.trim(main.path("id").asText(null));
        if (mainId != null) {
            List<String> ids = new ArrayList<>();
            ids.add(mainId);
            return ids;
        }
        throw new IllegalArgumentException("请至少选择一个主闸口");
    }

    /** 收集闸口 id（兼容 {"id":…} 对象与纯字符串两种形态；去重保序） */
    private List<String> collectGateIds(JsonNode arrayNode, String emptyMessage) {
        if (arrayNode == null || !arrayNode.isArray() || arrayNode.size() == 0) {
            throw new IllegalArgumentException(emptyMessage);
        }
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode item : arrayNode) {
            String gateId = item == null ? null
                    : (item.isTextual() ? item.asText() : item.path("id").asText(null));
            gateId = WaterUsageSupport.trim(gateId);
            if (gateId == null) {
                throw new IllegalArgumentException("关联闸口数据不完整，请重新选择");
            }
            ids.add(gateId);
        }
        return new ArrayList<>(ids);
    }

    /** 批量回查闸口名称（缺失即失效，报「不存在」） */
    private Map<String, String> queryGateNames(List<String> gateIds) {
        List<GateOptionVO> gates = userMapper.selectGateNamesByIds(gateIds);
        Map<String, String> names = new LinkedHashMap<>();
        for (GateOptionVO gate : gates) {
            names.put(gate.getId(), gate.getName());
        }
        for (String gateId : gateIds) {
            if (!names.containsKey(gateId)) {
                throw new IllegalArgumentException("关联闸口不存在，请重新选择");
            }
        }
        return names;
    }

    /**
     * 差额策略读数校验（设计方案 3.1）：主闸口读数合计 − 扣减闸口读数合计 ≤ 0 时禁止保存。
     * <p>读数口径与账单计量一致（最新累计流量 ttf，哨兵/缺失视为无读数）：
     * 任一主闸口或扣减闸口无读数时无法判定，跳过校验（保存不被流量数据阻塞）。
     */
    private void checkDiffReadings(List<String> mainIds, List<String> deductIds) {
        BigDecimal mainSum = BigDecimal.ZERO;
        for (String gateId : mainIds) {
            BigDecimal reading = usableReading(gateId);
            if (reading == null) {
                return;
            }
            mainSum = mainSum.add(reading);
        }
        BigDecimal deductSum = BigDecimal.ZERO;
        for (String gateId : deductIds) {
            BigDecimal reading = usableReading(gateId);
            if (reading == null) {
                return;
            }
            deductSum = deductSum.add(reading);
        }
        if (mainSum.subtract(deductSum).signum() <= 0) {
            throw new IllegalArgumentException("差额策略保存失败：主闸口读数合计（"
                    + WaterUsageSupport.scale2(mainSum).toPlainString() + " m³）未大于扣减闸口读数合计（"
                    + WaterUsageSupport.scale2(deductSum).toPlainString() + " m³），请检查闸口选择或待流量数据更新后重试");
        }
    }

    /** 闸口最新可用读数（ttf；缺失/哨兵返回 null） */
    private BigDecimal usableReading(String gateId) {
        return WaterUsageSupport.usableFlow(userMapper.selectLatestGateReading(gateId));
    }

    /** 选填数值 + 范围校验（null 放过；填写须在 [min, max] 且小数位 ≤ maxScale） */
    private static BigDecimal optionalRange(BigDecimal value, String label,
                                            BigDecimal min, BigDecimal max, int maxScale) {
        if (value == null) {
            return null;
        }
        if (value.scale() > maxScale) {
            throw new IllegalArgumentException(label + "最多保留 " + maxScale + " 位小数");
        }
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(label + "须在 " + min.toPlainString()
                    + " ~ " + max.toPlainString() + " 之间");
        }
        return value;
    }

    /** 年度计划水量（万m³ 录入 ≤2 位小数 → m³ 存储；null/0 视为未设定；负数报错） */
    private static BigDecimal annualPlanWaterOf(BigDecimal wan) {
        if (wan == null) {
            return null;
        }
        if (wan.scale() > 2) {
            throw new IllegalArgumentException("年度计划水量最多保留 2 位小数");
        }
        if (wan.signum() < 0) {
            throw new IllegalArgumentException("年度计划水量不能为负数");
        }
        if (wan.signum() == 0) {
            return null;
        }
        return WaterUsageSupport.toM3(wan);
    }

    // ==================== 装饰 ====================

    /** 填充字典名 + 解析闸口 JSON（原文 @JsonIgnore 不输出；展示文案复用 Support 解析产物） */
    private void decorate(WaterUserVO vo) {
        if (vo == null) {
            return;
        }
        vo.setUserTypeName(WaterUsageDict.userTypeName(vo.getUserType()));
        vo.setCalcModeName(WaterUsageDict.calcModeName(vo.getCalcMode()));
        vo.setSettleCycleName(WaterUsageDict.settleCycleName(vo.getSettleCycle()));
        vo.setStatusName(WaterUsageDict.userStatusName(vo.getStatus()));
        String raw = vo.getGateConfigRaw();
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            vo.setGateConfig(objectMapper.convertValue(node, Object.class));
            GateConfig config = WaterUsageSupport.parseGateConfig(node);
            if (config != null) {
                vo.setGateText(config.gateText);
            }
        } catch (Exception e) {
            log.warn("[灌区用水] 用水户 {} 闸口配置解析失败：{}", vo.getId(), e.getMessage());
        }
    }
}
