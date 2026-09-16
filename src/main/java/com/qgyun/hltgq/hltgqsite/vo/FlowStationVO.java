package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 流量图表 VO（固定八站瞬时流量，按日期时间点查询）
 * 时间点半小时内无入库报文时流量为 null（前端展示 '-'，表示该时间点无报文）
 */
@Data
public class FlowStationVO {

    /** 站点 ID（站点表 UUID，对应流量表 site） */
    private String id;

    /** 测站编码（部分站点无 stcd 时为空） */
    private String stcd;

    /** 站点名称 */
    private String name;

    /** 瞬时流量 (m³/s) */
    private BigDecimal q;
}
