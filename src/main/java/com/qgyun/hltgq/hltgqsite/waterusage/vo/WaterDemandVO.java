package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 需水上报（列表行 / 详情共用）。
 * <p>amount 对外为万m³（≤2 位小数），库内 m³；status 对外为前端英文码 wait/pass/reject，
 * statusName 为中文名；日期时间为 'YYYY-MM-DD HH:mm' 展示文本。
 */
@Data
public class WaterDemandVO {

    private String id;

    /** 编号：XS + 4 位序号 */
    private String demandNo;

    private String waterUserId;

    private String waterUserNo;

    private String waterUserName;

    /** 申报拟用闸口（可空） */
    private String gateId;

    private String gateName;

    /** 申报计划水量（万m³，对外口径） */
    private BigDecimal amount;

    /** 用水时段起（YYYY-MM-DD） */
    private String startDate;

    /** 用水时段止（YYYY-MM-DD） */
    private String endDate;

    /** 用途 */
    private String usage;

    /** 审核状态（前端英文码：wait/pass/reject） */
    private String status;

    private String statusName;

    /** 审核意见 */
    private String auditOpinion;

    private String auditBy;

    /** 审核时间（YYYY-MM-DD HH:mm） */
    private String auditAt;

    /** 申请时间（YYYY-MM-DD HH:mm） */
    private String applyTime;

    private String remark;
}
