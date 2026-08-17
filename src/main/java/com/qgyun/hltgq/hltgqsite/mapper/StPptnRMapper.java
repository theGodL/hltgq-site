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
            "INNER JOIN (SELECT STCD, MAX((TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '32 hours') AS MaxHydroDay FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info GROUP BY STCD) sub " +
            "ON t.STCD = sub.STCD AND (t.TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '32 hours' = sub.MaxHydroDay " +
            "GROUP BY t.STCD, sub.MaxHydroDay " +
            "ORDER BY t.STCD")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "drp", property = "drp")
    })
    List<StPptnR> selectLatestPerStation();

    @Select("SELECT STCD, (TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '32 hours' AS tm, MAX(DRP) AS drp " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info " +
            "${ew.customSqlSegment} " +
            "GROUP BY STCD, (TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '32 hours' " +
            "ORDER BY STCD, (TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '32 hours'")
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
     * 灌区雨量：每站点最新一条，含该时刻及1h/3h/6h前的DYP值（用于计算时段增量）
     * <p>性能：用 DISTINCT ON 替代 ROW_NUMBER 窗口（配合 (STCD, TM DESC) 索引，
     * 每组直接取最新行，无需全量窗口排序）；基线子查询仅对每站最新一行执行。
     * 支持按站点编号、监测日期范围筛选
     * <p>drp 基线（dyp_day）口径：
     * 未带时间筛选（实时列表）→ 服务器当前水文日 8 点起点（hydroBase），
     * "当前雨量"始终表示当前水文日累计，最新报文停留在上一水文日时不会与"昨日雨量"重合；
     * 带时间筛选（历史视图）→ 该行记录所属水文日 8 点起点，保证每行 drp 自洽。
     */
    @Select("<script>"
            + "SELECT t.STCD AS stcd, t.TM AS tm, t.DRP AS drp, t.DYP AS dyp, "
            + "  s.zzkaec AS stnm, s.id AS id, s.bviiio_x AS lon, s.bviiio_y AS lat, "
            + "  fv.vol AS vol, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= t.TM - INTERVAL '1 hour' + INTERVAL '1 second' "
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_1h, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= t.TM - INTERVAL '3 hours' + INTERVAL '1 second' "
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_3h, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= t.TM - INTERVAL '6 hours' + INTERVAL '1 second' "
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_6h, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= "
            + "            <choose>"
            + "              <when test=\"startTime != null or endTime != null\">"
            + "                ((t.TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '8 hours') "
            + "              </when>"
            + "              <otherwise>#{hydroBase} </otherwise>"
            + "            </choose>"
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_day "
            + "FROM ( "
            + "  SELECT DISTINCT ON (STCD) STCD, TM, DRP, DYP "
            + "  FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "  WHERE 1=1 "
            + "  <if test=\"stcd != null and stcd != ''\"> AND STCD = #{stcd} </if>"
            + "  <if test=\"startTime != null\"> AND TM &gt;= #{startTime} </if>"
            + "  <if test=\"endTime != null\"> AND TM &lt;= #{endTime} </if>"
            + "  ORDER BY STCD, TM DESC "
            + ") t "
            + "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.iofhpi = t.STCD "
            + "LEFT JOIN ( "
            + "  SELECT DISTINCT ON (v.site) v.site, v.vol "
            + "  FROM \"qixiao-apaas\".t_auto_hltgq_water_vol_info v "
            + "  ORDER BY v.site, v.tm DESC "
            + ") fv ON fv.site = s.id "
            + "ORDER BY t.STCD"
            + "</script>")
    List<Map<String, Object>> selectGqRainfallList(@Param("stcd") String stcd,
                                                    @Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime,
                                                    @Param("hydroBase") LocalDateTime hydroBase);

    /**
     * 灌区雨情历史：单站点分页记录（TM 倒序），每一条含1h/3h/6h前DYP值（用于计算时段增量）
     * <p>性能：先分页取当前页行，基线子查询仅对当前页行执行
     * （原实现全量行 × 4 次子查询，接口慢到 2 秒）。
     * stcd 必填，startTime/endTime 可选
     */
    @Select("<script>"
            + "WITH page AS ( "
            + "  SELECT t.STCD, t.TM, t.DRP, t.DYP "
            + "  FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info t "
            + "  WHERE t.STCD = #{stcd} "
            + "  <if test=\"startTime != null\"> AND t.TM &gt;= #{startTime} </if>"
            + "  <if test=\"endTime != null\"> AND t.TM &lt;= #{endTime} </if>"
            + "  ORDER BY t.TM DESC "
            + "  LIMIT #{limit} OFFSET #{offset} "
            + ") "
            + "SELECT t.STCD AS stcd, t.TM AS tm, t.DRP AS drp, t.DYP AS dyp, "
            + "  s.zzkaec AS stnm, s.id AS id, s.bviiio_x AS lon, s.bviiio_y AS lat, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= t.TM - INTERVAL '1 hour' + INTERVAL '1 second' "
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_1h, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= t.TM - INTERVAL '3 hours' + INTERVAL '1 second' "
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_3h, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= t.TM - INTERVAL '6 hours' + INTERVAL '1 second' "
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_6h, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND TM &lt;= ((t.TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '8 hours') "
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_day "
            + "FROM page t "
            + "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.iofhpi = t.STCD "
            + "ORDER BY t.TM DESC"
            + "</script>")
    List<Map<String, Object>> selectGqRainfallHistoryPage(@Param("stcd") String stcd,
                                                           @Param("startTime") LocalDateTime startTime,
                                                           @Param("endTime") LocalDateTime endTime,
                                                           @Param("limit") int limit,
                                                           @Param("offset") int offset);

    /**
     * 灌区雨情历史：分页总数（stcd 必填，startTime/endTime 可选）
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info t "
            + "WHERE t.STCD = #{stcd} "
            + "<if test=\"startTime != null\"> AND t.TM &gt;= #{startTime} </if>"
            + "<if test=\"endTime != null\"> AND t.TM &lt;= #{endTime} </if>"
            + "</script>")
    long countGqRainfallHistory(@Param("stcd") String stcd,
                                 @Param("startTime") LocalDateTime startTime,
                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 按站点和时间范围查询原始雨量记录，用于图表增量计算
     */
    @Select("SELECT STCD, TM, DRP, DYP " +
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
            "SELECT STCD, TM, DRP, DYP " +
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
