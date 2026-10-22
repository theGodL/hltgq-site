package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 问题记录管理列表行（/patrol-issue/page，patrol-issue-list.html）。
 * <p>状态口径（与站点详情一致）：handle_status #1# 待确认 / #2# 转工单 / #3# 直接处理；
 * status #1# 待处理 / #2# 处理中 / #3# 已转工单 / #4# 已关闭 / #5# 已作废。
 */
@Data
public class PatrolIssueRowVO {

    /** 问题主键 */
    private String id;

    /** 问题编号（code，QST-毫秒时间戳） */
    private String code;

    /** 问题标题 */
    private String title;

    /** 所属站点 id（site） */
    private String siteId;

    /** 所属站点名称（站点档案 zzkaec，站点被删为 null） */
    private String siteName;

    /** 设备 id（device，可空） */
    private String deviceId;

    /** 设备名称（设备表 name，设备被删为 null） */
    private String deviceName;

    /** 发现人 id（"user"） */
    private String userId;

    /** 发现人姓名（用户表 name） */
    private String userName;

    /** 发现时间（time，yyyy-MM-dd HH:mm:ss） */
    private String time;

    /** 处理方式编码（handle_status） */
    private String handleCode;

    /** 处理方式名称（待确认/转工单/直接处理） */
    private String handleLabel;

    /** 状态编码（status） */
    private String statusCode;

    /** 状态名称 */
    private String statusLabel;

    /** 处理结果（ezuvil） */
    private String result;

    /** 关联工单 id（work_order_id） */
    private String workOrderId;

    /** 关联工单编号（工单表 code） */
    private String workOrderCode;

    /** 关联巡检记录 id（abqezf） */
    private String recordId;

    /** 关联巡检记录巡检时间（记录表 "time"） */
    private String recordTime;
}
