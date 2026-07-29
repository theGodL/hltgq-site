package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 流量监测 VO（每个站点一条最新数据）
 */
@Data
public class FlowMonitoringVO {

    /** 站点编号 */
    private String stcd;

    /** 站点名称 */
    private String stnm;

    /** 监测时间 */
    private LocalDateTime tm;

    /** 流量 (m³/s) */
    private BigDecimal q;
}
