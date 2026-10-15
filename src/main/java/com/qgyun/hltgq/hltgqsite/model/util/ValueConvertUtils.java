package com.qgyun.hltgq.hltgqsite.model.util;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 数据库行值 → 目标类型的安全换算（唯一实现点）。
 * <p>历史上流量、闸门、墒情、水质、河道水情、灌溉取水等各 Service 与监控 Controller
 * 各抄一份私有的 {@code toBigDecimal(Object)}、{@code toLocalDateTime(Object)}、{@code str(Object)}、
 * {@code toDecimal(String)}、{@code parseStcds(String)}，实现逐字相同。
 * <p>本类为上述换算的唯一实现点：全部 null 安全，非法文本一律按 null 处理，不抛异常。
 */
public final class ValueConvertUtils {

    private ValueConvertUtils() {
    }

    /** Map 取值：null 安全转 String */
    public static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** Map 值 → BigDecimal（null 安全；非法文本按 null） */
    public static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 数值文本 → BigDecimal（空白/非法文本按 null） */
    public static BigDecimal decimalOf(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 分隔文本 → 列表（null/空白 → 空列表；逐项 trim 去空段） */
    public static List<String> split(String text, String regex) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String part : text.split(regex)) {
            String token = part.trim();
            if (!token.isEmpty()) {
                out.add(token);
            }
        }
        return out;
    }

    /** Map 值 → LocalDateTime（Timestamp/LocalDateTime 兼容；其余 null） */
    public static LocalDateTime dateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        return null;
    }
}
