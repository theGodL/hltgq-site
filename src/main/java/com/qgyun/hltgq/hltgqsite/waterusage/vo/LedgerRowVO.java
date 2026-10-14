package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 水费台账行（视图一：按用水户归集，一行为一户）。
 * <p>水量对外均为万m³、金额元：thisQty 本期水量（当前分期 key 内方案计入水量合计）；
 * pendingQty/pendingAmount 待出账（未计费且未取消方案的实时计量值与按现行水价折算金额）；
 * billedQty/billedAmount 已出账（方案计费快照 billed_qty 合计与账单已出账金额）。
 */
@Data
public class LedgerRowVO {

    private String userId;

    private String userNo;

    private String userName;

    private String userType;

    private String userTypeName;

    /** 计算策略（#N# 原值） */
    private String calcMode;

    /** 计算策略中文名（指定闸口累加 / 闸口差额计量） */
    private String strategy;

    /** 计量闸口展示文案（累加「闸A、闸B」；差额「主闸 − 扣A、扣B」） */
    private String gateText;

    /** 统计周期（前端英文码：month/quarter/year） */
    private String cycle;

    private String cycleName;

    /** 水价（元/m³） */
    private BigDecimal price;

    /** 本期水量（万m³） */
    private BigDecimal thisQty;

    /** 本期方案数 */
    private Integer thisCount;

    /** 待出账水量（万m³，带符号合计） */
    private BigDecimal pendingQty;

    /** 待出账金额（元，按现行水价折算） */
    private BigDecimal pendingAmount;

    /** 待出账方案数 */
    private Integer pendingCount;

    /** 已出账水量（万m³，计费快照合计） */
    private BigDecimal billedQty;

    /** 已出账金额（元，Σ 账单应收金额；汇总视图「应收金额」） */
    private BigDecimal billedAmount;

    /** 年度计划（万m³，当年已生效+已归档年度计划核定水量合计；无则 null 显示「—」） */
    private BigDecimal yearPlan;

    /** 账单数（张；汇总视图） */
    private Integer billCount;

    /** 已收金额（元，Σ 账单已收；汇总视图） */
    private BigDecimal paidAmount;

    /** 欠缴金额（元 = 应收 − 已收；汇总视图） */
    private BigDecimal oweAmount;

    /** 收缴率（整数百分比；应收为 0 时为 0） */
    private Integer collectRate;

    /** 数据校验（台账「数据校验」列 / 「存在异常方案」Tab）：待出账方案是否存在无有效计量 */
    private Boolean dataOk;

    /** 校验说明（异常原因；正常为空） */
    private String dataNote;
}
