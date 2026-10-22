package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 巡检记录详情中的关联问题行（问题表 t_auto_hltgq_knc3g_bpzjoh，abqezf = 记录 id）。
 */
@Data
public class PatrolRecordIssueItemVO {

    /** 问题 id */
    private String id;

    /** 问题编号（QST-） */
    private String code;

    /** 问题标题 */
    private String title;

    /** 问题描述 */
    private String content;

    /** 发现时间（yyyy-MM-dd HH:mm） */
    private String time;

    /** 状态编码（#1# 待处理 …） */
    private String statusCode;

    /** 状态名称（Service 填充） */
    private String statusLabel;

    /** 处理方式编码（#1# 待确认 …） */
    private String handleStatusCode;

    /** 处理方式名称（Service 填充） */
    private String handleStatusLabel;

    /** 风险等级编码（#1# 低 / #2# 中 / #3# 高，可空） */
    private String riskLevel;

    /** 风险等级名称（Service 填充） */
    private String riskLabel;
}
