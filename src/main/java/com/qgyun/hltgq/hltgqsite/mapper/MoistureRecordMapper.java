package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.MoistureRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 墒情预测方案主表 Mapper
 * （平台建表名为 t_auto_hltgq_water_moisture_detail，命名与实体名交叉，2026-09 定稿）
 */
@Mapper
public interface MoistureRecordMapper extends BaseMapper<MoistureRecord> {

    /** 启动清理：遗留计算中(#1#)超时置失败(#3#)，返回影响行数（status 为平台单选编码） */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_moisture_detail\" " +
            "SET \"status\" = '#3#', \"error_msg\" = #{errorMsg}, \"updated_at\" = #{now} " +
            "WHERE \"status\" = '#1#' AND \"created_at\" < #{deadline}")
    int markStaleCalculatingFailed(@Param("deadline") LocalDateTime deadline,
                                   @Param("errorMsg") String errorMsg,
                                   @Param("now") LocalDateTime now);
}
