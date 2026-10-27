package com.qgyun.hltgq.hltgqsite.waterusage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageDict;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport.GateConfig;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterDemandMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterDispatchMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterPlanMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterUserMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.DemandOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.PlanOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.UserOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDemandVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchSaveVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterPlanVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterUserVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度指令服务（列表/详情/编制/编辑/调整/状态机/删除；原「调度方案」）。
 * <p>行 = 闸口 × 执行窗口的放水载体：计入水量 = 该闸口 [执行时间, 完成时间]（未完成取当前时间）实测累计流量（带符号），
 * 出账时以 billed_qty 快照计费、bill_id 挂接账单。旧数据回落到原 [startTime, endTime]。
 * <p>状态机：待执行(pending) →（开始执行）执行中(running) →（完成）已完成(done)；
 * 待执行/执行中 →（取消）已取消(canceled)。已计费（bill_id 非空）禁改、禁取消、禁删，
 * 须先删除关联账单恢复未计费。
 * <p>用水户与计划放水量均为选填：未选户时跳过闸口归属与配额校验；未填水量时不参与配额累计。
 * <p>配额闭环：关联用水计划且填了用水户与水量时受「该计划该户 plan_water 合计」约束
 * （lockPlanForUpdate 锁行防并发），每次创建/编辑/调整/删除/取消后经 recalcPlanScheduled 回写计划
 * scheduled_qty（= Σ 未取消指令 plan_amount）。
 */
@Service
public class WaterDispatchService {

    private final WaterDispatchMapper dispatchMapper;

    private final WaterDemandMapper demandMapper;

    private final WaterDemandService demandService;

    private final WaterUserMapper userMapper;

    private final WaterPlanMapper planMapper;

    private final ShortIdGenerator shortIdGenerator;

    private final ObjectMapper objectMapper;

    private final PlatformUserService platformUsers;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public WaterDispatchService(WaterDispatchMapper dispatchMapper,
                                WaterDemandMapper demandMapper,
                                WaterDemandService demandService,
                                WaterUserMapper userMapper,
                                WaterPlanMapper planMapper,
                                ShortIdGenerator shortIdGenerator,
                                ObjectMapper objectMapper,
                                PlatformUserService platformUsers) {
        this.dispatchMapper = dispatchMapper;
        this.demandMapper = demandMapper;
        this.demandService = demandService;
        this.userMapper = userMapper;
        this.planMapper = planMapper;
        this.shortIdGenerator = shortIdGenerator;
        this.objectMapper = objectMapper;
        this.platformUsers = platformUsers;
    }

    // ==================== 元数据 / 统计 / 列表 / 详情 ====================

    /** 表单元数据（执行状态字典 + 调度负责人候选 + 当天日期） */
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("statuses", WaterUsageDict.dispatchStatusOptions());
        out.put("owners", platformUsers.options());
        out.put("today", LocalDate.now().toString());
        return out;
    }

    /** Tab 角标统计（全部/待执行/执行中/已完成/已取消） */
    public Map<String, Object> stats() {
        return WaterUsageSupport.statusStats(dispatchMapper.countByStatus(),
                WaterUsageDict::dispatchStatusCodeOf, "pending", "running", "done", "canceled");
    }

    /** 列表分页（status 传前端英文码 pending/running/done/canceled） */
    public Map<String, Object> page(int page, int size, String keyword, String status, String planId) {
        int p = WaterUsageSupport.clampPage(page);
        int s = WaterUsageSupport.clampSize(size);
        String kw = WaterUsageSupport.trim(keyword);
        String st = null;
        String unit = WaterUsageSupport.trim(status);
        if (unit != null) {
            st = WaterUsageDict.dispatchStatusOfCode(unit);
            if (st == null) {
                throw new IllegalArgumentException("执行状态参数不正确");
            }
        }
        String pid = WaterUsageSupport.trim(planId);
        long total = dispatchMapper.countDispatches(kw, st, pid);
        List<WaterDispatchVO> records = dispatchMapper.selectDispatchPage(kw, st, pid, s, (long) (p - 1) * s);
        for (WaterDispatchVO row : records) {
            decorate(row);
        }
        return WaterUsageSupport.pageResult(total, p, s, records);
    }

    /** 详情 */
    public WaterDispatchVO detail(String id) {
        String key = WaterUsageSupport.requireText(id, "指令 id 不能为空");
        WaterDispatchVO vo = dispatchMapper.selectDispatchById(key);
        if (vo == null) {
            throw new IllegalArgumentException("调度指令不存在");
        }
        decorate(vo);
        return vo;
    }

    /** 「关联用水计划」下拉（仅已生效；userId 非空时附该户配额：该户明细合计 / 已下达合计，单位 m³） */
    public List<PlanOptionVO> plans(String keyword, String userId) {
        List<PlanOptionVO> rows = dispatchMapper.selectActivePlanOptionsForPick(
                WaterUsageSupport.trim(keyword), WaterUsageSupport.trim(userId));
        for (PlanOptionVO row : rows) {
            row.setPlanTypeName(WaterUsageDict.planTypeName(row.getPlanType()));
        }
        return rows;
    }

    /** 「关联需水申请」下拉（仅已通过；可按用水户过滤；实现复用需水上报服务） */
    public List<DemandOptionVO> demands(String keyword, String userId) {
        return demandService.demands(keyword, userId);
    }

    /**
     * 「用水户」下拉（编制弹窗：仅正常状态）。planId 非空时仅返回该计划明细内的户——
     * 与 {@link #plans} 的按户收敛构成双向联动（选计划 → 户收敛；选户 → 计划收敛）。
     */
    public List<UserOptionVO> users(String keyword, String planId) {
        return userMapper.selectUserOptions(WaterUsageSupport.trim(keyword), WaterUsageSupport.trim(planId));
    }

    // ==================== 新增 / 编辑 / 调整 ====================

    /** 新增（待执行；填了户+计划+水量时校验配额并回写计划已下达水量） */
    @Transactional(rollbackFor = Exception.class)
    public WaterDispatchVO create(WaterDispatchSaveVO req) {
        Draft d = validate(req, null);
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        String id = shortIdGenerator.nextUUID(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("dispatch_no", String.format("DD%04d", dispatchMapper.selectMaxDispatchSeq() + 1));
        row.put("plan_id", d.planId);
        row.put("demand_id", d.demandId);
        row.put("water_user_id", d.userId);
        row.put("gate_id", d.gateId);
        row.put("plan_amount", d.planAmount);
        row.put("exec_time", d.execTime);
        row.put("gate_height_cm", d.gateHeightCm);
        row.put("start_time", d.execTime);
        row.put("owner", d.owner);
        row.put("remark", d.remark);
        row.put("now", now);
        row.put("operator", operator);
        dispatchMapper.insertDispatch(row);
        if (d.planId != null) {
            dispatchMapper.recalcPlanScheduled(d.planId, now, operator);
        }
        return detail(id);
    }

    /** 编辑（仅待执行且未计费：全字段；计划变更时旧/新计划已下达水量均回写） */
    @Transactional(rollbackFor = Exception.class)
    public WaterDispatchVO update(String id, WaterDispatchSaveVO req) {
        String key = WaterUsageSupport.requireText(id, "指令 id 不能为空");
        WaterDispatchVO current = dispatchMapper.selectDispatchById(key);
        if (current == null) {
            throw new IllegalArgumentException("调度指令不存在");
        }
        if (!WaterUsageDict.DISPATCH_STATUS_PENDING.equals(current.getStatus())) {
            throw new IllegalArgumentException("仅待执行状态的调度指令可编辑");
        }
        if (current.getBillId() != null) {
            throw new IllegalArgumentException("该指令已计费，请先删除关联账单后再编辑");
        }
        Draft d = validate(req, key);
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", key);
        row.put("plan_id", d.planId);
        row.put("demand_id", d.demandId);
        row.put("water_user_id", d.userId);
        row.put("gate_id", d.gateId);
        row.put("plan_amount", d.planAmount);
        row.put("exec_time", d.execTime);
        row.put("gate_height_cm", d.gateHeightCm);
        row.put("start_time", d.execTime);
        row.put("owner", d.owner);
        row.put("remark", d.remark);
        row.put("now", now);
        row.put("operator", operator);
        if (dispatchMapper.updateDispatch(row) == 0) {
            throw new IllegalArgumentException("指令状态已变更，请刷新后重试");
        }
        String oldPlanId = current.getPlanId();
        if (oldPlanId != null && !oldPlanId.equals(d.planId)) {
            dispatchMapper.recalcPlanScheduled(oldPlanId, now, operator);
        }
        if (d.planId != null) {
            dispatchMapper.recalcPlanScheduled(d.planId, now, operator);
        }
        return detail(key);
    }

    /**
     * 调整（水费页「编辑水量」：计划放水量 / 执行时间 / 备注）：未计费且未取消即可（不限执行态）；
     * 台账与待出账金额随新窗口实时重算、账期归属按新执行时间推导。
     * <p>execTime 非空时按新口径改写执行时间（并清空原执行时段止）；
     * 未传 execTime 时兼容旧口径 startTime / endTime。planAmount 未传则保持原值。
     */
    @Transactional(rollbackFor = Exception.class)
    public WaterDispatchVO adjust(String id, WaterDispatchSaveVO req) {
        String key = WaterUsageSupport.requireText(id, "指令 id 不能为空");
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        WaterDispatchVO current = dispatchMapper.selectDispatchById(key);
        if (current == null) {
            throw new IllegalArgumentException("调度指令不存在");
        }
        if (WaterUsageDict.DISPATCH_STATUS_CANCELED.equals(current.getStatus())) {
            throw new IllegalArgumentException("已取消的指令不可调整");
        }
        if (current.getBillId() != null) {
            throw new IllegalArgumentException("该指令已计费，请先删除关联账单后再调整");
        }
        BigDecimal planAmount = req.getPlanAmount() == null
                ? null
                : WaterUsageSupport.requirePositiveWan(req.getPlanAmount(), "计划放水量");
        String execText = WaterUsageSupport.trim(req.getExecTime());
        LocalDateTime execTime = execText == null
                ? null
                : WaterUsageSupport.parseDateTime(execText, "执行时间");
        LocalDateTime start = null;
        LocalDateTime end = null;
        if (execTime == null) {
            String startText = WaterUsageSupport.trim(req.getStartTime());
            start = startText == null ? null : WaterUsageSupport.parseDateTime(startText, "执行时段起");
            String endText = WaterUsageSupport.trim(req.getEndTime());
            end = endText == null ? null : WaterUsageSupport.parseDateTime(endText, "执行时段止");
            if (start != null && end != null && end.isBefore(start)) {
                throw new IllegalArgumentException("执行时段止不能早于执行时段起");
            }
        }
        String remark = WaterUsageSupport.maxLength(WaterUsageSupport.trim(req.getRemark()), 200, "备注");
        if (planAmount != null) {
            checkQuota(current.getPlanId(), current.getWaterUserId(), planAmount, key);
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", key);
        row.put("plan_amount", planAmount);
        row.put("exec_time", execTime);
        row.put("start_time", start);
        row.put("end_time", end);
        row.put("remark", remark);
        row.put("now", now);
        row.put("operator", operator);
        if (dispatchMapper.adjustDispatch(row) == 0) {
            throw new IllegalArgumentException("指令状态已变更，请刷新后重试");
        }
        if (current.getPlanId() != null) {
            dispatchMapper.recalcPlanScheduled(current.getPlanId(), now, operator);
        }
        return detail(key);
    }

    // ==================== 状态机 / 删除 ====================

    /** 状态流转：action = start（待执行→执行中）/ done（执行中→已完成）/ cancel（待执行/执行中→已取消） */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(String id, String action) {
        String key = WaterUsageSupport.requireText(id, "指令 id 不能为空");
        WaterDispatchVO current = dispatchMapper.selectDispatchById(key);
        if (current == null) {
            throw new IllegalArgumentException("调度指令不存在");
        }
        String act = WaterUsageSupport.requireText(action, "操作类型不能为空");
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        if ("start".equals(act)) {
            if (!WaterUsageDict.DISPATCH_STATUS_PENDING.equals(current.getStatus())) {
                throw new IllegalArgumentException("仅待执行指令可开始执行");
            }
            if (dispatchMapper.updateDispatchStatus(key, WaterUsageDict.DISPATCH_STATUS_PENDING,
                    WaterUsageDict.DISPATCH_STATUS_RUNNING, now, operator) == 0) {
                throw new IllegalArgumentException("指令状态已变更，请刷新后重试");
            }
            return;
        }
        if ("done".equals(act)) {
            if (!WaterUsageDict.DISPATCH_STATUS_RUNNING.equals(current.getStatus())) {
                throw new IllegalArgumentException("仅执行中指令可完成");
            }
            // 完成时写入关闸时刻（Mapper 内仅对 end_time 为空的新指令生效）
            if (dispatchMapper.updateDispatchStatus(key, WaterUsageDict.DISPATCH_STATUS_RUNNING,
                    WaterUsageDict.DISPATCH_STATUS_DONE, now, operator) == 0) {
                throw new IllegalArgumentException("指令状态已变更，请刷新后重试");
            }
            return;
        }
        if ("cancel".equals(act)) {
            if (current.getBillId() != null) {
                throw new IllegalArgumentException("该指令已计费，请先删除关联账单后再取消");
            }
            if (dispatchMapper.cancelDispatch(key, now, operator) == 0) {
                throw new IllegalArgumentException("仅待执行/执行中指令可取消");
            }
            if (current.getPlanId() != null) {
                dispatchMapper.recalcPlanScheduled(current.getPlanId(), now, operator);
            }
            return;
        }
        throw new IllegalArgumentException("不支持的操作类型：" + act);
    }

    /**
     * 删除（未计费即可，不限执行态）：记录挂接置空留痕后物理删除，计划已下达水量回写。
     * 已计费指令须先删除关联账单。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        String key = WaterUsageSupport.requireText(id, "指令 id 不能为空");
        WaterDispatchVO current = dispatchMapper.selectDispatchById(key);
        if (current == null) {
            throw new IllegalArgumentException("调度指令不存在");
        }
        if (current.getBillId() != null) {
            throw new IllegalArgumentException("该指令已计费，请先删除关联账单后再删除");
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        dispatchMapper.clearRecordDispatchRefs(key, now, operator);
        if (dispatchMapper.deleteDispatch(key) == 0) {
            throw new IllegalArgumentException("指令状态已变更，请刷新后重试");
        }
        if (current.getPlanId() != null) {
            dispatchMapper.recalcPlanScheduled(current.getPlanId(), now, operator);
        }
    }

    // ==================== 内部 ====================

    /** 校验产物：已归一化的入库值（m³ / LocalDateTime / trim 文本） */
    private static final class Draft {
        String planId;
        String demandId;
        String userId;
        String gateId;
        BigDecimal planAmount;
        LocalDateTime execTime;
        Integer gateHeightCm;
        String owner;
        String remark;
    }

    /** 编制/编辑共用校验：户（选填）正常与闸口归属、计划已生效、需水已通过且同户、执行时间、闸门高度、配额 */
    private Draft validate(WaterDispatchSaveVO req, String excludeId) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Draft d = new Draft();
        d.userId = WaterUsageSupport.trim(req.getWaterUserId());
        WaterUserVO user = null;
        if (d.userId != null) {
            user = userMapper.selectUserById(d.userId);
            if (user == null) {
                throw new IllegalArgumentException("用水户不存在");
            }
            if (!WaterUsageDict.USER_STATUS_NORMAL.equals(user.getStatus())) {
                throw new IllegalArgumentException("用水户已停用，不可下达调度指令");
            }
        }
        d.gateId = WaterUsageSupport.requireText(req.getGateId(), "请选择调度闸口");
        if (user != null) {
            GateConfig config = WaterUsageSupport.gateConfigOf(user.getGateConfigRaw(), objectMapper);
            if (config == null) {
                throw new IllegalArgumentException("该用水户未配置关联闸口，不可下达调度指令");
            }
            if (!config.contains(d.gateId)) {
                throw new IllegalArgumentException("调度闸口不属于该用水户的关联闸口组");
            }
        }
        d.planId = WaterUsageSupport.trim(req.getPlanId());
        if (d.planId != null) {
            WaterPlanVO plan = planMapper.selectPlanById(d.planId);
            if (plan == null) {
                throw new IllegalArgumentException("关联的用水计划不存在");
            }
            if (!WaterUsageDict.PLAN_STATUS_ACTIVE.equals(plan.getStatus())) {
                throw new IllegalArgumentException("仅已生效的用水计划可关联调度指令");
            }
        }
        d.demandId = WaterUsageSupport.trim(req.getDemandId());
        if (d.demandId != null) {
            WaterDemandVO demand = demandMapper.selectDemandById(d.demandId);
            if (demand == null) {
                throw new IllegalArgumentException("关联的需水上报单不存在");
            }
            if (!WaterUsageDict.DEMAND_STATUS_PASS.equals(demand.getStatus())) {
                throw new IllegalArgumentException("仅已通过的需水上报单可关联调度指令");
            }
            if (d.userId != null && !d.userId.equals(demand.getWaterUserId())) {
                throw new IllegalArgumentException("关联的需水上报单与所选用水户不一致");
            }
        }
        d.planAmount = WaterUsageSupport.optionalPositiveWan(req.getPlanAmount(), "计划放水量");
        d.execTime = WaterUsageSupport.parseDateTime(req.getExecTime(), "执行时间");
        d.gateHeightCm = req.getGateHeightCm();
        if (d.gateHeightCm != null && (d.gateHeightCm < 0 || d.gateHeightCm > 999)) {
            throw new IllegalArgumentException("闸门高度须在 0~999 cm 之间");
        }
        // 负责人：传 ownerUserId 时按平台用户回查固化姓名快照（不信任前端文本）；仅回传姓名（未换人/存量值）时按文本入库
        d.owner = platformUsers.resolveName(req.getOwnerUserId(), req.getOwner(), "调度负责人", false);
        d.remark = WaterUsageSupport.maxLength(WaterUsageSupport.trim(req.getRemark()), 200, "备注");
        checkQuota(d.planId, d.userId, d.planAmount, excludeId);
        return d;
    }

    /**
     * 配额校验：FOR UPDATE 锁计划行防并发；已下达合计（排除自身）+ 本次下达 ≤ 该计划该户计划水量合计。
     * 未关联计划、未选用水户或未填计划放水量时跳过。
     */
    private void checkQuota(String planId, String userId, BigDecimal planAmountM3, String excludeId) {
        if (planId == null || userId == null || planAmountM3 == null) {
            return;
        }
        if (dispatchMapper.lockPlanForUpdate(planId) == null) {
            throw new IllegalArgumentException("关联的用水计划不存在");
        }
        BigDecimal total = dispatchMapper.selectPlanUserTotal(planId, userId);
        BigDecimal used = dispatchMapper.selectActiveSumByPlanUser(planId, userId, excludeId);
        if (used.add(planAmountM3).compareTo(total) > 0) {
            throw new IllegalArgumentException("超出计划配额：该户关联计划水量 "
                    + WaterUsageSupport.formatWan(total) + " 万m³，已下达 "
                    + WaterUsageSupport.formatWan(used) + " 万m³，本次下达 "
                    + WaterUsageSupport.formatWan(planAmountM3) + " 万m³");
        }
    }

    /** 出口装饰：执行状态英文码 + 中文名、水量折算万m³（amount 为兼容别名）、已计费标记 */
    private void decorate(WaterDispatchVO vo) {
        if (vo == null) {
            return;
        }
        String raw = vo.getStatus();
        vo.setStatusName(WaterUsageDict.dispatchStatusName(raw));
        vo.setStatus(WaterUsageDict.dispatchStatusCodeOf(raw));
        vo.setBilled(vo.getBillId() != null);
        BigDecimal wan = WaterUsageSupport.toWan(vo.getPlanAmount());
        vo.setPlanAmount(wan);
        vo.setAmount(wan);
    }
}
