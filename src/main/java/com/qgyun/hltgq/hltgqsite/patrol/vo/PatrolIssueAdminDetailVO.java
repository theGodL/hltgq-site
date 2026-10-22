package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 问题记录详情（/patrol-issue/detail，查看弹窗）。
 * <p>在列表行字段基础上补充问题描述、风险等级与关联工单标题；现场照片走 /patrol-issue/photos。
 */
@Data
public class PatrolIssueAdminDetailVO {

    /** 问题主键 */
    private String id;

    /** 问题编号 */
    private String code;

    /** 问题标题 */
    private String title;

    /** 问题描述（content） */
    private String content;

    /** 所属站点 id */
    private String siteId;

    /** 所属站点名称 */
    private String siteName;

    /** 设备 id */
    private String deviceId;

    /** 设备名称 */
    private String deviceName;

    /** 发现人 id */
    private String userId;

    /** 发现人姓名 */
    private String userName;

    /** 发现时间（yyyy-MM-dd HH:mm:ss） */
    private String time;

    /** 处理方式编码 */
    private String handleCode;

    /** 处理方式名称 */
    private String handleLabel;

    /** 状态编码 */
    private String statusCode;

    /** 状态名称 */
    private String statusLabel;

    /** 风险等级编码（risk_level） */
    private String riskCode;

    /** 风险等级名称 */
    private String riskLabel;

    /** 处理结果（ezuvil） */
    private String result;

    /** 关联工单 id */
    private String workOrderId;

    /** 关联工单编号 */
    private String workOrderCode;

    /** 关联工单标题 */
    private String workOrderTitle;

    /** 关联工单状态编码 */
    private String workOrderStatusCode;

    /** 关联工单状态名称（待指派/处理中/已关闭/已取消/已逾期） */
    private String workOrderStatusLabel;

    /** 关联巡检记录 id */
    private String recordId;

    /** 关联巡检记录巡检时间 */
    private String recordTime;
}
