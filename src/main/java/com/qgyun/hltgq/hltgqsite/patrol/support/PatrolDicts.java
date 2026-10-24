package com.qgyun.hltgq.hltgqsite.patrol.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 巡检 / 问题字典（编码 → 中文）与计划展示态派生唯一实现点。
 * <p>巡检计划、巡检记录、问题记录三个服务原先各持一份同名 Map，取值逐字相同；
 * 其中巡检记录服务里的问题状态/处理方式字典只有 {@code #1#} 一项，导致记录详情中
 * 已处理（#2#~#5#）的问题状态直接显示原始编码。现统一收敛到本类，值仍为平台选项组原口径。
 * <p>展示态派生（{@link #displayStatus}）管理端与 H5 端原先各抄一份逐字相同的实现。
 * <p>失败/未知编码的中文兜底由 {@link com.qgyun.hltgq.hltgqsite.model.util.DictUtils#label} 负责。
 */
public final class PatrolDicts {

    /** 巡检结果编码 → 名称（选项组 IYBBDt3IqXJL71kqrYE） */
    public static final Map<String, String> RESULT_LABELS = labels(
            "#2#", "正常",
            "#3#", "异常",
            "#4#", "隐患",
            "#5#", "缺陷",
            "#6#", "故障");

    /** 巡检记录状态编码 → 名称（选项组 mITE3MttOBlt5Hb83Yw） */
    public static final Map<String, String> RECORD_STATUS_LABELS = labels(
            "#1#", "草稿",
            "#2#", "已提交");

    /** 问题状态编码 → 名称（与站点详情同口径） */
    public static final Map<String, String> ISSUE_STATUS_LABELS = labels(
            "#1#", "待处理",
            "#2#", "处理中",
            "#3#", "已转工单",
            "#4#", "已关闭",
            "#5#", "已作废");

    /** 问题处理方式编码 → 名称 */
    public static final Map<String, String> HANDLE_STATUS_LABELS = labels(
            "#1#", "待确认",
            "#2#", "转工单",
            "#3#", "直接处理");

    /** 风险等级编码 → 名称（选项组 Tzocc3TligWY99Y7QBo） */
    public static final Map<String, String> RISK_LABELS = labels(
            "#1#", "低风险",
            "#2#", "中风险",
            "#3#", "高风险");

    private PatrolDicts() {
    }

    /**
     * 巡查计划展示态派生：存储态「已发布 #2#」在尚未开始执行时展示为「未开始 #5#」，
     * 其余存储态原样返回（管理端列表与 H5 列表同一口径）。
     */
    public static String displayStatus(String storedStatus, boolean started) {
        if ("#2#".equals(storedStatus)) {
            return started ? "#2#" : "#5#";
        }
        return storedStatus;
    }

    /** 按「编码, 名称」成对入参构造只读有序字典。 */
    private static Map<String, String> labels(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
