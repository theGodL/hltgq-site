package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 巡查考核提交请求（POST /patrol-schedule/assess）。
 *
 * <p>仅「考核得分 / 考核结论」输入，统计项由后端按计划下巡检记录与问题重算，前端传了也不采用。
 * <p>考核等级由得分分档自动生成（≥90 优秀 / 80~89 良好 / 60~79 及格 / <60 不及格），
 * {@link #grade} 仅保留兼容前端回显，后端不采用。
 * <p>score 用字符串接收，便于原样校验「0~100、最多 1 位小数」而不受 JSON 数字精度影响。
 */
@Data
public class PatrolAssessRequest {

    /** 巡检计划 id（必填，须为已完成 #3# 且未考核） */
    private String scheduleId;

    /** 考核得分（必填，0~100，最多 1 位小数） */
    private String score;

    /** 考核等级编码（由得分自动推导，后端不采用；保留字段仅兼容前端回显） */
    private String grade;

    /** 考核结论（可选，≤1000 字） */
    private String conclusion;
}
