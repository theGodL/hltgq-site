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
     * 各站点最新一条流量数据（DISTINCT ON stcd）
     *
     * @param stcds     站点编号列表（可选），null/空 → 全部
     * @param startTime 起始时间（可选）
     * @param endTime   截止时间（可选）
     */
    @Select("<script>" +
            "SELECT DISTINCT ON (f.stcd) " +
            "f.stcd, COALESCE(s.zzkaec, f.stcd) AS stnm, f.tm, TRUNC(f.q, 2) AS q, TRUNC(f.tf, 2) AS tf " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON f.site = s.id " +
            "WHERE 1=1 " +
            "<if test='stcds != null and stcds.size() > 0'>" +
            "AND f.stcd IN " +
            "<foreach collection='stcds' item='s' open='(' separator=',' close=')'>#{s}</foreach>" +
            "</if>" +
            "<if test='startTime != null'>AND f.tm &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND f.tm &lt;= #{endTime} </if>" +
            "ORDER BY f.stcd, f.tm DESC" +
            "</script>")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "stnm", property = "stnm"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "q", property = "q"),
            @Result(column = "tf", property = "tf")
    })
    List<FlowMonitoringVO> selectLatestPerStation(
            @Param("stcds") List<String> stcds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询原始流量记录（用于图表 + 历史数据，按时间升序）
     */
    @Select("<script>" +
            "SELECT f.tm, TRUNC(f.q, 2) AS q, TRUNC(f.tf, 2) AS tf " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "WHERE f.stcd = #{stcd} " +
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
     */
    @Select("<script>" +
            "SELECT f.stcd, COALESCE(s.zzkaec, f.stcd) AS stnm, f.tm, TRUNC(f.q, 2) AS q, TRUNC(f.tf, 2) AS tf " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON f.site = s.id " +
            "WHERE f.stcd = #{stcd} " +
            "<if test='startTime != null'>AND f.tm &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND f.tm &lt;= #{endTime} </if>" +
            "ORDER BY f.tm DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    @Results({
            @Result(column = "stcd", property = "stcd"),
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
            "WHERE f.stcd = #{stcd} " +
            "<if test='startTime != null'>AND f.tm &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND f.tm &lt;= #{endTime} </if>" +
            "</script>")
    long selectHistoryCount(
            @Param("stcd") String stcd,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 流量监测全部站点编号+名称
     */
    @Select("SELECT DISTINCT f.stcd AS code, COALESCE(s.zzkaec, f.stcd) AS name " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON f.site = s.id " +
            "ORDER BY f.stcd")
    @Results({
            @Result(column = "code", property = "code"),
            @Result(column = "name", property = "name")
    })
    List<com.qgyun.hltgq.hltgqsite.vo.StationSiteVO> selectFlowStations();

    /**
     * 日时段水情表：查询选中站点在时间窗口内的原始水位记录（用于槽位匹配）
     */
    @Select("<script>" +
            "SELECT f.stcd, f.tm, TRUNC(f.z, 2) AS z " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_river_info\" f " +
            "WHERE f.stcd IN " +
            "<foreach collection='stcds' item='s' open='(' separator=',' close=')'>#{s}</foreach>" +
            "AND f.tm &gt;= #{startTime} " +
            "AND f.tm &lt;= #{endTime} " +
            "ORDER BY f.stcd, f.tm ASC" +
            "</script>")
    List<Map<String, Object>> selectPeriodRawRecords(
            @Param("stcds") List<String> stcds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
