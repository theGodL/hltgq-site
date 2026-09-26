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
 * 水质监测数据 Mapper（t_auto_hltgq_water_nmisp_info 七项 + t_auto_hltgq_water_pcp_info 水温，
 * 2026-09 起为纯水质表：原墒情/水质共表拆分为 nmisp_info 水质 + soil_data 墒情；
 * 本表仅 mq 报文 nmIspInfo 写入。档案 epjutj 含 #8# 视为水质站）
 * <p>数值哨兵约定：-999（设备不存在）转 null 返回；-9991（设备异常）保留透传由前端展示 '--'。
 * <p>联表键唯一性（2026-09-24 实测）：pcp_info 按 (stcd, spt) 唯一（678 行 = 678 去重键、无重复组），
 * nmisp_info 按 (stcd, tm) 唯一（679 = 679），故水温 LEFT JOIN 不放大行数——history 的 records 与 total
 * 同口径、trend 的桶均值不被重复行加权；若上游去重策略变化需复核（否则须先按键聚合再 JOIN）。
 * <p>指标口径（2026-09-24 实测）：CODMN 高锰酸盐指数为报文连续指标（661/661 有值）；
 * CODCR 化学需氧量多数为 0（低于检出限，属合法值照实返回，实测 621/661 行 =0）；
 * BOD5 间歇上报（85/661 行有值，其余 null 直通）；水温 wt 取自 pcp_info（时间列 spt，
 * 与 nmisp_info.tm 同组对齐，660/660 有值）；pH / 电导率为探头未采集（恒 null，不返回）。
 */
@Mapper
public interface WaterQualityMapper {

    /**
     * 各站点最新一条水质数据（首页）
     * <p>站点标识 skey = COALESCE(stcd, site)：老站点用编号，无 stcd 时回退到 site（UUID）。
     * site 字段输出 skey 供查询/筛选；siteId 输出站点档案真实主键（n.site，阈值行关联用）。
     * <p>经纬度：站点表 s 按 n.site = s.id 匹配，s2 按编号补位（老站 n.site 为空时
     * 用 s2.iofhpi = n.stcd 匹配档案），取 COALESCE(s.bviiio_x, s2.bviiio_x) / COALESCE(s.bviiio_y, s2.bviiio_y)。
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
            "t.stcd, t.skey AS site, t.site_id, t.stnm, t.lon, t.lat, t.tm, " +
            "t.nh3n, t.codmn, t.codcr, t.bod5, t.tp, t.tn, t.dox, t.wt " +
            "FROM ( " +
            "  SELECT n.stcd, COALESCE(n.stcd, n.site) AS skey, n.site AS site_id, " +
            "  COALESCE(s.zzkaec, n.stcd, n.site) AS stnm, " +
            "  COALESCE(s.bviiio_x, s2.bviiio_x) AS lon, COALESCE(s.bviiio_y, s2.bviiio_y) AS lat, " +
            "  n.tm, " +
            "  CASE WHEN n.nh3n = -999 THEN NULL ELSE TRUNC(n.nh3n, 3) END AS nh3n, " +
            "  CASE WHEN n.codmn = -999 THEN NULL ELSE TRUNC(n.codmn, 3) END AS codmn, " +
            "  CASE WHEN n.codcr = -999 THEN NULL ELSE TRUNC(n.codcr, 3) END AS codcr, " +
            "  CASE WHEN n.bod5 = -999 THEN NULL ELSE TRUNC(n.bod5, 3) END AS bod5, " +
            "  CASE WHEN n.tp = -999 THEN NULL ELSE TRUNC(n.tp, 3) END AS tp, " +
            "  CASE WHEN n.tn = -999 THEN NULL ELSE TRUNC(n.tn, 3) END AS tn, " +
            "  CASE WHEN n.dox = -999 THEN NULL ELSE TRUNC(n.dox, 3) END AS dox, " +
            "  CASE WHEN p.wt = -999 THEN NULL ELSE TRUNC(p.wt, 3) END AS wt " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_nmisp_info n " +
            "  LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON n.site = s.id " +
            "  LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s2 ON s.id IS NULL AND s2.iofhpi = n.stcd " +
            "  LEFT JOIN \"qixiao-apaas\".t_auto_hltgq_water_pcp_info p ON p.stcd = n.stcd AND p.spt = n.tm " +
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
            @Result(column = "lon", property = "lon"),
            @Result(column = "lat", property = "lat"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "nh3n", property = "nh3n"),
            @Result(column = "codmn", property = "codmn"),
            @Result(column = "codcr", property = "codcr"),
            @Result(column = "bod5", property = "bod5"),
            @Result(column = "tp", property = "tp"),
            @Result(column = "tn", property = "tn"),
            @Result(column = "dox", property = "dox"),
            @Result(column = "wt", property = "wt")
    })
    List<WaterQualityVO> selectLatestPerStation(
            @Param("stcds") List<String> stcds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 2 小时级水质趋势聚合（7 项指标取 2h 桶均值，桶起点对齐偶数小时 00:00/02:00/...；
     * -9991 设备异常/-999 设备不存在不参与聚合；BOD5 未上报为 null 自然跳过；
     * 水温 wt 联查 pcp_info（时间列 spt 与 nmisp tm 同组对齐））
     * <p>桶对齐实现：date_trunc('hour', tm) 后，若原小时为奇数则回退 1 小时到偶数桶起点。
     *
     * @param stcd      站点编号或 site UUID（必填）
     * @param startTime 起始时间（含，必填，Service 层已默认近 24h 并对齐偶数小时）
     * @param endTime   数据过滤上界（不含，必填；Service 层传序列末桶起点 + 2h，保证末桶为完整 2h 窗）
     */
    @Select("<script>" +
            "SELECT b.tm, " +
            "TRUNC(AVG(b.nh3n) FILTER (WHERE b.nh3n NOT IN (-999, -9991)), 3) AS nh3n, " +
            "TRUNC(AVG(b.codmn) FILTER (WHERE b.codmn NOT IN (-999, -9991)), 3) AS codmn, " +
            "TRUNC(AVG(b.codcr) FILTER (WHERE b.codcr NOT IN (-999, -9991)), 3) AS codcr, " +
            "TRUNC(AVG(b.bod5) FILTER (WHERE b.bod5 NOT IN (-999, -9991)), 3) AS bod5, " +
            "TRUNC(AVG(b.tp) FILTER (WHERE b.tp NOT IN (-999, -9991)), 3) AS tp, " +
            "TRUNC(AVG(b.tn) FILTER (WHERE b.tn NOT IN (-999, -9991)), 3) AS tn, " +
            "TRUNC(AVG(b.dox) FILTER (WHERE b.dox NOT IN (-999, -9991)), 3) AS dox, " +
            "TRUNC(AVG(b.wt) FILTER (WHERE b.wt NOT IN (-999, -9991)), 3) AS wt " +
            "FROM ( " +
            "  SELECT n.nh3n, n.codmn, n.codcr, n.bod5, n.tp, n.tn, n.dox, p.wt, " +
            "  date_trunc('hour', n.tm) - " +
            "  CASE WHEN EXTRACT(HOUR FROM n.tm)::int % 2 = 1 " +
            "       THEN INTERVAL '1 hour' ELSE INTERVAL '0 second' END AS tm " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_nmisp_info n " +
            "  LEFT JOIN \"qixiao-apaas\".t_auto_hltgq_water_pcp_info p ON p.stcd = n.stcd AND p.spt = n.tm " +
            "  WHERE (n.stcd = #{stcd} OR n.site = #{stcd}) " +
            "  AND n.tm &gt;= #{startTime} " +
            "  AND n.tm &lt; #{endTime} " +
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
     * <p>-999（设备不存在）转 null 返回；-9991（设备异常）保留透传由前端展示 '--'；
     * 水温 wt 联查 pcp_info（时间列 spt 与 nmisp tm 同组对齐，缺对标为 null）。
     *
     * @param stcd      站点编号或 site UUID（必填）
     * @param startTime 起始时间（含，可选）
     * @param endTime   截止时间（含，可选）
     */
    @Select("<script>" +
            "SELECT n.stcd AS stcd, COALESCE(n.stcd, n.site) AS site, n.site AS site_id, " +
            "COALESCE(s.zzkaec, n.stcd, n.site) AS stnm, n.tm, " +
            "CASE WHEN n.nh3n = -999 THEN NULL ELSE TRUNC(n.nh3n, 3) END AS nh3n, " +
            "CASE WHEN n.codmn = -999 THEN NULL ELSE TRUNC(n.codmn, 3) END AS codmn, " +
            "CASE WHEN n.codcr = -999 THEN NULL ELSE TRUNC(n.codcr, 3) END AS codcr, " +
            "CASE WHEN n.bod5 = -999 THEN NULL ELSE TRUNC(n.bod5, 3) END AS bod5, " +
            "CASE WHEN n.tp = -999 THEN NULL ELSE TRUNC(n.tp, 3) END AS tp, " +
            "CASE WHEN n.tn = -999 THEN NULL ELSE TRUNC(n.tn, 3) END AS tn, " +
            "CASE WHEN n.dox = -999 THEN NULL ELSE TRUNC(n.dox, 3) END AS dox, " +
            "CASE WHEN p.wt = -999 THEN NULL ELSE TRUNC(p.wt, 3) END AS wt " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_nmisp_info n " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON n.site = s.id " +
            "LEFT JOIN \"qixiao-apaas\".t_auto_hltgq_water_pcp_info p ON p.stcd = n.stcd AND p.spt = n.tm " +
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
            @Result(column = "codmn", property = "codmn"),
            @Result(column = "codcr", property = "codcr"),
            @Result(column = "bod5", property = "bod5"),
            @Result(column = "tp", property = "tp"),
            @Result(column = "tn", property = "tn"),
            @Result(column = "dox", property = "dox"),
            @Result(column = "wt", property = "wt")
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
     * 按站点批量查询水质阈值行（类型含 #8#；设备级配置可能多行，前端按 zb/device 区分）
     * <p>类型列口径：阈值设置写入 {@code zvieyb}（历史列 {@code type} 仅存量兜底），
     * 读取与过滤统一按 {@code COALESCE(NULLIF(zvieyb,''), type)}，输出到 type 字段供前端复用。
     * <p>指标列：输出 {@code zb}（指标编码 = 监测数据表字段名）与 {@code alarmdir}（告警方向），
     * 前端按 {@code zb} 精确取行画趋势参考线（{@code alarmdir} 决定文案为上限/下限）；
     * 存量未落指标的行回退 {@code remark} 关键字匹配。页面不做数值超限标红（标红口径为采集时间中断，与其他监测页一致）。
     *
     * @param siteIds 站点档案主键 UUID 列表（n.site）
     */
    @Select("<script>" +
            "SELECT id, site, device, \"zb\", COALESCE(NULLIF(\"zvieyb\", ''), \"type\", '') AS \"type\", " +
            "alarmdir, remark, threshold, guarantee, num " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_threshold " +
            "WHERE site IN " +
            "<foreach collection='siteIds' item='s' open='(' separator=',' close=')'>#{s}</foreach>" +
            " AND COALESCE(NULLIF(\"zvieyb\", ''), \"type\", '') LIKE '%#8#%'" +
            "</script>")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "site", property = "site"),
            @Result(column = "device", property = "device"),
            @Result(column = "zb", property = "zb"),
            @Result(column = "alarmdir", property = "alarmDir"),
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
