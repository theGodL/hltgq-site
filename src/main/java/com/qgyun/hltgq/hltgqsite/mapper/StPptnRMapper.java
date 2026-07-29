package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.StPptnR;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface StPptnRMapper extends BaseMapper<StPptnR> {

    @Select("SELECT t.STCD, sub.MaxHydroDay AS tm, MAX(t.DRP) AS drp " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info t " +
            "INNER JOIN (SELECT STCD, MAX((TM - INTERVAL '8 hours')::date + INTERVAL '32 hours') AS MaxHydroDay FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info GROUP BY STCD) sub " +
            "ON t.STCD = sub.STCD AND (t.TM - INTERVAL '8 hours')::date + INTERVAL '32 hours' = sub.MaxHydroDay " +
            "GROUP BY t.STCD, sub.MaxHydroDay " +
            "ORDER BY t.STCD")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "drp", property = "drp")
    })
    List<StPptnR> selectLatestPerStation();

    @Select("SELECT STCD, (TM - INTERVAL '8 hours')::date + INTERVAL '32 hours' AS tm, MAX(DRP) AS drp " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info " +
            "${ew.customSqlSegment} " +
            "GROUP BY STCD, (TM - INTERVAL '8 hours')::date + INTERVAL '32 hours' " +
            "ORDER BY STCD, (TM - INTERVAL '8 hours')::date + INTERVAL '32 hours'")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "drp", property = "drp")
    })
    List<StPptnR> selectDailyAll(@Param("ew") QueryWrapper<StPptnR> wrapper);

    @Select("SELECT STCD, MAX(DRP) AS drp FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info WHERE TM >= #{start}::timestamp AND TM <= #{end}::timestamp GROUP BY STCD")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "drp", property = "drp")
    })
    List<StPptnR> selectTodaySumPerStation(@Param("start") Timestamp start, @Param("end") Timestamp end);

    /**
     * 灌区雨量：每站点最新一条，含该时刻及1h/3h/6h前的DRP值
     * 支持按站点编号、监测日期范围筛选
     */
    @Select("<script>"
            + "SELECT t.STCD AS stcd, t.TM AS tm, t.DRP AS drp, t.DYP AS dyp, "
            + "  s.zzkaec AS stnm, s.id AS id, "
            + "  COALESCE((SELECT DRP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= t.TM - INTERVAL '1 hour' "
            + "            ORDER BY TM DESC LIMIT 1), 0) AS drp_1h, "
            + "  COALESCE((SELECT DRP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= t.TM - INTERVAL '3 hours' "
            + "            ORDER BY TM DESC LIMIT 1), 0) AS drp_3h, "
            + "  COALESCE((SELECT DRP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= t.TM - INTERVAL '6 hours' "
            + "            ORDER BY TM DESC LIMIT 1), 0) AS drp_6h "
            + "FROM ( "
            + "  SELECT STCD, TM, DRP, DYP, "
            + "    ROW_NUMBER() OVER (PARTITION BY STCD ORDER BY TM DESC) AS rn "
            + "  FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "  WHERE 1=1 "
            + "  <if test=\"stcd != null and stcd != ''\"> AND STCD = #{stcd} </if>"
            + "  <if test=\"startTime != null\"> AND TM &gt;= #{startTime} </if>"
            + "  <if test=\"endTime != null\"> AND TM &lt;= #{endTime} </if>"
            + ") t "
            + "LEFT JOIN \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s ON s.iofhpi = t.STCD "
            + "WHERE t.rn = 1 "
            + "ORDER BY t.STCD"
            + "</script>")
    List<Map<String, Object>> selectGqRainfallList(@Param("stcd") String stcd,
                                                    @Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);

    /**
     * 灌区雨量历史：单站点全部记录，每一条含1h/3h/6h前DRP值（用于计算时段增量）
     * stcd 必填，startTime/endTime 可选
     */
    @Select("<script>"
            + "SELECT t.STCD AS stcd, t.TM AS tm, t.DRP AS drp, t.DYP AS dyp, "
            + "  s.zzkaec AS stnm, s.id AS id, "
            + "  COALESCE((SELECT DRP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= t.TM - INTERVAL '1 hour' "
            + "            ORDER BY TM DESC LIMIT 1), 0) AS drp_1h, "
            + "  COALESCE((SELECT DRP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= t.TM - INTERVAL '3 hours' "
            + "            ORDER BY TM DESC LIMIT 1), 0) AS drp_3h, "
            + "  COALESCE((SELECT DRP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= t.TM - INTERVAL '6 hours' "
            + "            ORDER BY TM DESC LIMIT 1), 0) AS drp_6h "
            + "FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info t "
            + "LEFT JOIN \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s ON s.iofhpi = t.STCD "
            + "WHERE t.STCD = #{stcd} "
            + "<if test=\"startTime != null\"> AND t.TM &gt;= #{startTime} </if>"
            + "<if test=\"endTime != null\"> AND t.TM &lt;= #{endTime} </if>"
            + "ORDER BY t.TM DESC"
            + "</script>")
    List<Map<String, Object>> selectGqRainfallHistory(@Param("stcd") String stcd,
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 按站点和时间范围查询原始雨量记录，用于图表增量计算
     */
    @Select("SELECT STCD, TM, DRP " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info " +
            "WHERE STCD = #{stcd} " +
            "AND TM >= #{startTime} " +
            "AND TM <= #{endTime} " +
            "ORDER BY TM ASC")
    List<StPptnR> selectByStcdAndTimeRange(@Param("stcd") String stcd,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 按多个站点编号 + 时间范围批量查询原始雨量记录
     */
    @Select("<script>" +
            "SELECT STCD, TM, DRP " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info " +
            "WHERE STCD IN " +
            "<foreach collection='stcds' item='s' open='(' separator=',' close=')'>#{s}</foreach> " +
            "AND TM &gt;= #{startTime} " +
            "AND TM &lt;= #{endTime} " +
            "ORDER BY TM ASC" +
            "</script>")
    List<StPptnR> selectByStcdsAndTimeRange(@Param("stcds") List<String> stcds,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);

    /**
     * 雨量监测全部站点编号（去重）
     */
    @Select("SELECT DISTINCT STCD FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info ORDER BY STCD")
    List<String> selectDistinctRainfallStcds();
}
