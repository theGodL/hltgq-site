package com.qgyun.hltgq.hltgqsite.model.util;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 旬标签与日期转换工具。
 * <p>旬首日期规则：上旬=1日、中旬=11日、下旬=21日。
 */
public final class TenDayDateUtils {

    /** 旬标签格式："5月上旬" / "10月下旬" */
    private static final Pattern PATTERN = Pattern.compile("^(\\d{1,2})月(上|中|下)旬$");

    private TenDayDateUtils() {
    }

    /**
     * 旬标签 + 年份 → 该旬第一天。
     * 如 ("5月上旬", 2026) → 2026-05-01；("10月下旬", 2026) → 2026-10-21。
     * 无法解析时返回 null。
     */
    public static LocalDate toFirstDate(String label, int year) {
        if (label == null) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(label.trim());
        if (!matcher.matches()) {
            return null;
        }
        int month = Integer.parseInt(matcher.group(1));
        int day;
        switch (matcher.group(2)) {
            case "上":
                day = 1;
                break;
            case "中":
                day = 11;
                break;
            case "下":
                day = 21;
                break;
            default:
                return null;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 日期 → 旬标签。
     * 如 2026-05-01 → "5月上旬"，2026-05-21 → "5月下旬"。
     */
    public static String toLabel(LocalDate date) {
        if (date == null) {
            return null;
        }
        String phase;
        int day = date.getDayOfMonth();
        if (day <= 10) {
            phase = "上";
        } else if (day <= 20) {
            phase = "中";
        } else {
            phase = "下";
        }
        return date.getMonthValue() + "月" + phase + "旬";
    }
}
