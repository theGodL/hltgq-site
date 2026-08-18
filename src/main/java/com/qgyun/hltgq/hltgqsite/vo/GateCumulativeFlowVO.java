package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 闸站累计流量 VO（月累计 + 年累计）
 */
@Data
public class GateCumulativeFlowVO {

    /** 站点 UUID */
    private String siteId;

    /** 站点名称 */
    private String siteName;

    /** 月累计流量 (m³)：当月 1日 0点起至最新数据时间 = ttf(最新) − ttf(当月 1日前最近行) */
    private BigDecimal monthCumulativeFlow;

    /** 年累计流量 (m³)：当年 1月1日 0点起至最新数据时间（流量表 ytf） */
    private BigDecimal yearCumulativeFlow;
}
