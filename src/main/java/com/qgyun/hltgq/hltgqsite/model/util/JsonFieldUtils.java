package com.qgyun.hltgq.hltgqsite.model.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 模型响应 JSON 字段解析辅助。
 * <p>中文业务字段原样解析；日期字符串兼容多种格式；缺失/NaN 统一返回 null。
 */
public final class JsonFieldUtils {

    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private JsonFieldUtils() {
    }

    /** 取字符串字段，缺失/空返回 null */
    public static String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** 兼容多个候选字段名，返回第一个非空文本 */
    public static String textOfAny(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = textOf(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** 取数值字段，缺失/null/NaN 返回 null */
    public static Double doubleOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        double number = value.asDouble();
        return Double.isNaN(number) || Double.isInfinite(number) ? null : number;
    }

    /** 兼容多个候选字段名，返回第一个非空数值 */
    public static Double doubleOfAny(JsonNode node, String... fields) {
        for (String field : fields) {
            Double value = doubleOf(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** 兼容 "2026-08-01"、"2026-08-01 00:00:00"、"2026-08-01T00:00:00" 等格式，解析失败返回 null */
    public static LocalDateTime parseDateTime(String text) {
        LocalDate date = parseDate(text);
        return date == null ? null : date.atStartOfDay();
    }

    /** 取日期字符串前 10 位解析为 LocalDate，解析失败返回 null */
    public static LocalDate parseDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String value = text.trim();
        if (value.length() > 10) {
            value = value.substring(0, 10);
        }
        try {
            return LocalDate.parse(value, DATE_ONLY);
        } catch (Exception e) {
            return null;
        }
    }

    /** 保留 2 位小数 */
    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
