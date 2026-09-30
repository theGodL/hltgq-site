package com.qgyun.hltgq.hltgqsite.mapper;

import com.qgyun.hltgq.hltgqsite.vo.IrrigationWaterLevelVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 水位监测-灌区：每站最新水位 + 1h涨幅
 *
 * 数据源统一口径（2026-09-30 起）：河道/水库站取 t_auto_hltgq_water_river_info；
 * 闸站（档案监测类型含水位 #1# 且 river_info 无记录）取 t_auto_hltgq_water_gate 闸前水位 up_z。
 */
@Mapper
public interface IrrigationWaterLevelMapper {

    /**
     * 分页 UNION 分支一 SQL（河道/水库站）：每站最新一条水位 + 1h 涨幅；
     * 日期过滤由调用注解在 ${ew.customSqlSegment} 占位处注入
     */
    String RIVER_LATEST_SQL =
            "SELECT r.STCD AS stcd, s.zzkaec AS stnm, s.id AS id, r.TM AS tm, " +
            "CASE WHEN r.Z = -999 THEN NULL ELSE TRUNC(r.Z, 2) END AS z, " +
            "CASE WHEN r.Z IN (-999, -9991) THEN NULL ELSE " +
            "TRUNC(COALESCE((r.Z - (" +
            "  SELECT r2.Z FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r2 " +
            "  WHERE r2.STCD = r.STCD AND r2.TM &lt;= r.TM - INTERVAL '1 hour' AND r2.Z NOT IN (-999, -9991) " +
            "  ORDER BY r2.TM DESC LIMIT 1" +
            ")) * 100, 0), 2) END AS rise1h, " +
            "fv.vol AS vol " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r " +
            "INNER JOIN (" +
            "  SELECT STCD, MAX(TM) AS MaxTM " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info " +
            "  ${ew.customSqlSegment} " +
            "  GROUP BY STCD" +
            ") rm ON r.STCD = rm.STCD AND r.TM = rm.MaxTM " +
            "LEFT JOIN \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s ON r.STCD = s.iofhpi " +
            "LEFT JOIN (" +
            "  SELECT DISTINCT ON (v.site) v.site, v.vol " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_vol_info v " +
            "  ORDER BY v.site, v.tm DESC " +
            ") fv ON fv.site = s.id ";

    /**
     * 分页 UNION 分支二 SQL（闸站）：每站最新一批有效闸前水位 up_z（哨兵 -999/-9991 预过滤），
     * 同一时刻多闸孔（gate_no）取一条；1h 涨幅取至少 1 小时前最近有效值，无更早记录按 0
     */
    String GATE_LATEST_SQL =
            "SELECT s2.iofhpi AS stcd, s2.zzkaec AS stnm, s2.id AS id, gm.tm AS tm, " +
            "CASE WHEN gm.up_z IN (-999, -9991) THEN NULL ELSE TRUNC(gm.up_z, 2) END AS z, " +
            "CASE WHEN gm.up_z IN (-999, -9991) THEN NULL ELSE " +
            "TRUNC(COALESCE((gm.up_z - (" +
            "  SELECT g2.up_z FROM \"qixiao-apaas\".t_auto_hltgq_water_gate g2 " +
            "  WHERE g2.site = gm.site AND g2.tm &lt;= gm.tm - INTERVAL '1 hour' AND g2.up_z NOT IN (-999, -9991) " +
            "  ORDER BY g2.tm DESC LIMIT 1" +
            ")) * 100, 0), 2) END AS rise1h, " +
            "fv2.vol AS vol " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s2 " +
            "INNER JOIN (" +
            "  SELECT DISTINCT ON (x.site) x.site, x.tm, x.up_z FROM (" +
            "    SELECT g.site, g.tm, g.up_z FROM \"qixiao-apaas\".t_auto_hltgq_water_gate g " +
            "    ${ew.customSqlSegment} " +
            "  ) x WHERE x.up_z IS NOT NULL AND x.up_z NOT IN (-999, -9991) " +
            "  ORDER BY x.site, x.tm DESC" +
            ") gm ON gm.site = s2.id " +
            "LEFT JOIN (" +
            "  SELECT DISTINCT ON (v.site) v.site, v.vol " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_vol_info v " +
            "  ORDER BY v.site, v.tm DESC " +
            ") fv2 ON fv2.site = s2.id ";

    /**
     * 分页查询：每个站点最新一条水位数据
     *
     * @param dateWrapper 监测日期过滤条件（作用于确定"最新"的子查询）
     * @param stcd        站点编号过滤（直接参数绑定，作用于外层结果），null 表示不过滤（全站模式仅返回监测类型含水位 #1# 的站点）
     * @param limit       每页条数
     * @param offset      偏移量
     */
    @Select("<script>" +
            RIVER_LATEST_SQL +
            "<if test='stcd != null'>WHERE r.STCD = #{stcd} </if>" +
            "<if test='stcd == null'>WHERE s.epjutj LIKE '%#1#%' </if>" +
            "UNION ALL " +
            GATE_LATEST_SQL +
            "<if test='stcd != null'>WHERE s2.iofhpi = #{stcd} </if>" +
            "<if test='stcd == null'>WHERE s2.epjutj LIKE '%#1#%' </if>" +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info rx WHERE rx.STCD = s2.iofhpi" +
            ") " +
            "ORDER BY stcd " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "stnm", property = "stnm"),
            @Result(column = "id", property = "id"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "z", property = "z"),
            @Result(column = "rise1h", property = "rise1h"),
            @Result(column = "vol", property = "vol")
    })
    List<IrrigationWaterLevelVO> selectPage(
            @Param("ew") com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?> dateWrapper,
            @Param("stcd") String stcd,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 计数查询：符合条件的站点总数（与 selectPage 同口径：河道站 + 闸站 UNION）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM (" +
            "  SELECT r.STCD AS stcd " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r " +
            "  INNER JOIN (" +
            "    SELECT STCD, MAX(TM) AS MaxTM " +
            "    FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info " +
            "    ${ew.customSqlSegment} " +
            "    GROUP BY STCD" +
            "  ) rm ON r.STCD = rm.STCD AND r.TM = rm.MaxTM " +
            "  LEFT JOIN \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s ON r.STCD = s.iofhpi " +
            "  <if test='stcd != null'>WHERE r.STCD = #{stcd} </if>" +
            "  <if test='stcd == null'>WHERE s.epjutj LIKE '%#1#%' </if>" +
            "  UNION ALL " +
            "  SELECT s2.iofhpi AS stcd " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s2 " +
            "  INNER JOIN (" +
            "    SELECT DISTINCT ON (x.site) x.site FROM (" +
            "      SELECT g.site, g.tm, g.up_z FROM \"qixiao-apaas\".t_auto_hltgq_water_gate g " +
            "      ${ew.customSqlSegment} " +
            "    ) x WHERE x.up_z IS NOT NULL AND x.up_z NOT IN (-999, -9991) " +
            "    ORDER BY x.site, x.tm DESC" +
            "  ) gm ON gm.site = s2.id " +
            "  <if test='stcd != null'>WHERE s2.iofhpi = #{stcd} </if>" +
            "  <if test='stcd == null'>WHERE s2.epjutj LIKE '%#1#%' </if>" +
            "  AND NOT EXISTS (" +
            "    SELECT 1 FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info rx WHERE rx.STCD = s2.iofhpi" +
            "  )" +
            ") t" +
            "</script>")
    long selectCount(
            @Param("ew") com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?> dateWrapper,
            @Param("stcd") String stcd);

    /**
     * 查询站点水位历史数据（用于水位变化图表）
     * 按时间升序返回 TM, Z
     */
    @Select("SELECT r.TM AS \"TM\", TRUNC(r.Z, 2) AS \"Z\" " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r " +
            "WHERE r.STCD = #{stcd} " +
            "AND r.TM >= #{startTime}::timestamp " +
            "AND r.TM <= #{endTime}::timestamp " +
            "ORDER BY r.TM ASC")
    List<Map<String, Object>> selectHistoryRaw(
            @Param("stcd") String stcd,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    /**
     * 查询闸站水位历史数据（闸门表闸前水位 up_z，河道数据源无记录时回退使用）
     * 按时间升序返回 TM, Z；同一时刻多闸孔（gate_no）记录取一条；哨兵值 -999/-9991 已排除
     */
    @Select("SELECT DISTINCT ON (g.tm) g.tm AS \"TM\", TRUNC(g.up_z, 2) AS \"Z\" " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_gate g " +
            "WHERE g.site = (" +
            "  SELECT s.id FROM \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s WHERE s.iofhpi = #{stcd} LIMIT 1" +
            ") " +
            "AND g.tm >= #{startTime}::timestamp " +
            "AND g.tm <= #{endTime}::timestamp " +
            "AND g.up_z IS NOT NULL AND g.up_z NOT IN (-999, -9991) " +
            "ORDER BY g.tm ASC, g.gate_no")
    List<Map<String, Object>> selectGateUpZRaw(
            @Param("stcd") String stcd,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    /**
     * 水位监测全部站点编号+名称（监测类型含水位 #1#：河道站有水位历史 + 闸站有有效闸前水位）
     */
    @Select("SELECT t.code, t.name FROM (" +
            "  SELECT DISTINCT r.STCD AS code, COALESCE(s.zzkaec, r.STCD) AS name " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r " +
            "  LEFT JOIN \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s ON s.iofhpi = r.STCD " +
            "  WHERE s.epjutj LIKE '%#1#%' " +
            "  UNION " +
            "  SELECT s2.iofhpi AS code, s2.zzkaec AS name " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s2 " +
            "  WHERE s2.epjutj LIKE '%#1#%' " +
            "  AND EXISTS (" +
            "    SELECT 1 FROM \"qixiao-apaas\".t_auto_hltgq_water_gate g " +
            "    WHERE g.site = s2.id AND g.up_z IS NOT NULL AND g.up_z NOT IN (-999, -9991)" +
            "  )" +
            ") t ORDER BY t.code")
    @Results({
            @Result(column = "code", property = "code"),
            @Result(column = "name", property = "name")
    })
    List<StationSiteVO> selectWaterLevelStations();
}
