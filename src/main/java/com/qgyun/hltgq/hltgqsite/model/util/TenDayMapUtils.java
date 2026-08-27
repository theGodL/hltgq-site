package com.qgyun.hltgq.hltgqsite.model.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 旬标签排序映射工具（禁止硬编码散落各处）。
 * <p>TEN_DAY_MAP：5~10月 18旬（sort_order 1~18），用于需水预测、配水调度。
 * <p>FULL_TEN_DAY_MAP：1~12月 36旬（sort_order 1~36），用于中长期来水、水量损失（中长期）、水资源配置。
 */
public final class TenDayMapUtils {

    /** 灌溉旬映射（5~10月，18旬），sort_order 1~18 */
    public static final Map<String, Integer> TEN_DAY_MAP;

    /** 全年旬映射（1~12月，36旬），sort_order 1~36 */
    public static final Map<String, Integer> FULL_TEN_DAY_MAP;

    static {
        Map<String, Integer> tenDay = new LinkedHashMap<>(18);
        Map<String, Integer> fullTenDay = new LinkedHashMap<>(36);
        int order = 0;
        for (int month = 1; month <= 12; month++) {
            for (String phase : new String[]{"上", "中", "下"}) {
                order++;
                String label = month + "月" + phase + "旬";
                fullTenDay.put(label, order);
                if (month >= 5 && month <= 10) {
                    tenDay.put(label, order - 12);
                }
            }
        }
        TEN_DAY_MAP = Collections.unmodifiableMap(tenDay);
        FULL_TEN_DAY_MAP = Collections.unmodifiableMap(fullTenDay);
    }

    private TenDayMapUtils() {
    }

    /** 旬标签 → 灌溉旬排序值（1~18），未知标签返回 null */
    public static Integer sortOrderOf(String label) {
        return label == null ? null : TEN_DAY_MAP.get(label);
    }

    /** 旬标签 → 全年旬排序值（1~36），未知标签返回 null */
    public static Integer fullSortOrderOf(String label) {
        return label == null ? null : FULL_TEN_DAY_MAP.get(label);
    }

    /** 灌溉旬排序值（1~18）→ 旬标签，越界返回 null */
    public static String labelOf(int sortOrder) {
        for (Map.Entry<String, Integer> entry : TEN_DAY_MAP.entrySet()) {
            if (entry.getValue() == sortOrder) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** 全年旬排序值（1~36）→ 旬标签，越界返回 null */
    public static String fullLabelOf(int sortOrder) {
        for (Map.Entry<String, Integer> entry : FULL_TEN_DAY_MAP.entrySet()) {
            if (entry.getValue() == sortOrder) {
                return entry.getKey();
            }
        }
        return null;
    }
}
