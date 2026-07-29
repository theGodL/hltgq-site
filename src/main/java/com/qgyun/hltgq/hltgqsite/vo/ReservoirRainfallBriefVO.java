package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 水库雨情简报 VO
 * 每个站点在指定日期的日、旬、月三级雨量
 */
@Data
public class ReservoirRainfallBriefVO {

    /** 站点编号 */
    private String stcd;
    /** 站点名称 */
    private String stnm;
    /** 日雨量 (mm) */
    private BigDecimal dailyRainfall;
    /** 旬雨量 (mm) */
    private BigDecimal tenDayRainfall;
    /** 月雨量 (mm) */
    private BigDecimal monthlyRainfall;
}
