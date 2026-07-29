package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.GateMonitor;
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
    @Select("SELECT DISTINCT gate_no " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" " +
            "WHERE site = #{siteId} ORDER BY gate_no")
    List<String> selectGatesBySite(@Param("siteId") String siteId);

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
            "date_trunc('hour', tm) AS tm, " +
            "site, " +
            "gate_no, " +
            "AVG(open_degree) AS open_degree, " +
            "AVG(up_z) AS up_z, " +
            "AVG(down_z) AS down_z " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" " +
            "WHERE site = #{siteId} " +
            "AND tm &gt;= #{startTime}::timestamp " +
            "AND tm &lt;= #{endTime}::timestamp " +
            "<if test='gateNos != null and gateNos.size() > 0'>" +
            "AND gate_no IN " +
            "<foreach collection='gateNos' item='g' open='(' separator=',' close=')'>#{g}</foreach>" +
            "</if>" +
            "GROUP BY date_trunc('hour', tm), site, gate_no " +
            "ORDER BY date_trunc('hour', tm)" +
            "</script>")
    @Results({
            @Result(column = "tm", property = "tm"),
            @Result(column = "site", property = "site"),
            @Result(column = "gate_no", property = "gateNo"),
            @Result(column = "open_degree", property = "openDegree"),
            @Result(column = "up_z", property = "upZ"),
            @Result(column = "down_z", property = "downZ")
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
            "g.site, s.zzkaec AS site_name, g.gate_no, g.tm, " +
            "g.open_degree, g.up_z, g.down_z, g.status " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" g " +
            "INNER JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON g.site = s.id " +
            "WHERE 1=1 " +
            "<if test='startTime != null'>AND g.tm &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND g.tm &lt;= #{endTime} </if>" +
            "ORDER BY g.site, g.gate_no, g.tm DESC" +
            "</script>")
    @Results({
            @Result(column = "site", property = "site"),
            @Result(column = "site_name", property = "siteName"),
            @Result(column = "gate_no", property = "gateNo"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "open_degree", property = "openDegree"),
            @Result(column = "up_z", property = "upZ"),
            @Result(column = "down_z", property = "downZ"),
            @Result(column = "status", property = "status")
    })
    List<GateMonitor> selectLatestPerHole(@Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);
}
