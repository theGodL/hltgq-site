package com.qgyun.hltgq.hltgqsite.wateruse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用水总结 · 水费表单原始记录（内部使用，不出 JSON）。
 * <p>来源表单：t_auto_hltgq_yn8cm_gfiidm（业主手动录入，统计周期为区间字段 xxmefs）。
 * <p>各数值直取表单现成值、不做推算；联调按日志核对单位（计划供水量假设 m³、应收水费假设 元）。
 */
@Data
public class WaterUseFeeRecordVO {

    /** 水费编号 ztmhwx */
    private String feeNo;

    /** 用水单位 fbnomc */
    private String unitName;

    /** 统计周期起点 xxmefs_min（时间戳） */
    private LocalDateTime periodStartTime;

    /** 统计周期终点 xxmefs_max（时间戳，归桶锚点；终点缺失回退起点） */
    private LocalDateTime periodEndTime;

    /** 计划供水量 mlljya（原始值，单位 m³） */
    private BigDecimal plannedSupplyRaw;

    /** 执行水价 lgwutj（元/m³，随日志输出核对） */
    private BigDecimal priceRaw;

    /** 应收水费 hsfvdh（原始值，按 元 换算） */
    private BigDecimal feeRaw;
}
