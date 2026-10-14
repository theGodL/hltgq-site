package com.qgyun.hltgq.hltgqsite.waterusage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 灌区用水业务字典与周期口径（单一维护点，前端经 meta 接口获取，不写死）。
 * <p>字典编码统一 "#N#" 文本（与平台字段风格一致）：
 * 用水类型 / 计算策略 / 统计周期 / 用水户状态 / 计划类型 / 计划状态。
 * <p>闸口计算策略 JSON 结构（gate_config 列，与设计方案 2.1 一致）：
 * <pre>
 * 累加 {"mode":"SUM","gates":[{"id":"…","name":"…"},…]}
 * 差额 {"mode":"DIFF","main":{"id":"…","name":"…"},"deduct":[{…},…]}
 * </pre>
 * <p>计划周期由类型自动推导（年度=自然年；月度=自然月；旬度=上旬 1-10 / 中旬 11-20 / 下旬 21-自然月末），
 * 前后端同公式（前端以纯函数复制同一口径，见设计方案 2.4 裁剪版：不做 rules.json / 版本黄条）。
 */
public final class WaterUsageDict {

    /** 计算策略 JSON：mode 累加 */
    public static final String GATE_MODE_SUM = "SUM";
    /** 计算策略 JSON：mode 差额 */
    public static final String GATE_MODE_DIFF = "DIFF";

    /** 计算策略（列 calc_mode）：指定闸口累加 */
    public static final String CALC_MODE_SUM = "#1#";
    /** 计算策略（列 calc_mode）：闸口差额计量 */
    public static final String CALC_MODE_DIFF = "#2#";

    /** 用水类型 */
    private static final Map<String, String> USER_TYPES = new LinkedHashMap<>();
    /** 计算策略 */
    private static final Map<String, String> CALC_MODES = new LinkedHashMap<>();
    /** 统计周期 */
    private static final Map<String, String> SETTLE_CYCLES = new LinkedHashMap<>();
    /** 用水户状态 */
    private static final Map<String, String> USER_STATUS = new LinkedHashMap<>();
    /** 计划类型 */
    private static final Map<String, String> PLAN_TYPES = new LinkedHashMap<>();
    /** 计划状态 */
    private static final Map<String, String> PLAN_STATUS = new LinkedHashMap<>();
    /** 需水上报审核状态（#N# → 中文） */
    private static final Map<String, String> DEMAND_STATUS = new LinkedHashMap<>();
    /** 需水上报审核状态（#N# → 前端英文码 wait/pass/reject） */
    private static final Map<String, String> DEMAND_STATUS_CODE = new LinkedHashMap<>();
    /** 调度方案执行状态（#N# → 中文） */
    private static final Map<String, String> DISPATCH_STATUS = new LinkedHashMap<>();
    /** 调度方案执行状态（#N# → 前端英文码 pending/running/done/canceled） */
    private static final Map<String, String> DISPATCH_STATUS_CODE = new LinkedHashMap<>();
    /** 调度记录操作类型（#N# → 中文） */
    private static final Map<String, String> RECORD_TYPES = new LinkedHashMap<>();
    /** 调度记录操作类型（#N# → 前端英文码 up/down/adjust） */
    private static final Map<String, String> RECORD_TYPE_CODE = new LinkedHashMap<>();
    /** 账单状态（#N# → 中文） */
    private static final Map<String, String> BILL_STATUS = new LinkedHashMap<>();
    /** 账单状态（#N# → 前端英文码 generated/paid） */
    private static final Map<String, String> BILL_STATUS_CODE = new LinkedHashMap<>();

    /** 计划类型：年度 */
    public static final String PLAN_TYPE_YEAR = "#1#";
    /** 计划类型：月度 */
    public static final String PLAN_TYPE_MONTH = "#2#";
    /** 计划类型：旬度 */
    public static final String PLAN_TYPE_XUN = "#3#";

    /** 计划状态：编制中 */
    public static final String PLAN_STATUS_DRAFT = "#1#";
    /** 计划状态：已生效 */
    public static final String PLAN_STATUS_ACTIVE = "#2#";
    /** 计划状态：已归档 */
    public static final String PLAN_STATUS_ARCHIVED = "#3#";
    /** 计划状态：已作废 */
    public static final String PLAN_STATUS_VOIDED = "#4#";

    /** 用水户状态：正常 */
    public static final String USER_STATUS_NORMAL = "#1#";

    /** 需水上报审核状态：待审核 */
    public static final String DEMAND_STATUS_WAIT = "#1#";
    /** 需水上报审核状态：已通过 */
    public static final String DEMAND_STATUS_PASS = "#2#";
    /** 需水上报审核状态：已驳回 */
    public static final String DEMAND_STATUS_REJECT = "#3#";

    /** 调度方案执行状态：待执行 */
    public static final String DISPATCH_STATUS_PENDING = "#1#";
    /** 调度方案执行状态：执行中 */
    public static final String DISPATCH_STATUS_RUNNING = "#2#";
    /** 调度方案执行状态：已完成 */
    public static final String DISPATCH_STATUS_DONE = "#3#";
    /** 调度方案执行状态：已取消（不占配额、不参与出账） */
    public static final String DISPATCH_STATUS_CANCELED = "#4#";

    /** 调度记录操作类型：闸门提升 */
    public static final String RECORD_TYPE_UP = "#1#";
    /** 调度记录操作类型：闸门下降 */
    public static final String RECORD_TYPE_DOWN = "#2#";
    /** 调度记录操作类型：流量调整 */
    public static final String RECORD_TYPE_ADJUST = "#3#";

    /** 账单状态：已生成 */
    public static final String BILL_STATUS_GENERATED = "#1#";
    /** 账单状态：已缴清 */
    public static final String BILL_STATUS_PAID = "#2#";

    /** 统计周期：月度结算（对应 water_user.settle_cycle 列 #1#） */
    public static final String SETTLE_CYCLE_MONTH = "#1#";
    /** 统计周期：季度结算 */
    public static final String SETTLE_CYCLE_QUARTER = "#2#";
    /** 统计周期：年度结算 */
    public static final String SETTLE_CYCLE_YEAR = "#3#";

    static {
        USER_TYPES.put("#1#", "农业");
        USER_TYPES.put("#2#", "工业");
        USER_TYPES.put("#3#", "生活");
        USER_TYPES.put("#4#", "生态");

        CALC_MODES.put(CALC_MODE_SUM, "指定闸口累加");
        CALC_MODES.put(CALC_MODE_DIFF, "闸口差额计量");

        SETTLE_CYCLES.put("#1#", "月度结算");
        SETTLE_CYCLES.put("#2#", "季度结算");
        SETTLE_CYCLES.put("#3#", "年度结算");

        USER_STATUS.put("#1#", "正常");
        USER_STATUS.put("#2#", "停用");

        PLAN_TYPES.put(PLAN_TYPE_YEAR, "年度");
        PLAN_TYPES.put(PLAN_TYPE_MONTH, "月度");
        PLAN_TYPES.put(PLAN_TYPE_XUN, "旬度");

        PLAN_STATUS.put(PLAN_STATUS_DRAFT, "编制中");
        PLAN_STATUS.put(PLAN_STATUS_ACTIVE, "已生效");
        PLAN_STATUS.put(PLAN_STATUS_ARCHIVED, "已归档");
        PLAN_STATUS.put(PLAN_STATUS_VOIDED, "已作废");

        DEMAND_STATUS.put(DEMAND_STATUS_WAIT, "待审核");
        DEMAND_STATUS.put(DEMAND_STATUS_PASS, "已通过");
        DEMAND_STATUS.put(DEMAND_STATUS_REJECT, "已驳回");
        DEMAND_STATUS_CODE.put(DEMAND_STATUS_WAIT, "wait");
        DEMAND_STATUS_CODE.put(DEMAND_STATUS_PASS, "pass");
        DEMAND_STATUS_CODE.put(DEMAND_STATUS_REJECT, "reject");

        DISPATCH_STATUS.put(DISPATCH_STATUS_PENDING, "待执行");
        DISPATCH_STATUS.put(DISPATCH_STATUS_RUNNING, "执行中");
        DISPATCH_STATUS.put(DISPATCH_STATUS_DONE, "已完成");
        DISPATCH_STATUS.put(DISPATCH_STATUS_CANCELED, "已取消");
        DISPATCH_STATUS_CODE.put(DISPATCH_STATUS_PENDING, "pending");
        DISPATCH_STATUS_CODE.put(DISPATCH_STATUS_RUNNING, "running");
        DISPATCH_STATUS_CODE.put(DISPATCH_STATUS_DONE, "done");
        DISPATCH_STATUS_CODE.put(DISPATCH_STATUS_CANCELED, "canceled");

        RECORD_TYPES.put(RECORD_TYPE_UP, "闸门提升");
        RECORD_TYPES.put(RECORD_TYPE_DOWN, "闸门下降");
        RECORD_TYPES.put(RECORD_TYPE_ADJUST, "流量调整");
        RECORD_TYPE_CODE.put(RECORD_TYPE_UP, "up");
        RECORD_TYPE_CODE.put(RECORD_TYPE_DOWN, "down");
        RECORD_TYPE_CODE.put(RECORD_TYPE_ADJUST, "adjust");

        BILL_STATUS.put(BILL_STATUS_GENERATED, "已生成");
        BILL_STATUS.put(BILL_STATUS_PAID, "已缴清");
        BILL_STATUS_CODE.put(BILL_STATUS_GENERATED, "generated");
        BILL_STATUS_CODE.put(BILL_STATUS_PAID, "paid");
    }

    private WaterUsageDict() {
    }

    /** 用水类型名称 */
    public static String userTypeName(String code) {
        return nameOf(USER_TYPES, code);
    }

    /** 计算策略名称 */
    public static String calcModeName(String code) {
        return nameOf(CALC_MODES, code);
    }

    /** 统计周期名称 */
    public static String settleCycleName(String code) {
        return nameOf(SETTLE_CYCLES, code);
    }

    /** 用水户状态名称 */
    public static String userStatusName(String code) {
        return nameOf(USER_STATUS, code);
    }

    /** 计划类型名称 */
    public static String planTypeName(String code) {
        return nameOf(PLAN_TYPES, code);
    }

    /** 计划状态名称 */
    public static String planStatusName(String code) {
        return nameOf(PLAN_STATUS, code);
    }

    /** 用水类型编码合法性（农业/生态需灌溉面积，见 {@link #requireIrrigationArea}） */
    public static boolean isValidUserType(String code) {
        return USER_TYPES.containsKey(code);
    }

    /** 农业 / 生态类型灌溉面积必填 */
    public static boolean requireIrrigationArea(String userType) {
        return "#1#".equals(userType) || "#4#".equals(userType);
    }

    /** 计算策略编码合法性 */
    public static boolean isValidCalcMode(String code) {
        return CALC_MODES.containsKey(code);
    }

    /** 计算策略 → 闸口 JSON mode（#1# 指定闸口累加 → SUM；#2# 闸口差额计量 → DIFF；非法返回 null） */
    public static String gateModeOf(String calcMode) {
        if (CALC_MODE_SUM.equals(calcMode)) {
            return GATE_MODE_SUM;
        }
        if (CALC_MODE_DIFF.equals(calcMode)) {
            return GATE_MODE_DIFF;
        }
        return null;
    }

    /** 统计周期编码合法性 */
    public static boolean isValidSettleCycle(String code) {
        return SETTLE_CYCLES.containsKey(code);
    }

    /** 计划类型编码合法性 */
    public static boolean isValidPlanType(String code) {
        return PLAN_TYPES.containsKey(code);
    }

    /** 旬名（1 上旬 / 2 中旬 / 3 下旬），非法返回空串 */
    public static String xunName(Integer xun) {
        if (xun == null) {
            return "";
        }
        if (xun == 1) {
            return "上";
        }
        if (xun == 2) {
            return "中";
        }
        if (xun == 3) {
            return "下";
        }
        return "";
    }

    /**
     * 按计划类型推导周期起止（与服务端唯一口径一致；前端纯函数同公式）：
     * <ul>
     *   <li>#1# 年度：year-01-01 ~ year-12-31</li>
     *   <li>#2# 月度：自然月</li>
     *   <li>#3# 旬度：上旬 1-10 / 中旬 11-20 / 下旬 21-自然月末（自动处理 2 月及各小月）</li>
     * </ul>
     * 参数缺失或非法返回 null。
     */
    public static LocalDate[] periodOf(String planType, Integer year, Integer month, Integer xun) {
        if (planType == null || year == null || year < 2000 || year > 2100) {
            return null;
        }
        if (PLAN_TYPE_YEAR.equals(planType)) {
            return new LocalDate[]{LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)};
        }
        if (month == null || month < 1 || month > 12) {
            return null;
        }
        YearMonth ym = YearMonth.of(year, month);
        if (PLAN_TYPE_MONTH.equals(planType)) {
            return new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
        }
        if (PLAN_TYPE_XUN.equals(planType) && xun != null && xun >= 1 && xun <= 3) {
            if (xun == 1) {
                return new LocalDate[]{ym.atDay(1), ym.atDay(10)};
            }
            if (xun == 2) {
                return new LocalDate[]{ym.atDay(11), ym.atDay(20)};
            }
            return new LocalDate[]{ym.atDay(21), ym.atEndOfMonth()};
        }
        return null;
    }

    /** 计划名称自动生成（如「2026年10月中旬旬度调度计划」；规则缺失返回 null） */
    public static String autoPlanName(String planType, Integer year, Integer month, Integer xun) {
        if (periodOf(planType, year, month, xun) == null) {
            return null;
        }
        if (PLAN_TYPE_YEAR.equals(planType)) {
            return year + "年度调度计划";
        }
        if (PLAN_TYPE_MONTH.equals(planType)) {
            return year + "年" + month + "月月度调度计划";
        }
        return year + "年" + month + "月" + xunName(xun) + "旬旬度调度计划";
    }

    // ==================== 调度与水费四页面字典（接口对外输出前端英文码 + 中文名） ====================

    /** 需水上报审核状态名称 */
    public static String demandStatusName(String code) {
        return nameOf(DEMAND_STATUS, code);
    }

    /** 需水上报审核状态 → 前端英文码（wait/pass/reject） */
    public static String demandStatusCodeOf(String code) {
        return nameOf(DEMAND_STATUS_CODE, code);
    }

    /** 前端英文码 → 需水上报审核状态（非法返回 null） */
    public static String demandStatusOfCode(String englishCode) {
        return keyOf(DEMAND_STATUS_CODE, englishCode);
    }

    /** 调度方案执行状态名称 */
    public static String dispatchStatusName(String code) {
        return nameOf(DISPATCH_STATUS, code);
    }

    /** 调度方案执行状态 → 前端英文码（pending/running/done/canceled） */
    public static String dispatchStatusCodeOf(String code) {
        return nameOf(DISPATCH_STATUS_CODE, code);
    }

    /** 前端英文码 → 调度方案执行状态（非法返回 null） */
    public static String dispatchStatusOfCode(String englishCode) {
        return keyOf(DISPATCH_STATUS_CODE, englishCode);
    }

    /** 调度记录操作类型名称 */
    public static String recordTypeName(String code) {
        return nameOf(RECORD_TYPES, code);
    }

    /** 调度记录操作类型 → 前端英文码（up/down/adjust） */
    public static String recordTypeCodeOf(String code) {
        return nameOf(RECORD_TYPE_CODE, code);
    }

    /** 前端英文码 → 调度记录操作类型（非法返回 null） */
    public static String recordTypeOfCode(String englishCode) {
        return keyOf(RECORD_TYPE_CODE, englishCode);
    }

    /** 账单状态名称 */
    public static String billStatusName(String code) {
        return nameOf(BILL_STATUS, code);
    }

    /** 账单状态 → 前端英文码（generated/paid） */
    public static String billStatusCodeOf(String code) {
        return nameOf(BILL_STATUS_CODE, code);
    }

    /** 前端英文码 → 账单状态（非法返回 null） */
    public static String billStatusOfCode(String englishCode) {
        return keyOf(BILL_STATUS_CODE, englishCode);
    }

    /**
     * 统计周期（#N#）→ 前端英文码（month/quarter/year；口径与四页面一致）。
     * 非法值原样返回（运营数据容错，不抛异常）。
     */
    public static String settleCycleUnitOf(String settleCycle) {
        if (SETTLE_CYCLE_MONTH.equals(settleCycle)) {
            return "month";
        }
        if (SETTLE_CYCLE_QUARTER.equals(settleCycle)) {
            return "quarter";
        }
        if (SETTLE_CYCLE_YEAR.equals(settleCycle)) {
            return "year";
        }
        return settleCycle == null ? "" : settleCycle;
    }

    /**
     * 方案开始时间 + 统计周期 → 分期 key（口径与前端 cycleKeyOf 完全一致）：
     * 月度 2026-10 / 季度 2026-Q3 / 年度 2026。参数缺失返回 null。
     */
    public static String cycleKeyOf(LocalDateTime start, String settleCycle) {
        if (start == null || settleCycle == null) {
            return null;
        }
        int year = start.getYear();
        int month = start.getMonthValue();
        if (SETTLE_CYCLE_MONTH.equals(settleCycle)) {
            return year + "-" + (month < 10 ? "0" + month : String.valueOf(month));
        }
        if (SETTLE_CYCLE_QUARTER.equals(settleCycle)) {
            return year + "-Q" + ((month + 2) / 3);
        }
        if (SETTLE_CYCLE_YEAR.equals(settleCycle)) {
            return String.valueOf(year);
        }
        return null;
    }

    /** 当前日期所在分期 key（周期缺失返回 null） */
    public static String currentCycleKey(String settleCycle) {
        return cycleKeyOf(LocalDate.now().atStartOfDay(), settleCycle);
    }

    /**
     * 分期 key → 账期区间 [起, 止]（口径与前端 cycleRange 完全一致）：
     * 2026-10 → [10-01, 10-31]；2026-Q3 → [07-01, 09-30]；2026 → [01-01, 12-31]。非法返回 null。
     */
    public static LocalDate[] cycleRange(String cycleKey) {
        if (cycleKey == null || cycleKey.trim().isEmpty()) {
            return null;
        }
        String key = cycleKey.trim();
        try {
            int quarterIndex = key.indexOf("-Q");
            if (quarterIndex > 0) {
                int year = Integer.parseInt(key.substring(0, quarterIndex));
                int quarter = Integer.parseInt(key.substring(quarterIndex + 2));
                int startMonth = quarter * 3 - 2;
                int endMonth = quarter * 3;
                return new LocalDate[]{LocalDate.of(year, startMonth, 1), YearMonth.of(year, endMonth).atEndOfMonth()};
            }
            int dashIndex = key.indexOf('-');
            if (dashIndex > 0) {
                YearMonth ym = YearMonth.of(Integer.parseInt(key.substring(0, dashIndex)),
                        Integer.parseInt(key.substring(dashIndex + 1)));
                return new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
            }
            int year = Integer.parseInt(key);
            return new LocalDate[]{LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)};
        } catch (Exception e) {
            return null;
        }
    }

    /** 分期 key → 账单号期间码（2026-10 → 202610；2026-Q3 → 2026Q3；2026 → 2026） */
    public static String cycleCode(String cycleKey) {
        return cycleKey == null ? null : cycleKey.replace("-", "");
    }

    /** 分期 key → 中文标签（2026年10月 / 2026年第3季度 / 2026年度） */
    public static String cycleLabel(String cycleKey) {
        if (cycleKey == null || cycleKey.trim().isEmpty()) {
            return "";
        }
        String key = cycleKey.trim();
        int quarterIndex = key.indexOf("-Q");
        if (quarterIndex > 0) {
            return key.substring(0, quarterIndex) + "年第" + key.substring(quarterIndex + 2) + "季度";
        }
        int dashIndex = key.indexOf('-');
        if (dashIndex > 0) {
            int month = Integer.parseInt(key.substring(dashIndex + 1));
            return key.substring(0, dashIndex) + "年" + month + "月";
        }
        return key + "年度";
    }

    /** 字典 → 下拉选项（有序） */
    public static List<Map<String, String>> options(Map<String, String> dict) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map.Entry<String, String> e : dict.entrySet()) {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("value", e.getKey());
            o.put("label", e.getValue());
            out.add(o);
        }
        return out;
    }

    /** 用水类型选项 */
    public static List<Map<String, String>> userTypeOptions() {
        return options(USER_TYPES);
    }

    /** 计算策略选项 */
    public static List<Map<String, String>> calcModeOptions() {
        return options(CALC_MODES);
    }

    /** 统计周期选项 */
    public static List<Map<String, String>> settleCycleOptions() {
        return options(SETTLE_CYCLES);
    }

    /** 计划类型选项 */
    public static List<Map<String, String>> planTypeOptions() {
        return options(PLAN_TYPES);
    }

    /** 计划状态选项 */
    public static List<Map<String, String>> planStatusOptions() {
        return options(PLAN_STATUS);
    }

    /** 旬选项（1 上旬 / 2 中旬 / 3 下旬） */
    public static List<Map<String, Object>> xunOptions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("value", i);
            o.put("label", xunName(i) + "旬");
            out.add(o);
        }
        return out;
    }

    /** 需水上报审核状态选项（value = 前端英文码 wait/pass/reject） */
    public static List<Map<String, String>> demandStatusOptions() {
        return englishOptions(DEMAND_STATUS, DEMAND_STATUS_CODE);
    }

    /** 调度方案执行状态选项（value = 前端英文码） */
    public static List<Map<String, String>> dispatchStatusOptions() {
        return englishOptions(DISPATCH_STATUS, DISPATCH_STATUS_CODE);
    }

    /** 调度记录操作类型选项（value = 前端英文码 up/down/adjust） */
    public static List<Map<String, String>> recordTypeOptions() {
        return englishOptions(RECORD_TYPES, RECORD_TYPE_CODE);
    }

    /** 账单状态选项（value = 前端英文码 generated/paid） */
    public static List<Map<String, String>> billStatusOptions() {
        return englishOptions(BILL_STATUS, BILL_STATUS_CODE);
    }

    /** 统计周期选项（value = 前端英文码 month/quarter/year） */
    public static List<Map<String, String>> settleCycleUnitOptions() {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map.Entry<String, String> e : SETTLE_CYCLES.entrySet()) {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("value", settleCycleUnitOf(e.getKey()));
            o.put("label", e.getValue());
            out.add(o);
        }
        return out;
    }

    /** 字典 → 英文码选项（value = 前端英文码，label = 中文名） */
    private static List<Map<String, String>> englishOptions(Map<String, String> names, Map<String, String> codes) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map.Entry<String, String> e : names.entrySet()) {
            Map<String, String> o = new LinkedHashMap<>();
            o.put("value", codes.get(e.getKey()));
            o.put("label", e.getValue());
            out.add(o);
        }
        return out;
    }

    /** 值反查键（英文码 → #N#，未命中返回 null） */
    private static String keyOf(Map<String, String> dict, String value) {
        if (value == null) {
            return null;
        }
        for (Map.Entry<String, String> e : dict.entrySet()) {
            if (e.getValue().equals(value)) {
                return e.getKey();
            }
        }
        return null;
    }

    private static String nameOf(Map<String, String> dict, String code) {
        return dict.getOrDefault(code == null ? "" : code, code == null ? "" : code);
    }
}
