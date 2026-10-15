package com.qgyun.hltgq.hltgqsite.waterusage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageDict;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport.GateConfig;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterBillMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterDispatchMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterUserMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.BillPlanRowVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.DispatchExecRowVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.LedgerRowVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterBillVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterUserVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 水费账单服务（台账 / 明细 / 账单 / 出账 / 收款 / 删除）。
 * <p>计入水量计量统一走 {@link WaterUsageMeter}（与流量监测/灌溉用水、用水计划灌溉进度同源同一实现）：
 * 区间累计 = ttf(窗口内末行) − prevTtf(起点前最近一条 ttf 非空行)，
 * 起点前无基准按 0，哨兵值 -999/-9991 视为缺失；差额策略（calc_mode=#2#）扣减闸口取负参与合计。
 * <p>出账：按 (用水户, 分期 key) 分组拆分多张账单（1 账单 : N 方案），组内逐方案实时计量、
 * 净计入水量 ≤0 拦截；成功后以 billed_qty 快照逐行打标、bill_id 挂接；账单删除清空挂接、
 * 方案恢复未计费（实时重新计量）。
 * <p>台账 four 视图共用：已计费行取快照、未计费行实时计量；待出账方案无有效计量时标记 dataOk=false。
 * <p>编号 SF + 期间码 + '-' + 户号 + 4 位全局序号：MAX+1 取号、唯一索引兜底并发（冲突重试在 Controller 层）。
 * <p>数量口径：接口对外万m³（4 位小数），库内 m³；金额 = 计入水量(m³) × 水价(元/m³)。
 */
@Service
public class WaterBillService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 校验说明最多条数（超出折叠为「等 N 项」） */
    private static final int MAX_NOTES = 3;

    private final WaterBillMapper billMapper;

    private final WaterDispatchMapper dispatchMapper;

    private final WaterUserMapper userMapper;

    private final ShortIdGenerator shortIdGenerator;

    private final ObjectMapper objectMapper;

    private final WaterUsageMeter meter;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public WaterBillService(WaterBillMapper billMapper,
                            WaterDispatchMapper dispatchMapper,
                            WaterUserMapper userMapper,
                            ShortIdGenerator shortIdGenerator,
                            ObjectMapper objectMapper,
                            WaterUsageMeter meter) {
        this.billMapper = billMapper;
        this.dispatchMapper = dispatchMapper;
        this.userMapper = userMapper;
        this.shortIdGenerator = shortIdGenerator;
        this.objectMapper = objectMapper;
        this.meter = meter;
    }

    // ==================== 元数据 / 列表 / 详情 ====================

    /** 表单元数据（账单状态 + 统计周期字典 + 当天日期） */
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("statuses", WaterUsageDict.billStatusOptions());
        out.put("cycles", WaterUsageDict.settleCycleUnitOptions());
        out.put("today", LocalDate.now().toString());
        return out;
    }

    /** 账单列表分页（status 传前端英文码 generated/paid） */
    public Map<String, Object> page(int page, int size, String keyword, String status) {
        int p = WaterUsageSupport.clampPage(page);
        int s = WaterUsageSupport.clampSize(size);
        String kw = WaterUsageSupport.trim(keyword);
        String st = null;
        String unit = WaterUsageSupport.trim(status);
        if (unit != null) {
            st = WaterUsageDict.billStatusOfCode(unit);
            if (st == null) {
                throw new IllegalArgumentException("账单状态参数不正确");
            }
        }
        long total = billMapper.countBills(kw, st);
        List<WaterBillVO> records = billMapper.selectBillPage(kw, st, s, (long) (p - 1) * s);
        for (WaterBillVO row : records) {
            decorate(row);
        }
        return WaterUsageSupport.pageResult(total, p, s, records);
    }

    /** 账单列表统计条（全量汇总，无筛选参数：账单总数/计费总水量(万m³)/应收/已收/收缴率%） */
    public Map<String, Object> stats() {
        Map<String, Object> agg = billMapper.selectBillStats();
        BigDecimal amt = toDecimal(agg.get("amt"));
        BigDecimal paid = toDecimal(agg.get("paid"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("billCount", agg.get("cnt") == null ? 0L : ((Number) agg.get("cnt")).longValue());
        out.put("totalQty", WaterUsageSupport.toWan(toDecimal(agg.get("qty"))));
        out.put("totalAmount", amt);
        out.put("paidAmount", paid);
        out.put("collectRate", amt.signum() <= 0 ? 0
                : paid.multiply(HUNDRED).divide(amt, 0, RoundingMode.HALF_UP).intValue());
        return out;
    }

    /** 账单详情（含方案明细，计费快照） */
    public WaterBillVO detail(String id) {
        String key = WaterUsageSupport.requireText(id, "账单 id 不能为空");
        WaterBillVO vo = billMapper.selectBillById(key);
        if (vo == null) {
            throw new IllegalArgumentException("账单不存在");
        }
        decorate(vo);
        List<BillPlanRowVO> plans = billMapper.selectBillPlanRows(key);
        for (BillPlanRowVO plan : plans) {
            plan.setCycleKey(vo.getCycleKey());
            plan.setCycleLabel(WaterUsageDict.cycleLabel(vo.getCycleKey()));
            decoratePlanRow(plan);
        }
        vo.setPlans(plans);
        return vo;
    }

    // ==================== 台账 / 明细视图 ====================

    /** 台账（视图一：按用水户归集，一行为一户；含汇总 stat） */
    public Map<String, Object> ledger(String keyword) {
        String kw = WaterUsageSupport.trim(keyword);
        List<WaterUserVO> users = userMapper.selectLedgerUsers(kw);
        Map<String, Map<String, Object>> aggMap = new LinkedHashMap<>();
        for (Map<String, Object> row : billMapper.selectBillAggAll()) {
            Object uid = row.get("uid");
            if (uid != null) {
                aggMap.put(String.valueOf(uid), row);
            }
        }
        List<LedgerRowVO> records = new ArrayList<>();
        for (WaterUserVO user : users) {
            records.add(buildLedgerRow(user, aggMap.get(user.getId())));
        }
        BigDecimal pendingQty = BigDecimal.ZERO;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        BigDecimal billedTotal = BigDecimal.ZERO;
        long pendingCount = 0;
        for (LedgerRowVO row : records) {
            pendingQty = pendingQty.add(row.getPendingQty() == null ? BigDecimal.ZERO : row.getPendingQty());
            pendingAmount = pendingAmount.add(row.getPendingAmount() == null ? BigDecimal.ZERO : row.getPendingAmount());
            billedTotal = billedTotal.add(row.getBilledAmount() == null ? BigDecimal.ZERO : row.getBilledAmount());
            pendingCount += row.getPendingCount() == null ? 0 : row.getPendingCount();
        }
        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("farmerCount", records.size());
        stat.put("pendingCount", pendingCount);
        stat.put("pendingQty", pendingQty);
        stat.put("pendingAmount", pendingAmount);
        stat.put("billedTotal", billedTotal);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", records.size());
        out.put("records", records);
        out.put("stat", stat);
        return out;
    }

    /**
     * 户级待出账装配（用水户列表「待出账水量」列用：仅未计费未取消方案的实时计量合计，口径与台账 pendingQty 一致）。
     * <p>pendingCount = 未计费方案数（不分计量成败）；pendingQty = 有效计量带符号合计（m³，无有效计量按 0；
     * 前端按 {@code WaterRules.fmtWan} 折算万m³，与 annualPlanWater 同模式）；
     * 传入当前页用户，方案行一次批量查询后逐方案计量（与账单出账同源 WaterUsageMeter）。
     */
    public void fillPending(List<WaterUserVO> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        Map<String, WaterUserVO> byId = new LinkedHashMap<>();
        for (WaterUserVO user : users) {
            byId.put(user.getId(), user);
            user.setPendingQty(BigDecimal.ZERO);
            user.setPendingCount(0);
        }
        Map<String, GateConfig> configs = new LinkedHashMap<>();
        for (DispatchExecRowVO row : dispatchMapper.selectUnbilledDispatchRows(new ArrayList<>(byId.keySet()))) {
            WaterUserVO user = byId.get(row.getWaterUserId());
            if (user == null) {
                continue;
            }
            user.setPendingCount(user.getPendingCount() + 1);
            GateConfig config;
            if (configs.containsKey(row.getWaterUserId())) {
                config = configs.get(row.getWaterUserId());
            } else {
                config = WaterUsageSupport.gateConfigOf(user.getGateConfigRaw(), objectMapper);
                configs.put(row.getWaterUserId(), config);
            }
            if (config == null) {
                continue;
            }
            BigDecimal raw = meter.gateInterval(row.getGateId(), row.getStartTime(), row.getEndTime());
            if (raw == null || raw.signum() <= 0) {
                continue;
            }
            BigDecimal signed = WaterUsageMeter.isDeduct(config, row.getGateId()) ? raw.negate() : raw;
            user.setPendingQty(user.getPendingQty().add(signed));
        }
    }

    /** 户方案明细（视图二：单户全部未取消方案；未计费行实时计量、已计费行取快照） */
    public Map<String, Object> userPlans(String userId) {
        String key = WaterUsageSupport.requireText(userId, "用水户 id 不能为空");
        WaterUserVO user = userMapper.selectUserById(key);
        if (user == null) {
            throw new IllegalArgumentException("用水户不存在");
        }
        GateConfig config = WaterUsageSupport.gateConfigOf(user.getGateConfigRaw(), objectMapper);
        String settle = user.getSettleCycle();
        List<BillPlanRowVO> plans = dispatchMapper.selectActivePlansByUser(key);
        for (BillPlanRowVO plan : plans) {
            boolean billed = Boolean.TRUE.equals(plan.getBilled());
            if (!billed) {
                WaterUsageMeter.Result measured = meter.measure(
                        plan.getGateId(), plan.getStartTime(), plan.getEndTime());
                if (measured.getValue() == null) {
                    // 水量列留空，同时回传不可得原因（前端明细直接展示，避免只看到“没内容”）
                    plan.setMeterIssue(measured.getIssue());
                } else {
                    plan.setAmount(WaterUsageMeter.isDeduct(config, plan.getGateId())
                            ? measured.getValue().negate() : measured.getValue());
                }
            }
            plan.setSign(WaterUsageMeter.isDeduct(config, plan.getGateId()) ? -1 : 1);
            String cycleKey = WaterUsageDict.cycleKeyOf(tryParse(plan.getStartTime()), settle);
            plan.setCycleKey(cycleKey);
            plan.setCycleLabel(WaterUsageDict.cycleLabel(cycleKey));
            decoratePlanRow(plan);
        }
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("userNo", user.getUserNo());
        userInfo.put("name", user.getName());
        userInfo.put("waterPrice", user.getWaterPrice());
        userInfo.put("calcMode", user.getCalcMode());
        userInfo.put("calcModeName", WaterUsageDict.calcModeName(user.getCalcMode()));
        userInfo.put("gateText", config != null ? config.gateText : "未配置闸口");
        userInfo.put("settleCycle", WaterUsageDict.settleCycleUnitOf(settle));
        userInfo.put("settleCycleName", WaterUsageDict.settleCycleName(settle));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", userInfo);
        out.put("records", plans);
        return out;
    }

    // ==================== 出账 / 收款 / 删除 ====================

    /**
     * 出账（四个出账入口共用）：按 (用水户, 分期 key) 分组拆分多张账单。
     * <p>资格校验：存在、未取消、未计费；逐方案实时计量（差额扣减取负），
     * 组内净计入水量 ≤0 拦截；成功后写 billed_qty 快照并挂接 bill_id（并发抢标行数 0 回滚）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createBills(List<String> ids) {
        List<String> keys = normalizeIds(ids);
        List<WaterDispatchVO> rows = dispatchMapper.selectDispatchesByIds(keys);
        if (rows.size() != keys.size()) {
            throw new IllegalArgumentException("部分调度方案不存在，请刷新后重试");
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        Map<String, WaterUserVO> userCache = new LinkedHashMap<>();
        Map<String, GateConfig> configCache = new LinkedHashMap<>();
        Map<String, Group> groups = new LinkedHashMap<>();
        for (WaterDispatchVO row : rows) {
            if (WaterUsageDict.DISPATCH_STATUS_CANCELED.equals(row.getStatus())) {
                throw new IllegalArgumentException("方案 " + row.getDispatchNo() + " 已取消，不可出账");
            }
            if (row.getBillId() != null) {
                throw new IllegalArgumentException("方案 " + row.getDispatchNo()
                        + " 已计费（账单 " + row.getBillNo() + "），不可重复出账");
            }
            WaterUserVO user = userCache.get(row.getWaterUserId());
            if (user == null) {
                user = userMapper.selectUserById(row.getWaterUserId());
                if (user == null) {
                    throw new IllegalArgumentException("用水户不存在（方案 " + row.getDispatchNo() + "）");
                }
                userCache.put(row.getWaterUserId(), user);
            }
            String settle = user.getSettleCycle();
            if (settle == null || !WaterUsageDict.isValidSettleCycle(settle)) {
                throw new IllegalArgumentException("用水户 " + user.getName() + " 未配置统计周期，无法出账");
            }
            String cycleKey = WaterUsageDict.cycleKeyOf(
                    WaterUsageSupport.parseDateTime(row.getStartTime(), "执行时段起"), settle);
            if (cycleKey == null) {
                throw new IllegalArgumentException("方案 " + row.getDispatchNo() + " 账期推导失败");
            }
            GateConfig config = configCache.get(user.getId());
            if (config == null) {
                config = WaterUsageSupport.gateConfigOf(user.getGateConfigRaw(), objectMapper);
                if (config == null) {
                    throw new IllegalArgumentException("用水户 " + user.getName() + " 未配置关联闸口，无法出账");
                }
                configCache.put(user.getId(), config);
            }
            WaterUsageMeter.Result measured = meter.measure(
                    row.getGateId(), row.getStartTime(), row.getEndTime());
            BigDecimal raw = measured.getValue();
            if (raw == null) {
                throw new IllegalArgumentException("方案 " + row.getDispatchNo() + " 不可出账："
                        + measured.getIssue() + "（时段 " + row.getStartTime() + " ~ " + row.getEndTime()
                        + "，闸口 " + (row.getGateName() == null ? row.getGateId() : row.getGateName()) + "）");
            }
            if (raw.signum() <= 0) {
                throw new IllegalArgumentException("方案 " + row.getDispatchNo() + " 计入水量为 0 或负值（"
                        + raw.toPlainString() + " m³），请核对流量数据");
            }
            BigDecimal signed = WaterUsageMeter.isDeduct(config, row.getGateId()) ? raw.negate() : raw;
            String groupKey = row.getWaterUserId() + "|" + cycleKey;
            Group group = groups.get(groupKey);
            if (group == null) {
                group = new Group(user, cycleKey);
                groups.put(groupKey, group);
            }
            group.billings.add(new Billing(row.getId(), row.getDispatchNo(), signed));
            group.net = group.net.add(signed);
        }
        int seq = billMapper.selectMaxBillSeq();
        List<WaterBillVO> created = new ArrayList<>();
        for (Group group : groups.values()) {
            if (group.net.signum() <= 0) {
                throw new IllegalArgumentException("用水户 " + group.user.getName() + " "
                        + WaterUsageDict.cycleLabel(group.cycleKey) + " 净计入水量为 "
                        + WaterUsageSupport.formatWan(group.net) + " 万m³（≤0），不可出账，请核对各闸口流量数据");
            }
            BigDecimal price = group.user.getWaterPrice();
            if (price == null || price.signum() <= 0) {
                throw new IllegalArgumentException("用水户 " + group.user.getName() + " 未配置有效水价，无法出账");
            }
            LocalDate[] range = WaterUsageDict.cycleRange(group.cycleKey);
            if (range == null) {
                throw new IllegalArgumentException("账期区间推导失败：" + group.cycleKey);
            }
            BigDecimal quantity = WaterUsageSupport.scale2(group.net);
            BigDecimal amount = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
            seq++;
            String billId = shortIdGenerator.nextUUID(null);
            String billNo = "SF" + WaterUsageDict.cycleCode(group.cycleKey) + "-"
                    + group.user.getUserNo() + String.format("%04d", seq);
            Map<String, Object> billRow = new LinkedHashMap<>();
            billRow.put("id", billId);
            billRow.put("bill_no", billNo);
            billRow.put("water_user_id", group.user.getId());
            billRow.put("settle_cycle", group.user.getSettleCycle());
            billRow.put("cycle_key", group.cycleKey);
            billRow.put("period_start", range[0].toString());
            billRow.put("period_end", range[1].toString());
            billRow.put("quantity", quantity);
            billRow.put("price", price);
            billRow.put("amount", amount);
            billRow.put("now", now);
            billRow.put("operator", operator);
            billMapper.insertBill(billRow);
            for (Billing billing : group.billings) {
                if (dispatchMapper.markBilled(billing.dispatchId, billId,
                        WaterUsageSupport.scale2(billing.signed), now, operator) == 0) {
                    throw new IllegalArgumentException("方案 " + billing.dispatchNo
                            + " 已被其他出账操作占用，请刷新后重试");
                }
            }
            created.add(detail(billId));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("count", created.size());
        out.put("bills", created);
        return out;
    }

    /** 确认收款（仅已生成：已缴清 #2#、实收 = 应收、记录收款时间） */
    @Transactional(rollbackFor = Exception.class)
    public void pay(String id) {
        String key = WaterUsageSupport.requireText(id, "账单 id 不能为空");
        WaterBillVO bill = billMapper.selectBillById(key);
        if (bill == null) {
            throw new IllegalArgumentException("账单不存在");
        }
        if (WaterUsageDict.BILL_STATUS_PAID.equals(bill.getStatus())) {
            throw new IllegalArgumentException("该账单已缴清");
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        if (billMapper.payBill(key, now, operator) == 0) {
            throw new IllegalArgumentException("账单状态已变更，请刷新后重试");
        }
    }

    /** 删除（任意状态；方案挂接清空恢复未计费，可重新出账） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        String key = WaterUsageSupport.requireText(id, "账单 id 不能为空");
        WaterBillVO bill = billMapper.selectBillById(key);
        if (bill == null) {
            throw new IllegalArgumentException("账单不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        dispatchMapper.clearBillRefs(key, now, operator);
        if (billMapper.deleteBill(key) == 0) {
            throw new IllegalArgumentException("账单不存在，请刷新后重试");
        }
    }

    // ==================== 内部：台账行组装 ====================

    private LedgerRowVO buildLedgerRow(WaterUserVO user, Map<String, Object> agg) {
        LedgerRowVO row = new LedgerRowVO();
        row.setUserId(user.getId());
        row.setUserNo(user.getUserNo());
        row.setUserName(user.getName());
        row.setUserType(user.getUserType());
        row.setUserTypeName(WaterUsageDict.userTypeName(user.getUserType()));
        row.setCalcMode(user.getCalcMode());
        row.setStrategy(WaterUsageDict.calcModeName(user.getCalcMode()));
        String settle = user.getSettleCycle();
        row.setCycle(WaterUsageDict.settleCycleUnitOf(settle));
        row.setCycleName(WaterUsageDict.settleCycleName(settle));
        row.setPrice(user.getWaterPrice());
        GateConfig config = WaterUsageSupport.gateConfigOf(user.getGateConfigRaw(), objectMapper);
        row.setGateText(config != null ? config.gateText : "未配置闸口");
        String thisKey = WaterUsageDict.currentCycleKey(settle);
        BigDecimal thisQty = BigDecimal.ZERO;
        BigDecimal pendingQty = BigDecimal.ZERO;
        BigDecimal billedQtyM3 = BigDecimal.ZERO;
        int thisCount = 0;
        int pendingCount = 0;
        List<String> notes = new ArrayList<>();
        List<BillPlanRowVO> plans = dispatchMapper.selectActivePlansByUser(user.getId());
        for (BillPlanRowVO plan : plans) {
            String cycleKey = WaterUsageDict.cycleKeyOf(tryParse(plan.getStartTime()), settle);
            boolean billed = Boolean.TRUE.equals(plan.getBilled());
            BigDecimal signed = null;
            if (billed) {
                signed = plan.getAmount();
                if (signed != null) {
                    billedQtyM3 = billedQtyM3.add(signed);
                }
            } else {
                pendingCount++;
                if (config == null) {
                    if (!notes.contains("未配置关联闸口")) {
                        notes.add("未配置关联闸口");
                    }
                } else {
                    WaterUsageMeter.Result measured = meter.measure(
                            plan.getGateId(), plan.getStartTime(), plan.getEndTime());
                    BigDecimal raw = measured.getValue();
                    if (raw == null) {
                        notes.add(plan.getDispatchNo() + " " + measured.getIssue());
                    } else if (raw.signum() <= 0) {
                        notes.add(plan.getDispatchNo() + " 计量异常（" + raw.toPlainString() + " m³）");
                    } else {
                        signed = WaterUsageMeter.isDeduct(config, plan.getGateId()) ? raw.negate() : raw;
                        pendingQty = pendingQty.add(signed);
                    }
                }
            }
            if (thisKey != null && thisKey.equals(cycleKey)) {
                thisCount++;
                if (signed != null) {
                    thisQty = thisQty.add(signed);
                }
            }
        }
        row.setThisQty(WaterUsageSupport.toWan(thisQty));
        row.setThisCount(thisCount);
        row.setPendingQty(WaterUsageSupport.toWan(pendingQty));
        row.setPendingCount(pendingCount);
        row.setPendingAmount(user.getWaterPrice() == null ? null
                : pendingQty.multiply(user.getWaterPrice()).setScale(2, RoundingMode.HALF_UP));
        row.setBilledQty(WaterUsageSupport.toWan(billedQtyM3));
        row.setYearPlan(WaterUsageSupport.toWan(user.getAnnualPlanWater()));
        if (agg != null) {
            row.setBillCount(((Number) agg.get("cnt")).intValue());
            BigDecimal amt = toDecimal(agg.get("amt"));
            BigDecimal paid = toDecimal(agg.get("paid"));
            row.setBilledAmount(amt);
            row.setPaidAmount(paid);
            row.setOweAmount(amt.subtract(paid));
            row.setCollectRate(amt.signum() <= 0 ? 0
                    : paid.multiply(HUNDRED).divide(amt, 0, RoundingMode.HALF_UP).intValue());
        } else {
            row.setBillCount(0);
            row.setBilledAmount(BigDecimal.ZERO);
            row.setPaidAmount(BigDecimal.ZERO);
            row.setOweAmount(BigDecimal.ZERO);
            row.setCollectRate(0);
        }
        row.setDataOk(notes.isEmpty());
        if (notes.isEmpty()) {
            row.setDataNote(null);
        } else if (notes.size() > MAX_NOTES) {
            List<String> shown = new ArrayList<>(notes.subList(0, MAX_NOTES));
            shown.add("等 " + notes.size() + " 项");
            row.setDataNote(String.join("；", shown));
        } else {
            row.setDataNote(String.join("；", notes));
        }
        return row;
    }

    // ==================== 内部：通用 ====================

    /** 宽容解析（失败返回 null，用于周期推导等辅助场景） */
    private static LocalDateTime tryParse(String text) {
        try {
            return WaterUsageSupport.parseDateTime(text, "时间");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 任意数值 → BigDecimal（SUM 聚合列类型容错） */
    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }

    /** 入参归一：去空去重；为空报错 */
    private static List<String> normalizeIds(List<String> ids) {
        List<String> keys = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                String key = WaterUsageSupport.trim(id);
                if (key != null && !keys.contains(key)) {
                    keys.add(key);
                }
            }
        }
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("请选择要出账的调度方案");
        }
        return keys;
    }

    /** 出口装饰：账单状态英文码 + 中文名、周期英文码 + 中文标签、水量折算万m³ */
    private void decorate(WaterBillVO vo) {
        if (vo == null) {
            return;
        }
        String raw = vo.getStatus();
        vo.setStatusName(WaterUsageDict.billStatusName(raw));
        vo.setStatus(WaterUsageDict.billStatusCodeOf(raw));
        String settle = vo.getCycle();
        vo.setCycleName(WaterUsageDict.settleCycleName(settle));
        vo.setCycle(WaterUsageDict.settleCycleUnitOf(settle));
        vo.setCycleLabel(WaterUsageDict.cycleLabel(vo.getCycleKey()));
        vo.setQuantity(WaterUsageSupport.toWan(vo.getQuantity()));
    }

    /** 方案行装饰：执行状态英文码 + 中文名、水量折算万m³ */
    private void decoratePlanRow(BillPlanRowVO plan) {
        if (plan == null) {
            return;
        }
        String raw = plan.getStatus();
        plan.setStatusName(WaterUsageDict.dispatchStatusName(raw));
        plan.setStatus(WaterUsageDict.dispatchStatusCodeOf(raw));
        plan.setPlanAmount(WaterUsageSupport.toWan(plan.getPlanAmount()));
        plan.setAmount(WaterUsageSupport.toWan(plan.getAmount()));
    }

    /** 出账分组暂存：用水户 + 分期 key + 方案计量行（signed = 带符号计入水量 m³） */
    private static final class Group {
        final WaterUserVO user;
        final String cycleKey;
        final List<Billing> billings = new ArrayList<>();
        BigDecimal net = BigDecimal.ZERO;

        Group(WaterUserVO user, String cycleKey) {
            this.user = user;
            this.cycleKey = cycleKey;
        }
    }

    private static final class Billing {
        final String dispatchId;
        final String dispatchNo;
        final BigDecimal signed;

        Billing(String dispatchId, String dispatchNo, BigDecimal signed) {
            this.dispatchId = dispatchId;
            this.dispatchNo = dispatchNo;
            this.signed = signed;
        }
    }
}
