package com.qgyun.hltgq.hltgqsite.waterusage.mapper;

import com.qgyun.hltgq.hltgqsite.waterusage.vo.DemandOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDemandVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 需水上报（t_auto_hltgq_water_demand）。表结构见 init.sql 灌区用水管理 2.1 / water-dispatch-bill-ddl.sql。
 * <p>审核态：待审核(#1#) →（审核）已通过(#2#)/已驳回(#3#)；待审核/已驳回可编辑
 * （保存后重置为待审核并清空审核痕迹，重新进入审核流程）；已通过锁定编辑（审核结论）。
 * <p>删除保护：被调度方案（demand_id）引用时禁止删除（Service 前置校验）。
 * <p>编号 XS + 4 位序号：应用层 MAX+1 取号、唯一索引 uk_water_demand_no 兜底并发（Controller 重试）。
 * <p>写入走 {@code @Param("row") Map}：key 为 snake_case 列名（Service 组装）；审计列 corp_code 恒 'hltgq'、时间由 Java 传入。
 * <p>KES 注意：模糊匹配一律 {@code CONCAT('%', #{x}, '%')}；日期参数 CAST 为 date。
 */
public interface WaterDemandMapper {

    String T_DEMAND = "\"qixiao-apaas\".\"t_auto_hltgq_water_demand\"";
    String T_DISPATCH = "\"qixiao-apaas\".\"t_auto_hltgq_water_dispatch\"";
    String T_USER = "\"qixiao-apaas\".\"t_auto_hltgq_water_user\"";
    String T_STATION = "\"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\"";

    /** 列表条件（page/count 共用，作用于别名 d；status 传库内 #N# 编码） */
    String LIST_CONDITIONS =
            "<if test='keyword != null and keyword != \"\"'>AND (d.demand_no LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.name LIKE CONCAT('%', #{keyword}, '%') OR u.user_no LIKE CONCAT('%', #{keyword}, '%') " +
            "OR d.usage_desc LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='status != null and status != \"\"'>AND d.status = #{status} </if>";

    /** 基础列（列表/详情共用；别名 d；amount 为库内 m³，Service 出口折算万m³；status 出口映射英文码） */
    String BASE_COLS = "d.id AS id, d.demand_no AS demandNo, d.water_user_id AS waterUserId, " +
            "u.user_no AS waterUserNo, u.name AS waterUserName, d.gate_id AS gateId, " +
            "s.zzkaec AS gateName, d.amount AS amount, " +
            "d.period_year AS periodYear, d.period_month AS periodMonth, d.xun_list AS xunValues, " +
            "to_char(d.start_date, 'YYYY-MM-DD') AS startDate, to_char(d.end_date, 'YYYY-MM-DD') AS endDate, " +
            "d.usage_desc AS usage, d.status AS status, d.audit_opinion AS auditOpinion, d.audit_by AS auditBy, " +
            "to_char(d.audit_at, 'YYYY-MM-DD HH24:MI') AS auditAt, " +
            "to_char(d.created_at, 'YYYY-MM-DD HH24:MI') AS applyTime, d.remark AS remark";

    /** 基础联表（户名/号 + 站档闸口名；别名 d/u/s） */
    String BASE_JOINS = " FROM " + T_DEMAND + " d " +
            "LEFT JOIN " + T_USER + " u ON u.id = d.water_user_id " +
            "LEFT JOIN " + T_STATION + " s ON s.id = d.gate_id ";

    /** 列表分页（编号倒序，最新上报在前） */
    @Select("<script>SELECT " + BASE_COLS + BASE_JOINS + "WHERE d.corp_code = 'hltgq' " + LIST_CONDITIONS +
            "ORDER BY d.demand_no DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<WaterDemandVO> selectDemandPage(@Param("keyword") String keyword,
                                         @Param("status") String status,
                                         @Param("size") int size,
                                         @Param("offset") long offset);

    /** 列表计数（条件与 {@link #selectDemandPage} 一致） */
    @Select("<script>SELECT COUNT(*)" + BASE_JOINS + "WHERE d.corp_code = 'hltgq' " + LIST_CONDITIONS + "</script>")
    long countDemands(@Param("keyword") String keyword,
                      @Param("status") String status);

    /** 按 ID 查（详情/流转前置校验共用） */
    @Select("SELECT " + BASE_COLS + BASE_JOINS + "WHERE d.id = #{id} AND d.corp_code = 'hltgq'")
    WaterDemandVO selectDemandById(@Param("id") String id);

    /**
     * 指定年度已通过申请（用水计划「实际保证率」统计用）：amount 库内 m³；
     * 按**需水开始日期所属年度**归集（`[yearStart, yearEnd)` 左闭右开，跨年申请整条归入开始年份、不拆分）；
     * 按用水户 + 开始日期排序，Service 据此做时段重叠检测与户级计量。
     * <p>分子、分母共用本查询结果（同一批申请）。
     */
    @Select("SELECT " + BASE_COLS + BASE_JOINS + "WHERE d.corp_code = 'hltgq' AND d.status = '#2#' " +
            "AND d.start_date >= CAST(#{yearStart} AS date) AND d.start_date < CAST(#{yearEnd} AS date) " +
            "ORDER BY d.water_user_id, d.start_date, d.demand_no")
    List<WaterDemandVO> selectApprovedDemandsOfYear(@Param("yearStart") String yearStart,
                                                    @Param("yearEnd") String yearEnd);

    /** 当前最大编号序号（XS + 4 位；表空返回 0） */
    @Select("SELECT COALESCE(MAX(CAST(substr(demand_no, 3) AS INTEGER)), 0) FROM " + T_DEMAND +
            " WHERE corp_code = 'hltgq' AND demand_no ~ '^XS[0-9]+$'")
    int selectMaxDemandSeq();

    /** 新增（row key：id/demand_no/water_user_id/gate_id/amount/period_year/period_month/xun_list/start_date/end_date/usage_desc/remark/now/operator） */
    @Insert("INSERT INTO " + T_DEMAND + " " +
            "(\"id\", \"demand_no\", \"water_user_id\", \"gate_id\", \"amount\", " +
            "\"period_year\", \"period_month\", \"xun_list\", \"start_date\", \"end_date\", " +
            "\"usage_desc\", \"status\", \"remark\", \"corp_code\", \"created_at\", \"created_by\", " +
            "\"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.demand_no}, #{row.water_user_id}, #{row.gate_id}, #{row.amount}, " +
            "#{row.period_year}, #{row.period_month}, #{row.xun_list}, " +
            "CAST(#{row.start_date} AS date), CAST(#{row.end_date} AS date), #{row.usage_desc}, '#1#', " +
            "#{row.remark}, 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertDemand(@Param("row") Map<String, Object> row);

    /** 编辑（仅待审核/已驳回；保存后重置为待审核并清空审核痕迹，重新进入审核流程） */
    @Update("UPDATE " + T_DEMAND + " SET \"water_user_id\" = #{row.water_user_id}, " +
            "\"gate_id\" = #{row.gate_id}, \"amount\" = #{row.amount}, " +
            "\"period_year\" = #{row.period_year}, \"period_month\" = #{row.period_month}, " +
            "\"xun_list\" = #{row.xun_list}, " +
            "\"start_date\" = CAST(#{row.start_date} AS date), \"end_date\" = CAST(#{row.end_date} AS date), " +
            "\"usage_desc\" = #{row.usage_desc}, \"remark\" = #{row.remark}, \"status\" = '#1#', " +
            "\"audit_opinion\" = NULL, \"audit_by\" = NULL, \"audit_at\" = NULL, " +
            "\"updated_at\" = #{row.now}, \"updated_by\" = #{row.operator} " +
            "WHERE \"id\" = #{row.id} AND \"status\" IN ('#1#', '#3#') AND \"corp_code\" = 'hltgq'")
    int updateDemand(@Param("row") Map<String, Object> row);

    /** 审核（仅待审核；toStatus = #2# 通过 / #3# 驳回；意见必填与否由 Service 校验） */
    @Update("UPDATE " + T_DEMAND + " SET \"status\" = #{toStatus}, \"audit_opinion\" = #{opinion}, " +
            "\"audit_by\" = #{operator}, \"audit_at\" = #{now}, \"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"id\" = #{id} AND \"status\" = '#1#' AND \"corp_code\" = 'hltgq'")
    int auditDemand(@Param("id") String id,
                    @Param("toStatus") String toStatus,
                    @Param("opinion") String opinion,
                    @Param("now") LocalDateTime now,
                    @Param("operator") String operator);

    /** 物理删除（引用保护由 Service 前置校验：被调度方案 demand_id 引用时禁止） */
    @Delete("DELETE FROM " + T_DEMAND + " WHERE id = #{id} AND corp_code = 'hltgq'")
    int deleteDemand(@Param("id") String id);

    /** 状态计数（Tab 角标：全部/待审核/已通过/已驳回；group by 一次取回，key：status/total） */
    @Select("SELECT d.status AS status, COUNT(*) AS total FROM " + T_DEMAND +
            " d WHERE d.corp_code = 'hltgq' GROUP BY d.status")
    List<Map<String, Object>> countByStatus();

    /** 被调度方案引用数（删除保护；含已取消方案，留痕不悬空） */
    @Select("SELECT COUNT(*) FROM " + T_DISPATCH + " WHERE demand_id = #{demandId} AND corp_code = 'hltgq'")
    int countDispatchRefs(@Param("demandId") String demandId);

    /** 已通过上报单下拉（调度方案「关联需水申请」；可按用水户过滤；amount 库内 m³，Service 折算万m³） */
    @Select("<script>SELECT d.id AS id, d.demand_no AS demandNo, d.water_user_id AS waterUserId, " +
            "u.user_no AS waterUserNo, u.name AS waterUserName, d.amount AS amount, " +
            "to_char(d.start_date, 'YYYY-MM-DD') AS startDate, to_char(d.end_date, 'YYYY-MM-DD') AS endDate " +
            BASE_JOINS + "WHERE d.corp_code = 'hltgq' AND d.status = '#2#' " +
            "<if test='userId != null and userId != \"\"'>AND d.water_user_id = #{userId} </if>" +
            "<if test='keyword != null and keyword != \"\"'>AND (d.demand_no LIKE CONCAT('%', #{keyword}, '%') " +
            "OR u.name LIKE CONCAT('%', #{keyword}, '%') OR u.user_no LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY d.demand_no DESC LIMIT 200</script>")
    List<DemandOptionVO> selectPassedOptions(@Param("keyword") String keyword,
                                             @Param("userId") String userId);
}
