package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.MoistureDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 墒情预测逐小时明细 Mapper
 * （平台建表名为 t_auto_hltgq_water_moisture_record，命名与实体名交叉，2026-09 定稿）
 */
@Mapper
public interface MoistureDetailMapper extends BaseMapper<MoistureDetail> {
}
