package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.StationSort;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站点排序配置 Mapper（按监测类型读取顺序、整表覆盖保存）。
 */
@Mapper
public interface StationSortMapper extends BaseMapper<StationSort> {
}
