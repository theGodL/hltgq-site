package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 设备状态概览（大屏统计）
 * <p>两个统计对象都在设备台账（设备/闸孔），与 /network-device/summary 完全同源：
 * 设备数 = 台账全部行；闸门数 = 台账 type 含 #4# 的闸孔设备。
 */
@Data
public class DashboardOverviewVO {

    /** 设备总数（设备台账全部行；= 各分类之和 + 未分类，与 /network-device/summary 同源） */
    private long totalDeviceCount;

    /** 在线设备数（设备 status 优先、空回退所属站点 zebpsu=#1#；与 /network-device/summary 同源） */
    private long onlineDeviceCount;

    /** 在线设备百分比（2 位小数 HALF_UP；设备总数为 0 时为 null） */
    private BigDecimal onlineDevicePercent;

    /** 闸门设备总数（设备台账 type 含 #4# 的闸孔设备数，不限定是否有上报；与 /network-device/summary 闸门分类同源） */
    private long totalGateCount;

    /** 开启闸孔设备数（该设备近 24h 最新闸门开度 > 0，排除站级行与无信号/异常哨兵值） */
    private long openGateCount;

    /** 闸门设备开启百分比（2 位小数 HALF_UP；闸门设备总数为 0 时为 null） */
    private BigDecimal openGatePercent;

    /** 未处理告警数（未关闭：#1# 未确认/#2# 已确认/#3# 处理中） */
    private long unhandledAlarmCount;
}
