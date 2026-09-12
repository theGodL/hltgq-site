package com.qgyun.hltgq.hltgqsite.workorder.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单管理列表行（表 t_auto_hltgq_water_work_order）。
 * <p>枚举字段返回中文（工单类型/状态）并附编码原文（权威值）；
 * org 兼容部门 id 与短码（00000003/00000004）双写法解析部门名；
 * time 为要求完成时间、handleTime（pyxcen）为处理时间（未处理为 null）。
 */
@Data
public class WorkOrderListVO {

    /** 工单主键 id */
    private String id;

    /** 工单编号（code，前缀 GD） */
    private String code;

    /** 工单标题（title） */
    private String title;

    /** 工单类型翻译（qjulvf） */
    private String type;

    /** 工单类型编码原文（qjulvf，权威值） */
    private String typeCode;

    /** 内容描述（content） */
    private String content;

    /** 所属站点 id（site = 站点档案 id） */
    private String siteId;

    /** 所属站点名称（JOIN 站点表 zzkaec，站点被删时为 null） */
    private String siteName;

    /** 关联设备 id（device），未关联为 null */
    private String deviceId;

    /** 关联设备名称（JOIN 设备表 name，设备被删时为 null） */
    private String deviceName;

    /** 负责部门 id/短码原文（org，权威值） */
    private String orgId;

    /** 负责部门名称（JOIN 部门表按 id 或 code 双匹配，未匹配为 null） */
    private String orgName;

    /** 处理人员 id（user） */
    private String userId;

    /** 处理人员姓名（JOIN 用户表 name，未指派为 null） */
    private String userName;

    /** 要求完成时间（time） */
    private LocalDateTime time;

    /** 处理时间（pyxcen，未处理为 null） */
    private LocalDateTime handleTime;

    /** 处理结果（result，未处理为 null） */
    private String result;

    /** 状态翻译（status：#1# 待处理、#2# 处理中、#3# 已关闭、#4# 已取消、#iizl# 已逾期） */
    private String status;

    /** 状态编码原文（status，权威值） */
    private String statusCode;

    /** 关联问题记录 id（azgquf） */
    private String issueId;

    /** 关联问题名称（JOIN 问题表 title，未关联为 null） */
    private String issueTitle;
}
