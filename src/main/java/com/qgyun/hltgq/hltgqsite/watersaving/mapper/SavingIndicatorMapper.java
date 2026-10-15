package com.qgyun.hltgq.hltgqsite.watersaving.mapper;

import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingIndicatorVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 节水指标（t_auto_hltgq_water_saving_indicator）。表结构见 init.sql 节水管理 5.3。
 * <p>名称全局唯一（含停用；uk_water_saving_indicator_name 兜底并发）；计算方式固定三选一（#1# 节水量 /
 * #2# 节水率 / #3# 目标完成率，方法驱动服务端计算）；已关联方案的指标计算方式锁定、不可删除（Service 校验）。
 */
public interface SavingIndicatorMapper {

    String T_INDICATOR = "\"qixiao-apaas\".\"t_auto_hltgq_water_saving_indicator\"";

    /** 列（列表/详情共用；别名 i） */
    String BASE_COLS = "i.id AS id, i.name AS name, i.method AS method, i.target_value AS targetValue, " +
            "i.purpose AS purpose, i.enabled AS enabled, " +
            "to_char(i.created_at, 'YYYY-MM-DD HH24:MI') AS createdText, " +
            "to_char(i.updated_at, 'YYYY-MM-DD HH24:MI') AS updatedText";

    /** 全量列表（名称关键字可选；创建顺序） */
    @Select("<script>SELECT " + BASE_COLS + " FROM " + T_INDICATOR + " i WHERE i.corp_code = 'hltgq' " +
            "<if test='keyword != null and keyword != \"\"'>AND i.name LIKE CONCAT('%', #{keyword}, '%') </if>" +
            "ORDER BY i.created_at, i.id</script>")
    List<SavingIndicatorVO> selectList(@Param("keyword") String keyword);

    /** 按 id 查 */
    @Select("SELECT " + BASE_COLS + " FROM " + T_INDICATOR + " i WHERE i.id = #{id} AND i.corp_code = 'hltgq'")
    SavingIndicatorVO selectById(@Param("id") String id);

    /** 按 id 集合批量查（方案计算结果行 / 编辑回显，保持入参顺序由 Service 重排） */
    @Select("<script>SELECT " + BASE_COLS + " FROM " + T_INDICATOR + " i WHERE i.corp_code = 'hltgq' " +
            "AND i.id IN <foreach collection='ids' item='iid' open='(' separator=',' close=')'>#{iid}</foreach>" +
            "</script>")
    List<SavingIndicatorVO> selectByIds(@Param("ids") List<String> ids);

    /** 名称唯一性预检（含停用指标；编辑时排除自身） */
    @Select("<script>SELECT COUNT(*) FROM " + T_INDICATOR + " WHERE corp_code = 'hltgq' AND name = #{name} " +
            "<if test='excludeId != null'>AND id != #{excludeId}</if></script>")
    int countByName(@Param("name") String name, @Param("excludeId") String excludeId);

    /** 新增（row key：id/name/method/target_value/purpose/enabled/now/operator） */
    @Insert("INSERT INTO " + T_INDICATOR + " " +
            "(\"id\", \"name\", \"method\", \"target_value\", \"purpose\", \"enabled\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.name}, #{row.method}, #{row.target_value}, #{row.purpose}, #{row.enabled}, " +
            "'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insert(@Param("row") Map<String, Object> row);

    /** 基础字段更新（名称/计算方式/目标值/用途；启停由专用语句维护） */
    @Update("UPDATE " + T_INDICATOR + " SET \"name\" = #{row.name}, \"method\" = #{row.method}, " +
            "\"target_value\" = #{row.target_value}, \"purpose\" = #{row.purpose}, " +
            "\"updated_at\" = #{row.now}, \"updated_by\" = #{row.operator} " +
            "WHERE \"id\" = #{row.id} AND \"corp_code\" = 'hltgq'")
    int update(@Param("row") Map<String, Object> row);

    /** 启停切换（#1# 启用 / #2# 停用；停用后新方案不可勾选，既有关联保留） */
    @Update("UPDATE " + T_INDICATOR + " SET \"enabled\" = #{enabled}, " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"id\" = #{id} AND \"corp_code\" = 'hltgq'")
    int updateEnabled(@Param("id") String id,
                      @Param("enabled") String enabled,
                      @Param("now") LocalDateTime now,
                      @Param("operator") String operator);

    /** 物理删除（Service 已前置引用校验：被方案关联不可删除） */
    @Delete("DELETE FROM " + T_INDICATOR + " WHERE id = #{id} AND corp_code = 'hltgq'")
    int delete(@Param("id") String id);
}
