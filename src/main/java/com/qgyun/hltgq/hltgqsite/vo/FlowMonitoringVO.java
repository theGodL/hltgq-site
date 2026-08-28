package com.qgyun.hltgq.hltgqsite.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /** 累计流量 (m³)：默认（无起始时间）= 末行年累计 ytf；指定起始时间 = ttf(末行) − ttf(起点前一行) */
    private BigDecimal cumulativeFlow;

    /** 电压 (V)，取关联电压表 t_auto_hltgq_water_vol_info 最新值 */
    private BigDecimal vol;

    /** 年累计流量（内部计算用，不出 JSON） */
    @JsonIgnore
    private BigDecimal ytf;

    /** 总累计流量（内部计算用，不出 JSON） */
    @JsonIgnore
    private BigDecimal ttf;

    /** 起始时间前最近一条 ttf 非空行的总累计（内部计算用，不出 JSON） */
    @JsonIgnore
    private BigDecimal prevTtf;
}
