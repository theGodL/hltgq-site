package com.qgyun.hltgq.hltgqsite.workorder.vo;

import lombok.Data;

/**
 * 工单管理列表行（/work-order/page，work-order-list.html）。
 * <p>状态口径：#1# 待指派、#2# 处理中、#3# 已关闭、#4# 已取消、#iizl# 已逾期；
 * time 为要求完成时间，handleTime（pyxcen）为处理时间（未处理为 null）。
 */
@Data
public class WorkOrderRowVO {

    /** 工单主键 id */
    private String id;

    /** 工单编号（code） */
    private String code;

    /** 工单标题（title） */
    private String title;

    /** 工单类型编码（qjulvf） */
    private String typeCode;

    /** 工单类型名称（9 档） */
    private String type;

    /** 内容描述（content） */
    private String content;

    /** 所属站点 id */
    private String siteId;

    /** 所属站点名称（站点被删为 null） */
    private String siteName;

    /** 关联设备 id（可空） */
    private String deviceId;

    /** 关联设备名称（设备被删为 null） */
    private String deviceName;

    /** 负责部门 id/短码原文（org） */
    private String orgId;

    /** 负责部门名称（部门被删为 null） */
    private String orgName;

    /** 处理人员 id（user） */
    private String userId;

    /** 处理人员姓名（未指派为 null） */
    private String userName;

    /** 要求完成时间（time，yyyy-MM-dd HH:mm:ss） */
    private String time;

    /** 处理时间（pyxcen，yyyy-MM-dd HH:mm:ss，未处理为 null） */
    private String handleTime;

    /** 处理结果（result，未填写为 null） */
    private String result;

    /** 状态编码（status） */
    private String statusCode;

    /** 状态名称（待指派/处理中/已关闭/已取消/已逾期） */
    private String status;

    /** 关联问题记录 id（问题表 work_order_id 反查，未关联为 null） */
    private String issueId;

    /** 关联问题标题（多条「、」拼接，未关联为 null） */
    private String issueTitle;
}
