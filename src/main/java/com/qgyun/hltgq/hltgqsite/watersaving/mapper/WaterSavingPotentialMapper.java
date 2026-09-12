package com.qgyun.hltgq.hltgqsite.watersaving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.WaterSavingPotential;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 节水潜力测算记录 Mapper（t_auto_hltgq_water_saving_potential）。
 * <p>查询/新增走 MyBatis-Plus 内建（selectList / insert，主键 ASSIGN_UUID 短ID）；
 * 整行更新手写 SQL——updateById 默认忽略 null 字段，无法将「清空的数值」存为 null，
 * 故全列显式 SET（含 null 覆盖）。
 */
@Mapper
public interface WaterSavingPotentialMapper extends BaseMapper<WaterSavingPotential> {

    /** 整行更新（按 id 设置全部业务列 + updated_at/updated_by，支持 null 清空） */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_saving_potential\" " +
            "SET \"total_resources\" = #{totalResources}, \"supply_capacity\" = #{supplyCapacity}, " +
            "\"available_supply\" = #{availableSupply}, \"basis_file\" = #{basisFile}, " +
            "\"agri_baseline\" = #{agriBaseline}, \"agri_target\" = #{agriTarget}, " +
            "\"ind_baseline\" = #{indBaseline}, \"ind_target\" = #{indTarget}, " +
            "\"life_baseline\" = #{lifeBaseline}, \"life_target\" = #{lifeTarget}, " +
            "\"calc_basis\" = #{calcBasis}, \"measures\" = #{measures}, \"status\" = #{status}, " +
            "\"updated_at\" = #{updatedAt}, \"updated_by\" = #{updatedBy} " +
            "WHERE \"id\" = #{id}")
    int updateRecord(WaterSavingPotential entity);
}
