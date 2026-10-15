package com.qgyun.hltgq.hltgqsite.watersaving;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节水管理业务字典与计算口径（单一维护点，前端经 /water-saving/meta 获取，不写死）。
 * <p>字典编码沿用 "#N#" 文本（与平台字段/灌区用水一致）；布尔语义用 #1#/#2# 文本（项目惯例）。
 * <ul>
 *   <li>方案状态：草稿 →（核验确认对比条件）已完成；核验未确认 → 待复核；
 *       核验作废（依据变更）→ 待复核（方案A：verify_* 保留、verify_invalid=#1#）；</li>
 *   <li>指标计算方式固定三种（选定不可自由编辑）：节水量 万m³ / 节水率 % / 目标完成率 %；</li>
 *   <li>达标优先级链（与《节水潜力开发实施方案》V1.4 §2.2 一致，从高到低）：
 *       待核验 &gt; 待设置目标 &gt; 待关联指标 &gt; 无法计算(待复核) &gt; 不达标 &gt; 部分达标 &gt; 达标；</li>
 *   <li>水量一律万m³（6 位小数），潜力 = 实际 − 计划（可为负，不截断）。</li>
 * </ul>
 */
public final class SavingDict {

    /** 方案状态：草稿 */
    public static final String SCHEME_STATUS_DRAFT = "#1#";
    /** 方案状态：实施中 */
    public static final String SCHEME_STATUS_DOING = "#2#";
    /** 方案状态：已完成（核验确认对比条件后由核验动作驱动） */
    public static final String SCHEME_STATUS_DONE = "#3#";
    /** 方案状态：待复核（对比条件未确认 / 核验被作废） */
    public static final String SCHEME_STATUS_RECHECK = "#4#";

    /** 复核原因：对比条件未确认（核验时未勾选） */
    public static final String RECHECK_REASON_UNCONFIRMED = "对比条件未确认";
    /** 复核原因：核验依据已变更（年度记录水量被改 / 编辑换依据 / 刷新对比依据） */
    public static final String RECHECK_REASON_INVALIDATED = "核验依据已变更";
    /** 复核原因：指标无法计算（分母为 0，成果显示"待复核"） */
    public static final String RECHECK_REASON_DIV_ZERO = "指标无法计算";

    /** 指标计算方式：节水量（万m³）= 实施前 − 实施后 */
    public static final String METHOD_VOLUME = "#1#";
    /** 指标计算方式：节水率（%）= 节水量 ÷ 实施前 × 100 */
    public static final String METHOD_RATE = "#2#";
    /** 指标计算方式：目标完成率（%）= 节水量 ÷ 方案目标 × 100 */
    public static final String METHOD_TARGET = "#3#";

    /** 布尔语义（文本，项目惯例）：是 */
    public static final String YES = "#1#";
    /** 布尔语义（文本，项目惯例）：否 */
    public static final String NO = "#2#";

    /** 计划来源：用水计划自动带出（plan_id 留痕） */
    public static final String PLAN_SOURCE_AUTO = "auto_plan";
    /** 计划来源：手工录入 */
    public static final String PLAN_SOURCE_MANUAL = "manual";
    /** 实际来源：手工录入（本期唯一开放值） */
    public static final String ACTUAL_SOURCE_MANUAL = "manual";
    /** 实际来源：渠首自动计量（预留，本期不开放） */
    public static final String ACTUAL_SOURCE_CANAL = "canal_head_meter";

    /** 达标等级（方案级，接口输出 key）：达标 */
    public static final String ATTAIN_PASS = "pass";
    /** 达标等级：部分达标 */
    public static final String ATTAIN_PARTIAL = "partial";
    /** 达标等级：不达标 */
    public static final String ATTAIN_FAIL = "fail";
    /** 达标等级：待核验（未核验 / 核验作废 / 对比条件未确认） */
    public static final String ATTAIN_PENDING_VERIFY = "pending_verify";
    /** 达标等级：待设置目标（存在未设目标值的关联指标） */
    public static final String ATTAIN_PENDING_TARGET = "pending_target";
    /** 达标等级：待关联指标（未勾选评估指标，异常态） */
    public static final String ATTAIN_PENDING_INDICATOR = "pending_indicator";
    /** 达标等级：待复核（存在无法计算的指标，分母为 0） */
    public static final String ATTAIN_UNCOMPUTABLE = "uncomputable";

    /** 指标行展示态：达标 */
    public static final String ROW_PASS = "pass";
    /** 指标行展示态：不达标 */
    public static final String ROW_FAIL = "fail";
    /** 指标行展示态：待核验（未核验 / 核验作废 / 对比条件未确认，不计算结果） */
    public static final String ROW_PENDING = "pending";
    /** 指标行展示态：无法计算（分母为 0） */
    public static final String ROW_UNCOMPUTABLE = "uncomputable";
    /** 指标行展示态：待设置（指标未维护目标值） */
    public static final String ROW_NO_TARGET = "no_target";

    /** 达标位数口径：比率类（%）计算与展示保留 2 位 */
    private static final int PERCENT_SCALE = 2;
    /** 节水量结果保留位数（与库内水量口径一致） */
    private static final int VOLUME_SCALE = 6;

    /** 方案状态（#N# → 中文） */
    private static final Map<String, String> SCHEME_STATUS = new LinkedHashMap<>();
    /** 指标计算方式（#N# → 中文） */
    private static final Map<String, String> METHODS = new LinkedHashMap<>();
    /** 指标计算方式（#N# → 单位） */
    private static final Map<String, String> METHOD_UNITS = new LinkedHashMap<>();
    /** 指标计算方式（#N# → 公式文案） */
    private static final Map<String, String> METHOD_FORMULAS = new LinkedHashMap<>();
    /** 达标等级（key → 中文） */
    private static final Map<String, String> ATTAIN_LABELS = new LinkedHashMap<>();
    /** 指标行展示态（key → 中文） */
    private static final Map<String, String> ROW_LABELS = new LinkedHashMap<>();

    static {
        SCHEME_STATUS.put(SCHEME_STATUS_DRAFT, "草稿");
        SCHEME_STATUS.put(SCHEME_STATUS_DOING, "实施中");
        SCHEME_STATUS.put(SCHEME_STATUS_DONE, "已完成");
        SCHEME_STATUS.put(SCHEME_STATUS_RECHECK, "待复核");

        METHODS.put(METHOD_VOLUME, "节水量");
        METHODS.put(METHOD_RATE, "节水率");
        METHODS.put(METHOD_TARGET, "目标完成率");

        METHOD_UNITS.put(METHOD_VOLUME, "万m³");
        METHOD_UNITS.put(METHOD_RATE, "%");
        METHOD_UNITS.put(METHOD_TARGET, "%");

        METHOD_FORMULAS.put(METHOD_VOLUME, "实施前 − 实施后");
        METHOD_FORMULAS.put(METHOD_RATE, "（实施前 − 实施后）÷ 实施前 × 100%");
        METHOD_FORMULAS.put(METHOD_TARGET, "（实施前 − 实施后）÷ 方案目标 × 100%");

        ATTAIN_LABELS.put(ATTAIN_PASS, "达标");
        ATTAIN_LABELS.put(ATTAIN_PARTIAL, "部分达标");
        ATTAIN_LABELS.put(ATTAIN_FAIL, "不达标");
        ATTAIN_LABELS.put(ATTAIN_PENDING_VERIFY, "待核验");
        ATTAIN_LABELS.put(ATTAIN_PENDING_TARGET, "待设置目标");
        ATTAIN_LABELS.put(ATTAIN_PENDING_INDICATOR, "待关联指标");
        ATTAIN_LABELS.put(ATTAIN_UNCOMPUTABLE, "待复核");

        ROW_LABELS.put(ROW_PASS, "达标");
        ROW_LABELS.put(ROW_FAIL, "不达标");
        ROW_LABELS.put(ROW_PENDING, "待核验");
        ROW_LABELS.put(ROW_UNCOMPUTABLE, "无法计算");
        ROW_LABELS.put(ROW_NO_TARGET, "待设置");
    }

    private SavingDict() {
    }

    // ==================== 字典名称 ====================

    /** 方案状态中文名 */
    public static String schemeStatusName(String code) {
        String name = SCHEME_STATUS.get(code);
        return name == null ? "未知" : name;
    }

    /** 指标计算方式中文名（节水量/节水率/目标完成率） */
    public static String methodName(String code) {
        String name = METHODS.get(code);
        return name == null ? "未知" : name;
    }

    /** 指标单位（万m³ / %） */
    public static String methodUnit(String code) {
        String unit = METHOD_UNITS.get(code);
        return unit == null ? "" : unit;
    }

    /** 指标计算公式文案 */
    public static String methodFormula(String code) {
        String formula = METHOD_FORMULAS.get(code);
        return formula == null ? "" : formula;
    }

    /** 达标等级中文名（方案级） */
    public static String attainLabel(String level) {
        String label = ATTAIN_LABELS.get(level);
        return label == null ? "待核验" : label;
    }

    /** 指标行展示态中文名 */
    public static String rowLabel(String state) {
        String label = ROW_LABELS.get(state);
        return label == null ? "待核验" : label;
    }

    /** 指标计算方式是否为合法三值 */
    public static boolean isValidMethod(String code) {
        return METHODS.containsKey(code);
    }

    /** 布尔语义标记归一（#1# 是 / 其余按 #2# 否；新建方案/记录默认正式） */
    public static String normalizeFlag(String raw) {
        return YES.equals(raw) ? YES : NO;
    }

    /** 计划来源中文名（auto_plan 用水计划带出 / manual 手工录入） */
    public static String planSourceName(String code) {
        if (PLAN_SOURCE_AUTO.equals(code)) {
            return "用水计划带出";
        }
        if (PLAN_SOURCE_MANUAL.equals(code)) {
            return "手工录入";
        }
        return "未知";
    }

    /** 实际来源中文名（manual 手工录入 / canal_head_meter 渠首自动计量） */
    public static String actualSourceName(String code) {
        if (ACTUAL_SOURCE_MANUAL.equals(code)) {
            return "手工录入";
        }
        if (ACTUAL_SOURCE_CANAL.equals(code)) {
            return "渠首自动计量";
        }
        return "未知";
    }

    // ==================== 字典选项（meta 下发） ====================

    /** 方案状态选项（value/label） */
    public static List<Map<String, Object>> schemeStatusOptions() {
        return options(SCHEME_STATUS);
    }

    /** 指标计算方式选项（value/label/unit/formula） */
    public static List<Map<String, Object>> methodOptions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, String> e : METHODS.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", e.getKey());
            item.put("label", e.getValue());
            item.put("unit", methodUnit(e.getKey()));
            item.put("formula", methodFormula(e.getKey()));
            out.add(item);
        }
        return out;
    }

    /** 表单元数据（状态/计算方式/来源/标记字典；前端不写死） */
    public static Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemeStatuses", schemeStatusOptions());
        out.put("methods", methodOptions());
        Map<String, Object> sources = new LinkedHashMap<>();
        sources.put("planAuto", PLAN_SOURCE_AUTO);
        sources.put("planManual", PLAN_SOURCE_MANUAL);
        sources.put("actualManual", ACTUAL_SOURCE_MANUAL);
        out.put("sources", sources);
        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("yes", YES);
        flags.put("no", NO);
        out.put("flags", flags);
        List<Map<String, Object>> reasons = new ArrayList<>();
        reasons.add(reasonItem(RECHECK_REASON_UNCONFIRMED));
        reasons.add(reasonItem(RECHECK_REASON_DIV_ZERO));
        reasons.add(reasonItem(RECHECK_REASON_INVALIDATED));
        out.put("recheckReasons", reasons);
        return out;
    }

    private static Map<String, Object> reasonItem(String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", text);
        return item;
    }

    private static List<Map<String, Object>> options(Map<String, String> dict) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, String> e : dict.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", e.getKey());
            item.put("label", e.getValue());
            out.add(item);
        }
        return out;
    }

    // ==================== 计算口径（服务端唯一实现） ====================

    /**
     * 潜力 = 上一年实际放水量 − 上一年计划放水量（万m³）。
     * <p>方案口径：可为负、不截断；任一为空 → null（不可计算，禁止建方案）。
     */
    public static BigDecimal potential(BigDecimal prevPlanVolume, BigDecimal prevActualVolume) {
        if (prevPlanVolume == null || prevActualVolume == null) {
            return null;
        }
        return prevActualVolume.subtract(prevPlanVolume);
    }

    /** 节水量 = 实施前 − 实施后（万m³） */
    public static BigDecimal savedVolume(BigDecimal baseline, BigDecimal afterVolume) {
        if (baseline == null || afterVolume == null) {
            return null;
        }
        return baseline.subtract(afterVolume).setScale(VOLUME_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 指标结果按计算方式求值（分母为 0 / 入参缺失 → null，展示"无法计算"/"待核验"）：
     * <pre>
     * 节水量(#1#)     = baseline − after（万m³，6 位小数）
     * 节水率(#2#)     = (baseline − after) ÷ baseline × 100（%，2 位小数；baseline = 0 → null）
     * 目标完成率(#3#) = (baseline − after) ÷ 方案目标 × 100（%，2 位小数；方案目标 = 0 → null）
     * </pre>
     */
    public static BigDecimal methodResult(String method, BigDecimal baseline, BigDecimal afterVolume,
                                          BigDecimal schemeTarget) {
        BigDecimal saved = savedVolume(baseline, afterVolume);
        if (saved == null) {
            return null;
        }
        if (METHOD_VOLUME.equals(method)) {
            return saved;
        }
        BigDecimal denominator;
        if (METHOD_RATE.equals(method)) {
            denominator = baseline;
        } else if (METHOD_TARGET.equals(method)) {
            denominator = schemeTarget;
        } else {
            return null;
        }
        if (denominator == null || denominator.signum() == 0) {
            return null;
        }
        return saved.multiply(new BigDecimal("100"))
                .divide(denominator, PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
