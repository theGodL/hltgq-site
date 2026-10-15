package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 台账 / 账单方案明细行（水费页两层明细共用）。
 * <p>水量对外均为万m³：planAmount = 方案计划放水量；amount = 计入水量（带符号，差额策略扣减闸口为负）。
 * 台账明细 amount 为实时计量值（该闸口 [startTime, endTime] 实测累计流量折算）；
 * 账单明细 amount 为出账时点快照（方案 billed_qty）。
 */
@Data
public class BillPlanRowVO {

    private String dispatchId;

    /** 方案编号：DD + 4 位序号 */
    private String dispatchNo;

    /** 关联用水计划 ID / 名称（可空） */
    private String planId;

    private String planName;

    private String gateId;

    private String gateName;

    /** 计入符号：1 累加 / -1 扣减（差额策略扣减闸口） */
    private Integer sign;

    /** 执行时段起（YYYY-MM-DD HH:mm） */
    private String startTime;

    /** 执行时段止（YYYY-MM-DD HH:mm） */
    private String endTime;

    /** 计划放水量（万m³） */
    private BigDecimal planAmount;

    /** 计入水量（万m³，带符号）；未计费行实时计量不可得时为 null */
    private BigDecimal amount;

    /** 计量不可得原因（仅未计费行、amount 为空时有值，如「执行时段内该闸口无流量记录」；已计费 / 可计量为 null） */
    private String meterIssue;

    /** 计费账单编号（未计费为空） */
    private String billNo;

    /** 是否已计费 */
    private Boolean billed;

    /** 执行状态（前端英文码） */
    private String status;

    private String statusName;

    /** 调度记录数 */
    private Integer trail;

    /** 分期 key / 中文标签（台账明细按期分组用） */
    private String cycleKey;

    private String cycleLabel;
}
