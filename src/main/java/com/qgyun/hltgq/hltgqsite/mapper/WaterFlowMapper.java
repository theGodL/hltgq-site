package com.qgyun.hltgq.hltgqsite.mapper;

import com.qgyun.hltgq.hltgqsite.vo.FlowMonitoringVO;
import com.qgyun.hltgq.hltgqsite.vo.PeriodRegimeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流量监测数据 Mapper（t_auto_hltgq_water_wt_nfo）
 */
@Mapper
public interface WaterFlowMapper {

    /**
     * 各站点最新一条流量数据
     * <p>站点标识 skey = COALESCE(stcd, site)：老站点用编号，MQTT 站点无 stcd（为 NULL）时回退到 site（UUID）；
     * site_id 为站点管理主键（站点排序配置的匹配键）：优先取业务表 site 列，缺失时按测站编码回查站点档案表。
     * 输出 stcd 为原值（MQTT 站为 null，前端留空显示），另输出 site 字段承载站点标识供查询/筛选。
     * <p>注意：DISTINCT ON/ORDER BY 必须用简单列，不能直接用 COALESCE 函数表达式
     * （PG 会报 "SELECT DISTINCT ON expressions must match initial ORDER BY expressions"），
     * 故先在子查询中物化出 skey，外层按 skey 去重排序。
     * <p>累计流量取数（与闸门监测同口径）：内层直接取末行 ytf/ttf；
     * fq_prev（仅指定起始时间时拼接）返回起始时间前最近一条 ttf 非空行的 ttf，
     * 供 Service 层相减计算时间框范围累计流量。
     * <p>渠系/经纬度信息：站点表 s 按 f.site = s.id 匹配，s2 按编号补位（老站 f.site 为空时
     * 用 s2.iofhpi = f.stcd 匹配档案），canal_id = COALESCE(s.ywvyds, s2.ywvyds)，
     * 经纬度取 COALESCE(s.bviiio_x, s2.bviiio_x) / COALESCE(s.bviiio_y, s2.bviiio_y)；
     * canalIds 非空时按渠系树过滤（canalId 及其所有子孙渠系 id，由 CanalService 收集）。
     *
     * @param stcds     站点标识列表（编号或 site UUID，可选），null/空 → 全部（仅返回监测类型含流量 #3# 的站点）
     * @param canalIds  渠系 id 集合（可选，渠系树过滤；null/空 = 不过滤）
     * @param startTime 起始时间（可选）
     * @param endTime   截止时间（可选）
     */
    @Select("<script>" +
            "SELECT DISTINCT ON (t.skey) " +
            "t.stcd, t.skey AS site, t.site_id, t.stnm, t.lon, t.lat, t.tm, t.q, t.tf, t.ytf, t.ttf, t.vol, t.canal_id, t.canal_name" +
            "<if test='startTime != null'>, fq_prev.prev_ttf</if> " +
            "FROM ( " +
            "  SELECT f.stcd, COALESCE(f.stcd, f.site) AS skey, " +
            "  COALESCE(f.site, (SELECT a.id FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" a WHERE a.iofhpi = f.stcd LIMIT 1)) AS site_id, " +
            "  COALESCE(s.zzkaec, f.stcd, f.site) AS stnm, " +
            "  COALESCE(s.bviiio_x, s2.bviiio_x) AS lon, COALESCE(s.bviiio_y, s2.bviiio_y) AS lat, " +
            "  f.tm, TRUNC(f.q, 3) AS q, TRUNC(f.tf, 2) AS tf, f.ytf, f.ttf, fv.vol, " +
            "  COALESCE(s.ywvyds, s2.ywvyds) AS canal_id, c.gfaegg AS canal_name " +
            "  FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "  LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON f.site = s.id " +
            "  LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s2 ON s.id IS NULL AND s2.iofhpi = f.stcd " +
            "  LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_knc3g_egvnhw\" c ON c.id = COALESCE(s.ywvyds, s2.ywvyds) " +
            "  LEFT JOIN ( " +
            "    SELECT DISTINCT ON (v.site) v.site, v.vol " +
            "    FROM \"qixiao-apaas\".t_auto_hltgq_water_vol_info v " +
            "    ORDER BY v.site, v.tm DESC " +
            "  ) fv ON fv.site = s.id " +
            "  WHERE 1=1 " +
            "  <if test='stcds == null or stcds.size() == 0'>" +
            "  AND s.epjutj LIKE '%#3#%' " +
            "  </if>" +
            "  <if test='stcds != null and stcds.size() > 0'>" +
            "  AND (f.stcd IN " +
            "  <foreach collection='stcds' item='s' open='(' separator=',' close=')'>#{s}</foreach>" +
            "   OR f.site IN " +
            "  <foreach collection='stcds' item='s' open='(' separator=',' close=')'>#{s}</foreach>" +
            "  ) " +
            "  </if>" +
            "  <if test='canalIds != null and canalIds.size() > 0'>" +
            "  AND COALESCE(s.ywvyds, s2.ywvyds) IN " +
            "  <foreach collection='canalIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach>" +
            "  </if>" +
            "  <if test='startTime != null'>AND f.tm &gt;= #{startTime} </if>" +
            "  <if test='endTime != null'>AND f.tm &lt;= #{endTime} </if>" +
            ") t " +
            "  <if test='startTime != null'>" +
            "  LEFT JOIN ( " +
            "    SELECT DISTINCT ON (t2.skey) t2.skey, t2.ttf AS prev_ttf " +
            "    FROM ( " +
            "      SELECT COALESCE(f.stcd, f.site) AS skey, f.tm, f.ttf " +
            "      FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "      WHERE f.tm &lt; #{startTime} AND f.ttf IS NOT NULL " +
            "    ) t2 " +
            "    ORDER BY t2.skey, t2.tm DESC " +
            "  ) fq_prev ON fq_prev.skey = t.skey " +
            "  </if>" +
            "ORDER BY t.skey, t.tm DESC" +
            "</script>")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "site", property = "site"),
            @Result(column = "site_id", property = "siteId"),
            @Result(column = "stnm", property = "stnm"),
            @Result(column = "canal_id", property = "canalId"),
            @Result(column = "canal_name", property = "canalName"),
            @Result(column = "lon", property = "lon"),
            @Result(column = "lat", property = "lat"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "q", property = "q"),
            @Result(column = "tf", property = "tf"),
            @Result(column = "ytf", property = "ytf"),
            @Result(column = "ttf", property = "ttf"),
            @Result(column = "vol", property = "vol"),
            @Result(column = "prev_ttf", property = "prevTtf")
    })
    List<FlowMonitoringVO> selectLatestPerStation(
            @Param("stcds") List<String> stcds,
            @Param("canalIds") List<String> canalIds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询原始流量记录（用于图表 + 历史数据，按时间升序）
     */
    @Select("<script>" +
            "SELECT f.tm, TRUNC(f.q, 3) AS q, TRUNC(f.tf, 2) AS tf " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "WHERE (f.stcd = #{stcd} OR f.site = #{stcd}) " +
            "<if test='startTime != null'>AND f.tm &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND f.tm &lt;= #{endTime} </if>" +
            "ORDER BY f.tm ASC" +
            "</script>")
    List<Map<String, Object>> selectRawByStcd(
            @Param("stcd") String stcd,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 流量历史分页查询
     * <p>stcd 输出原值（MQTT 站为 null），site 输出站点标识（stcd 或 site UUID）供查询/筛选。
     */
    @Select("<script>" +
            "SELECT f.stcd AS stcd, COALESCE(f.stcd, f.site) AS site, COALESCE(s.zzkaec, f.stcd, f.site) AS stnm, f.tm, TRUNC(f.q, 3) AS q, TRUNC(f.tf, 2) AS tf " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON f.site = s.id " +
            "WHERE (f.stcd = #{stcd} OR f.site = #{stcd}) " +
            "<if test='startTime != null'>AND f.tm &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND f.tm &lt;= #{endTime} </if>" +
            "ORDER BY f.tm DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "site", property = "site"),
            @Result(column = "stnm", property = "stnm"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "q", property = "q"),
            @Result(column = "tf", property = "tf")
    })
    List<FlowMonitoringVO> selectHistoryPage(
            @Param("stcd") String stcd,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 流量历史总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "WHERE (f.stcd = #{stcd} OR f.site = #{stcd}) " +
            "<if test='startTime != null'>AND f.tm &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND f.tm &lt;= #{endTime} </if>" +
            "</script>")
    long selectHistoryCount(
            @Param("stcd") String stcd,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 流量图表：各站在选中时间点 ±30 分钟内距时间点最近的瞬时流量（每站一条）
     * <p>站点标识 skey = COALESCE(stcd, site)：老站点用编号，MQTT 站点无 stcd（为 NULL）时回退到 site（UUID），
     * 故候选标识（测站编码 + 站点 UUID）一并传入，任一命中即可取数；
     * DISTINCT ON (skey) + ORDER BY 时间距离 保证每个标识取距 time 最近的一条；
     * 无效流量（空、-999 设备不存在、-9991 设备异常）不参与，窗口内无有效记录则该标识不返回行。
     *
     * @param codes     候选站点标识列表（测站编码 + 站点 UUID）
     * @param time      选中时间点（半小时粒度）
     * @param startTime 命中窗口起点 = time - 30 分钟
     * @param endTime   命中窗口终点 = time + 30 分钟
     */
    @Select("<script>" +
            "SELECT DISTINCT ON (t.skey) t.skey, t.q, t.tm " +
            "FROM ( " +
            "  SELECT COALESCE(f.stcd, f.site) AS skey, TRUNC(f.q, 3) AS q, f.tm " +
            "  FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "  WHERE (f.stcd IN " +
            "  <foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach> " +
            "   OR f.site IN " +
            "  <foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach>) " +
            "  AND f.tm &gt;= #{startTime} " +
            "  AND f.tm &lt;= #{endTime} " +
            "  AND f.q IS NOT NULL " +
            "  AND f.q NOT IN (-999, -9991) " +
            ") t " +
            "ORDER BY t.skey, ABS(EXTRACT(EPOCH FROM CAST(t.tm AS TIMESTAMP)) - EXTRACT(EPOCH FROM #{time}::timestamp))" +
            "</script>")
    List<Map<String, Object>> selectClosestFlowBySites(
            @Param("codes") List<String> codes,
            @Param("time") LocalDateTime time,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 闸站监测同批次流量取数：取指定站点在 [windowStart, windowEnd] 窗口内的最新一条流量记录
     * <p>报文按批次入库，流量（t_auto_hltgq_water_wt_nfo）tm 应与闸门表最新时刻接近（±20 分钟）；
     * 窗口内取 tm 最新一条，窗口外无记录说明该批次无流量数据（由 Service 层置 null）。
     *
     * @param siteId      站点 UUID
     * @param windowStart 窗口起点（闸门表最新时刻 − 20 分钟）
     * @param windowEnd   窗口终点（闸门表最新时刻 + 20 分钟）
     * @param startTime   查询起始时间（可选，与闸门表范围过滤保持一致）
     * @param endTime     查询截止时间（可选）
     */
    @Select("<script>" +
            "SELECT TRUNC(f.q, 3) AS q, f.ytf, f.ttf " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "WHERE f.site = #{siteId} " +
            "<if test='windowStart != null'>AND f.tm &gt;= #{windowStart} </if>" +
            "<if test='windowEnd != null'>AND f.tm &lt;= #{windowEnd} </if>" +
            "<if test='startTime != null'>AND f.tm &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND f.tm &lt;= #{endTime} </if>" +
            "ORDER BY f.tm DESC LIMIT 1" +
            "</script>")
    Map<String, Object> selectLatestInWindow(
            @Param("siteId") String siteId,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 闸站累计流量取数（月累计 + 年累计，单站单行）
     * <p>year_flow = 最新非空 ytf（当年 1月1日 0点起累计）；
     * total_flow = [monthStart, ∞) 内最新非空 ttf（月内最新累计；月内无 ttf 行为 null）；
     * month_prev_ttf = monthStart 前最近一条 ttf 非空行的 ttf，
     * 供 Service 层相减计算月累计（当月 1日 0点起累计）。
     * <p>主表为站点表，站点存在即返回一行（子查询全空时各值为 null）。
     * 三个 LIMIT 1 子查询走 (site, tm) 索引，单站查询无性能问题。
     * <p>返回值为库中 m³ 原值：相减在 Service 层按原值完成后统一换算为万m³（见 WaterVolumeUtils）。
     *
     * @param siteId     站点 UUID（必填）
     * @param monthStart 月累计起点（当月 1日 0点）
     */
    @Select("SELECT s.zzkaec AS site_name, " +
            "fq.ytf AS year_flow, " +
            "fcur.ttf AS total_flow, " +
            "fprev.ttf AS month_prev_ttf " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s " +
            "LEFT JOIN ( " +
            "  SELECT f.site, f.ytf FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "  WHERE f.site = #{siteId} AND f.ytf IS NOT NULL ORDER BY f.tm DESC LIMIT 1 " +
            ") fq ON fq.site = s.id " +
            "LEFT JOIN ( " +
            "  SELECT f.site, f.ttf FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "  WHERE f.site = #{siteId} AND f.ttf IS NOT NULL AND f.tm >= #{monthStart} " +
            "  ORDER BY f.tm DESC LIMIT 1 " +
            ") fcur ON fcur.site = s.id " +
            "LEFT JOIN ( " +
            "  SELECT f.site, f.ttf FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "  WHERE f.site = #{siteId} AND f.ttf IS NOT NULL AND f.tm < #{monthStart} ORDER BY f.tm DESC LIMIT 1 " +
            ") fprev ON fprev.site = s.id " +
            "WHERE s.id = #{siteId}")
    Map<String, Object> selectCumulativeFlow(
            @Param("siteId") String siteId,
            @Param("monthStart") LocalDateTime monthStart);

    /**
     * 闸站近 N 个月月累计流量趋势：generate_series 生成每月起点（含当月），
     * 每月 2 个 LIMIT 1 子查询走 (site, tm) 索引取月内最新 ttf（end_ttf）与月初前最近 ttf（start_ttf），
     * 单站 2×N 次索引点查无性能问题。月累计口径与 selectCumulativeFlow 一致。
     *
     * @param siteId          站点 UUID（必填）
     * @param firstMonthStart 最早月起点（最早月 1日 0点）
     * @param curMonthStart   当前月起点（当月 1日 0点）
     * @return 每月一行：month_start（月起点）、end_ttf（月内最新 ttf，限 gs ≤ tm < gs+1月）、
     * start_ttf（月初前最近 ttf），按月升序；月内无 ttf 行的月份 end_ttf 为 null（不取上月值结转）
     */
    @Select("SELECT gs AS month_start, " +
            "(SELECT f.ttf FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "  WHERE f.site = #{siteId} AND f.ttf IS NOT NULL AND f.tm >= gs " +
            "  AND f.tm < gs + INTERVAL '1 month' " +
            "  ORDER BY f.tm DESC LIMIT 1) AS end_ttf, " +
            "(SELECT f.ttf FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "  WHERE f.site = #{siteId} AND f.ttf IS NOT NULL AND f.tm < gs " +
            "  ORDER BY f.tm DESC LIMIT 1) AS start_ttf " +
            "FROM generate_series(#{firstMonthStart}::timestamp, #{curMonthStart}::timestamp, INTERVAL '1 month') gs " +
            "ORDER BY gs")
    List<Map<String, Object>> selectMonthlyCumulativeFlow(
            @Param("siteId") String siteId,
            @Param("firstMonthStart") LocalDateTime firstMonthStart,
            @Param("curMonthStart") LocalDateTime curMonthStart);

    /**
     * 流量监测全部站点（站点标识 = COALESCE(stcd, site)，MQTT 站点无 stcd 时以 site 主键兜底；
     * 仅保留监测类型含流量 #3# 的站点）
     * <p>注意：DISTINCT ON/ORDER BY 必须用简单列，函数表达式（COALESCE）会报
     * "SELECT DISTINCT ON expressions must match initial ORDER BY expressions"，故子查询先物化 skey。
     */
    @Select("SELECT DISTINCT ON (t.skey) t.skey AS code, t.name " +
            "FROM ( " +
            "  SELECT COALESCE(f.stcd, f.site) AS skey, COALESCE(s.zzkaec, f.stcd, f.site) AS name " +
            "  FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "  LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON f.site = s.id " +
            "  WHERE s.epjutj LIKE '%#3#%' " +
            ") t " +
            "ORDER BY t.skey")
    @Results({
            @Result(column = "code", property = "code"),
            @Result(column = "name", property = "name")
    })
    List<com.qgyun.hltgq.hltgqsite.vo.StationSiteVO> selectFlowStations();

    /**
     * 日时段水情表：查询选中站点在时间窗口内的原始水位记录（用于槽位匹配）
     * 只取有效水位采集（Z 非空，业主口径“整点前最后一条采集”不包含空采集），
     * 同时携带水势（WPTN）与流量（Q），供槽位最新记录填充表列。
     */
    @Select("<script>" +
            "SELECT f.stcd, f.tm, TRUNC(f.z, 2) AS z, f.wptn AS wptn, TRUNC(f.q, 3) AS q " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_river_info\" f " +
            "WHERE f.stcd IN " +
            "<foreach collection='stcds' item='s' open='(' separator=',' close=')'>#{s}</foreach>" +
            "AND f.tm &gt;= #{startTime} " +
            "AND f.tm &lt;= #{endTime} " +
            "AND f.z IS NOT NULL " +
            "AND f.z NOT IN (-999, -9991) " +
            "ORDER BY f.stcd, f.tm ASC" +
            "</script>")
    List<Map<String, Object>> selectPeriodRawRecords(
            @Param("stcds") List<String> stcds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
