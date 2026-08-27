package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.ShortForecastRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 短期来水预测方案主表 Mapper（t_auto_hltgq_water_short_forecast_record）
 */
@Mapper
public interface ShortForecastRecordMapper extends BaseMapper<ShortForecastRecord> {

    /** 启动清理：遗留 calculating 超时置 failed，返回影响行数 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_short_forecast_record\" " +
            "SET \"status\" = 'failed', \"error_msg\" = #{errorMsg}, \"updated_at\" = #{now} " +
            "WHERE \"status\" = 'calculating' AND \"created_at\" < #{deadline}")
    int markStaleCalculatingFailed(@Param("deadline") LocalDateTime deadline,
                                   @Param("errorMsg") String errorMsg,
                                   @Param("now") LocalDateTime now);
}
