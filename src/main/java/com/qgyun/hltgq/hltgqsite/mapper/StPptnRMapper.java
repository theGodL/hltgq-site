package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.StPptnR;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface StPptnRMapper extends BaseMapper<StPptnR> {

    @Select("SELECT t.STCD, sub.MaxHydroDay AS tm, MAX(t.DRP) AS drp " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info t " +
            "INNER JOIN (SELECT STCD, MAX((TM - INTERVAL '8 hours')::date + INTERVAL '32 hours') AS MaxHydroDay FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info GROUP BY STCD) sub " +
            "ON t.STCD = sub.STCD AND (t.TM - INTERVAL '8 hours')::date + INTERVAL '32 hours' = sub.MaxHydroDay " +
            "GROUP BY t.STCD, sub.MaxHydroDay " +
            "ORDER BY t.STCD")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "drp", property = "drp")
    })
    List<StPptnR> selectLatestPerStation();

    @Select("SELECT STCD, (TM - INTERVAL '8 hours')::date + INTERVAL '32 hours' AS tm, MAX(DRP) AS drp " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info " +
            "${ew.customSqlSegment} " +
            "GROUP BY STCD, (TM - INTERVAL '8 hours')::date + INTERVAL '32 hours' " +
            "ORDER BY STCD, (TM - INTERVAL '8 hours')::date + INTERVAL '32 hours'")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "drp", property = "drp")
    })
    List<StPptnR> selectDailyAll(@Param("ew") QueryWrapper<StPptnR> wrapper);

    @Select("SELECT STCD, MAX(DRP) AS drp FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info WHERE TM >= #{start}::timestamp AND TM <= #{end}::timestamp GROUP BY STCD")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "drp", property = "drp")
    })
    List<StPptnR> selectTodaySumPerStation(@Param("start") Timestamp start, @Param("end") Timestamp end);
}
