package com.qgyun.hltgq.hltgqsite.workorder.vo;

import lombok.Data;

import java.util.List;

/**
 * 工单详情（/work-order/detail，查看弹窗 / 编辑回填）。
 */
@Data
public class WorkOrderDetailVO {

    /** 工单主键 id */
    private String id;

    /** 工单编号 */
    private String code;

    /** 工单标题 */
    private String title;

    /** 工单类型编码 */
    private String typeCode;

    /** 工单类型名称 */
    private String type;

    /** 内容描述 */
    private String content;

    /** 所属站点 id */
    private String siteId;

    /** 所属站点名称 */
    private String siteName;

    /** 关联设备 id */
    private String deviceId;

    /** 关联设备名称 */
    private String deviceName;

    /** 负责部门 id/短码原文 */
    private String orgId;

    /** 负责部门名称 */
    private String orgName;

    /** 处理人员 id */
    private String userId;

    /** 处理人员姓名 */
    private String userName;

    /** 要求完成时间（yyyy-MM-dd HH:mm:ss） */
    private String time;

    /** 处理结果 */
    private String result;

    /** 状态编码 */
    private String statusCode;

    /** 状态名称 */
    private String status;

    /** 处理时间（yyyy-MM-dd HH:mm:ss） */
    private String handleTime;

    /** 创建时间（yyyy-MM-dd HH:mm:ss） */
    private String createdAt;

    /** 关联问题记录 id */
    private String issueId;

    /** 关联问题标题（多条「、」拼接） */
    private String issueTitle;

    /** 处理结果附件清单（按上传时间升序；无附件为空列表） */
    private List<WorkOrderAttachmentVO> attachments;
}
