package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.StPptnR;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface StPptnRMapper extends BaseMapper<StPptnR> {

    // 全站模式仅返回监测类型含雨量 #2# 的站点
    // 软删过滤（监测数据删除方案 §5.4）：子查询与主查询同步跳过已删行，
    // 漏一处即“最新水文日取到已删行”的混合口径
    @Select("SELECT t.STCD, sub.MaxHydroDay AS tm, MAX(t.DRP) AS drp " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info t " +
            "INNER JOIN (SELECT STCD, MAX((TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '32 hours') AS MaxHydroDay FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info WHERE deleted IS NOT TRUE GROUP BY STCD) sub " +
            "ON t.STCD = sub.STCD AND (t.TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '32 hours' = sub.MaxHydroDay " +
            "LEFT JOIN \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s ON s.iofhpi = t.STCD " +
            "WHERE s.epjutj LIKE '%#2#%' AND t.deleted IS NOT TRUE " +
            "GROUP BY t.STCD, sub.MaxHydroDay " +
            "ORDER BY t.STCD")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "drp", property = "drp")
    })
    List<StPptnR> selectLatestPerStation();

    // 注：本 SQL 为 wrapper 注入式，软删过滤由调用方附加（见 StPptnRController#pageDaily 的 deleted IS NOT TRUE）
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

    @Select("SELECT STCD, MAX(DRP) AS drp FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info WHERE deleted IS NOT TRUE AND TM >= #{start}::timestamp AND TM <= #{end}::timestamp GROUP BY STCD")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "drp", property = "drp")
    })
    List<StPptnR> selectTodaySumPerStation(@Param("start") Timestamp start, @Param("end") Timestamp end);

    /**
     * 灌区雨量：每站点最新一条，含该时刻及1h/3h/6h前的DYP值（用于计算时段增量；
     * 1h/3h/6h 窗口按当前水文日 8:00 边界截断，不跨水文日，8 点后自动"清零"）
     * <p>性能：用 DISTINCT ON 替代 ROW_NUMBER 窗口（配合 (STCD, TM DESC) 索引，
     * 每组直接取最新行，无需全量窗口排序）；基线子查询仅对每站最新一行执行。
     * 支持按站点编号、监测日期范围筛选
     * <p>drp 基线（dyp_day）口径：
     * 未带时间筛选（实时列表）→ 服务器当前水文日 8 点起点（hydroBase），
     * "当前雨量"始终表示当前水文日累计，最新报文停留在上一水文日时不会与"昨日雨量"重合；
     * 带时间筛选（历史视图）→ 该行记录所属水文日 8 点起点，保证每行 drp 自洽。
     * <p>canalIds 非空时按渠系树过滤：仅返回站点表 ywvyds 落在渠系集合内的站点
     * （canalId 及其所有子孙渠系 id，由 CanalService 收集）。
     * <p>软删过滤（监测数据删除方案 §5.4）：主查询与 4 个基线子查询（dyp_1h/3h/6h/day）
     * 逐处追加 AND deleted IS NOT TRUE，漏一处即“列表基线取到已删行”的混合口径。
     */
    @Select("<script>"
            + "SELECT t.STCD AS stcd, t.TM AS tm, t.DRP AS drp, t.DYP AS dyp, "
            + "  s.zzkaec AS stnm, s.id AS id, s.bviiio_x AS lon, s.bviiio_y AS lat, "
            + "  s.ywvyds AS canal_id, c.gfaegg AS canal_name, "
            + "  fv.vol AS vol, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND deleted IS NOT TRUE AND TM &lt;= GREATEST(t.TM - INTERVAL '1 hour', ((t.TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '8 hours')) + INTERVAL '1 second' "
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_1h, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND deleted IS NOT TRUE AND TM &lt;= GREATEST(t.TM - INTERVAL '3 hours', ((t.TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '8 hours')) + INTERVAL '1 second' "
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_3h, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND deleted IS NOT TRUE AND TM &lt;= GREATEST(t.TM - INTERVAL '6 hours', ((t.TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '8 hours')) + INTERVAL '1 second' "
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_6h, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND deleted IS NOT TRUE AND TM &lt;= "
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
            + "  WHERE 1=1 AND deleted IS NOT TRUE "
            + "  <if test=\"stcd != null and stcd != ''\"> AND STCD = #{stcd} </if>"
            + "  <if test=\"startTime != null\"> AND TM &gt;= #{startTime} </if>"
            + "  <if test=\"endTime != null\"> AND TM &lt;= #{endTime} </if>"
            + "  ORDER BY STCD, TM DESC "
            + ") t "
            + "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.iofhpi = t.STCD "
            + "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_knc3g_egvnhw\" c ON c.id = s.ywvyds "
            + "LEFT JOIN ( "
            + "  SELECT DISTINCT ON (v.site) v.site, v.vol "
            + "  FROM \"qixiao-apaas\".t_auto_hltgq_water_vol_info v "
            + "  ORDER BY v.site, v.tm DESC "
            + ") fv ON fv.site = s.id "
            + "<if test=\"canalIds != null and canalIds.size() > 0\">"
            + "WHERE s.ywvyds IN "
            + "<foreach collection=\"canalIds\" item=\"cid\" open=\"(\" separator=\",\" close=\")\">#{cid}</foreach> "
            + "</if>"
            + "ORDER BY t.STCD"
            + "</script>")
    List<Map<String, Object>> selectGqRainfallList(@Param("stcd") String stcd,
                                                    @Param("canalIds") List<String> canalIds,
                                                    @Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime,
                                                    @Param("hydroBase") LocalDateTime hydroBase);

    /**
     * 灌区雨情历史：单站点分页记录（TM 倒序），每一条含1h/3h/6h前DYP值（用于计算时段增量；
     * 1h/3h/6h 窗口按该行所属水文日 8:00 边界截断，不跨水文日）
     * <p>性能：先分页取当前页行，基线子查询仅对当前页行执行
     * （原实现全量行 × 4 次子查询，接口慢到 2 秒）。
     * stcd 必填，startTime/endTime 可选
     * <p>软删过滤（监测数据删除方案 §5.4）：分页 CTE 与 4 个基线子查询逐处过滤；
     * countGqRainfallHistory 必须与本法同口径（同过滤），否则分页错位。
     */
    @Select("<script>"
            + "WITH page AS ( "
            + "  SELECT t.STCD, t.TM, t.DRP, t.DYP "
            + "  FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info t "
            + "  WHERE t.STCD = #{stcd} AND t.deleted IS NOT TRUE "
            + "  <if test=\"startTime != null\"> AND t.TM &gt;= #{startTime} </if>"
            + "  <if test=\"endTime != null\"> AND t.TM &lt;= #{endTime} </if>"
            + "  ORDER BY t.TM DESC "
            + "  LIMIT #{limit} OFFSET #{offset} "
            + ") "
            + "SELECT t.STCD AS stcd, t.TM AS tm, t.DRP AS drp, t.DYP AS dyp, "
            + "  s.zzkaec AS stnm, s.id AS id, s.bviiio_x AS lon, s.bviiio_y AS lat, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND deleted IS NOT TRUE AND TM &lt;= GREATEST(t.TM - INTERVAL '1 hour', ((t.TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '8 hours')) + INTERVAL '1 second' "
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_1h, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND deleted IS NOT TRUE AND TM &lt;= GREATEST(t.TM - INTERVAL '3 hours', ((t.TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '8 hours')) + INTERVAL '1 second' "
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_3h, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND deleted IS NOT TRUE AND TM &lt;= GREATEST(t.TM - INTERVAL '6 hours', ((t.TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '8 hours')) + INTERVAL '1 second' "
            + "            ORDER BY TM DESC LIMIT 1), t.DYP) AS dyp_6h, "
            + "  COALESCE((SELECT DYP FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info "
            + "            WHERE STCD = t.STCD AND deleted IS NOT TRUE AND TM &lt;= ((t.TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '8 hours') "
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
     * 灌区雨情历史：分页总数（stcd 必填，startTime/endTime 可选；软删过滤与分页列表同口径）
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info t "
            + "WHERE t.STCD = #{stcd} AND t.deleted IS NOT TRUE "
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
            "WHERE STCD = #{stcd} AND deleted IS NOT TRUE " +
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
            "AND deleted IS NOT TRUE " +
            "ORDER BY TM ASC" +
            "</script>")
    List<StPptnR> selectByStcdsAndTimeRange(@Param("stcds") List<String> stcds,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);

    /**
     * 雨量监测全部站点编号（去重，仅保留监测类型含雨量 #2# 的站点）
     * <p>刻意不过滤软删行（监测数据删除方案 §5.4，勿改）：本查询是「该站是否雨量站」的
     * 存在性资格判定（表中有记录即为雨量站，软删行保留占位），非数值口径——
     * 站点资格不因删除问题数据行而改变。
     */
    @Select("SELECT DISTINCT r.STCD FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info r " +
            "LEFT JOIN \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s ON s.iofhpi = r.STCD " +
            "WHERE s.epjutj LIKE '%#2#%' " +
            "ORDER BY r.STCD")
    List<String> selectDistinctRainfallStcds();

    /**
     * 历史数据管理——软删一行（监测数据删除方案 §5.2）：deleted 翻转 + 审计列写入；
     * deleted_at 用 DB 时钟 now()（勿用客户端时间）。
     * <p>条件 deleted IS NOT TRUE：行不存在或已处于删除态时影响 0 行，由上层提示刷新重试。
     *
     * @return 影响行数（1=成功；0=行不存在或已处于删除态）
     */
    @Update("UPDATE \"qixiao-apaas\".t_auto_hltgq_water_rain_info " +
            "SET deleted = TRUE, deleted_at = now(), deleted_by = #{by} " +
            "WHERE \"STCD\" = #{stcd} AND \"TM\" = #{tm} AND deleted IS NOT TRUE")
    int softDeleteByKey(@Param("stcd") String stcd, @Param("tm") Timestamp tm, @Param("by") String by);

    /**
     * 历史数据管理——恢复一行：deleted 翻转回 false + 审计列刷新
     * （恢复不清空 deleted_at，语义＝最近一次状态变更时刻；删除与恢复均刷新）。
     *
     * @return 影响行数（1=成功；0=行不存在或未处于删除态）
     */
    @Update("UPDATE \"qixiao-apaas\".t_auto_hltgq_water_rain_info " +
            "SET deleted = FALSE, deleted_at = now(), deleted_by = #{by} " +
            "WHERE \"STCD\" = #{stcd} AND \"TM\" = #{tm} AND deleted IS TRUE")
    int restoreByKey(@Param("stcd") String stcd, @Param("tm") Timestamp tm, @Param("by") String by);

    /**
     * 当前基线行 TM（删除护栏一级判定用）：stcd 匹配、未删、tm ≤ 当前水文日起点、tm 最大的一行。
     * <p>当前水文日起点（调用方计算）：今日 08:00，当前时刻 &lt;08:00 回退昨日 08:00——
     * 与 mq computeCurrentRainfall / queryHydroDayBaseDyp 同口径（方案 §5.3）。
     *
     * @return 基线行 TM；该站无符合条件的行时为 null
     */
    @Select("SELECT MAX(\"TM\") FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info " +
            "WHERE \"STCD\" = #{stcd} AND deleted IS NOT TRUE AND \"TM\" <= #{hydroStart}")
    Timestamp selectBaselineTm(@Param("stcd") String stcd, @Param("hydroStart") Timestamp hydroStart);
}
