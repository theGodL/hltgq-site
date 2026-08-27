package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.LevelStorage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 库容曲线表 Mapper（t_auto_hltgq_water_level_storage）
 */
@Mapper
public interface LevelStorageMapper extends BaseMapper<LevelStorage> {

    /** 精确匹配水位 → 库容，无匹配返回 null */
    @Select("SELECT \"storage\" FROM \"qixiao-apaas\".\"t_auto_hltgq_water_level_storage\" " +
            "WHERE \"water_level\" = #{waterLevel} LIMIT 1")
    Double selectStorageByLevel(@Param("waterLevel") Double waterLevel);

    /** 相邻下界：water_level < 目标 中最大的一条 */
    @Select("SELECT * FROM \"qixiao-apaas\".\"t_auto_hltgq_water_level_storage\" " +
            "WHERE \"water_level\" < #{waterLevel} ORDER BY \"water_level\" DESC LIMIT 1")
    @Results({
            @Result(column = "water_level", property = "waterLevel"),
            @Result(column = "storage", property = "storage")
    })
    LevelStorage selectNearestLower(@Param("waterLevel") Double waterLevel);

    /** 相邻上界：water_level > 目标 中最小的一条 */
    @Select("SELECT * FROM \"qixiao-apaas\".\"t_auto_hltgq_water_level_storage\" " +
            "WHERE \"water_level\" > #{waterLevel} ORDER BY \"water_level\" ASC LIMIT 1")
    @Results({
            @Result(column = "water_level", property = "waterLevel"),
            @Result(column = "storage", property = "storage")
    })
    LevelStorage selectNearestUpper(@Param("waterLevel") Double waterLevel);
}
