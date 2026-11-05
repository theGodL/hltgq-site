package com.qgyun.hltgq.hltgqsite.mapper;

import com.qgyun.hltgq.hltgqsite.vo.IrrigationWaterLevelVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
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
     * 分页 UNION 分支一 SQL（河道/水库站）：每站最新一条水位 + 1h 涨幅。
     * <p>站点表驱动 + 每站 LATERAL 点查最新一条（命中 (stcd, tm DESC) 索引），
     * 原写法对 river_info 全表 GROUP BY STCD 再回表，表越大越慢；日期过滤下沉到 LATERAL 内。
     * vol 走 LEFT JOIN LATERAL 按站点索引点查。
     */
    String RIVER_LATEST_SQL =
            "SELECT r.stcd AS stcd, s.zzkaec AS stnm, s.id AS id, r.tm AS tm, " +
            "CASE WHEN r.z = -999 THEN NULL ELSE TRUNC(r.z, 2) END AS z, " +
            "CASE WHEN r.z IN (-999, -9991) THEN NULL ELSE " +
            "TRUNC(COALESCE((r.z - (" +
            "  SELECT r2.z FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r2 " +
            "  WHERE r2.stcd = r.stcd AND r2.tm &lt;= r.tm - INTERVAL '1 hour' AND r2.z NOT IN (-999, -9991) " +
            "  ORDER BY r2.tm DESC LIMIT 1" +
            ")) * 100, 0), 2) END AS rise1h, " +
            "fv.vol AS vol " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s " +
            "INNER JOIN LATERAL (" +
            "  SELECT r.stcd, r.tm, r.z " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r " +
            "  WHERE r.stcd = s.iofhpi " +
            "  <if test='startTime != null'>AND r.tm &gt;= #{startTime} </if>" +
            "  <if test='endTime != null'>AND r.tm &lt;= #{endTime} </if>" +
            "  ORDER BY r.tm DESC LIMIT 1" +
            ") r ON true " +
            "LEFT JOIN LATERAL (" +
            "  SELECT v.vol " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_vol_info v " +
            "  WHERE v.site = s.id " +
            "  ORDER BY v.tm DESC LIMIT 1" +
            ") fv ON true ";

    /**
     * 分页 UNION 分支二 SQL（闸站）：每站最新一批有效闸前水位 up_z（哨兵 -999/-9991 预过滤），
     * 同一时刻多闸孔（gate_no）取一条；1h 涨幅取至少 1 小时前最近有效值，无更早记录按 0。
     * <p>站点表驱动 + 每站 LATERAL 点查最新一条（命中 (site, tm DESC) 索引 idx_gate_site_tm_latest），
     * 原写法对 gate 全表 GROUP BY site 再回表（34 万行实测约 0.43s），改为每站一次索引点查；
     * 日期过滤下沉到 LATERAL 内。
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
            "INNER JOIN LATERAL (" +
            "  SELECT g.site, g.tm, g.up_z " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_gate g " +
            "  WHERE g.site = s2.id AND g.up_z IS NOT NULL AND g.up_z NOT IN (-999, -9991) " +
            "  <if test='startTime != null'>AND g.tm &gt;= #{startTime} </if>" +
            "  <if test='endTime != null'>AND g.tm &lt;= #{endTime} </if>" +
            "  ORDER BY g.tm DESC LIMIT 1" +
            ") gm ON true " +
            "LEFT JOIN LATERAL (" +
            "  SELECT v.vol " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_vol_info v " +
            "  WHERE v.site = s2.id " +
            "  ORDER BY v.tm DESC LIMIT 1" +
            ") fv2 ON true ";

    /**
     * 分页查询：每个站点最新一条水位数据
     *
     * @param startTime 监测开始时间（可选，下沉到各站 LATERAL 内）
     * @param endTime   监测结束时间（可选，下沉到各站 LATERAL 内）
     * @param stcd      站点编号过滤（直接参数绑定，作用于站点表），null 表示不过滤（全站模式仅返回监测类型含水位 #1# 的站点）
     * @param limit     每页条数
     * @param offset    偏移量
     */
    @Select("<script>" +
            RIVER_LATEST_SQL +
            "<if test='stcd != null'>WHERE s.iofhpi = #{stcd} </if>" +
            "<if test='stcd == null'>WHERE s.epjutj LIKE '%#1#%' </if>" +
            "UNION ALL " +
            GATE_LATEST_SQL +
            "<if test='stcd != null'>WHERE s2.iofhpi = #{stcd} </if>" +
            "<if test='stcd == null'>WHERE s2.epjutj LIKE '%#1#%' </if>" +
            // EXISTS 前置剪枝：先用站点索引判断该站是否存在有效闸前水位（无数据的站点直接跳过 LATERAL，
            // 避免每站逆序扫 gate 全表）；不能用 (site, tm DESC) 索引时也能快速失败
            "AND EXISTS (" +
            "  SELECT 1 FROM \"qixiao-apaas\".t_auto_hltgq_water_gate ge " +
            "  WHERE ge.site = s2.id AND ge.up_z IS NOT NULL AND ge.up_z NOT IN (-999, -9991) " +
            "  <if test='startTime != null'>AND ge.tm &gt;= #{startTime} </if>" +
            "  <if test='endTime != null'>AND ge.tm &lt;= #{endTime} </if>" +
            ") " +
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
            @Param("startTime") Timestamp startTime,
            @Param("endTime") Timestamp endTime,
            @Param("stcd") String stcd,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 计数查询：符合条件的站点总数（与 selectPage 同口径：河道站 + 闸站 UNION）。
     * <p>计数只判存在性，不需要算最新值：站点表驱动 + EXISTS 点查，避免对 river_info / gate 全表聚合
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM (" +
            "  SELECT s.iofhpi AS stcd " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s " +
            "  WHERE <if test='stcd != null'>s.iofhpi = #{stcd} </if>" +
            "        <if test='stcd == null'>s.epjutj LIKE '%#1#%' </if>" +
            "  AND EXISTS (" +
            "    SELECT 1 FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r " +
            "    WHERE r.stcd = s.iofhpi " +
            "    <if test='startTime != null'>AND r.tm &gt;= #{startTime} </if>" +
            "    <if test='endTime != null'>AND r.tm &lt;= #{endTime} </if>" +
            "  )" +
            "  UNION ALL " +
            "  SELECT s2.iofhpi AS stcd " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s2 " +
            "  WHERE <if test='stcd != null'>s2.iofhpi = #{stcd} </if>" +
            "        <if test='stcd == null'>s2.epjutj LIKE '%#1#%' </if>" +
            "  AND EXISTS (" +
            "    SELECT 1 FROM \"qixiao-apaas\".t_auto_hltgq_water_gate g " +
            "    WHERE g.site = s2.id AND g.up_z IS NOT NULL AND g.up_z NOT IN (-999, -9991) " +
            "    <if test='startTime != null'>AND g.tm &gt;= #{startTime} </if>" +
            "    <if test='endTime != null'>AND g.tm &lt;= #{endTime} </if>" +
            "  )" +
            "  AND NOT EXISTS (" +
            "    SELECT 1 FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info rx WHERE rx.STCD = s2.iofhpi" +
            "  )" +
            ") t" +
            "</script>")
    long selectCount(
            @Param("startTime") Timestamp startTime,
            @Param("endTime") Timestamp endTime,
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
