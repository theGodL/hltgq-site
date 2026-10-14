package com.qgyun.hltgq.hltgqsite.waterusage.service;

import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageDict;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterPlanMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterQuotaMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterUserMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.QuotaOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.UserOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterPlanItemVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterPlanSaveVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterPlanVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterQuotaVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterUserVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用水计划服务（列表/详情/编制/编辑/状态机/生效校验）。
 * <p>状态机：编制中 →（生效）已生效 →（归档）已归档；已生效 →（作废）已作废；
 * 编制中可物理删除，已生效仅可改明细计划水量/超测算说明（用户/面积/定额/名称/类型/周期锁定），
 * 已归档/已作废为终态不可编不可删。
 * <p>生效三层校验（{@link #validatePlanActivation}，activate 事务内最终裁决，
 * conflict-check 为其只读包装，同一函数唯一口径）：
 * ① 至少 1 条明细且每条计划水量 &gt; 0（block）；② 同类型周期重叠拦截（仅对已生效，block）；
 * ③ 年度-月度弱约束（仅月度/旬度激活时校验，warn 不拦截）。
 * <p>周期由 planType + year(+month+xun) 服务端推导（与前端纯函数同公式）；编号 YJ + 3 位序号
 * MAX+1 生成、唯一索引兜底并发（冲突重试在 Controller 层）。
 */
@Service
public class WaterPlanService {

    private final WaterPlanMapper planMapper;

    private final WaterUserMapper userMapper;

    private final WaterQuotaMapper quotaMapper;

    private final ShortIdGenerator shortIdGenerator;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public WaterPlanService(WaterPlanMapper planMapper,
                            WaterUserMapper userMapper,
                            WaterQuotaMapper quotaMapper,
                            ShortIdGenerator shortIdGenerator) {
        this.planMapper = planMapper;
        this.userMapper = userMapper;
        this.quotaMapper = quotaMapper;
        this.shortIdGenerator = shortIdGenerator;
    }

    // ==================== 元数据 / 列表 / 详情 ====================

    /** 表单元数据（类型/状态/旬字典 + 当天日期；周期推导由前端纯函数同公式完成） */
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("planTypes", WaterUsageDict.planTypeOptions());
        out.put("planStatuses", WaterUsageDict.planStatusOptions());
        out.put("xuns", WaterUsageDict.xunOptions());
        out.put("today", LocalDate.now().toString());
        return out;
    }

    /** 列表分页 */
    public Map<String, Object> page(int page, int size, String keyword, String planType, String status) {
        int p = WaterUsageSupport.clampPage(page);
        int s = WaterUsageSupport.clampSize(size);
        String kw = WaterUsageSupport.trim(keyword);
        String pt = WaterUsageSupport.trim(planType);
        String st = WaterUsageSupport.trim(status);
        long total = planMapper.countPlans(kw, pt, st);
        List<WaterPlanVO> records = planMapper.selectPlanPage(kw, pt, st, s, (long) (p - 1) * s);
        for (WaterPlanVO row : records) {
            decorateHeader(row);
        }
        return WaterUsageSupport.pageResult(total, p, s, records);
    }

    /** 详情（主表 + 明细，明细含用水户/定额关联名与值） */
    public WaterPlanVO detail(String id) {
        String key = WaterUsageSupport.requireText(id, "计划 id 不能为空");
        WaterPlanVO vo = planMapper.selectPlanById(key);
        if (vo == null) {
            throw new IllegalArgumentException("计划不存在");
        }
        decorateHeader(vo);
        vo.setItems(planMapper.selectPlanItems(key));
        return vo;
    }

    /** 明细「用水户」下拉（仅正常状态） */
    public List<UserOptionVO> users(String keyword) {
        return userMapper.selectUserOptions(WaterUsageSupport.trim(keyword));
    }

    /** 明细「适用定额」下拉 */
    public List<QuotaOptionVO> quotas(String keyword) {
        return quotaMapper.selectQuotaOptions(WaterUsageSupport.trim(keyword));
    }

    /** 测算水量 = 灌溉面积 × 定额标准（明细行自动测算接口） */
    public Map<String, Object> calc(BigDecimal area, String quotaId) {
        BigDecimal validArea = WaterUsageSupport.requirePositive(area, "灌溉面积", 2);
        String quotaKey = WaterUsageSupport.requireText(quotaId, "请选择适用定额");
        WaterQuotaVO quota = quotaMapper.selectQuotaById(quotaKey);
        if (quota == null) {
            throw new IllegalArgumentException("定额不存在");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("quotaValue", quota.getQuotaValue());
        out.put("calcWater", WaterUsageSupport.calcWater(validArea, quota.getQuotaValue()));
        return out;
    }

    // ==================== 新增 / 编辑 ====================

    /** 新增（编制中；明细随主表同事务写入，总水量 = 明细计划水量之和） */
    @Transactional(rollbackFor = Exception.class)
    public WaterPlanVO create(WaterPlanSaveVO req) {
        PlanDraft draft = validatePlanReq(req, null);
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        String planId = shortIdGenerator.nextUUID(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", planId);
        row.put("plan_no", String.format("YJ%03d", planMapper.selectMaxPlanSeq() + 1));
        row.put("plan_name", draft.planName);
        row.put("plan_type", draft.planType);
        row.put("period_start", draft.periodStart.toString());
        row.put("period_end", draft.periodEnd.toString());
        row.put("total_water", draft.totalWater);
        row.put("now", now);
        row.put("operator", operator);
        planMapper.insertPlan(row);
        insertItems(planId, draft.items, now, operator);
        return detail(planId);
    }

    /**
     * 编辑：编制中 = 全量替换（名称/类型/周期/明细均可改）；
     * 已生效 = 受限编辑（仅明细计划水量与超测算说明，行集合不可增删）。
     */
    @Transactional(rollbackFor = Exception.class)
    public WaterPlanVO update(String id, WaterPlanSaveVO req) {
        String key = WaterUsageSupport.requireText(id, "计划 id 不能为空");
        WaterPlanVO plan = planMapper.selectPlanById(key);
        if (plan == null) {
            throw new IllegalArgumentException("计划不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        if (WaterUsageDict.PLAN_STATUS_DRAFT.equals(plan.getStatus())) {
            PlanDraft draft = validatePlanReq(req, key);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", key);
            row.put("plan_name", draft.planName);
            row.put("plan_type", draft.planType);
            row.put("period_start", draft.periodStart.toString());
            row.put("period_end", draft.periodEnd.toString());
            row.put("total_water", draft.totalWater);
            row.put("now", now);
            row.put("operator", operator);
            if (planMapper.updatePlanDraft(row) == 0) {
                throw new IllegalArgumentException("计划状态已变更，请刷新后重试");
            }
            planMapper.deletePlanItems(key);
            insertItems(key, draft.items, now, operator);
            return detail(key);
        }
        if (WaterUsageDict.PLAN_STATUS_ACTIVE.equals(plan.getStatus())) {
            updateActive(plan, req, now, operator);
            return detail(key);
        }
        throw new IllegalArgumentException("已归档 / 已作废计划不可编辑");
    }

    // ==================== 状态机 ====================

    /** 物理删除（仅编制中；主表 + 明细） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        String key = WaterUsageSupport.requireText(id, "计划 id 不能为空");
        WaterPlanVO plan = planMapper.selectPlanById(key);
        if (plan == null) {
            throw new IllegalArgumentException("计划不存在");
        }
        if (!WaterUsageDict.PLAN_STATUS_DRAFT.equals(plan.getStatus())) {
            throw new IllegalArgumentException("仅编制中计划可删除（已生效 / 已归档 / 已作废为终态保护）");
        }
        planMapper.deletePlanItems(key);
        if (planMapper.deletePlan(key) == 0) {
            throw new IllegalArgumentException("计划状态已变更，请刷新后重试");
        }
    }

    /** 生效（三层校验最终裁决在同一事务内执行；block 阻止、warn 放行） */
    @Transactional(rollbackFor = Exception.class)
    public void activate(String id) {
        String key = WaterUsageSupport.requireText(id, "计划 id 不能为空");
        WaterPlanVO plan = planMapper.selectPlanById(key);
        if (plan == null) {
            throw new IllegalArgumentException("计划不存在");
        }
        if (!WaterUsageDict.PLAN_STATUS_DRAFT.equals(plan.getStatus())) {
            throw new IllegalArgumentException("仅编制中计划可生效");
        }
        ValidationResult result = validatePlanActivation(plan);
        if (!result.blocked.isEmpty()) {
            throw new IllegalArgumentException("无法生效：" + String.join("；", result.blocked));
        }
        if (planMapper.updatePlanStatus(key, WaterUsageDict.PLAN_STATUS_DRAFT,
                WaterUsageDict.PLAN_STATUS_ACTIVE, LocalDateTime.now(),
                WaterUsageSupport.currentOperatorId(fallbackOperator)) == 0) {
            throw new IllegalArgumentException("计划状态已变更，请刷新后重试");
        }
    }

    /** 归档（仅已生效） */
    @Transactional(rollbackFor = Exception.class)
    public void archive(String id) {
        String key = WaterUsageSupport.requireText(id, "计划 id 不能为空");
        WaterPlanVO plan = planMapper.selectPlanById(key);
        if (plan == null) {
            throw new IllegalArgumentException("计划不存在");
        }
        if (!WaterUsageDict.PLAN_STATUS_ACTIVE.equals(plan.getStatus())) {
            throw new IllegalArgumentException("仅已生效计划可归档");
        }
        if (planMapper.updatePlanStatus(key, WaterUsageDict.PLAN_STATUS_ACTIVE,
                WaterUsageDict.PLAN_STATUS_ARCHIVED, LocalDateTime.now(),
                WaterUsageSupport.currentOperatorId(fallbackOperator)) == 0) {
            throw new IllegalArgumentException("计划状态已变更，请刷新后重试");
        }
    }

    /** 作废（仅已生效；记录作废人/时间/原因） */
    @Transactional(rollbackFor = Exception.class)
    public void voidPlan(String id, String reason) {
        String key = WaterUsageSupport.requireText(id, "计划 id 不能为空");
        WaterPlanVO plan = planMapper.selectPlanById(key);
        if (plan == null) {
            throw new IllegalArgumentException("计划不存在");
        }
        if (!WaterUsageDict.PLAN_STATUS_ACTIVE.equals(plan.getStatus())) {
            throw new IllegalArgumentException("仅已生效计划可作废");
        }
        String validReason = WaterUsageSupport.requireText(reason, "请填写作废原因");
        WaterUsageSupport.maxLength(validReason, 200, "作废原因");
        if (planMapper.voidPlan(key, validReason, LocalDateTime.now(),
                WaterUsageSupport.currentOperatorId(fallbackOperator)) == 0) {
            throw new IllegalArgumentException("计划状态已变更，请刷新后重试");
        }
    }

    /** 生效校验只读预览（前端生效前调用：blocked 非空则终止，warnings 非空则二次确认） */
    public Map<String, Object> conflictCheck(String id) {
        String key = WaterUsageSupport.requireText(id, "计划 id 不能为空");
        WaterPlanVO plan = planMapper.selectPlanById(key);
        if (plan == null) {
            throw new IllegalArgumentException("计划不存在");
        }
        ValidationResult result = validatePlanActivation(plan);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("blocked", result.blocked);
        out.put("warnings", result.warnings);
        return out;
    }

    // ==================== 生效三层校验（唯一口径） ====================

    /**
     * 生效三层校验：① 明细非空且每条计划水量 &gt; 0（block）；
     * ② 同类型已生效计划周期重叠（block；归档/作废不拦截）；
     * ③ 月度/旬度对年度基准的弱约束（warn；基准为 0 或缺失时不触发）。
     */
    private ValidationResult validatePlanActivation(WaterPlanVO plan) {
        ValidationResult result = new ValidationResult();
        List<WaterPlanItemVO> items = planMapper.selectPlanItems(plan.getId());
        if (items == null || items.isEmpty()) {
            result.blocked.add("计划至少需要 1 条明细");
            return result;
        }
        int line = 0;
        for (WaterPlanItemVO item : items) {
            line++;
            if (item.getPlanWater() == null || item.getPlanWater().signum() <= 0) {
                String userName = item.getWaterUserName() == null ? "未知用水户" : item.getWaterUserName();
                result.blocked.add("第 " + line + " 行（" + userName + "）：计划水量必须大于 0");
            }
        }
        List<WaterPlanVO> overlaps = planMapper.selectSameTypeOverlaps(plan.getId(), plan.getPlanType(),
                plan.getPeriodStart(), plan.getPeriodEnd());
        for (WaterPlanVO other : overlaps) {
            result.blocked.add("与 " + other.getPlanNo() + "（" + other.getPlanName() + "）周期重叠");
        }
        if (WaterUsageDict.PLAN_TYPE_MONTH.equals(plan.getPlanType())
                || WaterUsageDict.PLAN_TYPE_XUN.equals(plan.getPlanType())) {
            int year = LocalDate.parse(plan.getPeriodStart()).getYear();
            for (WaterPlanItemVO amountRow : planMapper.selectPlanAmountsByUser(plan.getId())) {
                BigDecimal amount = amountRow.getPlanWater();
                if (amount == null || amount.signum() <= 0) {
                    continue;
                }
                BigDecimal base = planMapper.selectAnnualBaseForUser(amountRow.getWaterUserId(), year);
                if (base == null || base.signum() <= 0) {
                    continue;
                }
                BigDecimal monthlySum = planMapper.selectMonthSumForUser(amountRow.getWaterUserId(), year, plan.getId());
                if (monthlySum == null) {
                    monthlySum = BigDecimal.ZERO;
                }
                BigDecimal total = monthlySum.add(amount);
                if (total.compareTo(base) > 0) {
                    String userName = amountRow.getWaterUserName() == null ? "未知用水户" : amountRow.getWaterUserName();
                    result.warnings.add(userName + "：月度 / 旬度合计将超出年度计划 "
                            + WaterUsageSupport.formatWan(total.subtract(base)) + " 万m³（年度基准 "
                            + WaterUsageSupport.formatWan(base) + " 万m³）");
                }
            }
        }
        return result;
    }

    // ==================== 编制校验（新增 / 编制中编辑） ====================

    private PlanDraft validatePlanReq(WaterPlanSaveVO req, String excludeId) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String planType = WaterUsageSupport.requireText(req.getPlanType(), "请选择计划类型");
        if (!WaterUsageDict.isValidPlanType(planType)) {
            throw new IllegalArgumentException("计划类型无效");
        }
        Integer year = req.getYear();
        if (year == null || year < 2000 || year > 2100) {
            throw new IllegalArgumentException("请选择年度（2000 ~ 2100）");
        }
        Integer month = req.getMonth();
        if (!WaterUsageDict.PLAN_TYPE_YEAR.equals(planType) && (month == null || month < 1 || month > 12)) {
            throw new IllegalArgumentException("请选择月份");
        }
        Integer xun = req.getXun();
        if (WaterUsageDict.PLAN_TYPE_XUN.equals(planType) && (xun == null || xun < 1 || xun > 3)) {
            throw new IllegalArgumentException("请选择旬");
        }
        LocalDate[] period = WaterUsageDict.periodOf(planType, year, month, xun);
        if (period == null) {
            throw new IllegalArgumentException("计划周期推导失败，请检查年度 / 月份 / 旬");
        }
        String planName = WaterUsageSupport.trim(req.getPlanName());
        if (planName == null) {
            planName = WaterUsageDict.autoPlanName(planType, year, month, xun);
        }
        if (planName == null) {
            throw new IllegalArgumentException("计划名称不能为空");
        }
        WaterUsageSupport.maxLength(planName, 200, "计划名称");
        if (planMapper.countPlanByName(planName, excludeId) > 0) {
            throw new IllegalArgumentException("计划名称已存在：" + planName);
        }

        List<WaterPlanSaveVO.Item> reqItems = req.getItems();
        requireItems(reqItems);
        List<ValidatedItem> items = new ArrayList<>();
        BigDecimal totalWater = BigDecimal.ZERO;
        int line = 0;
        for (WaterPlanSaveVO.Item item : reqItems) {
            line++;
            if (item == null) {
                throw new IllegalArgumentException("第 " + line + " 行明细为空");
            }
            String waterUserId = WaterUsageSupport.requireText(item.getWaterUserId(), "第 " + line + " 行：请选择用水户");
            WaterUserVO user = userMapper.selectUserById(waterUserId);
            if (user == null) {
                throw new IllegalArgumentException("第 " + line + " 行：用水户不存在");
            }
            BigDecimal irrigationArea = WaterUsageSupport.requirePositive(item.getIrrigationArea(),
                    "第 " + line + " 行：灌溉面积", 2);
            String quotaId = WaterUsageSupport.requireText(item.getQuotaId(), "第 " + line + " 行：请选择适用定额");
            WaterQuotaVO quota = quotaMapper.selectQuotaById(quotaId);
            if (quota == null) {
                throw new IllegalArgumentException("第 " + line + " 行：定额不存在");
            }
            BigDecimal calcWater = WaterUsageSupport.calcWater(irrigationArea, quota.getQuotaValue());
            BigDecimal planWater = WaterUsageSupport.requirePositive(item.getPlanWater(),
                    "第 " + line + " 行：计划水量", 2);
            String overrideReason = WaterUsageSupport.trim(item.getOverrideReason());
            if (planWater.compareTo(calcWater) > 0 && overrideReason == null) {
                throw new IllegalArgumentException("第 " + line + " 行：计划水量超过测算水量，请填写超测算说明");
            }
            WaterUsageSupport.maxLength(overrideReason, 200, "第 " + line + " 行：超测算说明");
            String stageTag = WaterUsageSupport.trim(item.getStageTag());
            WaterUsageSupport.maxLength(stageTag, 50, "第 " + line + " 行：阶段标签");

            ValidatedItem valid = new ValidatedItem();
            valid.waterUserId = waterUserId;
            valid.irrigationArea = irrigationArea;
            valid.quotaId = quotaId;
            valid.calcWater = calcWater;
            valid.planWater = planWater;
            valid.overrideReason = overrideReason;
            valid.stageTag = stageTag;
            valid.sortOrder = line;
            items.add(valid);
            totalWater = totalWater.add(planWater);
        }

        PlanDraft draft = new PlanDraft();
        draft.planType = planType;
        draft.planName = planName;
        draft.periodStart = period[0];
        draft.periodEnd = period[1];
        draft.totalWater = totalWater;
        draft.items = items;
        return draft;
    }

    // ==================== 已生效受限编辑 ====================

    /**
     * 已生效编辑：名称/类型/周期/行集合/用水户/面积/定额全部锁定（传了且不同即 400），
     * 仅明细计划水量与超测算说明可变；改后重算总水量并校验不低于已调度水量（调度回写预留）。
     */
    private void updateActive(WaterPlanVO plan, WaterPlanSaveVO req, LocalDateTime now, String operator) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String planName = WaterUsageSupport.trim(req.getPlanName());
        if (planName != null && !planName.equals(plan.getPlanName())) {
            throw new IllegalArgumentException("已生效计划不可修改名称");
        }
        String planType = WaterUsageSupport.trim(req.getPlanType());
        if (planType != null && !planType.equals(plan.getPlanType())) {
            throw new IllegalArgumentException("已生效计划不可修改类型");
        }
        if (req.getYear() != null) {
            LocalDate[] period = WaterUsageDict.periodOf(plan.getPlanType(), req.getYear(),
                    req.getMonth(), req.getXun());
            if (period == null || !period[0].toString().equals(plan.getPeriodStart())
                    || !period[1].toString().equals(plan.getPeriodEnd())) {
                throw new IllegalArgumentException("已生效计划不可修改周期");
            }
        }

        List<WaterPlanItemVO> existing = planMapper.selectPlanItems(plan.getId());
        Map<String, WaterPlanItemVO> existingMap = new LinkedHashMap<>();
        for (WaterPlanItemVO row : existing) {
            existingMap.put(row.getId(), row);
        }
        List<WaterPlanSaveVO.Item> reqItems = req.getItems();
        if (reqItems == null || reqItems.size() != existing.size()) {
            throw new IllegalArgumentException("已生效计划不可增删明细行，仅可修改计划水量");
        }
        Set<String> seen = new LinkedHashSet<>();
        BigDecimal totalWater = BigDecimal.ZERO;
        int line = 0;
        for (WaterPlanSaveVO.Item item : reqItems) {
            line++;
            if (item == null) {
                throw new IllegalArgumentException("第 " + line + " 行明细为空");
            }
            String itemId = WaterUsageSupport.requireText(item.getId(), "第 " + line + " 行：明细 id 不能为空");
            if (!seen.add(itemId)) {
                throw new IllegalArgumentException("第 " + line + " 行：明细重复");
            }
            WaterPlanItemVO current = existingMap.get(itemId);
            if (current == null) {
                throw new IllegalArgumentException("第 " + line + " 行：明细不存在或不属于本计划");
            }
            String waterUserId = WaterUsageSupport.trim(item.getWaterUserId());
            if (waterUserId != null && !waterUserId.equals(current.getWaterUserId())) {
                throw new IllegalArgumentException("第 " + line + " 行：已生效计划不可更换用水户");
            }
            BigDecimal irrigationArea = item.getIrrigationArea();
            if (irrigationArea != null && current.getIrrigationArea() != null
                    && irrigationArea.compareTo(current.getIrrigationArea()) != 0) {
                throw new IllegalArgumentException("第 " + line + " 行：已生效计划不可修改灌溉面积");
            }
            String quotaId = WaterUsageSupport.trim(item.getQuotaId());
            if (quotaId != null && !quotaId.equals(current.getQuotaId())) {
                throw new IllegalArgumentException("第 " + line + " 行：已生效计划不可修改适用定额");
            }
            BigDecimal planWater = WaterUsageSupport.requirePositive(item.getPlanWater(),
                    "第 " + line + " 行：计划水量", 2);
            String overrideReason = WaterUsageSupport.trim(item.getOverrideReason());
            BigDecimal calcWater = current.getCalcWater() == null ? BigDecimal.ZERO : current.getCalcWater();
            if (planWater.compareTo(calcWater) > 0 && overrideReason == null) {
                throw new IllegalArgumentException("第 " + line + " 行：计划水量超过测算水量，请填写超测算说明");
            }
            WaterUsageSupport.maxLength(overrideReason, 200, "第 " + line + " 行：超测算说明");
            planMapper.updatePlanItemWater(itemId, plan.getId(), planWater, overrideReason, now, operator);
            totalWater = totalWater.add(planWater);
        }
        planMapper.recalcPlanTotals(Collections.singletonList(plan.getId()), now, operator);
        BigDecimal scheduled = plan.getScheduledQty() == null ? BigDecimal.ZERO : plan.getScheduledQty();
        if (scheduled.signum() > 0 && totalWater.compareTo(scheduled) < 0) {
            // TODO 调度模块接入回写 scheduled_qty 后该拦截随实际数据生效
            throw new IllegalArgumentException("计划总水量（" + WaterUsageSupport.formatWan(totalWater)
                    + " 万m³）不能低于已调度水量（" + WaterUsageSupport.formatWan(scheduled) + " 万m³）");
        }
    }

    // ==================== 私有支撑 ====================

    /** 明细行数约束（至少 1 行，防御上限 500 行） */
    private void requireItems(List<WaterPlanSaveVO.Item> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("计划至少需要 1 条明细");
        }
        if (items.size() > WaterUsageSupport.MAX_ITEMS) {
            throw new IllegalArgumentException("明细行数不能超过 " + WaterUsageSupport.MAX_ITEMS + " 行");
        }
    }

    /** 批量写入明细行 */
    private void insertItems(String planId, List<ValidatedItem> items, LocalDateTime now, String operator) {
        for (ValidatedItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", shortIdGenerator.nextUUID(null));
            row.put("plan_id", planId);
            row.put("water_user_id", item.waterUserId);
            row.put("irrigation_area", item.irrigationArea);
            row.put("quota_id", item.quotaId);
            row.put("calc_water", item.calcWater);
            row.put("plan_water", item.planWater);
            row.put("override_reason", item.overrideReason);
            row.put("stage_tag", item.stageTag);
            row.put("sort_order", item.sortOrder);
            row.put("now", now);
            row.put("operator", operator);
            planMapper.insertPlanItem(row);
        }
    }

    /** 字典名装饰（列表/详情） */
    private void decorateHeader(WaterPlanVO vo) {
        if (vo == null) {
            return;
        }
        vo.setPlanTypeName(WaterUsageDict.planTypeName(vo.getPlanType()));
        vo.setStatusName(WaterUsageDict.planStatusName(vo.getStatus()));
    }

    /** 编制校验产物（周期/名称/明细/总水量） */
    private static class PlanDraft {

        String planType;

        String planName;

        LocalDate periodStart;

        LocalDate periodEnd;

        BigDecimal totalWater;

        List<ValidatedItem> items;
    }

    /** 已校验明细行 */
    private static class ValidatedItem {

        String waterUserId;

        BigDecimal irrigationArea;

        String quotaId;

        BigDecimal calcWater;

        BigDecimal planWater;

        String overrideReason;

        String stageTag;

        int sortOrder;
    }

    /** 生效校验结果：blocked 阻止生效；warnings 需用户确认后放行 */
    private static class ValidationResult {

        final List<String> blocked = new ArrayList<>();

        final List<String> warnings = new ArrayList<>();
    }
}
