package com.qgyun.hltgq.hltgqsite.workorder.vo;

import lombok.Data;

/**
 * 工单编辑请求（/work-order/update，仅待指派 #1# 可编辑）。
 * <p>站点/设备/部门/人员/要求完成时间均可清空（空串按 null 处理）。
 */
@Data
public class WorkOrderSaveRequest {

    /** 工单 id（必填） */
    private String id;

    /** 工单标题（必填，≤200 字） */
    private String title;

    /** 工单类型编码（必填，9 档：#1#/#yjje#/#hxqm#/#zopb#/#kjua#/#enak#/#bgtg#/#pfqj#/#uvxb#） */
    private String type;

    /** 内容描述（可选，≤500 字） */
    private String content;

    /** 所属站点 id（必填） */
    private String siteId;

    /** 关联设备 id（可选，须属于所属站点） */
    private String deviceId;

    /** 负责部门 id（可选） */
    private String orgId;

    /** 处理人员 id（可选） */
    private String userId;

    /** 要求完成时间（可选，yyyy-MM-dd HH:mm 或 yyyy-MM-dd HH:mm:ss） */
    private String time;
}
