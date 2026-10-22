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
 * <p>巡查范围为多对多关系（主表无列），落库在中间表
 * （表名与列结构均已实证，见 patrol.mapper.PatrolScheduleMapper）。
 * <p>巡检人员已于 2026-10 由多选关系改造为主表 "user" 单选列（平台字段 base_type=#MODEL#、
 * MANY_TO_ONE）；旧的 t_auto_hltgq_knc3g_nlbdju_user_rel 仅保留存量数据，不再写入。
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

    /** 状态：#1# 草稿 / #5# 未开始 / #2# 进行中 / #3# 已完成 / #4# 已取消 */
    @TableField("\"status\"")
    private String status;

    /** 巡检人员用户 id（单选，主表 "user" 列；改造前的存量数据该列为空，人员仍在旧多选关系表） */
    @TableField("\"user\"")
    private String user;
}
