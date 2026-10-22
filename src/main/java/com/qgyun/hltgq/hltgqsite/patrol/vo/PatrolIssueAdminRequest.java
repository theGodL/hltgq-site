package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 问题记录管理操作请求（/patrol-issue/void|close|handle|result|transfer）。
 * <p>作废/关闭/直接处理仅用 id；填写处理结果用 id + result；
 * 转工单用 id + type/title/content/orgId/userId/time（站点/设备取问题记录本身）。
 */
@Data
public class PatrolIssueAdminRequest {

    /** 问题 id（所有操作必填） */
    private String id;

    /** 处理结果（填写处理结果必填） */
    private String result;

    /** 工单类型编码（转工单必填：#1#/#yjje#/#hxqm#/#zopb#/#kjua#/#enak#/#bgtg#/#pfqj#/#uvxb#） */
    private String type;

    /** 工单标题（转工单必填，默认取问题标题） */
    private String title;

    /** 工单内容描述（转工单可选，默认取问题描述） */
    private String content;

    /** 负责部门 id（转工单可选） */
    private String orgId;

    /** 处理人员 id（转工单可选） */
    private String userId;

    /** 要求完成时间（转工单可选，yyyy-MM-dd HH:mm 或带秒） */
    private String time;
}
