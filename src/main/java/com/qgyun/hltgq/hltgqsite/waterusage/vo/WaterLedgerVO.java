package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 手工用水台账（列表行）。
 * <p>对外口径：volume 用水量 m³、price 单价 元/m³、amount 应收金额 元（volume × price，2 位小数）；
 * cycle 为前端英文码 month/quarter/year，cycleKey 为结算周期（2026-10 / 2026-Q3 / 2026），
 * cycleLabel 如「2026年10月」；billed=true 表示已出账（billId 非空），此时禁改禁删。
 */
@Data
public class WaterLedgerVO {

    private String id;

    /** 编号：TZ + 期间码 + '-' + 户号 + 4 位序号 */
    private String ledgerNo;

    private String waterUserId;

    private String waterUserNo;

    private String waterUserName;

    /** 统计周期（库内 #1# 月度 / #2# 季度 / #3# 年度） */
    private String settleCycle;

    /** 统计周期（前端英文码：month/quarter/year） */
    private String cycle;

    private String cycleName;

    /** 结算周期 key（2026-10 / 2026-Q3 / 2026） */
    private String cycleKey;

    /** 结算周期中文标签（2026年10月 / 2026年第3季度 / 2026年度） */
    private String cycleLabel;

    /** 用水量（m³） */
    private BigDecimal volume;

    /** 单价（元/m³） */
    private BigDecimal price;

    /** 应收金额（元） */
    private BigDecimal amount;

    /** 出账后账单 ID（null = 未出账，禁改禁删） */
    private String billId;

    /** 账单编号（未出账为 null） */
    private String billNo;

    /** 账单状态（前端英文码 generated/paid；未出账为 null） */
    private String billStatus;

    private String billStatusName;

    /** 是否已出账（billId 非空） */
    private Boolean billed;

    private String remark;

    /** 创建时间（YYYY-MM-DD HH:mm） */
    private String createTime;
}
