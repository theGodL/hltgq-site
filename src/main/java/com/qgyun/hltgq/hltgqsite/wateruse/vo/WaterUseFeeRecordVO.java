package com.qgyun.hltgq.hltgqsite.wateruse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用水总结 · 水费表单原始记录（内部使用，不出 JSON）。
 * <p>来源表单：t_auto_hltgq_yn8cm_gfiidm（业主手动录入，统计周期为时间戳）。
 * <p>原始值直接透传以便联调日志核对单位（hqwvsf 假设 m³、hsfvdh 假设 元）。
 */
@Data
public class WaterUseFeeRecordVO {

    /** 水费编号 ztmhwx */
    private String feeNo;

    /** 用水单位 fbnomc */
    private String unitName;

    /** 统计周期 pyyftf（时间戳，后端按锚点归桶） */
    private LocalDateTime periodTime;

    /** 计算用水量 hqwvsf（原始值，按 m³ 换算） */
    private BigDecimal usageRaw;

    /** 执行水价 lgwutj（元/m³，仅核对用，不参与出数） */
    private BigDecimal priceRaw;

    /** 应用水费 hsfvdh（原始值，按 元 换算） */
    private BigDecimal feeRaw;
}
