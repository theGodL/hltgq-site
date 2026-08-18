package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.StRiverR;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface StRiverRMapper extends BaseMapper<StRiverR> {

    @Select("SELECT r.STCD AS stcd, r.TM AS tm, COALESCE(r.Z, r.Z1) AS z, r.Z2 AS z2, TRUNC(w.Q, 3) AS q, " +
            "r.XSA AS xsa, r.XSAVV AS xsavv, r.XSMXV AS xsmxv, r.FLWCHRCD AS flwchrcd, r.WPTN AS wptn " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r " +
            "INNER JOIN (SELECT STCD, MAX(TM) AS MaxTM FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info GROUP BY STCD) rm " +
            "ON r.STCD = rm.STCD AND r.TM = rm.MaxTM " +
            "LEFT JOIN ( " +
            "  SELECT w2.STCD, w2.Q FROM \"qixiao-apaas\".t_auto_hltgq_water_wt_nfo w2 " +
            "  INNER JOIN (SELECT STCD, MAX(TM) AS MaxTM FROM \"qixiao-apaas\".t_auto_hltgq_water_wt_nfo GROUP BY STCD) wm " +
            "  ON w2.STCD = wm.STCD AND w2.TM = wm.MaxTM " +
            ") w ON r.STCD = w.STCD " +
            "ORDER BY r.STCD")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "z", property = "z"),
            @Result(column = "z2", property = "z2"),
            @Result(column = "q", property = "q"),
            @Result(column = "xsa", property = "xsa"),
            @Result(column = "xsavv", property = "xsavv"),
            @Result(column = "xsmxv", property = "xsmxv"),
            @Result(column = "flwchrcd", property = "flwchrcd"),
            @Result(column = "wptn", property = "wptn")
    })
    List<StRiverR> selectLatestPerStation();

    /**
     * 水情简报：站点在时间窗口内的最高水位及出现时间（Z 非空，同高取最早）
     * 注意：非 <script> 注解 SQL 必须直接使用 >= / <=，不能写 &gt; / &lt;（不会解码，会报 column "gt" does not exist）
     */
    @Select("SELECT TRUNC(r.Z, 2) AS z, r.TM AS tm " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r " +
            "WHERE r.STCD = #{stcd} " +
            "AND r.TM >= #{startTime} " +
            "AND r.TM <= #{endTime} " +
            "AND r.Z IS NOT NULL " +
            "AND r.Z NOT IN (-999, -9991) " +
            "ORDER BY r.Z DESC, r.TM ASC LIMIT 1")
    Map<String, Object> selectMaxZInRange(
            @Param("stcd") String stcd,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 多年同期水情：各站点按自然年月的平均水位（截断 2 位小数）
     */
    @Select("<script>" +
            "SELECT r.STCD AS stcd, CAST(EXTRACT(YEAR FROM r.TM) AS INTEGER) AS yr, " +
            "CAST(EXTRACT(MONTH FROM r.TM) AS INTEGER) AS mon, TRUNC(AVG(r.Z), 2) AS avgz " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r " +
            "WHERE r.STCD IN " +
            "<foreach collection='stcds' item='s' open='(' separator=',' close=')'>#{s}</foreach> " +
            "AND r.TM &gt;= #{startTime} " +
            "AND r.TM &lt;= #{endTime} " +
            "AND r.Z NOT IN (-999, -9991) " +
            "GROUP BY r.STCD, EXTRACT(YEAR FROM r.TM), EXTRACT(MONTH FROM r.TM)" +
            "</script>")
    List<Map<String, Object>> selectMonthlyAvgZ(
            @Param("stcds") List<String> stcds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
