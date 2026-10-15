package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用水定额标准（列表行 / 详情共用）。
 * <p>irrigationMethod 为字典型编码（{@code #1#~#10#}，字典见 WaterUsageDict.IRRIGATION_METHODS），
 * irrigationMethodName 由 Service 装饰填充；
 * refCount（被引用计划数，DISTINCT plan 实时 count，不过滤状态）由查询 SQL 计算。
 */
@Data
public class WaterQuotaVO {

    private String id;

    /** 作物种类（水稻/冬小麦/…，亦含工业用水、城镇生活等行业类型） */
    private String cropType;

    /** 灌溉方式（字典型编码 #1# 淹灌 ~ #10# 计划直供；必填） */
    private String irrigationMethod;

    /** 灌溉方式名称（列表/详情装饰输出） */
    private String irrigationMethodName;

    /** 定额标准（m³/亩，>0，2 位小数） */
    private BigDecimal quotaValue;

    /** 备注 */
    private String remark;

    /** 被引用计划数（实时 count） */
    private Long refCount;
}
