package com.qgyun.hltgq.hltgqsite.inventoryorder;

import java.util.Arrays;
import java.util.List;

/**
 * 物资类别口径（库存管理 = 常用物资 / 应急物资管理 = 应急物资）。
 * <p>类别承载列取物资目录 {@code slaifi.type}（varchar，低代码物资类别字段）：
 * {@code #1#} 常用物资、{@code #2#} 应急物资，编码与低代码枚举一致。
 * <p>历史 21 条目录数据的 {@code type} 为 null 或空串，一律按「常用物资」处理；
 * 因此「常用物资」条件必须是「不等于应急物资」而不是「等于常用物资」。
 * <p>页面未带 scope（库存管理旧菜单）一律按 common 处理：库存管理只看常用物资，
 * 应急物资管理（scope=emergency）只看应急物资，两侧互不可见。
 * <p>SQL 片段要求所在查询把物资目录表 JOIN 成别名 {@code m}。
 */
public final class InventoryCategory {

    /** 类别物理列（slaifi."type"，低代码字段名 type，SQL 中以双引号包裹） */
    public static final String COLUMN = "type";

    /** 常用物资（低代码枚举编码） */
    public static final String COMMON = "#1#";

    /** 应急物资（低代码枚举编码） */
    public static final String EMERGENCY = "#2#";

    /** 常用物资显示名 */
    public static final String COMMON_LABEL = "常用物资";

    /** 应急物资显示名 */
    public static final String EMERGENCY_LABEL = "应急物资";

    /** 页面 scope 取值：库存管理 */
    public static final String SCOPE_COMMON = "common";

    /** 页面 scope 取值：应急物资管理 */
    public static final String SCOPE_EMERGENCY = "emergency";

    /**
     * 类别归一表达式（要求所在查询把物资目录表 JOIN 成别名 {@code m}）：
     * 去首尾空格，兼容旧版中文值，空/历史其他值一律按常用物资。
     * <p>SQL 条件与列表展示共用此表达式，保证「过滤口径」与「展示口径」同源。
     */
    public static final String NORMALIZED_EXPR =
            "CASE WHEN TRIM(COALESCE(m.\"" + COLUMN + "\", '')) IN ('" + EMERGENCY + "', '" + EMERGENCY_LABEL
                    + "') THEN '" + EMERGENCY + "' ELSE '" + COMMON + "' END";

    /** 仅应急物资。 */
    public static final String CONDITION_EMERGENCY = NORMALIZED_EXPR + " = '" + EMERGENCY + "'";

    /** 仅常用物资（与应急条件严格互补，任一取值恰好落在一侧、不漏行）。 */
    public static final String CONDITION_COMMON = NORMALIZED_EXPR + " = '" + COMMON + "'";

    private InventoryCategory() {
    }

    /** scope 归一：只认 emergency，其余（含 null/空/common）返回 null 表示「常用物资视角」。 */
    public static String normalizeScope(String scope) {
        if (scope == null) {
            return null;
        }
        String value = scope.trim().toLowerCase();
        return value.isEmpty() ? null : value;
    }

    /** 是否应急物资视角。 */
    public static boolean isEmergency(String scope) {
        return SCOPE_EMERGENCY.equals(normalizeScope(scope));
    }

    /** 是否「常用物资」视角（含未指定 scope：库存管理旧菜单按常用处理）。 */
    public static boolean isCommon(String scope) {
        return !isEmergency(scope);
    }

    /** 当前视角应有的类别（落库/校验用）。 */
    public static String scopeCategory(String scope) {
        return isEmergency(scope) ? EMERGENCY : COMMON;
    }

    /** 当前视角的类别显示名（报错文案用）。 */
    public static String scopeLabel(String scope) {
        return isEmergency(scope) ? EMERGENCY_LABEL : COMMON_LABEL;
    }

    /** 目录行的类别归一：空/null 视作常用物资；兼容旧版中文值（常用物资/应急物资）。 */
    public static String normalizeCategory(String category) {
        if (category == null) {
            return COMMON;
        }
        String value = category.trim();
        if (value.isEmpty() || COMMON_LABEL.equals(value)) {
            return COMMON;
        }
        if (EMERGENCY_LABEL.equals(value)) {
            return EMERGENCY;
        }
        return value;
    }

    /** 类别编码 → 中文显示名（未知值按常用物资展示）。 */
    public static String labelOf(String category) {
        return EMERGENCY.equals(normalizeCategory(category)) ? EMERGENCY_LABEL : COMMON_LABEL;
    }

    /** 目录行是否应急物资（唯一判定点：列表条件与校验必须同源，避免脏类别值两侧结论相反）。 */
    public static boolean isEmergencyCategory(String category) {
        return EMERGENCY.equals(normalizeCategory(category));
    }

    /**
     * 保存校验：物资类别必须与当前视角一致（库存管理拒绝应急物资，应急物资管理拒绝常用物资）。
     * <p>按「是否应急」比对而非「是否等于某个编码」：否则 type 为第三值（脏数据）时
     * 会同时命中「常用列表可见」与「常用详情被拒」。
     *
     * @param scope    页面视角（null/common/emergency）
     * @param category 物资目录类别（可空，空视作常用物资）
     * @param message  不一致时的报错文案
     */
    public static void requireCategory(String scope, String category, String message) {
        if (isEmergency(scope) != isEmergencyCategory(category)) {
            throw new IllegalArgumentException(message);
        }
    }

    /** 目录新增/编辑落库的类别：应急视角强制应急物资，其余只认两个合法编码。 */
    public static String resolveCategory(String scope, String requested) {
        if (isEmergency(scope)) {
            return EMERGENCY;
        }
        String value = normalizeCategory(requested);
        if (!COMMON.equals(value) && !EMERGENCY.equals(value)) {
            throw new IllegalArgumentException("物资类别只能是「" + COMMON_LABEL
                    + "」或「" + EMERGENCY_LABEL + "」");
        }
        return value;
    }

    /** 类别编码列表 → 下拉选项值（物资目录表单用，显示名走 {@link #labelOf}）。 */
    public static List<String> options() {
        return Arrays.asList(COMMON, EMERGENCY);
    }
}
