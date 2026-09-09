package com.qgyun.hltgq.hltgqsite.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据统计（大屏）本地查询：与 mq 统计无关的 site 侧数据源。
 */
public interface DataStatsMapper {

    /**
     * 今日预警发布聚合：本项目无发布动作，业务口径「每次生成告警即发布」，
     * 统计告警表当日生成总数与最近一次生成时间（time >= 当日 0 点，含全部处理状态）。
     * 返回 {total: Long, latest: String}；当日无告警时 total=0、latest=null。
     * latest 为北京时间文本（yyyy-MM-dd HH:mm:ss）：MAX(time) 经 AT TIME ZONE 转墙钟后 to_char，
     * 避免 timestamptz 经 JDBC 按 JVM 时区（UTC）转换输出带 +00:00 偏移的 ISO 格式。
     */
    @Select("SELECT COUNT(*) AS total, " +
            "to_char(MAX(time) AT TIME ZONE 'Asia/Shanghai', 'YYYY-MM-DD HH24:MI:SS') AS latest " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_alert\" " +
            "WHERE time >= #{startTime}")
    Map<String, Object> selectTodayAlertPublish(@Param("startTime") LocalDateTime startTime);
}
