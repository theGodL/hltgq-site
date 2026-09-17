package com.qgyun.hltgq.hltgqsite.wateruse.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用水总结报表行（dimension 决定统计桶粒度：月 / 灌季 / 年）。
 * <p>数值精度：用水量（累计流量口径）保留 3 位小数（截断）；应收水费保留 2 位小数（截断）；
 * 灌溉水利用系数保留 3 位小数。
 * <p>单位：用水量 万m³（计划供水量 mlljya，表单即万m³，直取不换算）；应收水费 万元
 * （hsfvdh 表单按「元」录入，服务层 ÷10^4 换算为万元后按展示精度截断）。
 * <p>桶内无数据时为 null（不补 0）；实收侧（实收水费/收缴率）不提供（产品已裁剪）。
 */
@Data
public class WaterUseReportRowVO {

    /** 统计桶键：2026-08 / 2026-summer / 2026 */
    private String period;

    /** 统计时段展示标签：2026-08 / 2026夏灌 / 2026 */
    private String label;

    /** 桶起始日期（yyyy-MM-dd） */
    private String periodStart;

    /** 桶结束日期（yyyy-MM-dd） */
    private String periodEnd;

    /** 用水量(万m³，3 位小数截断)：计划供水量 mlljya 桶内求和（表单单位即万m³）；无记录为 null */
    private BigDecimal usage;

    /** 灌溉水利用系数（近似值，3 位小数）：Σ四干渠进水闸区间累计 ÷ 渠首进水闸区间累计；不可计算为 null */
    private BigDecimal irrigationCoef;

    /** 应收水费(万元，2 位小数截断)：水费表单 hsfvdh 桶内求和（表单按元录入，÷10^4 换算）；无记录为 null */
    private BigDecimal receivable;
}
