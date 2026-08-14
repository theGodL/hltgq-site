package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 流量监测 VO（每个站点一条最新数据）
 */
@Data
public class FlowMonitoringVO {

    /** 站点编号（MQTT 站点无编号时为 null，前端留空显示） */
    private String stcd;

    /** 站点标识（stcd 或 site UUID，无编号站点查询/筛选用，不用于展示） */
    private String site;

    /** 站点名称 */
    private String stnm;

    /** 监测时间 */
    private LocalDateTime tm;

    /** 流量 (m³/s) */
    private BigDecimal q;

    /** 累计流量 (万 m³) */
    private BigDecimal tf;
}
