package com.qgyun.hltgq.hltgqsite.emergencysupport;

import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.PickerOptionVO;
import com.qgyun.hltgq.hltgqsite.model.util.DictUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应急保障字典（保障类别 pxsefg / 当前状态 gmypct / 编号序列）。
 * <p>编码取自低代码选项组（保障类别选项 / 当前状态选项），落库格式统一带井号
 * （如 {@code #nkzk#}），与既有 4 条历史数据一致；前端经 /emergency-support/meta 获取，不写死。
 * <p>「物资装备」是唯一需要与应急物资台账联动的类别（选择台账行自动带出数量）。
 */
public final class EmergencySupportDict {

    /** 编号序列类型（复用 water_order_no_seq；列宽 8 容得下） */
    public static final String SERIAL_TYPE = "YJBZ";

    /** 保障类别：物资装备（联动应急物资台账） */
    public static final String CATEGORY_MATERIAL = "#nkzk#";

    /** 当前状态：可用 */
    public static final String STATUS_USABLE = "#1#";

    /** 当前状态：停用 */
    public static final String STATUS_DISABLED = "#fwdj#";

    /** 保障类别编码 → 中文（顺序与选项组一致，直接用于下拉） */
    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    /** 当前状态编码 → 中文 */
    private static final Map<String, String> STATUSES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("#1#", "队伍专家");
        CATEGORIES.put("#nkzk#", "物资装备");
        CATEGORIES.put("#jcpi#", "通信电力");
        CATEGORIES.put("#ysfb#", "车辆交通");
        CATEGORIES.put("#lzyz#", "医疗后勤");
        CATEGORIES.put("#uvbu#", "资源调度");
        CATEGORIES.put("#flhd#", "培训演练");

        STATUSES.put(STATUS_USABLE, "可用");
        STATUSES.put(STATUS_DISABLED, "停用");
    }

    private EmergencySupportDict() {
    }

    /** 保障类别名称（未知编码原样返回，空返回 ''） */
    public static String categoryName(String code) {
        return DictUtils.nameOf(CATEGORIES, code);
    }

    /** 当前状态名称 */
    public static String statusName(String code) {
        return DictUtils.nameOf(STATUSES, code);
    }

    /** 保障类别编码校验 */
    public static boolean isValidCategory(String code) {
        return code != null && CATEGORIES.containsKey(code);
    }

    /** 当前状态编码校验 */
    public static boolean isValidStatus(String code) {
        return code != null && STATUSES.containsKey(code);
    }

    /** 保障类别归一：接受中文或编码，未知原样返回（交给校验报错） */
    public static String resolveCategory(String value) {
        return DictUtils.resolveCode(CATEGORIES, value);
    }

    /** 当前状态归一：接受中文或编码 */
    public static String resolveStatus(String value) {
        return DictUtils.resolveCode(STATUSES, value);
    }

    /** 是否物资装备（联动台账） */
    public static boolean isMaterialCategory(String code) {
        return CATEGORY_MATERIAL.equals(code);
    }

    /** 保障类别下拉选项 */
    public static List<PickerOptionVO> categoryOptions() {
        return options(CATEGORIES);
    }

    /** 当前状态下拉选项 */
    public static List<PickerOptionVO> statusOptions() {
        return options(STATUSES);
    }

    private static List<PickerOptionVO> options(Map<String, String> dict) {
        List<PickerOptionVO> list = new ArrayList<>();
        for (Map.Entry<String, String> e : dict.entrySet()) {
            PickerOptionVO vo = new PickerOptionVO();
            vo.setValue(e.getKey());
            vo.setLabel(e.getValue());
            list.add(vo);
        }
        return list;
    }
}
