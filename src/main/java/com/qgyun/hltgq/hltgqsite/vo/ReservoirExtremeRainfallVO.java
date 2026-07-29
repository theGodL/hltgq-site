package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 水库极值雨情 VO
 * 每个站点在不同时间窗口内的最大降雨量
 */
@Data
public class ReservoirExtremeRainfallVO {

    /** 站点编号 */
    private String stcd;
    /** 站点名称 */
    private String stnm;
    /** 最大 3 小时降雨量 (mm) */
    private BigDecimal max3h;
    /** 最大 6 小时降雨量 (mm) */
    private BigDecimal max6h;
    /** 最大 24 小时降雨量 (mm) */
    private BigDecimal max24h;
    /** 最大 2 天降雨量 (mm) */
    private BigDecimal max2d;
    /** 最大 3 天降雨量 (mm) */
    private BigDecimal max3d;
    /** 最大 7 天降雨量 (mm) */
    private BigDecimal max7d;
}
