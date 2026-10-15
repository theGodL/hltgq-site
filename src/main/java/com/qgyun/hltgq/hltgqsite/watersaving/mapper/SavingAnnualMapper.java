package com.qgyun.hltgq.hltgqsite.watersaving.mapper;

import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingAnnualVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingPrevPlanVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 年度水量记录（t_auto_hltgq_water_saving_annual）。表结构见 init.sql 节水管理 5.1。
 * <p>行 = 一个潜力年度；potential_year 全表唯一（uk_water_saving_annual_year 兜底并发）。
 * 被方案引用（scheme.evidence_id）后禁止删除、潜力年度禁止修改（Service 层校验）。
 * <p>上一年度计划带出跨读灌区用水 t_auto_hltgq_water_plan：plan_type '#1#'（年度）且
 * status ≠ '#4#'（已作废），按创建时间倒序第一条；total_water 库内 m³，输出 SQL 内 ÷10000 转万m³。
 */
public interface SavingAnnualMapper {

    String T_ANNUAL = "\"qixiao-apaas\".\"t_auto_hltgq_water_saving_annual\"";
    String T_SCHEME = "\"qixiao-apaas\".\"t_auto_hltgq_water_saving_scheme\"";
    String T_PLAN = "\"qixiao-apaas\".\"t_auto_hltgq_water_plan\"";

    /** 列（列表/详情共用；别名 a） */
    String BASE_COLS = "a.id AS id, a.potential_year AS potentialYear, a.basis_year AS basisYear, " +
            "a.prev_plan_volume AS prevPlanVolume, a.prev_actual_volume AS prevActualVolume, " +
            "a.plan_source AS planSource, a.plan_id AS planId, a.actual_source AS actualSource, " +
            "a.is_demo AS isDemo, " +
            "to_char(a.created_at, 'YYYY-MM-DD HH24:MI') AS createdText, " +
            "to_char(a.updated_at, 'YYYY-MM-DD HH24:MI') AS updatedText";

    /** 全量列表（潜力年度倒序；前端年度下拉 + 年度水量设置列表） */
    @Select("SELECT " + BASE_COLS + " FROM " + T_ANNUAL + " a " +
            "WHERE a.corp_code = 'hltgq' ORDER BY a.potential_year DESC")
    List<SavingAnnualVO> selectList();

    /** 按 id 查 */
    @Select("SELECT " + BASE_COLS + " FROM " + T_ANNUAL + " a WHERE a.id = #{id} AND a.corp_code = 'hltgq'")
    SavingAnnualVO selectById(@Param("id") String id);

    /** 按 id 集合批量查（方案列表依据摘要装配，避免逐行查询） */
    @Select("<script>SELECT " + BASE_COLS + " FROM " + T_ANNUAL + " a WHERE a.corp_code = 'hltgq' " +
            "AND a.id IN <foreach collection='ids' item='aid' open='(' separator=',' close=')'>#{aid}</foreach>" +
            "</script>")
    List<SavingAnnualVO> selectByIds(@Param("ids") List<String> ids);

    /** 潜力年度唯一性预检（编辑时排除自身） */
    @Select("<script>SELECT COUNT(*) FROM " + T_ANNUAL + " WHERE corp_code = 'hltgq' AND potential_year = #{year} " +
            "<if test='excludeId != null'>AND id != #{excludeId}</if></script>")
    int countByYear(@Param("year") int year, @Param("excludeId") String excludeId);

    /** 引用方案数（删除保护 / 年度修改保护） */
    @Select("SELECT COUNT(*) FROM " + T_SCHEME + " WHERE evidence_id = #{evidenceId} AND corp_code = 'hltgq'")
    int countSchemesByEvidence(@Param("evidenceId") String evidenceId);

    /** 新增（row key：id/potential_year/basis_year/prev_plan_volume/prev_actual_volume/plan_source/plan_id/
     *  actual_source/is_demo/now/operator） */
    @Insert("INSERT INTO " + T_ANNUAL + " " +
            "(\"id\", \"potential_year\", \"basis_year\", \"prev_plan_volume\", \"prev_actual_volume\", " +
            "\"plan_source\", \"plan_id\", \"actual_source\", \"is_demo\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.potential_year}, #{row.basis_year}, #{row.prev_plan_volume}, " +
            "#{row.prev_actual_volume}, #{row.plan_source}, #{row.plan_id}, #{row.actual_source}, #{row.is_demo}, " +
            "'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insert(@Param("row") Map<String, Object> row);

    /** 更新（潜力年度仅未被方案引用时可变，Service 校验；水量/来源/标记可改） */
    @Update("UPDATE " + T_ANNUAL + " SET \"potential_year\" = #{row.potential_year}, " +
            "\"basis_year\" = #{row.basis_year}, " +
            "\"prev_plan_volume\" = #{row.prev_plan_volume}, " +
            "\"prev_actual_volume\" = #{row.prev_actual_volume}, \"plan_source\" = #{row.plan_source}, " +
            "\"plan_id\" = #{row.plan_id}, \"actual_source\" = #{row.actual_source}, " +
            "\"is_demo\" = #{row.is_demo}, " +
            "\"updated_at\" = #{row.now}, \"updated_by\" = #{row.operator} " +
            "WHERE \"id\" = #{row.id} AND \"corp_code\" = 'hltgq'")
    int update(@Param("row") Map<String, Object> row);

    /** 物理删除（Service 已前置引用校验） */
    @Delete("DELETE FROM " + T_ANNUAL + " WHERE id = #{id} AND corp_code = 'hltgq'")
    int delete(@Param("id") String id);

    /**
     * 上一年度用水计划带出（basisYear 的年度计划，创建时间倒序第一条）。
     * <p>口径：plan_type = '#1#' 且 status ≠ '#4#'；total_water ÷ 10000 → 万m³（6 位）。
     * <p>边界日期由服务层拼好传入：KingbaseES 下 {@code #{year} || '-01-01'} 拼接会把参数按布尔解释
     * （cannot cast type boolean to date），统一走 CAST(#{...} AS date) 绑定参数模式。
     */
    @Select("SELECT p.id AS planId, p.plan_no AS planNo, p.plan_name AS planName, " +
            "ROUND(p.total_water / 10000, 6) AS planVolume, " +
            "to_char(p.created_at, 'YYYY-MM-DD HH24:MI') AS createdText " +
            "FROM " + T_PLAN + " p " +
            "WHERE p.corp_code = 'hltgq' AND p.plan_type = '#1#' AND p.status != '#4#' " +
            "AND p.period_start >= CAST(#{start} AS date) " +
            "AND p.period_start <= CAST(#{end} AS date) " +
            "ORDER BY p.created_at DESC, p.id DESC LIMIT 1")
    SavingPrevPlanVO selectPrevYearPlan(@Param("start") String start, @Param("end") String end);
}
