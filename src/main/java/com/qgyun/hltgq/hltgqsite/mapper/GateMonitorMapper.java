package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.GateMonitor;
import com.qgyun.hltgq.hltgqsite.vo.GateDeviceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GateMonitorMapper extends BaseMapper<GateMonitor> {

    /**
     * 有闸门数据的站点列表（关联站点表获取名称）
     */
    @Select("SELECT DISTINCT g.site, s.zzkaec AS site_name " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" g " +
            "INNER JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON g.site = s.id " +
            "ORDER BY s.zzkaec")
    @Results({
            @Result(column = "site", property = "site"),
            @Result(column = "site_name", property = "siteName")
    })
    List<GateMonitor> selectGateSites();

    /**
     * 指定站点的闸孔列表
     */
    @Select("SELECT DISTINCT CASE WHEN gate_no = '0' THEN '1' ELSE gate_no END AS gate_no " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" " +
            "WHERE site = #{siteId} ORDER BY gate_no")
    List<String> selectGatesBySite(@Param("siteId") String siteId);

    /**
     * 指定站点的闸孔设备列表（设备名称、ID、闸孔编号）
     */
    @Select("SELECT DISTINCT ON (g.gate_no) g.id, " +
            "CASE WHEN g.gate_no = '0' THEN '1' ELSE g.gate_no END AS gate_no, " +
            "CONCAT(s.zzkaec, CASE WHEN g.gate_no = '0' THEN '1' ELSE g.gate_no END, '#') AS device_name " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" g " +
            "INNER JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON g.site = s.id " +
            "WHERE g.site = #{siteId} " +
            "ORDER BY g.gate_no, g.tm DESC")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "gate_no", property = "gateNo"),
            @Result(column = "device_name", property = "deviceName")
    })
    List<GateDeviceVO> selectDevicesBySite(@Param("siteId") String siteId);

    /**
     * 按站点 + 可选多闸孔查询，按小时聚合（AVG）
     *
     * @param siteId    站点 UUID
     * @param gateNos   闸孔编号列表，null/空 → 全部
     * @param startTime 开始时间 yyyy-MM-dd HH:mm:ss
     * @param endTime   结束时间 yyyy-MM-dd HH:mm:ss
     */
    @Select("<script>" +
            "SELECT " +
            "date_trunc('hour', g.tm) AS tm, " +
            "g.site, " +
            "CASE WHEN g.gate_no = '0' THEN '1' ELSE g.gate_no END AS gate_no, " +
            "TRUNC(AVG(g.open_degree), 2) AS open_degree, " +
            "TRUNC(AVG(g.up_z), 2) AS up_z, " +
            "TRUNC(AVG(g.down_z), 2) AS down_z, " +
            "TRUNC(fq.q, 2) AS q " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" g " +
            "LEFT JOIN (" +
            "  SELECT f.site, date_trunc('hour', f.tm) AS hour, AVG(f.q) AS q " +
            "  FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "  WHERE f.tm &gt;= #{startTime}::timestamp AND f.tm &lt;= #{endTime}::timestamp " +
            "  GROUP BY f.site, date_trunc('hour', f.tm)" +
            ") fq ON fq.site = g.site AND fq.hour = date_trunc('hour', g.tm) " +
            "WHERE g.site = #{siteId} " +
            "AND g.tm &gt;= #{startTime}::timestamp " +
            "AND g.tm &lt;= #{endTime}::timestamp " +
            "<if test='gateNos != null and gateNos.size() > 0'>" +
            "AND CASE WHEN g.gate_no = '0' THEN '1' ELSE g.gate_no END IN " +
            "<foreach collection='gateNos' item='g2' open='(' separator=',' close=')'>#{g2}</foreach>" +
            "</if>" +
            "GROUP BY date_trunc('hour', g.tm), g.site, CASE WHEN g.gate_no = '0' THEN '1' ELSE g.gate_no END, fq.q " +
            "ORDER BY date_trunc('hour', g.tm)" +
            "</script>")
    @Results({
            @Result(column = "tm", property = "tm"),
            @Result(column = "site", property = "site"),
            @Result(column = "gate_no", property = "gateNo"),
            @Result(column = "open_degree", property = "openDegree"),
            @Result(column = "up_z", property = "upZ"),
            @Result(column = "down_z", property = "downZ"),
            @Result(column = "q", property = "q")
    })
    List<GateMonitor> selectHourlyAggregated(@Param("siteId") String siteId,
                                              @Param("gateNos") List<String> gateNos,
                                              @Param("startTime") String startTime,
                                              @Param("endTime") String endTime);

    /**
     * 各闸孔最新一条数据（按站点 + 闸孔分组，取最新 TM）
     * <p>使用 PostgreSQL DISTINCT ON 对 (site, gate_no) 去重，每组取 tm 最大的一条。
     *
     * @param startTime 起始时间（含），null 表示不限制
     * @param endTime   截止时间（含），null 表示不限制
     */
    @Select("<script>" +
            "SELECT DISTINCT ON (g.site, g.gate_no) " +
            "g.site, s.zzkaec AS site_name, s.bviiio_x AS lon, s.bviiio_y AS lat, " +
            "CASE WHEN g.gate_no = '0' THEN '1' ELSE g.gate_no END AS gate_no, g.tm, " +
            "TRUNC(g.open_degree, 2) AS open_degree, TRUNC(g.up_z, 2) AS up_z, TRUNC(g.down_z, 2) AS down_z, g.status, " +
            "TRUNC((SELECT f.q FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "  WHERE f.site = g.site " +
            "  <if test='startTime != null'>AND f.tm &gt;= #{startTime} </if>" +
            "  <if test='endTime != null'>AND f.tm &lt;= #{endTime} </if>" +
            "  ORDER BY f.tm DESC LIMIT 1), 2) AS q " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" g " +
            "INNER JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON g.site = s.id " +
            "WHERE 1=1 " +
            "<if test='site != null and site != \"\"'>AND g.site = #{site} </if>" +
            "<if test='startTime != null'>AND g.tm &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND g.tm &lt;= #{endTime} </if>" +
            "ORDER BY g.site, g.gate_no, g.tm DESC" +
            "</script>")
    @Results({
            @Result(column = "site", property = "site"),
            @Result(column = "site_name", property = "siteName"),
            @Result(column = "lon", property = "lon"),
            @Result(column = "lat", property = "lat"),
            @Result(column = "gate_no", property = "gateNo"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "open_degree", property = "openDegree"),
            @Result(column = "up_z", property = "upZ"),
            @Result(column = "down_z", property = "downZ"),
            @Result(column = "status", property = "status"),
            @Result(column = "q", property = "q")
    })
    List<GateMonitor> selectLatestPerHole(@Param("site") String site,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 闸门历史数据 — 按监测时间（tm）去重计数（分页用）
     */
    @Select("<script>" +
            "SELECT COUNT(DISTINCT g.tm) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" g " +
            "WHERE 1=1 " +
            "<if test='siteId != null and siteId != \"\"'>AND g.site = #{siteId} </if>" +
            "<if test='startTime != null'>AND g.tm &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND g.tm &lt;= #{endTime} </if>" +
            "</script>")
    long selectHistoryTmCount(@Param("siteId") String siteId,
                               @Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime);

    /**
     * 闸门历史数据 — 按监测时间（tm）去重分页，倒序
     */
    @Select("<script>" +
            "SELECT DISTINCT g.tm " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" g " +
            "WHERE 1=1 " +
            "<if test='siteId != null and siteId != \"\"'>AND g.site = #{siteId} </if>" +
            "<if test='startTime != null'>AND g.tm &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND g.tm &lt;= #{endTime} </if>" +
            "ORDER BY g.tm DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<LocalDateTime> selectHistoryTmPage(@Param("siteId") String siteId,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    /**
     * 闸门历史数据 — 根据监测时间列表查询所有闸孔明细（开度、水位）
     */
    @Select("<script>" +
            "SELECT " +
            "CASE WHEN g.gate_no = '0' THEN '1' ELSE g.gate_no END AS gate_no, " +
            "g.tm, TRUNC(g.open_degree, 2) AS open_degree, TRUNC(g.up_z, 2) AS up_z, TRUNC(g.down_z, 2) AS down_z, " +
            "TRUNC((SELECT f.q FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "  WHERE f.site = g.site AND f.tm &lt;= g.tm ORDER BY f.tm DESC LIMIT 1), 2) AS q " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" g " +
            "WHERE 1=1 " +
            "<if test='siteId != null and siteId != \"\"'>AND g.site = #{siteId} </if>" +
            "<if test='tms != null and tms.size() > 0'>" +
            "AND g.tm IN " +
            "<foreach collection='tms' item='t' open='(' separator=',' close=')'>#{t}</foreach>" +
            "</if>" +
            "ORDER BY g.tm DESC, g.gate_no" +
            "</script>")
    @Results({
            @Result(column = "gate_no", property = "gateNo"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "open_degree", property = "openDegree"),
            @Result(column = "up_z", property = "upZ"),
            @Result(column = "down_z", property = "downZ"),
            @Result(column = "q", property = "q")
    })
    List<GateMonitor> selectHistoryDetail(@Param("siteId") String siteId,
                                           @Param("tms") List<LocalDateTime> tms);
}
