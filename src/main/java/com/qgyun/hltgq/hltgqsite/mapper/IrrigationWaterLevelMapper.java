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
 */
@Mapper
public interface IrrigationWaterLevelMapper {

    /**
     * 分页查询：每个站点最新一条水位数据
     *
     * @param dateWrapper 监测日期过滤条件（作用于确定"最新"的子查询）
     * @param stcdWrapper 站点编号过滤条件（作用于外层结果）
     * @param limit       每页条数
     * @param offset      偏移量
     */
    @Select("SELECT r.STCD AS stcd, s.zzkaec AS stnm, s.id AS id, r.TM AS tm, r.Z AS z, " +
            "COALESCE((r.Z - (" +
            "  SELECT r2.Z FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r2 " +
            "  WHERE r2.STCD = r.STCD AND r2.TM <= r.TM - INTERVAL '1 hour' " +
            "  ORDER BY r2.TM DESC LIMIT 1" +
            ")) * 100, 0) AS rise1h " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r " +
            "INNER JOIN (" +
            "  SELECT STCD, MAX(TM) AS MaxTM " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info " +
            "  ${ew.customSqlSegment} " +
            "  GROUP BY STCD" +
            ") rm ON r.STCD = rm.STCD AND r.TM = rm.MaxTM " +
            "LEFT JOIN \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s ON r.STCD = s.iofhpi " +
            "${ew2.customSqlSegment} " +
            "ORDER BY r.STCD " +
            "LIMIT #{limit} OFFSET #{offset}")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "stnm", property = "stnm"),
            @Result(column = "id", property = "id"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "z", property = "z"),
            @Result(column = "rise1h", property = "rise1h")
    })
    List<IrrigationWaterLevelVO> selectPage(
            @Param("ew") com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?> dateWrapper,
            @Param("ew2") com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?> stcdWrapper,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 计数查询：符合条件的站点总数
     */
    @Select("SELECT COUNT(*) FROM (" +
            "  SELECT r.STCD " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r " +
            "  INNER JOIN (" +
            "    SELECT STCD, MAX(TM) AS MaxTM " +
            "    FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info " +
            "    ${ew.customSqlSegment} " +
            "    GROUP BY STCD" +
            "  ) rm ON r.STCD = rm.STCD AND r.TM = rm.MaxTM " +
            "  ${ew2.customSqlSegment} " +
            ") t")
    long selectCount(
            @Param("ew") com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?> dateWrapper,
            @Param("ew2") com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?> stcdWrapper);

    /**
     * 查询站点水位历史数据（用于水位变化图表）
     * 按时间升序返回 TM, Z
     */
    @Select("SELECT r.TM AS tm, r.Z AS z " +
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
     * 水位监测全部站点编号+名称
     */
    @Select("SELECT DISTINCT r.STCD AS code, COALESCE(s.zzkaec, r.STCD) AS name " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info r " +
            "LEFT JOIN \"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef s ON s.iofhpi = r.STCD " +
            "ORDER BY r.STCD")
    @Results({
            @Result(column = "code", property = "code"),
            @Result(column = "name", property = "name")
    })
    List<StationSiteVO> selectWaterLevelStations();
}
