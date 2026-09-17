package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 闸站月度累计取水量（月度趋势图数据点）
 * <p>单位统一为万m³（库中 m³ 原值 ÷ 10000，3 位小数截断，见 WaterVolumeUtils）
 */
@Data
public class GateMonthCumulativeFlowVO {

    /** 月份（yyyy-MM，如 2025-09），按时间升序返回（最早在前） */
    private String month;

    /** 月累计取水量 (万m³)：当月 1日 0点起至月末（当月为截至最新数据时间）
     *  = ttf(月内最新) − ttf(月初前最近)，3 位小数截断；月内无 ttf 数据为 null */
    private BigDecimal cumulativeFlow;
}
