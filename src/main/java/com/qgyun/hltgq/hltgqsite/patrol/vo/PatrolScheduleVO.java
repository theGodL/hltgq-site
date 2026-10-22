package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检计划列表行（GET /patrol-schedule/page）。
 * <p>计划类型/状态返回编码 + 后端权威中文双字段；创建人取主表 created_by 关联用户姓名；
 * 巡检人员取主表 "user" 单选列（存量数据该列为空时回退旧多选关系表拼接）。
 * <p>巡查范围由关系表聚合站点名称（顿号分隔）并给出站点数。
 */
@Data
public class PatrolScheduleVO {

    /** 计划主键 */
    private String id;

    /** 计划编号（XCJH + 13 位毫秒时间戳，或填写值） */
    private String code;

    /** 计划名称 */
    private String title;

    /** 计划类型编码（xhonqv：#1#/#wavn# 年度计划、#zjgg# 月度计划） */
    private String planType;

    /** 计划类型名称（权威映射） */
    private String planTypeLabel;

    /** 巡查内容 */
    private String content;

    /** 计划开始时间 */
    private LocalDateTime startTime;

    /** 计划结束时间 */
    private LocalDateTime endTime;

    /**
     * 状态编码：草稿 #1# / 已完成 #3# / 已取消 #4# 为存储值；
     * 已发布按巡检记录推导展示态：无记录=未开始 #5# / 有记录=进行中 #2#
     */
    private String statusCode;

    /** 状态名称（权威映射） */
    private String statusLabel;

    /** 创建人用户 id（主表 created_by） */
    private String creatorId;

    /** 创建人姓名（用户表关联；用户不存在为 null） */
    private String creatorName;

    /** 巡检人员用户 id（主表 "user" 单选列；存量数据为空） */
    private String inspectorId;

    /** 巡检人员姓名（主表 "user" 关联；为空时回退旧多选关系表拼接） */
    private String inspectorName;

    /** 巡查范围站点名称（顿号分隔，按关系顺序；无范围为 null） */
    private String siteNames;

    /** 巡查范围站点数 */
    private Integer siteCount;
}
