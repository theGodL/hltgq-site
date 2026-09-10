package com.qgyun.hltgq.hltgqsite.stationdetail.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 站点详情-设备信息列表行（表 t_auto_hltgq_water_device）。
 * <p>实时数据按设备监测类型（type 编码）取对应监测表最新值，由 Service 按
 * 闸门→水位→雨量→流量→墒情→水质→视频 的优先级取第一个可匹配类型；
 * 数值型走 value（BigDecimal 序列化保留尾零），文本型（视频巡检结果）走 textValue。
 */
@Data
public class DeviceVO {

    /** 设备主键 id */
    private String id;

    /** 设备编码（设备表 code，如 1000231$1$0$10） */
    private String code;

    /** 设备名称（设备表 name，如「南山寺节制闸1#」） */
    private String name;

    /** 设备类型（type 翻译文本，多类型「、」分隔，如「闸门、水位」） */
    private String type;

    /** 设备类型编码原文（type，如 #4#|#1#，权威值） */
    private String typeCodes;

    /** 所属闸口（由设备名解析：站点名前缀去除后剩余部分去 #，如「1」；无闸孔信息为 null） */
    private String gate;

    /** 设备状态（status 翻译：#1# 在线、#2# 离线） */
    private String status;

    /** 设备状态编码原文（status） */
    private String statusCode;

    /** 实时数据指标名（如「当前开度」「瞬时流量」，无实时数据为 null） */
    private String metric;

    /** 实时数据数值（-999/-9991 异常值已过滤，无有效值为 null） */
    private BigDecimal value;

    /** 实时数据文本值（视频巡检结果：正常/检出故障/检测异常；其他类型为 null） */
    private String textValue;

    /** 实时数据单位（如 m、mm、m³/s、%，文本型为 null） */
    private String unit;
}
