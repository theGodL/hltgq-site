package com.qgyun.hltgq.hltgqsite.mapper;

import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterQualityVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterThresholdVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 水质监测数据 Mapper（t_auto_hltgq_water_nmisp_info，2026-09 起为纯水质表：原墒情/水质共表拆分为
 * nmisp_info 水质 + soil_data 墒情；本表仅 mq 报文 nmIspInfo 写入。档案 epjutj 含 #8# 视为水质站）
 * <p>数值哨兵约定：-999（设备不存在）转 null 返回；-9991（设备异常）保留透传由前端展示 '--'。
 * <p>BOD5 恒为 null（实验室指标设备不上报）直通；CODCR=0.0 按低于检出限直通（0 合法）。
 */
@Mapper
public interface WaterQualityMapper {

    /**
     * 各站点最新一条水质数据（首页）
     * <p>站点标识 skey = COALESCE(stcd, site)：老站点用编号，无 stcd 时回退到 site（UUID）。
     * site 字段输出 skey 供查询/筛选；siteId 输出站点档案真实主键（n.site，阈值行关联用）。
     * <p>注意：DISTINCT ON/ORDER BY 必须用简单列，不能直接用 COALESCE 函数表达式
     * （PG 会报 "SELECT DISTINCT ON expressions must match initial ORDER BY expressions"），
     * 故先在子查询中物化出 skey，外层按 skey 去重排序。
     *
     * @param stcds     站点标识列表（编号或 site UUID，可选），null/空 → 全部（仅返回监测类型含水质 #8# 的站点）
     * @param startTime 起始时间（含，可选）
     * @param endTime   截止时间（不含，可选）
     */
    @Select("<script>" +
            "SELECT DISTINCT ON (t.skey) " +
            "t.stcd, t.skey AS site, t.site_id, t.stnm, t.tm, " +
            "t.nh3n, t.codcr, t.bod5, t.tp, t.tn, t.dox " +
            "FROM ( " +
            "  SELECT n.stcd, COALESCE(n.stcd, n.site) AS skey, n.site AS site_id, " +
            "  COALESCE(s.zzkaec, n.stcd, n.site) AS stnm, n.tm, " +
            "  CASE WHEN n.nh3n = -999 THEN NULL ELSE TRUNC(n.nh3n, 3) END AS nh3n, " +
            "  CASE WHEN n.codcr = -999 THEN NULL ELSE TRUNC(n.codcr, 3) END AS codcr, " +
            "  CASE WHEN n.bod5 = -999 THEN NULL ELSE TRUNC(n.bod5, 3) END AS bod5, " +
            "  CASE WHEN n.tp = -999 THEN NULL ELSE TRUNC(n.tp, 3) END AS tp, " +
            "  CASE WHEN n.tn = -999 THEN NULL ELSE TRUNC(n.tn, 3) END AS tn, " +
            "  CASE WHEN n.dox = -999 THEN NULL ELSE TRUNC(n.dox, 3) END AS dox " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_nmisp_info n " +
            "  LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON n.site = s.id " +
            "  WHERE 1=1 " +
            "  <if test='stcds == null or stcds.size() == 0'>" +
            "  AND s.epjutj LIKE '%#8#%' " +
            "  </if>" +
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
            @Result(column = "site_id", property = "siteId"),
            @Result(column = "stnm", property = "stnm"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "nh3n", property = "nh3n"),
            @Result(column = "codcr", property = "codcr"),
            @Result(column = "bod5", property = "bod5"),
            @Result(column = "tp", property = "tp"),
            @Result(column = "tn", property = "tn"),
            @Result(column = "dox", property = "dox")
    })
    List<WaterQualityVO> selectLatestPerStation(
            @Param("stcds") List<String> stcds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 2 小时级水质趋势聚合（6 指标取 2h 桶均值，桶起点对齐偶数小时 00:00/02:00/...；
     * -9991 设备异常/-999 设备不存在不参与聚合；BOD5 未上报为 null 自然跳过）
     * <p>桶对齐实现：date_trunc('hour', tm) 后，若原小时为奇数则回退 1 小时到偶数桶起点。
     *
     * @param stcd      站点编号或 site UUID（必填）
     * @param startTime 起始时间（含，必填，Service 层已默认近 24h 并对齐偶数小时）
     * @param endTime   截止时间（含，必填）
     */
    @Select("<script>" +
            "SELECT b.tm, " +
            "TRUNC(AVG(b.nh3n) FILTER (WHERE b.nh3n NOT IN (-999, -9991)), 3) AS nh3n, " +
            "TRUNC(AVG(b.codcr) FILTER (WHERE b.codcr NOT IN (-999, -9991)), 3) AS codcr, " +
            "TRUNC(AVG(b.bod5) FILTER (WHERE b.bod5 NOT IN (-999, -9991)), 3) AS bod5, " +
            "TRUNC(AVG(b.tp) FILTER (WHERE b.tp NOT IN (-999, -9991)), 3) AS tp, " +
            "TRUNC(AVG(b.tn) FILTER (WHERE b.tn NOT IN (-999, -9991)), 3) AS tn, " +
            "TRUNC(AVG(b.dox) FILTER (WHERE b.dox NOT IN (-999, -9991)), 3) AS dox " +
            "FROM ( " +
            "  SELECT n.nh3n, n.codcr, n.bod5, n.tp, n.tn, n.dox, " +
            "  date_trunc('hour', n.tm) - " +
            "  CASE WHEN EXTRACT(HOUR FROM n.tm)::int % 2 = 1 " +
            "       THEN INTERVAL '1 hour' ELSE INTERVAL '0 second' END AS tm " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_nmisp_info n " +
            "  WHERE (n.stcd = #{stcd} OR n.site = #{stcd}) " +
            "  AND n.tm &gt;= #{startTime} " +
            "  AND n.tm &lt;= #{endTime} " +
            ") b " +
            "GROUP BY b.tm " +
            "ORDER BY b.tm" +
            "</script>")
    List<Map<String, Object>> selectTwoHourTrend(
            @Param("stcd") String stcd,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 历史水质数据分页查询（按监测时间倒序）
     * <p>-999（设备不存在）转 null 返回；-9991（设备异常）保留透传由前端展示 '--'。
     *
     * @param stcd      站点编号或 site UUID（必填）
     * @param startTime 起始时间（含，可选）
     * @param endTime   截止时间（含，可选）
     */
    @Select("<script>" +
            "SELECT n.stcd AS stcd, COALESCE(n.stcd, n.site) AS site, n.site AS site_id, " +
            "COALESCE(s.zzkaec, n.stcd, n.site) AS stnm, n.tm, " +
            "CASE WHEN n.nh3n = -999 THEN NULL ELSE TRUNC(n.nh3n, 3) END AS nh3n, " +
            "CASE WHEN n.codcr = -999 THEN NULL ELSE TRUNC(n.codcr, 3) END AS codcr, " +
            "CASE WHEN n.bod5 = -999 THEN NULL ELSE TRUNC(n.bod5, 3) END AS bod5, " +
            "CASE WHEN n.tp = -999 THEN NULL ELSE TRUNC(n.tp, 3) END AS tp, " +
            "CASE WHEN n.tn = -999 THEN NULL ELSE TRUNC(n.tn, 3) END AS tn, " +
            "CASE WHEN n.dox = -999 THEN NULL ELSE TRUNC(n.dox, 3) END AS dox " +
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
            @Result(column = "site_id", property = "siteId"),
            @Result(column = "stnm", property = "stnm"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "nh3n", property = "nh3n"),
            @Result(column = "codcr", property = "codcr"),
            @Result(column = "bod5", property = "bod5"),
            @Result(column = "tp", property = "tp"),
            @Result(column = "tn", property = "tn"),
            @Result(column = "dox", property = "dox")
    })
    List<WaterQualityVO> selectHistoryPage(
            @Param("stcd") String stcd,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 历史水质数据总数
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
     * 按站点批量查询水质阈值行（type 含 #8#；设备级配置可能多行，前端按 remark/device 区分）
     *
     * @param siteIds 站点档案主键 UUID 列表（n.site）
     */
    @Select("<script>" +
            "SELECT id, site, device, \"type\", remark, threshold, guarantee, num " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_threshold " +
            "WHERE site IN " +
            "<foreach collection='siteIds' item='s' open='(' separator=',' close=')'>#{s}</foreach>" +
            " AND \"type\" LIKE '%#8#%'" +
            "</script>")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "site", property = "site"),
            @Result(column = "device", property = "device"),
            @Result(column = "type", property = "type"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "threshold", property = "threshold"),
            @Result(column = "guarantee", property = "guarantee"),
            @Result(column = "num", property = "num")
    })
    List<WaterThresholdVO> selectThresholdsBySites(@Param("siteIds") List<String> siteIds);

    /**
     * 水质监测全部站点（站点标识 = COALESCE(stcd, site)，MQTT 站点无 stcd 时以 site 主键兜底；
     * 仅保留监测类型含水质 #8# 的站点）
     * <p>注意：DISTINCT ON/ORDER BY 必须用简单列，函数表达式（COALESCE）会报
     * "SELECT DISTINCT ON expressions must match initial ORDER BY expressions"，故子查询先物化 skey。
     */
    @Select("SELECT DISTINCT ON (t.skey) t.skey AS code, t.name " +
            "FROM ( " +
            "  SELECT COALESCE(n.stcd, n.site) AS skey, COALESCE(s.zzkaec, n.stcd, n.site) AS name " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_nmisp_info n " +
            "  LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON n.site = s.id " +
            "  WHERE s.epjutj LIKE '%#8#%' " +
            ") t " +
            "ORDER BY t.skey")
    @Results({
            @Result(column = "code", property = "code"),
            @Result(column = "name", property = "name")
    })
    List<StationSiteVO> selectWaterQualityStations();
}
