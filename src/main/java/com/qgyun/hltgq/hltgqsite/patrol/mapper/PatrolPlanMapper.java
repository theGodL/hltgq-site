package com.qgyun.hltgq.hltgqsite.patrol.mapper;

import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolOptionVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolScheduleDetailVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolScheduleVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolSiteVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 巡检计划管理页查询/写入（列表、详情、巡检记录、状态流转、新增、下拉候选）。
 * <p>表与列均已实证：
 * <ul>
 *   <li>计划主表 {@code t_auto_hltgq_water_patrol_schedule}（巡检人员为主表 "user" 单选列）</li>
 *   <li>巡查范围关系 {@code t_auto_hltgq_knc3g_nlbdju_tkwovk_rel}</li>
 *   <li>旧巡检人员多选关系 {@code t_auto_hltgq_knc3g_nlbdju_user_rel}（仅存量数据回退读取 + 删除时清理）</li>
 *   <li>巡检记录 {@code t_auto_hltgq_water_inspection_record}（巡检对象动态列 "_d_10497_device"）</li>
 * </ul>
 * <p>列表/详情：巡检人员优先取主表 "user"，为空时回退旧关系表拼接（单选改造前存量的 39 条计划人员数据仍在旧表）。
 */
@Mapper
public interface PatrolPlanMapper {

    // ==================== 计划列表 / 详情 ====================

    /** 计划分页总数（与 selectPage 同条件）。planType 由 Service 归一：annual / monthly / null。 */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" p " +
            "WHERE p.corp_code = 'hltgq' " +
            "<if test='name != null and name != \"\"'>AND p.title LIKE CONCAT('%', #{name}, '%') </if>" +
            "<if test='creatorId != null and creatorId != \"\"'>AND p.created_by = #{creatorId} </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND p.status = #{statusCode} </if>" +
            "<if test='planType == \"annual\"'>AND p.xhonqv IN ('#1#', '#wavn#') </if>" +
            "<if test='planType == \"monthly\"'>AND p.xhonqv = '#zjgg#' </if>" +
            "</script>")
    long countPage(@Param("name") String name,
                   @Param("creatorId") String creatorId,
                   @Param("planType") String planType,
                   @Param("statusCode") String statusCode);

    /**
     * 计划分页：按计划开始时间倒序、id 降序兜底（分页稳定）。
     * <p>创建人取主表 created_by；巡检人员取主表 "user"（存量空则回退旧多选关系表）；
     * 巡查范围按关系表聚合站点名称与站点数。
     */
    @Select("<script>" +
            "SELECT p.id AS id, p.code AS code, p.title AS title, p.xhonqv AS planType, p.content AS content, " +
            "p.start_time AS startTime, p.end_time AS endTime, p.status AS statusCode, " +
            "p.created_by AS creatorId, cu.name AS creatorName, " +
            "p.\"user\" AS inspectorId, COALESCE(iu.name, ur.inspector_names) AS inspectorName, " +
            "sr.site_names AS siteNames, COALESCE(sr.site_count, 0) AS siteCount " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" p " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" cu ON cu.id = p.created_by " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" iu ON iu.id = p.\"user\" " +
            "LEFT JOIN (SELECT rx.biz_id AS biz_id, " +
            "string_agg(COALESCE(s.zzkaec, rx.rel_id), '、' ORDER BY rx.nature_order) AS site_names, " +
            "COUNT(*) AS site_count " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_nlbdju_tkwovk_rel\" rx " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.id = rx.rel_id " +
            "WHERE rx.corp_code = 'hltgq' GROUP BY rx.biz_id) sr ON sr.biz_id = p.id " +
            "LEFT JOIN (SELECT rx2.biz_id AS biz_id, " +
            "string_agg(u2.name, '、' ORDER BY rx2.nature_order) AS inspector_names " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_nlbdju_user_rel\" rx2 " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u2 ON u2.id = rx2.rel_id " +
            "WHERE rx2.corp_code = 'hltgq' GROUP BY rx2.biz_id) ur ON ur.biz_id = p.id " +
            "WHERE p.corp_code = 'hltgq' " +
            "<if test='name != null and name != \"\"'>AND p.title LIKE CONCAT('%', #{name}, '%') </if>" +
            "<if test='creatorId != null and creatorId != \"\"'>AND p.created_by = #{creatorId} </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND p.status = #{statusCode} </if>" +
            "<if test='planType == \"annual\"'>AND p.xhonqv IN ('#1#', '#wavn#') </if>" +
            "<if test='planType == \"monthly\"'>AND p.xhonqv = '#zjgg#' </if>" +
            "ORDER BY p.start_time DESC, p.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<PatrolScheduleVO> selectPage(@Param("name") String name,
                                      @Param("creatorId") String creatorId,
                                      @Param("planType") String planType,
                                      @Param("statusCode") String statusCode,
                                      @Param("limit") long limit,
                                      @Param("offset") long offset);

    /** 计划详情（字段口径同列表 + createdAt）；不存在返回 null。 */
    @Select("SELECT p.id AS id, p.code AS code, p.title AS title, p.xhonqv AS planType, p.content AS content, " +
            "p.start_time AS startTime, p.end_time AS endTime, p.status AS statusCode, " +
            "p.created_at AS createdAt, " +
            "p.created_by AS creatorId, cu.name AS creatorName, " +
            "p.\"user\" AS inspectorId, COALESCE(iu.name, ur.inspector_names) AS inspectorName, " +
            "sr.site_names AS siteNames, COALESCE(sr.site_count, 0) AS siteCount " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" p " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" cu ON cu.id = p.created_by " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" iu ON iu.id = p.\"user\" " +
            "LEFT JOIN (SELECT rx.biz_id AS biz_id, " +
            "string_agg(COALESCE(s.zzkaec, rx.rel_id), '、' ORDER BY rx.nature_order) AS site_names, " +
            "COUNT(*) AS site_count " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_nlbdju_tkwovk_rel\" rx " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.id = rx.rel_id " +
            "WHERE rx.corp_code = 'hltgq' GROUP BY rx.biz_id) sr ON sr.biz_id = p.id " +
            "LEFT JOIN (SELECT rx2.biz_id AS biz_id, " +
            "string_agg(u2.name, '、' ORDER BY rx2.nature_order) AS inspector_names " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_nlbdju_user_rel\" rx2 " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u2 ON u2.id = rx2.rel_id " +
            "WHERE rx2.corp_code = 'hltgq' GROUP BY rx2.biz_id) ur ON ur.biz_id = p.id " +
            "WHERE p.id = #{id} AND p.corp_code = 'hltgq'")
    PatrolScheduleDetailVO selectDetail(@Param("id") String id);

    /** 计划巡查范围站点明细（关系表 LEFT JOIN 档案；站点已删仍返回 id，名称/编号为 null）。 */
    @Select("SELECT rx.rel_id AS id, s.zzkaec AS name, s.iofhpi AS code, rx.nature_order AS \"order\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_nlbdju_tkwovk_rel\" rx " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.id = rx.rel_id " +
            "WHERE rx.corp_code = 'hltgq' AND rx.biz_id = #{scheduleId} " +
            "ORDER BY rx.nature_order NULLS LAST")
    List<PatrolSiteVO> selectPlanSites(@Param("scheduleId") String scheduleId);

    /** 站点是否在该计划巡查范围内（添加记录校验）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_nlbdju_tkwovk_rel\" " +
            "WHERE corp_code = 'hltgq' AND biz_id = #{scheduleId} AND rel_id = #{siteId}")
    long countSiteInPlan(@Param("scheduleId") String scheduleId, @Param("siteId") String siteId);

    // ==================== 状态流转 / 删除 ====================

    /**
     * 批量更新状态（完成 #3# / 取消 #4#）；返回实际更新行数。
     * <p>带 status != '#1#' 条件：草稿必须先发布，不能直接完成/取消。
     */
    @Update("<script>" +
            "UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" " +
            "SET status = #{statusCode}, updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE corp_code = 'hltgq' AND status != '#1#' AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int updateStatus(@Param("ids") List<String> ids,
                     @Param("statusCode") String statusCode,
                     @Param("operatorId") String operatorId,
                     @Param("now") LocalDateTime now);

    /**
     * 发布前校验用：取计划名称/存储状态/结束日期。
     * <p>只用于发布前拦截（非草稿、已过结束日期），实际写入仍由 publishStatus 的 status='#1#' 条件兜底。
     */
    @Select("<script>" +
            "SELECT p.id AS id, p.title AS title, p.status AS statusCode, p.end_time AS endTime " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" p " +
            "WHERE p.corp_code = 'hltgq' AND p.id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<PatrolScheduleVO> selectForPublish(@Param("ids") List<String> ids);

    /**
     * 发布草稿计划：status 由 #1# 草稿 置为 #2# 已发布；带 status = '#1#' 条件保证仅草稿可发布。
     * <p>返回实际更新行数（0 表示所选计划均非草稿或已删除）。
     */
    @Update("<script>" +
            "UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" " +
            "SET status = '#2#', updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE corp_code = 'hltgq' AND status = '#1#' AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int publishStatus(@Param("ids") List<String> ids,
                      @Param("operatorId") String operatorId,
                      @Param("now") LocalDateTime now);

    /** 该批计划下的巡检记录数（>0 时拒绝删除，保护记录数据）。 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "WHERE corp_code = 'hltgq' AND patrol_schedule IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    long countRecordsBySchedules(@Param("ids") List<String> ids);

    /** 批量删除计划主表行。 */
    @Delete("<script>" +
            "DELETE FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" " +
            "WHERE corp_code = 'hltgq' AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int deleteSchedules(@Param("ids") List<String> ids);

    /** 批量删除巡查范围关系行。 */
    @Delete("<script>" +
            "DELETE FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_nlbdju_tkwovk_rel\" " +
            "WHERE corp_code = 'hltgq' AND biz_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int deleteSiteRelations(@Param("ids") List<String> ids);

    /** 批量删除旧巡检人员多选关系行（存量数据清理）。 */
    @Delete("<script>" +
            "DELETE FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_nlbdju_user_rel\" " +
            "WHERE corp_code = 'hltgq' AND biz_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int deleteUserRelations(@Param("ids") List<String> ids);

    // ==================== 新增计划 ====================

    /** 计划编号是否已存在（新增时自动编号防重）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" " +
            "WHERE code = #{code} AND corp_code = 'hltgq'")
    long countByCode(@Param("code") String code);

    /**
     * 编辑草稿计划主表字段：带 status = '#1#' 条件保证仅草稿可编辑；
     * 返回实际更新行数（0 表示计划不存在或已发布）。
     */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" " +
            "SET title = #{title}, content = #{content}, xhonqv = #{planType}, " +
            "start_time = #{startTime}, end_time = #{endTime}, \"user\" = #{inspectorId}, " +
            "updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#1#'")
    int updateDraft(@Param("id") String id,
                    @Param("title") String title,
                    @Param("content") String content,
                    @Param("planType") String planType,
                    @Param("startTime") LocalDateTime startTime,
                    @Param("endTime") LocalDateTime endTime,
                    @Param("inspectorId") String inspectorId,
                    @Param("operatorId") String operatorId,
                    @Param("now") LocalDateTime now);

    // ==================== 巡检记录 ====================

    /**
     * 一批计划中「已开始巡检」的计划 id（存在巡检记录）：列表批量推导展示态，一次 IN 查询避免逐行。
     */
    @Select("<script>" +
            "SELECT DISTINCT patrol_schedule AS scheduleId " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "WHERE corp_code = 'hltgq' AND patrol_schedule IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<String> selectStartedScheduleIds(@Param("ids") List<String> ids);

    /** 计划下的巡检记录总数（不含空白占位草稿，与 selectRecordPage 同条件）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "WHERE r.corp_code = 'hltgq' AND r.patrol_schedule = #{scheduleId} " +
            "AND NOT (r.status = '#1#' AND r.site IS NULL AND r.\"_d_10497_device\" IS NULL " +
            "AND r.result IS NULL AND r.content IS NULL)")
    long countRecords(@Param("scheduleId") String scheduleId);

    /**
     * 计划下的巡检记录分页：按巡检时间倒序、id 降序兜底；空白占位草稿（H5 开始巡检写入、四项全空）不计入。
     * <p>站点/设备/人员均 LEFT JOIN（关联行被删时本行不丢，名称列为 null）。
     */
    @Select("SELECT r.id AS id, r.\"time\" AS \"time\", r.site AS siteId, s.zzkaec AS siteName, " +
            "r.\"_d_10497_device\" AS deviceId, d.name AS deviceName, " +
            "r.\"user\" AS userId, u.name AS userName, " +
            "r.result AS resultCode, r.content AS content, r.status AS statusCode " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.id = r.site " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON d.id = r.\"_d_10497_device\" " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON u.id = r.\"user\" " +
            "WHERE r.corp_code = 'hltgq' AND r.patrol_schedule = #{scheduleId} " +
            "AND NOT (r.status = '#1#' AND r.site IS NULL AND r.\"_d_10497_device\" IS NULL " +
            "AND r.result IS NULL AND r.content IS NULL) " +
            "ORDER BY r.\"time\" DESC, r.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<PatrolRecordVO> selectRecordPage(@Param("scheduleId") String scheduleId,
                                          @Param("limit") long limit,
                                          @Param("offset") long offset);

    // ==================== 下拉候选 ====================

    /** 站点候选（名称/编号模糊；按名称升序，上限 500 条防大表）。 */
    @Select("<script>" +
            "SELECT s.id AS value, s.zzkaec AS label, s.iofhpi AS sub " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s " +
            "WHERE s.corp_code = 'hltgq' " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (s.zzkaec LIKE CONCAT('%', #{keyword}, '%') OR s.iofhpi LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY s.zzkaec ASC, s.id ASC LIMIT 500" +
            "</script>")
    List<PatrolOptionVO> selectSiteOptions(@Param("keyword") String keyword);

    /** 设备候选（按站点；名称/编号/型号，按名称升序）。 */
    @Select("SELECT d.id AS value, d.name AS label, d.code AS sub, d.evkmpl AS model " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d " +
            "WHERE d.site = #{siteId} " +
            "ORDER BY d.name ASC, d.code ASC LIMIT 500")
    List<PatrolOptionVO> selectDeviceOptions(@Param("siteId") String siteId);

    /** 用户候选（姓名/登录名模糊；按姓名升序，上限 500 条）。 */
    @Select("<script>" +
            "SELECT u.id AS value, u.name AS label, u.login_name AS sub " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_user\" u " +
            "WHERE u.corp_code = 'hltgq' AND u.name IS NOT NULL AND u.name != '' " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (u.name LIKE CONCAT('%', #{keyword}, '%') OR u.login_name LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY u.name ASC, u.id ASC LIMIT 500" +
            "</script>")
    List<PatrolOptionVO> selectUserOptions(@Param("keyword") String keyword);
}
