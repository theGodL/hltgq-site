package com.qgyun.hltgq.hltgqsite.model.util;

/**
 * 布尔值文本转换工具。
 * <p>数据库布尔字段统一使用 VARCHAR(10) 存储：{@code #1#}=是、{@code #2#}=否。
 */
public final class BoolTextUtils {

    /** 是 */
    public static final String TRUE = "#1#";

    /** 否 */
    public static final String FALSE = "#2#";

    private BoolTextUtils() {
    }

    /** 布尔值转文本：true→#1#、false→#2#，null 返回 null */
    public static String boolToText(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? TRUE : FALSE;
    }

    /** 文本转布尔值：#1#→true、#2#→false，其他返回 null */
    public static Boolean textToBool(String value) {
        if (value == null) {
            return null;
        }
        if (TRUE.equals(value)) {
            return Boolean.TRUE;
        }
        if (FALSE.equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * 接口布尔文本归一化：true/是/#1# → #1#；false/否/#2# → #2#；其他原样返回。
     */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if ("true".equalsIgnoreCase(text) || "是".equals(text) || TRUE.equals(text)) {
            return TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "否".equals(text) || FALSE.equals(text)) {
            return FALSE;
        }
        return value;
    }
}
