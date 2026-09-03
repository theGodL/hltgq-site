package com.qgyun.hltgq.hltgqsite.decision.mapper;

import com.qgyun.hltgq.hltgqsite.decision.vo.ObsDailyVO;
import com.qgyun.hltgq.hltgqsite.decision.vo.ObsPointVO;
import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 防洪抗旱水文分析数据查询：
 * 站点解析（配置未指定时按站点类型自动选站）+ 雨量水文日聚合 + 水位/流量原始采集点。
 * <p>列别名与 VO 属性同名自动映射（map-underscore-to-camel-case=false）。
 */
public interface FloodDroughtMapper {

    /**
     * 按站点类型编码选一个站点（如 #2# 雨量、#1# 水位、#3# 流量）；无则 null。
     * 确定性排序保证多次解析结果一致。
     */
    @Select("SELECT iofhpi FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" " +
            "WHERE epjutj LIKE CONCAT('%', #{typeCode}, '%') " +
            "ORDER BY iofhpi LIMIT 1")
    String selectStationByType(@Param("typeCode") String typeCode);

    /** 站点经纬度（雨量站坐标，天气预测用）。 */
    @Select("SELECT bviiio_x AS lon, bviiio_y AS lat " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" " +
            "WHERE iofhpi = #{stcd} LIMIT 1")
    SiteCoord selectStationCoord(@Param("stcd") String stcd);

    /**
     * 雨量水文日聚合：标签 D = (D-1 08:00, D 08:00] 区间内 DYP 正向增量之和。
     * <p>窗口由调用方给 [startDate-1 08:00, obsEnd+1 08:00]（覆盖目标标签全部样本）；
     * LAG 窗口 + GREATEST 截断 DYP 归零/回退产生的负增量。
     * <p>标签用 date_trunc('day', tm-8h) 返回 timestamp（KingbaseES 的 ::date 自定义类型
     * JDBC 无法转 LocalDate，项目统一用 date_trunc + LocalDateTime）。
     */
    @Select("SELECT date_trunc('day', tm - INTERVAL '8 hours') AS d, " +
            "SUM(GREATEST(dyp - COALESCE(lag_dyp, 0), 0)) AS \"value\" " +
            "FROM (SELECT \"TM\" AS tm, \"DYP\" AS dyp, " +
            "             LAG(\"DYP\") OVER (ORDER BY \"TM\") AS lag_dyp " +
            "      FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info " +
            "      WHERE \"STCD\" = #{stcd} AND \"TM\" >= #{start} AND \"TM\" <= #{end}) t " +
            "GROUP BY date_trunc('day', tm - INTERVAL '8 hours') " +
            "ORDER BY d")
    List<ObsDailyVO> selectRainDaily(@Param("stcd") String stcd,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    /** 水位原始采集点（每日 8 时整点值由 Service 内存挑选）。 */
    @Select("SELECT \"TM\" AS tm, \"Z\" AS \"value\" " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info " +
            "WHERE \"STCD\" = #{stcd} AND \"TM\" >= #{start} AND \"TM\" <= #{end} " +
            "ORDER BY \"TM\"")
    List<ObsPointVO> selectLevelPoints(@Param("stcd") String stcd,
                                       @Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    /** 流量原始采集点（每日 8 时整点值由 Service 内存挑选）；站点键兼容 stcd 编号与 site UUID（MQTT 站无 stcd）。 */
    @Select("SELECT \"tm\" AS tm, \"q\" AS \"value\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" " +
            "WHERE (\"stcd\" = #{stcd} OR \"site\" = #{stcd}) " +
            "AND \"tm\" >= #{start} AND \"tm\" <= #{end} " +
            "ORDER BY \"tm\"")
    List<ObsPointVO> selectFlowPoints(@Param("stcd") String stcd,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /** 站点经纬度 */
    @Data
    class SiteCoord {
        private Double lon;
        private Double lat;
    }
}
