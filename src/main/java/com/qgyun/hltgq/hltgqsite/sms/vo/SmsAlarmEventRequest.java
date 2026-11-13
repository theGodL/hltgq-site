package com.qgyun.hltgq.hltgqsite.sms.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 告警回调请求（mq → hltgq-site，POST /sms/alarm-event）。
 * <p>字段口径见《短信告警回调接口.md》：alertId 必填；metric=rain 时 threshold/hour 必填；
 * metric=water_level 时仅 alertId/stcd 必需（1 小时涨幅由本系统归纳）。
 */
@Data
public class SmsAlarmEventRequest {

    /** 告警表 t_auto_hltgq_water_alert.id（幂等键） */
    private String alertId;

    /** rain=雨量告警 / water_level=水位告警 */
    private String metric;

    /** 告警站点编码（站点档案 iofhpi 新编号，如 3206400001 / 9000000003；老编号 0000000x 本系统不映射） */
    private String stcd;

    /** 触发阈值（mm，雨量告警必填） */
    private BigDecimal threshold;

    /** 告警引擎判定窗口小时数（1/3/6，雨量告警必填；模板 ${hour}） */
    private Integer hour;

    /** 实测值（雨量为窗口累积雨量，可选；拼在站名后展示） */
    private BigDecimal value;
}
