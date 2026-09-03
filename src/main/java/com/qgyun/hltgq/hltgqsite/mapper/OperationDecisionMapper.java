package com.qgyun.hltgq.hltgqsite.mapper;

import com.qgyun.hltgq.hltgqsite.vo.OperationDecisionVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 运行管理决策聚合统计（巡检记录 / 问题记录 / 工单 三张低代码平台表）。
 * <p>时间口径：巡检记录与问题记录有业务发生时间字段 time，按 time 过滤；
 * 工单表无业务产生时间（time 为要求完成时间），按平台公共字段 created_at 过滤。
 * <p>区间参数均可选（null = 不限）；状态分布按 status 编码分组，中文名与补 0 由 Controller 完成。
 */
public interface OperationDecisionMapper {

    /**
     * 巡查总次数：只统计已提交的巡检记录（status=#2#），排除 #1# 草稿。
     */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "WHERE status = '#2#' " +
            "<if test='startTime != null'>AND time &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND time &lt;= #{endTime} </if>" +
            "</script>")
    long countPatrol(@Param("startTime") LocalDateTime startTime,
                     @Param("endTime") LocalDateTime endTime);

    /**
     * 问题状态分布：name = status 编码（如 #1#），value = 计数。
     * 问题上报数与问题符合度由本结果在 Controller 内存聚合（同源一致）。
     * 列别名与 StatusItem 属性同名自动映射（map-underscore-to-camel-case=false）。
     */
    @Select("<script>" +
            "SELECT status AS name, COUNT(*) AS \"value\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "<where>" +
            "<if test='startTime != null'>AND time &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND time &lt;= #{endTime} </if>" +
            "</where>" +
            "GROUP BY status" +
            "</script>")
    List<OperationDecisionVO.StatusItem> groupIssueStatus(@Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 工单数：按平台公共字段 created_at 过滤区间（工单无业务产生时间）。
     */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" " +
            "<where>" +
            "<if test='startTime != null'>AND created_at &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND created_at &lt;= #{endTime} </if>" +
            "</where>" +
            "</script>")
    long countOrder(@Param("startTime") LocalDateTime startTime,
                    @Param("endTime") LocalDateTime endTime);

    /**
     * 巡查符合度分母：区间内应完成巡检计划数（排除草稿 #1# 与已取消 #4#），按计划开始时间 start_time 过滤。
     */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" " +
            "WHERE status IN ('#2#', '#3#') " +
            "<if test='startTime != null'>AND start_time &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND start_time &lt;= #{endTime} </if>" +
            "</script>")
    long countDuePatrolSchedules(@Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 巡查符合度分子：区间内应完成计划中「已存在已提交巡检记录」的计划数。
     * 与分母同源（同按计划 start_time 过滤、同 status 白名单），保证符合度恒 ≤ 100%。
     */
    @Select("<script>" +
            "SELECT COUNT(DISTINCT p.id) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" p " +
            "WHERE p.status IN ('#2#', '#3#') " +
            "<if test='startTime != null'>AND p.start_time &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND p.start_time &lt;= #{endTime} </if>" +
            "AND EXISTS (SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "            WHERE r.patrol_schedule = p.id AND r.status = '#2#')" +
            "</script>")
    long countCompliantPatrolSchedules(@Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 工单状态分布：name = status 编码（如 #1#），value = 计数。
     */
    @Select("<script>" +
            "SELECT status AS name, COUNT(*) AS \"value\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" " +
            "<where>" +
            "<if test='startTime != null'>AND created_at &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND created_at &lt;= #{endTime} </if>" +
            "</where>" +
            "GROUP BY status" +
            "</script>")
    List<OperationDecisionVO.StatusItem> groupOrderStatus(@Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime);
}
