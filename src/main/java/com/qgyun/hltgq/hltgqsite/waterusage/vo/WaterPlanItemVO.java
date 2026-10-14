package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用水计划明细行（行 = 用水户 × 定额；同一期可多行，区分春灌/夏灌等阶段）。
 * <p>灌溉面积为选用水户时带出的快照（编制中可改，不回写用水户）；
 * 测算水量 = 面积 × 定额（只读自动计算）；计划水量为核定值，默认 = 测算水量。
 */
@Data
public class WaterPlanItemVO {

    private String id;

    private String waterUserId;

    /** 用水户名称（关联查询） */
    private String waterUserName;

    /** 用水户编号（关联查询） */
    private String waterUserNo;

    /** 灌溉面积（亩，快照） */
    private BigDecimal irrigationArea;

    private String quotaId;

    /** 定额-作物种类（关联查询） */
    private String quotaCropType;

    /** 定额-灌溉方式（关联查询） */
    private String quotaIrrigationMethod;

    /** 定额标准（m³/亩，关联查询） */
    private BigDecimal quotaValue;

    /** 测算水量（m³，= 面积 × 定额，只读） */
    private BigDecimal calcWater;

    /** 计划水量（m³，核定填写，默认 = 测算水量） */
    private BigDecimal planWater;

    /** 超测算说明（计划水量 > 测算水量 时必填） */
    private String overrideReason;

    /** 阶段标签（春灌/夏灌等，可空） */
    private String stageTag;

    /** 行序 */
    private Integer sortOrder;
}
