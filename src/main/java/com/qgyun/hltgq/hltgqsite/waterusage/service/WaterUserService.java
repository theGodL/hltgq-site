package com.qgyun.hltgq.hltgqsite.waterusage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageDict;
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
 * 删除保护：存在四页业务引用（需水 / 调度方案 / 调度记录 / 账单）→ 禁止删除；
 * 否则按计划引用分级——被非编制中计划引用 → 禁止；仅被编制中计划引用 → 二次确认（force=true）
 * 后连带清理对应明细并重算计划总水量。
 * <p>编号 YH + 3 位序号由 MAX+1 生成，唯一索引兜底并发（冲突重试在 Controller 层）。
 */
@Service
public class WaterUserService {

    private static final Logger log = LoggerFactory.getLogger(WaterUserService.class);

    private final WaterUserMapper userMapper;

    private final WaterPlanMapper planMapper;

    private final ShortIdGenerator shortIdGenerator;

    private final ObjectMapper objectMapper;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public WaterUserService(WaterUserMapper userMapper,
                            WaterPlanMapper planMapper,
                            ShortIdGenerator shortIdGenerator,
                            ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.planMapper = planMapper;
        this.shortIdGenerator = shortIdGenerator;
        this.objectMapper = objectMapper;
    }

    // ==================== 列表 / 详情 / 元数据 ====================

    /** 表单元数据（页面加载一次：字典 + 闸口下拉） */
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userTypes", WaterUsageDict.userTypeOptions());
        out.put("calcModes", WaterUsageDict.calcModeOptions());
        out.put("settleCycles", WaterUsageDict.settleCycleOptions());
        out.put("gates", userMapper.selectGates(null));
        out.put("today", LocalDate.now().toString());
        return out;
    }

    /** 列表分页（年度计划水量/年度已调度为当年派生列） */
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

    // ==================== 新增 / 编辑 ====================

    /** 新增（编号 YH + 3 位序号） */
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

    /** 编辑（编号与状态不变） */
    @Transactional(rollbackFor = Exception.class)
    public WaterUserVO update(String id, WaterUserSaveVO req) {
        String key = WaterUsageSupport.requireText(id, "用水户 id 不能为空");
        if (userMapper.selectUserById(key) == null) {
            throw new IllegalArgumentException("用水户不存在");
        }
        Map<String, Object> row = checkAndBuild(req, key);
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
                    + " 条 / 调度方案 " + refCount(bizRefs, "dispatches") + " 条 / 调度记录 "
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
        String contactName = WaterUsageSupport.requireText(req.getContactName(), "请填写联系人");
        WaterUsageSupport.maxLength(contactName, 50, "联系人");
        String contactPhone = WaterUsageSupport.trim(req.getContactPhone());
        if (contactPhone != null && !contactPhone.matches("\\d{11}")) {
            throw new IllegalArgumentException("联系电话须为 11 位数字");
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
        String gateConfigJson = buildGateConfig(req.getGateConfig(), calcMode);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("user_type", userType);
        row.put("contact_name", contactName);
        row.put("contact_phone", contactPhone);
        row.put("irrigation_area", area);
        row.put("water_price", waterPrice);
        row.put("calc_mode", calcMode);
        row.put("gate_config", gateConfigJson);
        row.put("settle_cycle", settleCycle);
        return row;
    }

    /**
     * 闸口配置校验并重建 JSON 快照：结构须与计算策略一致
     * （#1# → SUM：gates 至少 1 个；#2# → DIFF：main 必填、deduct 至少 1 个、主闸口不得出现在扣减列表）；
     * 名称一律按 id 回查站档固化，不信任前端传入。
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
            JsonNode mainNode = node.get("main");
            String mainId = mainNode == null || !mainNode.isObject()
                    ? null : WaterUsageSupport.trim(mainNode.path("id").asText(null));
            if (mainId == null) {
                throw new IllegalArgumentException("请选择主闸口");
            }
            List<String> deductIds = collectGateIds(node.get("deduct"), "请至少选择一个扣减闸口");
            if (deductIds.contains(mainId)) {
                throw new IllegalArgumentException("主闸口不能同时作为扣减闸口");
            }
            List<String> allIds = new ArrayList<>();
            allIds.add(mainId);
            allIds.addAll(deductIds);
            Map<String, String> names = queryGateNames(allIds);
            root.put("mode", WaterUsageDict.GATE_MODE_DIFF);
            ObjectNode main = root.putObject("main");
            main.put("id", mainId);
            main.put("name", names.get(mainId));
            ArrayNode deduct = root.putArray("deduct");
            for (String gateId : deductIds) {
                ObjectNode gate = deduct.addObject();
                gate.put("id", gateId);
                gate.put("name", names.get(gateId));
            }
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("关联闸口数据格式错误");
        }
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
