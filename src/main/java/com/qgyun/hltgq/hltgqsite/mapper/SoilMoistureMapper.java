package com.qgyun.hltgq.hltgqsite.mapper;

import com.qgyun.hltgq.hltgqsite.vo.SoilMoistureVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 墒情监测数据 Mapper（t_auto_hltgq_water_nmisp_info）
 */
@Mapper
public interface SoilMoistureMapper {

    /**
     * 各站点最新一条墒情数据（首页）
     * <p>站点标识 skey = COALESCE(stcd, site)：老站点用编号，无 stcd 时回退到 site（UUID）。
     * 输出 stcd 为原值，另输出 site 字段承载站点标识供查询/筛选。
     * <p>注意：DISTINCT ON/ORDER BY 必须用简单列，不能直接用 COALESCE 函数表达式
     * （PG 会报 "SELECT DISTINCT ON expressions must match initial ORDER BY expressions"），
     * 故先在子查询中物化出 skey，外层按 skey 去重排序。
     * <p>-9991（设备异常）透传由前端展示 '--'；-999（设备不存在）透传由前端不展示。
     * <p>电压 vol 关联电压表 t_auto_hltgq_water_vol_info（电压表 site = 站点 UUID = n.site），
     * 取筛选时间范围内最新一条，无数据为 null。
     *
     * @param stcds     站点标识列表（编号或 site UUID，可选），null/空 → 全部
     * @param startTime 起始时间（含，可选）
     * @param endTime   截止时间（不含，可选）
     */
    @Select("<script>" +
            "SELECT DISTINCT ON (t.skey) " +
            "t.stcd, t.skey AS site, t.stnm, t.tm, t.vol, " +
            "t.mten, t.mtwenty, t.mthirty, t.mforty, t.mfifty, t.msixty, t.meighty, t.mhundred " +
            "FROM ( " +
            "  SELECT n.stcd, COALESCE(n.stcd, n.site) AS skey, COALESCE(s.zzkaec, n.stcd, n.site) AS stnm, n.tm, fv.vol, " +
            "  TRUNC(n.mten, 2) AS mten, TRUNC(n.mtwenty, 2) AS mtwenty, TRUNC(n.mthirty, 2) AS mthirty, " +
            "  TRUNC(n.mforty, 2) AS mforty, TRUNC(n.mfifty, 2) AS mfifty, TRUNC(n.msixty, 2) AS msixty, " +
            "  TRUNC(n.meighty, 2) AS meighty, TRUNC(n.mhundred, 2) AS mhundred " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_nmisp_info n " +
            "  LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON n.site = s.id " +
            "  LEFT JOIN ( " +
            "    SELECT DISTINCT ON (v.site) v.site, v.vol " +
            "    FROM \"qixiao-apaas\".t_auto_hltgq_water_vol_info v " +
            "    WHERE 1=1 " +
            "    <if test='startTime != null'>AND v.tm &gt;= #{startTime} </if>" +
            "    <if test='endTime != null'>AND v.tm &lt; #{endTime} </if>" +
            "    ORDER BY v.site, v.tm DESC " +
            "  ) fv ON fv.site = n.site " +
            "  WHERE 1=1 " +
            "  <if test='stcds != null and stcds.size() > 0'>" +
            "  AND (n.stcd IN " +
            "  <foreach collection='stcds' item='s' open='(' separator=',' close=')'>#{s}</foreach>" +
            "   OR n.site IN " +
            "  <foreach collection='stcds' item='s' open='(' separator=',' close=')'>#{s}</foreach>" +
            "  ) " +
            "  </if>" +
            "  <if test='startTime != null'>AND n.tm &gt;= #{startTime} </if>" +
            "  <if test='endTime != null'>AND n.tm &lt; #{endTime} </if>" +
            ") t " +
            "ORDER BY t.skey, t.tm DESC" +
            "</script>")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "site", property = "site"),
            @Result(column = "stnm", property = "stnm"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "vol", property = "vol"),
            @Result(column = "mten", property = "mten"),
            @Result(column = "mtwenty", property = "mtwenty"),
            @Result(column = "mthirty", property = "mthirty"),
            @Result(column = "mforty", property = "mforty"),
            @Result(column = "mfifty", property = "mfifty"),
            @Result(column = "msixty", property = "msixty"),
            @Result(column = "meighty", property = "meighty"),
            @Result(column = "mhundred", property = "mhundred")
    })
    List<SoilMoistureVO> selectLatestPerStation(
            @Param("stcds") List<String> stcds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 小时级墒情趋势聚合（每个深度字段取小时平均，-9991 设备异常/-999 设备不存在不参与聚合）
     *
     * @param stcd      站点编号或 site UUID（必填）
     * @param startTime 起始时间（含，必填，Service 层已默认近七天）
     * @param endTime   截止时间（含，必填）
     */
    @Select("<script>" +
            "SELECT date_trunc('hour', n.tm) AS tm, " +
            "TRUNC(AVG(n.mten) FILTER (WHERE n.mten NOT IN (-999, -9991)), 2) AS mten, " +
            "TRUNC(AVG(n.mtwenty) FILTER (WHERE n.mtwenty NOT IN (-999, -9991)), 2) AS mtwenty, " +
            "TRUNC(AVG(n.mthirty) FILTER (WHERE n.mthirty NOT IN (-999, -9991)), 2) AS mthirty, " +
            "TRUNC(AVG(n.mforty) FILTER (WHERE n.mforty NOT IN (-999, -9991)), 2) AS mforty, " +
            "TRUNC(AVG(n.mfifty) FILTER (WHERE n.mfifty NOT IN (-999, -9991)), 2) AS mfifty, " +
            "TRUNC(AVG(n.msixty) FILTER (WHERE n.msixty NOT IN (-999, -9991)), 2) AS msixty, " +
            "TRUNC(AVG(n.meighty) FILTER (WHERE n.meighty NOT IN (-999, -9991)), 2) AS meighty, " +
            "TRUNC(AVG(n.mhundred) FILTER (WHERE n.mhundred NOT IN (-999, -9991)), 2) AS mhundred " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_nmisp_info n " +
            "WHERE (n.stcd = #{stcd} OR n.site = #{stcd}) " +
            "AND n.tm &gt;= #{startTime} " +
            "AND n.tm &lt;= #{endTime} " +
            "GROUP BY date_trunc('hour', n.tm) " +
            "ORDER BY date_trunc('hour', n.tm)" +
            "</script>")
    List<Map<String, Object>> selectHourlyTrend(
            @Param("stcd") String stcd,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 历史墒情数据分页查询（按监测时间倒序，-9991/-999 透传）
     *
     * @param stcd      站点编号或 site UUID（必填）
     * @param startTime 起始时间（含，可选）
     * @param endTime   截止时间（含，可选）
     */
    @Select("<script>" +
            "SELECT n.stcd AS stcd, COALESCE(n.stcd, n.site) AS site, " +
            "COALESCE(s.zzkaec, n.stcd, n.site) AS stnm, n.tm, " +
            "TRUNC(n.mten, 2) AS mten, TRUNC(n.mtwenty, 2) AS mtwenty, TRUNC(n.mthirty, 2) AS mthirty, " +
            "TRUNC(n.mforty, 2) AS mforty, TRUNC(n.mfifty, 2) AS mfifty, TRUNC(n.msixty, 2) AS msixty, " +
            "TRUNC(n.meighty, 2) AS meighty, TRUNC(n.mhundred, 2) AS mhundred " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_nmisp_info n " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON n.site = s.id " +
            "WHERE (n.stcd = #{stcd} OR n.site = #{stcd}) " +
            "<if test='startTime != null'>AND n.tm &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND n.tm &lt;= #{endTime} </if>" +
            "ORDER BY n.tm DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "site", property = "site"),
            @Result(column = "stnm", property = "stnm"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "mten", property = "mten"),
            @Result(column = "mtwenty", property = "mtwenty"),
            @Result(column = "mthirty", property = "mthirty"),
            @Result(column = "mforty", property = "mforty"),
            @Result(column = "mfifty", property = "mfifty"),
            @Result(column = "msixty", property = "msixty"),
            @Result(column = "meighty", property = "meighty"),
            @Result(column = "mhundred", property = "mhundred")
    })
    List<SoilMoistureVO> selectHistoryPage(
            @Param("stcd") String stcd,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 历史墒情数据总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_nmisp_info n " +
            "WHERE (n.stcd = #{stcd} OR n.site = #{stcd}) " +
            "<if test='startTime != null'>AND n.tm &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND n.tm &lt;= #{endTime} </if>" +
            "</script>")
    long selectHistoryCount(
            @Param("stcd") String stcd,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 墒情监测全部站点（表中有数据即视为墒情站，站点标识 = COALESCE(stcd, site)）
     * <p>注意：DISTINCT ON/ORDER BY 必须用简单列，函数表达式（COALESCE）会报
     * "SELECT DISTINCT ON expressions must match initial ORDER BY expressions"，故子查询先物化 skey。
     */
    @Select("SELECT DISTINCT ON (t.skey) t.skey AS code, t.name " +
            "FROM ( " +
            "  SELECT COALESCE(n.stcd, n.site) AS skey, COALESCE(s.zzkaec, n.stcd, n.site) AS name " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_nmisp_info n " +
            "  LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON n.site = s.id " +
            ") t " +
            "ORDER BY t.skey")
    @Results({
            @Result(column = "code", property = "code"),
            @Result(column = "name", property = "name")
    })
    List<StationSiteVO> selectMoistureStations();
}
