package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单个闸孔的最近一次开闸区间（开闸 → 其后最近一次关闸）。
 *
 * <p>供闸门监测首页「开度」列展示：该列汇聚多个闸孔的开度，动作时间按孔逐行显示，
 * 避免多孔时只显示一个孔造成歧义。时间取 DZJL 动作表 action_time（设备时标）。
 */
@Data
public class GateActionVO {

    /** 闸孔号（如 1、2、3） */
    private String gateNo;

    /** 开闸时间（设备时标） */
    private LocalDateTime startTime;

    /** 关闸时间（设备时标）；null = 尚未收到关闸动作（进行中） */
    private LocalDateTime endTime;
}
