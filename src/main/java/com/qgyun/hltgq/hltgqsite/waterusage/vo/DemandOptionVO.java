package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 需水上报下拉选项（调度方案"关联需水申请"；仅已通过）。
 * <p>amount 对外为万m³（下拉文案「XS0015 · 户名 · 1.98万m³」）。
 */
@Data
public class DemandOptionVO {

    private String id;

    /** 编号：XS + 4 位序号 */
    private String demandNo;

    private String waterUserId;

    private String waterUserNo;

    private String waterUserName;

    /** 申报计划水量（万m³） */
    private BigDecimal amount;

    /** 用水时段起（YYYY-MM-DD） */
    private String startDate;

    /** 用水时段止（YYYY-MM-DD） */
    private String endDate;
}
