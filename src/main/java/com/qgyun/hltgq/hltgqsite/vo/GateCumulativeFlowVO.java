package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 闸站累计流量 VO（月累计 + 年累计）
 * <p>单位统一为万m³（库中 m³ 原值 ÷ 10000，3 位小数截断，见 WaterVolumeUtils）
 */
@Data
public class GateCumulativeFlowVO {

    /** 站点 UUID */
    private String siteId;

    /** 站点名称（站点表未查到为 null） */
    private String siteName;

    /** 月累计流量 (万m³)：当月 1日 0点起至最新数据时间 = ttf(最新) − ttf(当月 1日前最近行) */
    private BigDecimal monthCumulativeFlow;

    /** 年累计流量 (万m³)：当年 1月1日 0点起至最新数据时间（流量表 ytf） */
    private BigDecimal yearCumulativeFlow;
}
