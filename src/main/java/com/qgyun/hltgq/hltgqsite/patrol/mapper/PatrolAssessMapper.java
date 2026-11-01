package com.qgyun.hltgq.hltgqsite.patrol.mapper;

import com.qgyun.hltgq.hltgqsite.entity.PatrolAssess;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolAssessVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 巡查考核读写（自建表 t_auto_hltgq_water_patrol_assess）。
 *
 * <p>统计口径（全部按计划下巡检记录关联，与列表/记录弹窗口径一致）：
 * <ul>
 *   <li>巡查记录排除空白占位草稿（H5 开始巡检写入、四项全空的行）</li>
 *   <li>问题经 abqezf（巡检记录 id）关联到计划的巡检记录</li>
 *   <li>作废 #5# 不计入任何一项：发现问题 = 已整改 #4# + 整改中 #2#/#3# + 未整改 #1#</li>
 * </ul>
 */
@Mapper
public interface PatrolAssessMapper {

    /**
     * 计划下考核自动项的实时统计（未考核预览用；提交时同一 SQL 重算后落库形成快照）。
     * <p>三段派生表各返回 1 行：巡查范围站点数 / 巡检记录与站点数 / 问题分状态计数。
     */
    @Select("SELECT " +
            "p.site_count AS planSite, " +
            "r.record_count AS recordCount, " +
            "r.site_count AS patrolledSite, " +
            "COALESCE(i.issue_total, 0) AS issueTotal, " +
            "COALESCE(i.issue_fixed, 0) AS issueFixed, " +
            "COALESCE(i.issue_fixing, 0) AS issueFixing, " +
            "COALESCE(i.issue_open, 0) AS issueOpen " +
            "FROM " +
            "(SELECT COUNT(*) AS site_count " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_nlbdju_tkwovk_rel\" " +
            "WHERE corp_code = 'hltgq' AND biz_id = #{scheduleId}) p, " +
            "(SELECT COUNT(*) AS record_count, COUNT(DISTINCT r.site) AS site_count " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "WHERE r.corp_code = 'hltgq' AND r.patrol_schedule = #{scheduleId} " +
            "AND NOT (r.status = '#1#' AND r.site IS NULL AND r.\"_d_10497_device\" IS NULL " +
            "AND r.result IS NULL AND r.content IS NULL)) r, " +
            "(SELECT SUM(CASE WHEN i.status <> '#5#' THEN 1 ELSE 0 END) AS issue_total, " +
            "SUM(CASE WHEN i.status = '#4#' THEN 1 ELSE 0 END) AS issue_fixed, " +
            "SUM(CASE WHEN i.status IN ('#2#', '#3#') THEN 1 ELSE 0 END) AS issue_fixing, " +
            "SUM(CASE WHEN i.status = '#1#' THEN 1 ELSE 0 END) AS issue_open " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i " +
            "JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" ir ON ir.id = i.abqezf " +
            "WHERE ir.corp_code = 'hltgq' AND ir.patrol_schedule = #{scheduleId}) i")
    PatrolAssessVO selectStat(@Param("scheduleId") String scheduleId);

    /** 计划下的考核快照；未考核返回 null。 */
    @Select("SELECT a.schedule_id AS scheduleId, a.plan_code AS planCode, a.plan_title AS planTitle, " +
            "a.plan_site AS planSite, a.patrolled_site AS patrolledSite, a.record_count AS recordCount, " +
            "a.issue_total AS issueTotal, a.issue_fixed AS issueFixed, a.issue_fixing AS issueFixing, " +
            "a.issue_open AS issueOpen, a.score AS score, a.grade AS grade, a.conclusion AS conclusion, " +
            "a.assessor_id AS assessorId, a.assessor_name AS assessorName, a.created_at AS assessAt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_assess\" a " +
            "WHERE a.corp_code = 'hltgq' AND a.schedule_id = #{scheduleId}")
    PatrolAssessVO selectBySchedule(@Param("scheduleId") String scheduleId);

    /** 该计划是否已考核（提交前防重复）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_assess\" " +
            "WHERE corp_code = 'hltgq' AND schedule_id = #{scheduleId}")
    long countBySchedule(@Param("scheduleId") String scheduleId);

    /** 用户姓名（考核人姓名快照；用户不存在返回 null）。 */
    @Select("SELECT u.name FROM \"qixiao-apaas\".\"t_apaas_uc_user\" u WHERE u.id = #{userId}")
    String selectUserName(@Param("userId") String userId);

    /** 一批计划中已考核的计划 id（列表批量判断按钮形态，一次 IN 查询）。 */
    @Select("<script>" +
            "SELECT a.schedule_id AS scheduleId " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_assess\" a " +
            "WHERE a.corp_code = 'hltgq' AND a.schedule_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<String> selectAssessedScheduleIds(@Param("ids") List<String> ids);

    /** 写入考核快照（id 由 Service 生成；schedule_id 唯一索引兜底防重复）。 */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_assess\" (" +
            "id, schedule_id, plan_code, plan_title, plan_site, patrolled_site, record_count, " +
            "issue_total, issue_fixed, issue_fixing, issue_open, score, grade, conclusion, " +
            "assessor_id, assessor_name, corp_code, created_at, created_by, updated_at, updated_by) VALUES (" +
            "#{id}, #{scheduleId}, #{planCode}, #{planTitle}, #{planSite}, #{patrolledSite}, #{recordCount}, " +
            "#{issueTotal}, #{issueFixed}, #{issueFixing}, #{issueOpen}, #{score}, #{grade}, #{conclusion}, " +
            "#{assessorId}, #{assessorName}, #{corpCode}, #{createdAt}, #{createdBy}, #{updatedAt}, #{updatedBy})")
    int insert(PatrolAssess assess);
}
