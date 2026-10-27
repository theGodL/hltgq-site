package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

import java.util.List;

/**
 * 问题记录管理操作请求（/patrol-issue/void|close|handle|result|transfer）。
 * <p>作废/关闭/直接处理仅用 id；填写处理结果用 issueId（兼容 id）+ result + photoIds；
 * 转工单用 id + type/title/content/orgId/userId/time（站点/设备取问题记录本身）。
 */
@Data
public class PatrolIssueAdminRequest {

    /** 问题 id（所有操作必填） */
    private String id;

    /** 问题 id（H5 填写处理结果用；与 id 二选一，优先取本字段） */
    private String issueId;

    /** 处理结果（填写处理结果必填） */
    private String result;

    /**
     * 处理结果附件 id 集合（填写处理结果可选）：
     * 不传/null 表示不调整附件；空数组表示清空；非空表示以集合为准（仅图片、最多 5 张）。
     */
    private List<String> photoIds;

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
