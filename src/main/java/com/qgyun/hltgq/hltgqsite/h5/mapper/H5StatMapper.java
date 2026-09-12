package com.qgyun.hltgq.hltgqsite.h5.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * H5 统计聚合查询（巡查及问题统计 / 维修养护统计）。
 * <p>柱状图巡查数按巡检计划统计（排除草稿 #1#、含已取消 #4#）、按计划开始时间 start_time 过滤；
 * 问题记录全状态、按发现时间 time 过滤，风险等级三档 FILTER 拆分。
 */
public interface H5StatMapper {

    /**
     * 巡检月度计数：巡检计划按计划开始时间 start_time 聚合到月。
     * <p>status 白名单 #2#/#3#/#4#（排除草稿，保留已取消——业务口径：取消的计划仍计入巡查任务数）。
     */
    @Select("SELECT to_char(start_time, 'YYYY-MM') AS month, COUNT(*) AS cnt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" " +
            "WHERE status IN ('#2#', '#3#', '#4#') AND start_time >= #{startTime} AND start_time < #{endTime} " +
            "GROUP BY to_char(start_time, 'YYYY-MM')")
    List<Map<String, Object>> selectPatrolMonthly(@Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 问题月度计数：全状态按发现时间 time 聚合，risk_level 三档 FILTER 拆分
     * （null/未知等级计入 cnt 但不入三档）。
     */
    @Select("SELECT to_char(time, 'YYYY-MM') AS month, COUNT(*) AS cnt, " +
            "COUNT(*) FILTER (WHERE risk_level = '#1#') AS low, " +
            "COUNT(*) FILTER (WHERE risk_level = '#2#') AS mid, " +
            "COUNT(*) FILTER (WHERE risk_level = '#3#') AS high " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "WHERE time >= #{startTime} AND time < #{endTime} " +
            "GROUP BY to_char(time, 'YYYY-MM')")
    List<Map<String, Object>> selectIssueMonthly(@Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 巡检结果分布：已提交记录按 result 分组计数（时间区间可选）。
     */
    @Select("<script>" +
            "SELECT result AS name, COUNT(*) AS \"value\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "WHERE status = '#2#' " +
            "<if test='startTime != null'>AND time &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND time &lt;= #{endTime} </if>" +
            "GROUP BY result" +
            "</script>")
    List<Map<String, Object>> selectInspectionResultStats(@Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime);
}
