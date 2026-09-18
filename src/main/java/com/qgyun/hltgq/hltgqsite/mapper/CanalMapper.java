package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.Canal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 渠系管理表 t_auto_hltgq_knc3g_egvnhw
 */
@Mapper
public interface CanalMapper extends BaseMapper<Canal> {

    /**
     * 全量渠系（数据量小，供内存构建渠系树、收集子孙渠系用）
     */
    @Select("SELECT \"id\", \"gfaegg\" AS name, \"cyjihq\" AS parentId FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_egvnhw\"")
    List<Canal> selectAll();
}
