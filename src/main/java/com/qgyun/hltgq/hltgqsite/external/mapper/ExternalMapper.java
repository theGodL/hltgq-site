package com.qgyun.hltgq.hltgqsite.external.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 三维系统对接（/external）聚合查询。
 * <p>数据口径与页面接口一致：表均带 schema `qixiao-apaas`；
 * 巡检次数仅统计已提交记录（status=#2# 排除草稿）；问题按发现时间 time 全状态统计。
 */
@Mapper
public interface ExternalMapper {

    /**
     * 渠首进水闸最新一条闸门数据（闸前/闸后水位）。
     * 无效值清洗（-999 设备不存在、-9991 设备异常）由 Service 层处理。
     */
    @Select("SELECT tm, up_z, down_z " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" " +
            "WHERE site = #{site} " +
            "ORDER BY tm DESC LIMIT 1")
    Map<String, Object> selectLatestGateLevel(@Param("site") String site);

    /**
     * 渠首进水闸最新一条有效流量（排除 -999/-9991 无效值）。
     */
    @Select("SELECT tm, q " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" " +
            "WHERE site = #{site} AND q NOT IN (-999, -9991) " +
            "ORDER BY tm DESC LIMIT 1")
    Map<String, Object> selectLatestFlow(@Param("site") String site);

    /**
     * 巡检汇总：累计巡检次数（已提交）/ 巡检计划总数 / 完成巡检数（计划已完成）。
     * 三条计数合并为一条 SQL（三张表同 schema，逐条 COUNT 子查询）。
     */
    @Select("SELECT " +
            "(SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" WHERE status = '#2#') AS patrol_count, " +
            "(SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\") AS schedule_count, " +
            "(SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" WHERE status = '#3#') AS finished_count")
    Map<String, Object> selectPatrolSummary();

    /**
     * 逐日巡检次数：已提交记录按巡检时间 time 取日期聚合。
     */
    @Select("SELECT to_char(time, 'YYYY-MM-DD') AS day, COUNT(*) AS cnt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "WHERE status = '#2#' AND time >= #{startTime} AND time <= #{endTime} " +
            "GROUP BY to_char(time, 'YYYY-MM-DD')")
    List<Map<String, Object>> selectDailyPatrol(@Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 逐日问题数量：问题按发现时间 time 取日期聚合（全状态）。
     */
    @Select("SELECT to_char(time, 'YYYY-MM-DD') AS day, COUNT(*) AS cnt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "WHERE time >= #{startTime} AND time <= #{endTime} " +
            "GROUP BY to_char(time, 'YYYY-MM-DD')")
    List<Map<String, Object>> selectDailyIssue(@Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    /**
     * 问题状态分布：name = status 编码（#1# 待处理 / #2# 处理中 / #3# 已转工单 / #4# 已关闭 / #5# 已作废）。
     */
    @Select("SELECT status AS name, COUNT(*) AS \"value\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "GROUP BY status")
    List<Map<String, Object>> selectIssueStatus();

    /**
     * 视频设备按管理所聚合：安装位置 wlcvig 按「-」截取首段归组，
     * 空/无分隔符归 null（由 Service 层填充「未知」）；status #1#=在线、其他=离线。
     */
    @Select("SELECT split_part(wlcvig, '-', 1) AS org, " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN status = '#1#' THEN 1 ELSE 0 END) AS online " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" " +
            "WHERE type LIKE '%#5#%' " +
            "GROUP BY split_part(wlcvig, '-', 1) " +
            "ORDER BY org")
    List<Map<String, Object>> selectVideoSummary();
}
