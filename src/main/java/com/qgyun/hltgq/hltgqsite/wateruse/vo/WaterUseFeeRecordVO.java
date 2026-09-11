package com.qgyun.hltgq.hltgqsite.wateruse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用水总结 · 水费表单原始记录（内部使用，不出 JSON）。
 * <p>来源表单：t_auto_hltgq_yn8cm_gfiidm（业主手动录入，统计周期为时间戳）。
 * <p>表单无「计算用水量」列：computedUsage 按 应收水费 ÷ 执行水价 计算得出，其余数值直取表单现成值；
 * 联调按日志核对单位（计算用水量假设 m³、应收水费假设 元）。
 */
@Data
public class WaterUseFeeRecordVO {

    /** 水费编号 ztmhwx */
    private String feeNo;

    /** 用水单位 fbnomc */
    private String unitName;

    /** 统计周期 pyyftf（时间戳，后端按锚点归桶） */
    private LocalDateTime periodTime;

    /** 计算用水量 = 应收水费 ÷ 执行水价（原始值，单位 m³；水价为空/0 时为 null） */
    private BigDecimal computedUsage;

    /** 执行水价 lgwutj（元/m³，用作计算用水量的分母） */
    private BigDecimal priceRaw;

    /** 应收水费 hsfvdh（原始值，按 元 换算） */
    private BigDecimal feeRaw;
}
