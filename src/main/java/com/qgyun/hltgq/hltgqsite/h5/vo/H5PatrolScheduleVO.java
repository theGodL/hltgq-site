package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * H5 巡检计划列表行（/h5/patrol-schedule/list）。
 * <p>仅返回「进行中 #2# / 已完成 #3#」两类计划（草稿/已取消不入列）；
 * 计划类型与状态均为编码 + 后端权威中文映射双字段。
 */
@Data
public class H5PatrolScheduleVO {

    /** 计划主键 */
    private String id;

    /** 计划编号（XCJH + 13 位毫秒时间戳，或填写值） */
    private String code;

    /** 任务名称（title） */
    private String title;

    /** 巡查内容 */
    private String content;

    /** 计划开始时间 */
    private LocalDateTime startTime;

    /** 计划结束时间 */
    private LocalDateTime endTime;

    /** 计划类型编码（xhonqv：#wavn#/#1# 年度计划、#zjgg# 月度计划） */
    private String planType;

    /** 计划类型名称（权威映射） */
    private String planTypeLabel;

    /** 状态编码（#2# 进行中 / #3# 已完成） */
    private String statusCode;

    /** 状态名称（权威映射） */
    private String statusLabel;

    /** 创建人名称（关联用户表 t_apaas_uc_user；无创建人时为 null） */
    private String creatorName;

    /** 巡检范围关联的站点ID（多值逗号分隔，按展示顺序；无范围关系时为 null） */
    private String rangeStationIds;
}
