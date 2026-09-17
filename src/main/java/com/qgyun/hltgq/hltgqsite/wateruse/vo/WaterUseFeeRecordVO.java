package com.qgyun.hltgq.hltgqsite.wateruse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用水总结 · 水费表单原始记录（内部使用，不出 JSON）。
 * <p>来源表单：t_auto_hltgq_yn8cm_gfiidm（业主手动录入，统计周期为区间字段 xxmefs）。
 * <p>各数值直取表单现成值、不做推算；单位按表单实际录入口径（2026-09-17 线上数据复核）：
 * 计划供水量 万m³、执行水价 元/m³、应收水费 元（表单标签即「应收水费（元）」，出参由服务层换算为万元）。
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

    /** 计划供水量 mlljya（原始值，单位 万m³） */
    private BigDecimal plannedSupplyRaw;

    /** 执行水价 lgwutj（元/m³，随日志输出核对） */
    private BigDecimal priceRaw;

    /** 应收水费 hsfvdh（原始值，单位 元；出参由服务层 ÷10^4 换算为万元） */
    private BigDecimal feeRaw;
}
