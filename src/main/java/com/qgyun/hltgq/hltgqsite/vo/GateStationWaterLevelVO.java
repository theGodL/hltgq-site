package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 闸站图表 VO（固定七站闸前/闸后水位，按日期时间点查询）
 * 水位为 -999 时表示设备异常（前端展示 '--'），后端透传不拦截；
 * 时间点半小时内无入库数据时水位为 null（前端展示 '-'，表示该时间点无报文）
 */
@Data
public class GateStationWaterLevelVO {

    /** 站点 ID（站点表 UUID，对应闸门表 site） */
    private String id;

    /** 测站编码（部分站点无 stcd 时为空） */
    private String stcd;

    /** 闸站名称 */
    private String name;

    /** 闸前水位（上游） */
    private BigDecimal upZ;

    /** 闸后水位（下游） */
    private BigDecimal downZ;
}
