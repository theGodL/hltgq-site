package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用水计划明细行（行 = 用水户；同一计划中同一用水户仅可出现一次，设计方案 2.3）。
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

    /** 定额-灌溉方式（关联查询；字典型编码 #N#，名称由 Service 装饰填充） */
    private String quotaIrrigationMethod;

    /** 定额-灌溉方式名称（装饰输出；前端展示不写死字典） */
    private String quotaIrrigationMethodName;

    /** 定额标准（m³/亩，关联查询） */
    private BigDecimal quotaValue;

    /** 测算水量（m³，= 面积 × 定额，只读） */
    private BigDecimal calcWater;

    /** 计划水量（m³，核定填写，默认 = 测算水量） */
    private BigDecimal planWater;

    /**
     * 已完成供水量（m³，实测，详情明细）：该户在本计划中未取消方案的实际放水量合计；
     * 户合计为负（扣减超过进水）时为 null（不可认定），由 missingCnt &gt; 0 提示「待补计量数据」。
     */
    private BigDecimal doneQty;

    /** 缺读数计数：该户计量不可得的方案数 +（合计异常记 1）；&gt; 0 时前端展示「待补计量数据」 */
    private Integer missingCnt;

    /** 超测算说明（计划水量 > 测算水量 时必填） */
    private String overrideReason;

    /** 阶段标签（春灌/夏灌等，可空） */
    private String stageTag;

    /** 行序 */
    private Integer sortOrder;
}
