package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 需水上报（列表行 / 详情共用）。
 * <p>amount 对外为万m³（≤2 位小数），库内 m³；status 对外为前端英文码 wait/pass/reject，
 * statusName 为中文名；日期时间为 'YYYY-MM-DD HH:mm' 展示文本。
 * <p>用水时间按旬维度：periodYear 年份、periodMonth 月份、xunList 旬编码（1/2/3，可多选）、
 * xunText 旬中文（如「上旬、中旬」）；startDate/endDate 为按所选旬派生的起止日期。
 * 旧数据无旬列时由起止日期反推（仅供回显）。
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

    /** 用水年份（如 2026） */
    private Integer periodYear;

    /** 用水月份（1~12） */
    private Integer periodMonth;

    /** 旬多选（编码 1 上旬 / 2 中旬 / 3 下旬） */
    private List<String> xunList;

    /** 旬编码原始串（库内 "1,3"，Service 拆分到 xunList） */
    private String xunValues;

    /** 旬中文（如「上旬、中旬」） */
    private String xunText;

    /** 用水时段起（YYYY-MM-DD，按所选旬派生） */
    private String startDate;

    /** 用水时段止（YYYY-MM-DD，按所选旬派生） */
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
