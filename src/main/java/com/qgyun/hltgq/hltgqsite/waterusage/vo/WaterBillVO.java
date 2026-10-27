package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 水费账单（列表行 / 详情共用；详情带方案明细 plans）。
 * <p>对外口径：quantity 万m³（Σ方案计入水量快照，带符号）、price 元/m³、amount/paid 元；
 * cycle 为前端英文码 month/quarter/year，status 为 generated/paid；cycleLabel 如「2026年10月」。
 */
@Data
public class WaterBillVO {

    private String id;

    /** 编号：SF + 期间码 + '-' + 户号 + 4 位序号 */
    private String billNo;

    private String waterUserId;

    private String waterUserNo;

    private String waterUserName;

    /** 统计周期（前端英文码：month/quarter/year） */
    private String cycle;

    private String cycleName;

    /** 分期 key（2026-10 / 2026-Q3 / 2026） */
    private String cycleKey;

    /** 分期中文标签（2026年10月 / 2026年第3季度 / 2026年度） */
    private String cycleLabel;

    /** 账期区间起（YYYY-MM-DD） */
    private String startDate;

    /** 账期区间止（YYYY-MM-DD） */
    private String endDate;

    /** 计费水量（万m³，Σ方案计入水量快照） */
    private BigDecimal quantity;

    /** 水价快照（元/m³） */
    private BigDecimal price;

    /** 应收金额（元） */
    private BigDecimal amount;

    /** 已收金额（元） */
    private BigDecimal paid;

    /** 账单状态（前端英文码：generated/paid） */
    private String status;

    private String statusName;

    /** 出账时间（YYYY-MM-DD HH:mm） */
    private String createTime;

    /** 收款时间（YYYY-MM-DD HH:mm；未收款为 null） */
    private String payTime;

    /** 包含方案数 */
    private Integer planCount;

    /** 来源手工台账 ID（方案出账为 null） */
    private String ledgerId;

    /** 来源手工台账编号（方案出账为 null） */
    private String ledgerNo;

    /** 方案明细（仅详情输出） */
    private java.util.List<BillPlanRowVO> plans;
}
