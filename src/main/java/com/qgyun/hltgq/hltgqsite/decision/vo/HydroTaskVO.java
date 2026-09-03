package com.qgyun.hltgq.hltgqsite.decision.vo;

import lombok.Data;

/**
 * 防洪抗旱任务中间态（Redis 存储，TTL 24h，不落库）。
 * <p>status 枚举：calculating / completed / failed。
 */
@Data
public class HydroTaskVO {

    /** 任务 ID */
    private String id;

    /** 状态：calculating / completed / failed */
    private String status;

    /** 失败原因（failed 时有值，截断 500 字符） */
    private String errorMsg;

    /** 图数据（completed 后有值） */
    private HydroChartVO chart;
}
