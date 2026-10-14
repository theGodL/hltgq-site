package com.qgyun.hltgq.hltgqsite.waterusage.mapper;

import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterPlanItemVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterPlanVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用水计划（t_auto_hltgq_water_plan + t_auto_hltgq_water_plan_item）。表结构见 init.sql 灌区用水管理 1.3/1.4。
 * <p>状态机：编制中 →（生效）已生效 →（归档）已归档；已生效 →（作废）已作废；
 * 编制中可物理删除（主表+明细）。
 * <p>状态流转一律带原状态守卫（WHERE status = 原状态，影响行数 0 = 状态已变更），
 * 生效三层校验见 Service#validatePlanActivation（activate 事务内 + conflict-check 只读包装共用）。
 */
public interface WaterPlanMapper {

    String T_PLAN = "\"qixiao-apaas\".\"t_auto_hltgq_water_plan\"";
    String T_ITEM = "\"qixiao-apaas\".\"t_auto_hltgq_water_plan_item\"";
    String T_USER = "\"qixiao-apaas\".\"t_auto_hltgq_water_user\"";
    String T_QUOTA = "\"qixiao-apaas\".\"t_auto_hltgq_water_quota\"";

    /** 列表条件（page/count 共用，作用于别名 p） */
    String LIST_CONDITIONS =
            "<if test='keyword != null and keyword != \"\"'>AND (p.plan_name LIKE CONCAT('%', #{keyword}, '%') " +
            "OR p.plan_no LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='planType != null and planType != \"\"'>AND p.plan_type = #{planType} </if>" +
            "<if test='status != null and status != \"\"'>AND p.status = #{status} </if>";

    /** 主表列（列表/详情共用；别名 p） */
    String BASE_COLS = "p.id AS id, p.plan_no AS planNo, p.plan_name AS planName, " +
            "p.plan_type AS planType, to_char(p.period_start, 'YYYY-MM-DD') AS periodStart, " +
            "to_char(p.period_end, 'YYYY-MM-DD') AS periodEnd, " +
            "to_char(p.created_at, 'YYYY-MM-DD HH24:MI') AS createdAtText, " +
            "p.total_water AS totalWater, p.scheduled_qty AS scheduledQty, p.status AS status, " +
            "p.void_by AS voidBy, to_char(p.void_at, 'YYYY-MM-DD HH24:MI') AS voidAt, " +
            "p.void_reason AS voidReason";

    /** 列表分页（用水户数 = 明细 DISTINCT 用水户；编号倒序） */
    @Select("<script>SELECT " + BASE_COLS + ", " +
            "(SELECT COUNT(DISTINCT i.water_user_id) FROM " + T_ITEM + " i WHERE i.plan_id = p.id) AS userCount " +
            "FROM " + T_PLAN + " p WHERE p.corp_code = 'hltgq' " + LIST_CONDITIONS +
            "ORDER BY p.plan_no DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<WaterPlanVO> selectPlanPage(@Param("keyword") String keyword,
                                     @Param("planType") String planType,
                                     @Param("status") String status,
                                     @Param("size") int size,
                                     @Param("offset") long offset);

    /** 列表计数（条件与 {@link #selectPlanPage} 一致） */
    @Select("<script>SELECT COUNT(*) FROM " + T_PLAN + " p WHERE p.corp_code = 'hltgq' " +
            LIST_CONDITIONS + "</script>")
    long countPlans(@Param("keyword") String keyword,
                    @Param("planType") String planType,
                    @Param("status") String status);

    /** 按 ID 查（详情/状态流转前置校验共用） */
    @Select("SELECT " + BASE_COLS + " FROM " + T_PLAN + " p WHERE p.id = #{id} AND p.corp_code = 'hltgq'")
    WaterPlanVO selectPlanById(@Param("id") String id);

    /** 明细行（含用水户/定额关联名与值；行序） */
    @Select("SELECT i.id AS id, i.water_user_id AS waterUserId, u.name AS waterUserName, " +
            "u.user_no AS waterUserNo, i.irrigation_area AS irrigationArea, i.quota_id AS quotaId, " +
            "q.crop_type AS quotaCropType, q.irrigation_method AS quotaIrrigationMethod, " +
            "q.quota_value AS quotaValue, i.calc_water AS calcWater, i.plan_water AS planWater, " +
            "i.override_reason AS overrideReason, i.stage_tag AS stageTag, i.sort_order AS sortOrder " +
            "FROM " + T_ITEM + " i " +
            "LEFT JOIN " + T_USER + " u ON u.id = i.water_user_id " +
            "LEFT JOIN " + T_QUOTA + " q ON q.id = i.quota_id " +
            "WHERE i.plan_id = #{planId} ORDER BY i.sort_order, i.id")
    List<WaterPlanItemVO> selectPlanItems(@Param("planId") String planId);

    /** 当前最大编号序号（YJ + 3 位序号；表空返回 0） */
    @Select("SELECT COALESCE(MAX(CAST(substr(plan_no, 3) AS INTEGER)), 0) FROM " + T_PLAN +
            " WHERE corp_code = 'hltgq' AND plan_no ~ '^YJ[0-9]+$'")
    int selectMaxPlanSeq();

    /** 名称唯一性预检（编辑时排除自身） */
    @Select("<script>SELECT COUNT(*) FROM " + T_PLAN + " WHERE corp_code = 'hltgq' AND plan_name = #{name} " +
            "<if test='excludeId != null'>AND id != #{excludeId}</if></script>")
    int countPlanByName(@Param("name") String name, @Param("excludeId") String excludeId);

    /** 新增主表（row key：id/plan_no/plan_name/plan_type/period_start/period_end/total_water/now/operator） */
    @Insert("INSERT INTO " + T_PLAN + " " +
            "(\"id\", \"plan_no\", \"plan_name\", \"plan_type\", \"period_start\", \"period_end\", " +
            "\"total_water\", \"scheduled_qty\", \"status\", \"corp_code\", \"created_at\", \"created_by\", " +
            "\"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.plan_no}, #{row.plan_name}, #{row.plan_type}, " +
            "CAST(#{row.period_start} AS date), CAST(#{row.period_end} AS date), #{row.total_water}, 0, '#1#', " +
            "'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertPlan(@Param("row") Map<String, Object> row);

    /** 新增明细（row key：id/plan_id/water_user_id/irrigation_area/quota_id/calc_water/plan_water/
     *  override_reason/stage_tag/sort_order/now/operator） */
    @Insert("INSERT INTO " + T_ITEM + " " +
            "(\"id\", \"plan_id\", \"water_user_id\", \"irrigation_area\", \"quota_id\", \"calc_water\", " +
            "\"plan_water\", \"override_reason\", \"stage_tag\", \"sort_order\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.plan_id}, #{row.water_user_id}, #{row.irrigation_area}, #{row.quota_id}, " +
            "#{row.calc_water}, #{row.plan_water}, #{row.override_reason}, #{row.stage_tag}, #{row.sort_order}, " +
            "'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertPlanItem(@Param("row") Map<String, Object> row);

    /** 编制中编辑主表（名称/类型/周期/总水量；带状态守卫） */
    @Update("UPDATE " + T_PLAN + " SET \"plan_name\" = #{row.plan_name}, \"plan_type\" = #{row.plan_type}, " +
            "\"period_start\" = CAST(#{row.period_start} AS date), \"period_end\" = CAST(#{row.period_end} AS date), " +
            "\"total_water\" = #{row.total_water}, \"updated_at\" = #{row.now}, \"updated_by\" = #{row.operator} " +
            "WHERE \"id\" = #{row.id} AND \"status\" = '#1#' AND \"corp_code\" = 'hltgq'")
    int updatePlanDraft(@Param("row") Map<String, Object> row);

    /** 总水量维护（已生效编辑 / 明细变更 / 删除连带清理共用）：明细之和回写主表（<b>全模块唯一实现</b>） */
    @Update("<script>UPDATE " + T_PLAN + " p SET total_water = " +
            "COALESCE((SELECT SUM(i.plan_water) FROM " + T_ITEM + " i WHERE i.plan_id = p.id), 0), " +
            "updated_at = #{now}, updated_by = #{operator} WHERE p.corp_code = 'hltgq' AND p.id IN " +
            "<foreach collection='planIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach></script>")
    int recalcPlanTotals(@Param("planIds") List<String> planIds,
                         @Param("now") LocalDateTime now,
                         @Param("operator") String operator);

    /** 明细核定水量/说明更新（已生效编辑：面积/定额/用户锁定，仅此两列可变） */
    @Update("UPDATE " + T_ITEM + " SET \"plan_water\" = #{planWater}, \"override_reason\" = #{overrideReason}, " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operator} WHERE \"id\" = #{id} AND \"plan_id\" = #{planId}")
    int updatePlanItemWater(@Param("id") String id,
                            @Param("planId") String planId,
                            @Param("planWater") BigDecimal planWater,
                            @Param("overrideReason") String overrideReason,
                            @Param("now") LocalDateTime now,
                            @Param("operator") String operator);

    /** 编制中编辑/删除：清空明细（明细全量替换前调用） */
    @Delete("DELETE FROM " + T_ITEM + " WHERE plan_id = #{planId}")
    int deletePlanItems(@Param("planId") String planId);

    /** 物理删除主表（仅编制中，Service 已前置校验） */
    @Delete("DELETE FROM " + T_PLAN + " WHERE id = #{id} AND status = '#1#' AND corp_code = 'hltgq'")
    int deletePlan(@Param("id") String id);

    /** 状态流转（带原状态守卫；影响行数 0 = 状态已变更，报「请刷新」） */
    @Update("UPDATE " + T_PLAN + " SET \"status\" = #{toStatus}, \"updated_at\" = #{now}, " +
            "\"updated_by\" = #{operator} WHERE \"id\" = #{id} AND \"status\" = #{fromStatus} " +
            "AND \"corp_code\" = 'hltgq'")
    int updatePlanStatus(@Param("id") String id,
                         @Param("fromStatus") String fromStatus,
                         @Param("toStatus") String toStatus,
                         @Param("now") LocalDateTime now,
                         @Param("operator") String operator);

    /** 作废（仅已生效；记录 void_by/at/reason） */
    @Update("UPDATE " + T_PLAN + " SET \"status\" = '#4#', \"void_by\" = #{operator}, \"void_at\" = #{now}, " +
            "\"void_reason\" = #{reason}, \"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"id\" = #{id} AND \"status\" = '#2#' AND \"corp_code\" = 'hltgq'")
    int voidPlan(@Param("id") String id,
                 @Param("reason") String reason,
                 @Param("now") LocalDateTime now,
                 @Param("operator") String operator);

    /**
     * 同类型周期重叠的已生效计划（生效校验第二层；周期 [start, end] 区间相交）。
     * 归档/作废不拦截（历史事实，见设计方案口径）。
     */
    @Select("SELECT p.plan_no AS planNo, p.plan_name AS planName FROM " + T_PLAN + " p " +
            "WHERE p.corp_code = 'hltgq' AND p.plan_type = #{planType} AND p.status = '#2#' " +
            "AND p.id != #{planId} AND p.period_start <= CAST(#{end} AS date) " +
            "AND p.period_end >= CAST(#{start} AS date) ORDER BY p.plan_no")
    List<WaterPlanVO> selectSameTypeOverlaps(@Param("planId") String planId,
                                             @Param("planType") String planType,
                                             @Param("start") String start,
                                             @Param("end") String end);

    /** 本计划中每用水户的计划水量合计（生效校验第三层逐户比对；planWater 复用为合计值） */
    @Select("SELECT i.water_user_id AS waterUserId, u.name AS waterUserName, " +
            "SUM(i.plan_water) AS planWater " +
            "FROM " + T_ITEM + " i LEFT JOIN " + T_USER + " u ON u.id = i.water_user_id " +
            "WHERE i.plan_id = #{planId} GROUP BY i.water_user_id, u.name ORDER BY u.name")
    List<WaterPlanItemVO> selectPlanAmountsByUser(@Param("planId") String planId);

    /** 当年已生效+已归档年度计划中该用水户的核定水量合计（弱约束基准；无则 0） */
    @Select("SELECT COALESCE(SUM(i.plan_water), 0) FROM " + T_ITEM + " i " +
            "JOIN " + T_PLAN + " p ON p.id = i.plan_id " +
            "WHERE i.water_user_id = #{waterUserId} AND p.plan_type = '#1#' AND p.status IN ('#2#', '#3#') " +
            "AND EXTRACT(YEAR FROM p.period_start) = #{year}")
    BigDecimal selectAnnualBaseForUser(@Param("waterUserId") String waterUserId, @Param("year") int year);

    /** 当年已生效+已归档月度/旬度计划中该用水户的合计（排除本计划；弱约束比对项） */
    @Select("SELECT COALESCE(SUM(i.plan_water), 0) FROM " + T_ITEM + " i " +
            "JOIN " + T_PLAN + " p ON p.id = i.plan_id " +
            "WHERE i.water_user_id = #{waterUserId} AND p.plan_type IN ('#2#', '#3#') " +
            "AND p.status IN ('#2#', '#3#') AND p.id != #{excludePlanId} " +
            "AND EXTRACT(YEAR FROM p.period_start) = #{year}")
    BigDecimal selectMonthSumForUser(@Param("waterUserId") String waterUserId,
                                     @Param("year") int year,
                                     @Param("excludePlanId") String excludePlanId);
}
