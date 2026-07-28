package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.StRiverR;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StRiverRMapper extends BaseMapper<StRiverR> {

    @Select("SELECT r.STCD AS stcd, r.TM AS tm, COALESCE(r.Z, r.Z1) AS z, r.Z2 AS z2, w.Q AS q, " +
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
}
