package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.SluiceDischarge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 闸站流量计算参数 Mapper（t_auto_hltgq_water_sluice_discharge）
 * <p>每次修改新增一条数据，回显与使用始终取该站点最新一条（version 最大）。
 */
@Mapper
public interface SluiceDischargeMapper extends BaseMapper<SluiceDischarge> {

    /**
     * 查询指定站点最新一条流量计算参数
     *
     * @param siteId 站点 UUID
     */
    @Select("SELECT * FROM \"qixiao-apaas\".\"t_auto_hltgq_water_sluice_discharge\" " +
            "WHERE \"site\" = #{siteId} " +
            "ORDER BY \"version\" DESC NULLS LAST LIMIT 1")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "corp_code", property = "corpCode"),
            @Result(column = "site", property = "site"),
            @Result(column = "full_open_free_coeff", property = "fullOpenFreeCoeff"),
            @Result(column = "submerged_flow_coeff", property = "submergedFlowCoeff"),
            @Result(column = "controlled_free_coeff", property = "controlledFreeCoeff"),
            @Result(column = "orifice_submerged_coeff", property = "orificeSubmergedCoeff"),
            @Result(column = "width", property = "width"),
            @Result(column = "height", property = "height"),
            @Result(column = "bottom_elevation", property = "bottomElevation"),
            @Result(column = "version", property = "version"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "created_by", property = "createdBy"),
            @Result(column = "updated_at", property = "updatedAt"),
            @Result(column = "updated_by", property = "updatedBy")
    })
    SluiceDischarge selectLatestBySite(@Param("siteId") String siteId);

    /**
     * 查询指定站点当前最大版本号，无数据时返回 0
     *
     * @param siteId 站点 UUID
     */
    @Select("SELECT COALESCE(MAX(\"version\"), 0) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_sluice_discharge\" " +
            "WHERE \"site\" = #{siteId}")
    Integer selectMaxVersionBySite(@Param("siteId") String siteId);
}
