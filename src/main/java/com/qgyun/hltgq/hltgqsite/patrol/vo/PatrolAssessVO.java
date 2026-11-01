package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡查考核（预览 / 查看共用，GET /patrol-schedule/assess-preview、GET /patrol-schedule/assess）。
 *
 * <p>自动统计项口径：
 * <ul>
 *   <li>计划巡查站点 = 巡查范围关系表行数；已巡查站点 = 计划下巡检记录站点去重</li>
 *   <li>巡查记录 = 计划下巡检记录数（不含空白占位草稿）</li>
 *   <li>发现问题 = 记录关联问题数（作废 #5# 不计）；恒等式 发现问题 = 已整改 + 整改中 + 未整改</li>
 *   <li>已整改 = 已关闭 #4#；整改中 = 处理中 #2# + 已转工单 #3#；未整改 = 待处理 #1#</li>
 * </ul>
 * <p>预览（未考核）返回实时统计，score/grade/conclusion 为空；查看返回提交时快照。
 */
@Data
public class PatrolAssessVO {

    /** 巡检计划 id */
    private String scheduleId;

    /** 计划编号（快照优先，预览取计划当前值） */
    private String planCode;

    /** 计划名称（快照优先，预览取计划当前值） */
    private String planTitle;

    /** 计划巡查站点（个） */
    private Integer planSite;

    /** 已巡查站点（个） */
    private Integer patrolledSite;

    /** 巡查记录（条） */
    private Integer recordCount;

    /** 发现问题（项） */
    private Integer issueTotal;

    /** 已整改（项） */
    private Integer issueFixed;

    /** 整改中（项） */
    private Integer issueFixing;

    /** 未整改（项） */
    private Integer issueOpen;

    /** 考核得分（0~100，1 位小数；预览为空） */
    private Double score;

    /** 考核等级编码（#1#~#4#；预览为空） */
    private String grade;

    /** 考核等级名称（权威映射；预览为空） */
    private String gradeLabel;

    /** 考核结论（预览为空） */
    private String conclusion;

    /** 考核人用户 id */
    private String assessorId;

    /** 考核人姓名 */
    private String assessorName;

    /** 考核提交时间（即记录创建时间） */
    private LocalDateTime assessAt;

    /** 是否已考核（查看接口恒 true；预览接口恒 false） */
    private Boolean assessed;
}
