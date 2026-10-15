package com.qgyun.hltgq.hltgqsite.model.util;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 全站入参校验与文本归一（唯一实现点）。
 * <p>各业务模块的 XxxSupport 保留门面并委托本类，禁止再各抄一份
 * （历史上 duty / inventoryorder / waterusage / watersaving 四个 Support 重复实现同名方法）。
 * <p>失败一律抛 {@link IllegalArgumentException}，由 GlobalExceptionHandler 转 400 +
 * {@code {"message": "…"}}，文案可直接前端上屏。
 */
public final class CheckUtils {

    private CheckUtils() {
    }

    /** 去首尾空白，空串按 null 处理 */
    public static String trim(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    /** 必填文本（trim 后为空报 400） */
    public static String requireText(String value, String message) {
        String s = trim(value);
        if (s == null) {
            throw new IllegalArgumentException(message);
        }
        return s;
    }

    /** 长度上限校验（列宽约束前置拦截；null 放过） */
    public static String maxLength(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(label + "不能超过 " + max + " 字");
        }
        return value;
    }

    /** 必填正数（>0、小数位不超过 maxScale；label 如「节水目标」「水价」） */
    public static BigDecimal requirePositive(BigDecimal value, String label, int maxScale) {
        if (value == null) {
            throw new IllegalArgumentException("请填写" + label);
        }
        if (value.scale() > maxScale) {
            throw new IllegalArgumentException(label + "最多保留 " + maxScale + " 位小数");
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(label + "必须大于 0");
        }
        return value;
    }

    /** 选填正数（null 放过；填写则 >0 且小数位不超过 maxScale） */
    public static BigDecimal optionalPositive(BigDecimal value, String label, int maxScale) {
        if (value == null) {
            return null;
        }
        return requirePositive(value, label, maxScale);
    }

    /** 必填非负数（≥0、小数位不超过 maxScale；水量/数量类） */
    public static BigDecimal requireNonNegative(BigDecimal value, String label, int maxScale) {
        if (value == null) {
            throw new IllegalArgumentException("请填写" + label);
        }
        if (value.scale() > maxScale) {
            throw new IllegalArgumentException(label + "最多保留 " + maxScale + " 位小数");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(label + "不能为负数");
        }
        return value;
    }

    /** 选填非负数（null 放过；填写则 ≥0、小数位不超过 maxScale） */
    public static BigDecimal optionalNonNegative(BigDecimal value, String label, int maxScale) {
        if (value == null) {
            return null;
        }
        return requireNonNegative(value, label, maxScale);
    }

    /** 年度校验（2000 ~ 2100，防御脏数据） */
    public static int requireYear(Integer year, String label) {
        if (year == null) {
            throw new IllegalArgumentException("请选择" + label);
        }
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException(label + "取值需在 2000 ~ 2100 之间");
        }
        return year;
    }

    /** 日期必填校验（yyyy-MM-dd；label 如「单据日期」「盘点日期」） */
    public static LocalDate requireDate(String value, String label) {
        String s = trim(value);
        if (s == null) {
            throw new IllegalArgumentException("请选择" + label);
        }
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "格式应为 yyyy-MM-dd：" + s);
        }
    }

    /** 日期参数归一（可空；yyyy-MM-dd，非法值报 400） */
    public static String normalizeDay(String value, String label) {
        String s = trim(value);
        if (s == null) {
            return null;
        }
        try {
            LocalDate.parse(s);
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "格式应为 yyyy-MM-dd：" + s);
        }
        return s;
    }
}
