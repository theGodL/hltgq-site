package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.Canal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
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

    /**
     * 全量渠系（含创建时间）：同级按 created_at 倒序，与平台层级树接口的展示顺序一致，
     * 供「渠系排序」页面生成分组清单、保存时按时间槽重排使用
     */
    @Select("SELECT \"id\", \"gfaegg\" AS name, \"cyjihq\" AS parentId, \"created_at\" AS createdAt "
            + "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_egvnhw\" ORDER BY \"created_at\" DESC NULLS LAST, \"id\"")
    List<Canal> selectAllWithTime();

    /**
     * 改写渠系创建时间（渠系排序的唯一依据；仅该列参与平台树接口排序，其余列不动）
     */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_knc3g_egvnhw\" SET \"created_at\" = #{createdAt} WHERE \"id\" = #{id}")
    int updateCreatedAt(@Param("id") String id, @Param("createdAt") LocalDateTime createdAt);
}
