package com.qgyun.hltgq.hltgqsite.waterusage.mapper;

import com.qgyun.hltgq.hltgqsite.waterusage.vo.PlanRefVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.QuotaOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterQuotaVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 用水定额标准（t_auto_hltgq_water_quota）。表结构见 init.sql 灌区用水管理 1.2。
 * <p>被引用数为实时 count（DISTINCT plan，不过滤状态，仅计划物理删除时释放）；
 * 删除保护两级规则同用水户（非编制中引用禁止 / 仅编制中引用二次确认后连带清理明细）。
 */
public interface WaterQuotaMapper {

    String T_QUOTA = "\"qixiao-apaas\".\"t_auto_hltgq_water_quota\"";
    String T_PLAN = "\"qixiao-apaas\".\"t_auto_hltgq_water_plan\"";
    String T_ITEM = "\"qixiao-apaas\".\"t_auto_hltgq_water_plan_item\"";

    /** 列表条件（page/count 共用，作用于别名 q） */
    String LIST_CONDITIONS =
            "<if test='keyword != null and keyword != \"\"'>AND (q.crop_type LIKE CONCAT('%', #{keyword}, '%') " +
            "OR q.irrigation_method LIKE CONCAT('%', #{keyword}, '%') " +
            "OR q.remark LIKE CONCAT('%', #{keyword}, '%')) </if>";

    /** 基础列（列表/详情共用；别名 q） */
    String BASE_COLS = "q.id AS id, q.crop_type AS cropType, q.irrigation_method AS irrigationMethod, " +
            "q.quota_value AS quotaValue, q.remark AS remark";

    /** 被引用计划数子查询（DISTINCT plan，实时计算） */
    String REF_COUNT = "(SELECT COUNT(DISTINCT i.plan_id) FROM " + T_ITEM + " i WHERE i.quota_id = q.id) AS refCount";

    /** 列表分页（被引用数实时 count；新建在前） */
    @Select("<script>SELECT " + BASE_COLS + ", " + REF_COUNT + " FROM " + T_QUOTA + " q " +
            "WHERE q.corp_code = 'hltgq' " + LIST_CONDITIONS +
            "ORDER BY q.created_at DESC, q.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<WaterQuotaVO> selectQuotaPage(@Param("keyword") String keyword,
                                       @Param("size") int size,
                                       @Param("offset") long offset);

    /** 列表计数（条件与 {@link #selectQuotaPage} 一致） */
    @Select("<script>SELECT COUNT(*) FROM " + T_QUOTA + " q WHERE q.corp_code = 'hltgq' " +
            LIST_CONDITIONS + "</script>")
    long countQuotas(@Param("keyword") String keyword);

    /** 按 ID 查（详情/校验/测算共用） */
    @Select("SELECT " + BASE_COLS + ", " + REF_COUNT + " FROM " + T_QUOTA + " q " +
            "WHERE q.id = #{id} AND q.corp_code = 'hltgq'")
    WaterQuotaVO selectQuotaById(@Param("id") String id);

    /** 定额下拉选项（计划明细「适用定额」；按作物种类排序） */
    @Select("<script>SELECT q.id AS id, q.crop_type AS cropType, q.irrigation_method AS irrigationMethod, " +
            "q.quota_value AS quotaValue FROM " + T_QUOTA + " q WHERE q.corp_code = 'hltgq' " +
            "<if test='keyword != null and keyword != \"\"'>AND (q.crop_type LIKE CONCAT('%', #{keyword}, '%') " +
            "OR q.irrigation_method LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY q.crop_type, q.irrigation_method, q.id</script>")
    List<QuotaOptionVO> selectQuotaOptions(@Param("keyword") String keyword);

    /** 新增（row key：id/crop_type/irrigation_method/quota_value/remark/now/operator） */
    @Insert("INSERT INTO " + T_QUOTA + " " +
            "(\"id\", \"crop_type\", \"irrigation_method\", \"quota_value\", \"remark\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.crop_type}, #{row.irrigation_method}, #{row.quota_value}, #{row.remark}, " +
            "'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertQuota(@Param("row") Map<String, Object> row);

    /** 编辑（编辑不回溯已生效/已归档计划核定水量，仅影响新建测算） */
    @Update("UPDATE " + T_QUOTA + " SET \"crop_type\" = #{row.crop_type}, " +
            "\"irrigation_method\" = #{row.irrigation_method}, \"quota_value\" = #{row.quota_value}, " +
            "\"remark\" = #{row.remark}, \"updated_at\" = #{row.now}, \"updated_by\" = #{row.operator} " +
            "WHERE \"id\" = #{row.id} AND \"corp_code\" = 'hltgq'")
    int updateQuota(@Param("row") Map<String, Object> row);

    /** 物理删除（引用保护由 Service 前置校验） */
    @Delete("DELETE FROM " + T_QUOTA + " WHERE id = #{id} AND corp_code = 'hltgq'")
    int deleteQuota(@Param("id") String id);

    /** 引用查询（两级删除保护）：引用方计划 + 明细行数 */
    @Select("SELECT p.id AS planId, p.plan_no AS planNo, p.plan_name AS planName, p.status AS status, " +
            "COUNT(*) AS itemCount " +
            "FROM " + T_ITEM + " i JOIN " + T_PLAN + " p ON p.id = i.plan_id " +
            "WHERE i.quota_id = #{quotaId} AND p.corp_code = 'hltgq' " +
            "GROUP BY p.id, p.plan_no, p.plan_name, p.status ORDER BY p.plan_no")
    List<PlanRefVO> selectQuotaRefs(@Param("quotaId") String quotaId);

    /** 连带清理：删除指定计划中使用该定额的明细行（仅编制中计划，Service 已分流） */
    @Delete("<script>DELETE FROM " + T_ITEM + " WHERE quota_id = #{quotaId} AND plan_id IN " +
            "<foreach collection='planIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach></script>")
    int deleteQuotaItemsInPlans(@Param("quotaId") String quotaId, @Param("planIds") List<String> planIds);
}
