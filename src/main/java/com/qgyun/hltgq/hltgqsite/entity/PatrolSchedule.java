package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 巡查计划实体（t_auto_hltgq_water_patrol_schedule）。
 * <p>用于 Excel 导入入库（POST /patrol-schedule/import）；仅映射导入所需业务列，
 * 隐藏统计列（pwtlmi 预算金额 / ftumeu~zxvbxm 巡查统计与考核）不映射不写入。
 * <p>时间口径：start_time / end_time 库中存 'YYYY-MM-DD 00:00:00'，导入按当日零点落库。
 * <p>巡查范围 / 巡检人员为多对多关系（主表无列），落库在中间表
 * （表名与列结构均已实证，见 patrol.mapper.PatrolScheduleMapper）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\"")
public class PatrolSchedule extends BaseWaterEntity {

    /** 计划编号（XCJH + 13 位毫秒时间戳，或导入填写值；全局唯一） */
    @TableField("\"code\"")
    private String code;

    /** 计划名称 */
    @TableField("\"title\"")
    private String title;

    /** 巡查内容 */
    @TableField("\"content\"")
    private String content;

    /** 计划开始时间（存当日零点） */
    @TableField("\"start_time\"")
    private LocalDateTime startTime;

    /** 计划结束时间（存当日零点） */
    @TableField("\"end_time\"")
    private LocalDateTime endTime;

    /** 计划类型：#1# 年度计划 / #zjgg# 月度计划 */
    @TableField("\"xhonqv\"")
    private String planType;

    /** 状态：#1# 草稿（导入统一落草稿） */
    @TableField("\"status\"")
    private String status;
}
