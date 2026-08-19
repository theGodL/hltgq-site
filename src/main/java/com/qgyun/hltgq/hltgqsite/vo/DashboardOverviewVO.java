package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 设备状态概览（大屏统计）
 */
@Data
public class DashboardOverviewVO {

    /** 设备总数（设备表全部行） */
    private long totalDeviceCount;

    /** 在线设备数（所属站点 zebpsu=#1# 在线的设备，站点状态由报文入库项目维护） */
    private long onlineDeviceCount;

    /** 在线设备百分比（1 位小数 HALF_UP；设备总数为 0 时为 null） */
    private BigDecimal onlineDevicePercent;

    /** 闸门总数（近 24h 内有上报、排除站级行 gate_no='0' 与无信号/异常闸孔） */
    private long totalGateCount;

    /** 开启闸门数（各闸孔最新开度 > 0） */
    private long openGateCount;

    /** 闸门开启百分比（1 位小数 HALF_UP；闸门总数为 0 时为 null） */
    private BigDecimal openGatePercent;

    /** 未处理告警数（未关闭：#1# 未确认/#2# 已确认/#3# 处理中） */
    private long unhandledAlarmCount;
}
