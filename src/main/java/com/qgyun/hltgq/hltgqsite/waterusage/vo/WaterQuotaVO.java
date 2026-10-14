package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用水定额标准（列表行 / 详情共用）。
 * <p>refCount（被引用计划数，DISTINCT plan 实时 count，不过滤状态）由查询 SQL 计算。
 */
@Data
public class WaterQuotaVO {

    private String id;

    /** 作物种类（水稻/冬小麦/…，亦含工业用水、城镇生活等行业类型） */
    private String cropType;

    /** 灌溉方式（可空） */
    private String irrigationMethod;

    /** 定额标准（m³/亩，>0，2 位小数） */
    private BigDecimal quotaValue;

    /** 备注 */
    private String remark;

    /** 被引用计划数（实时 count） */
    private Long refCount;
}
