package com.qgyun.hltgq.hltgqsite.patrol.mapper;

import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueAdminDetailVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssuePhotoFileVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssuePhotoItemVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueRecordContextVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueRowVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 巡检问题写入/校验/管理（/patrol-issue/*，PC 巡检记录抽屉与 H5 共用，另含问题记录管理页）。
 * <p>表与列均已实证：
 * <ul>
 *   <li>问题主表 {@code t_auto_hltgq_knc3g_bpzjoh}（site/device/"user"/time/abqezf/work_order_id/ezuvil 均为单值列）</li>
 *   <li>站点档案 {@code t_auto_hltgq_5nw74_vnqqef}、设备 {@code t_auto_hltgq_water_device}、
 *       人员 {@code t_apaas_uc_user}、巡检记录 {@code t_auto_hltgq_water_inspection_record}、
 *       工单 {@code t_auto_hltgq_water_work_order}</li>
 *   <li>现场照片自建表 {@code t_auto_hltgq_water_patrol_issue_photo}（不写平台 jmltds 关系表）</li>
 * </ul>
 */
@Mapper
public interface PatrolIssueMapper {

    // ==================== 前置校验 ====================

    /** 站点是否存在。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" " +
            "WHERE id = #{id} AND corp_code = 'hltgq'")
    long countSite(@Param("id") String id);

    /** 设备是否存在且属于该站点。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" " +
            "WHERE id = #{deviceId} AND site = #{siteId}")
    long countDeviceInSite(@Param("siteId") String siteId, @Param("deviceId") String deviceId);

    /** 发现人是否存在。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_apaas_uc_user\" " +
            "WHERE id = #{id} AND corp_code = 'hltgq'")
    long countUser(@Param("id") String id);

    /** 问题编号是否已存在（生成时防重）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "WHERE code = #{code} AND corp_code = 'hltgq'")
    long countByCode(@Param("code") String code);

    /** 问题记录是否存在（照片列表查询前置校验）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "WHERE id = #{id} AND corp_code = 'hltgq'")
    long countIssue(@Param("id") String id);

    /**
     * 巡检记录上下文：站点/设备/时间与所属计划（新增问题页回填）。
     * <p>站点/设备 LEFT JOIN，关联档案被删时名称列为 null、id 仍返回。
     */
    @Select("SELECT r.id AS recordId, r.patrol_schedule AS scheduleId, r.site AS siteId, " +
            "s.zzkaec AS siteName, r.\"_d_10497_device\" AS deviceId, d.name AS deviceName, " +
            "to_char(r.\"time\", 'YYYY-MM-DD HH24:MI:SS') AS recordTime " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.id = r.site " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON d.id = r.\"_d_10497_device\" " +
            "WHERE r.id = #{recordId} AND r.corp_code = 'hltgq'")
    PatrolIssueRecordContextVO selectRecordContext(@Param("recordId") String recordId);

    // ==================== 问题写入 ====================

    /**
     * 新增问题记录：handle_status/status 由 Service 固定（#1#），time 为发现时间，
     * abqezf 为关联巡检记录 id（可空），risk_level 可空。
     */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "(\"id\", \"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\", " +
            "\"code\", \"title\", \"site\", \"device\", \"content\", \"user\", \"time\", " +
            "\"handle_status\", \"status\", \"risk_level\", \"abqezf\") " +
            "VALUES (#{id}, #{corpCode}, #{now}, #{operatorId}, #{now}, #{operatorId}, " +
            "#{code}, #{title}, #{siteId}, #{deviceId}, #{content}, #{userId}, #{time}, " +
            "#{handleStatus}, #{status}, #{riskLevel}, #{recordId})")
    int insertIssue(@Param("id") String id,
                    @Param("corpCode") String corpCode,
                    @Param("operatorId") String operatorId,
                    @Param("now") LocalDateTime now,
                    @Param("code") String code,
                    @Param("title") String title,
                    @Param("siteId") String siteId,
                    @Param("deviceId") String deviceId,
                    @Param("content") String content,
                    @Param("userId") String userId,
                    @Param("time") LocalDateTime time,
                    @Param("handleStatus") String handleStatus,
                    @Param("status") String status,
                    @Param("riskLevel") String riskLevel,
                    @Param("recordId") String recordId);

    // ==================== 现场照片（自建表） ====================

    /** 上传成功即写照片行（issue_id / nature_order 为空，提交问题时回填）。 */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_issue_photo\" " +
            "(\"id\", \"issue_id\", \"file_name\", \"file_path\", \"file_size\", \"file_ext\", \"nature_order\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.issueId}, #{row.fileName}, #{row.filePath}, #{row.fileSize}, #{row.fileExt}, " +
            "#{row.natureOrder}, #{row.corpCode}, #{row.now}, #{row.operatorId}, #{row.now}, #{row.operatorId})")
    int insertPhoto(@Param("row") Map<String, Object> row);

    /** 按 id 取照片行（下载 / 归属校验 / 移除）。 */
    @Select("SELECT id, issue_id AS issueId, file_name AS fileName, file_path AS filePath, " +
            "file_size AS fileSize, file_ext AS fileExt, created_by AS createdBy " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_issue_photo\" WHERE id = #{id}")
    PatrolIssuePhotoFileVO selectPhoto(@Param("id") String id);

    /** 按问题 id 取照片列表（H5 / PC 回显，按提交时的上传顺序）。 */
    @Select("SELECT id, file_name AS \"name\", file_size AS \"size\", file_ext AS \"ext\", " +
            "nature_order AS natureOrder " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_issue_photo\" " +
            "WHERE issue_id = #{issueId} ORDER BY nature_order NULLS LAST, created_at")
    List<PatrolIssuePhotoItemVO> selectPhotosByIssue(@Param("issueId") String issueId);

    /**
     * 绑定照片到问题并写排序：带 created_by = 当前操作人、issue_id IS NULL 条件，
     * 防止绑定他人照片或重复绑定；返回 0 表示校验失败（事务整体回滚）。
     */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_issue_photo\" " +
            "SET issue_id = #{issueId}, nature_order = #{order}, updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND created_by = #{operatorId} AND issue_id IS NULL")
    int bindPhoto(@Param("id") String id,
                  @Param("issueId") String issueId,
                  @Param("order") int order,
                  @Param("operatorId") String operatorId,
                  @Param("now") LocalDateTime now);

    /** 移除未提交的照片行（已绑定问题的不允许移除）。 */
    @Delete("DELETE FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_issue_photo\" " +
            "WHERE id = #{id} AND created_by = #{operatorId} AND issue_id IS NULL")
    int deleteUnboundPhoto(@Param("id") String id, @Param("operatorId") String operatorId);

    // ==================== 管理页：分页 / 详情 ====================

    /** 问题分页总数（标题模糊 + 所属站点 + 状态）。 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i " +
            "WHERE i.corp_code = 'hltgq' " +
            "<if test='title != null and title != \"\"'>AND i.title LIKE CONCAT('%', #{title}, '%') </if>" +
            "<if test='siteId != null and siteId != \"\"'>AND i.site = #{siteId} </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND i.status = #{statusCode} </if>" +
            "</script>")
    long countAdminPage(@Param("title") String title,
                        @Param("siteId") String siteId,
                        @Param("statusCode") String statusCode);

    /**
     * 问题分页（按发现时间倒序）：站点/设备/发现人/关联工单/关联巡检记录均 LEFT JOIN，
     * 关联行被删时名称列为 null、id 仍返回。
     */
    @Select("<script>" +
            "SELECT i.id AS id, i.code AS code, i.title AS title, " +
            "i.site AS siteId, s.zzkaec AS siteName, i.device AS deviceId, d.name AS deviceName, " +
            "i.\"user\" AS userId, u.name AS userName, " +
            "to_char(i.\"time\", 'YYYY-MM-DD HH24:MI:SS') AS \"time\", " +
            "i.handle_status AS handleCode, i.status AS statusCode, i.ezuvil AS result, " +
            "i.work_order_id AS workOrderId, w.code AS workOrderCode, " +
            "i.abqezf AS recordId, to_char(r.\"time\", 'YYYY-MM-DD HH24:MI:SS') AS recordTime " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.id = i.site " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON d.id = i.device " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON u.id = i.\"user\" " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" w ON w.id = i.work_order_id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r ON r.id = i.abqezf " +
            "WHERE i.corp_code = 'hltgq' " +
            "<if test='title != null and title != \"\"'>AND i.title LIKE CONCAT('%', #{title}, '%') </if>" +
            "<if test='siteId != null and siteId != \"\"'>AND i.site = #{siteId} </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND i.status = #{statusCode} </if>" +
            "ORDER BY i.\"time\" DESC NULLS LAST, i.id DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<PatrolIssueRowVO> selectAdminPage(@Param("title") String title,
                                          @Param("siteId") String siteId,
                                          @Param("statusCode") String statusCode,
                                          @Param("limit") long limit,
                                          @Param("offset") long offset);

    /** 问题详情（查看弹窗）：含问题描述、风险等级与关联工单标题。 */
    @Select("SELECT i.id AS id, i.code AS code, i.title AS title, i.content AS content, " +
            "i.site AS siteId, s.zzkaec AS siteName, i.device AS deviceId, d.name AS deviceName, " +
            "i.\"user\" AS userId, u.name AS userName, " +
            "to_char(i.\"time\", 'YYYY-MM-DD HH24:MI:SS') AS \"time\", " +
            "i.handle_status AS handleCode, i.status AS statusCode, i.risk_level AS riskCode, " +
            "i.ezuvil AS result, " +
            "i.work_order_id AS workOrderId, w.code AS workOrderCode, w.title AS workOrderTitle, " +
            "w.status AS workOrderStatusCode, " +
            "i.abqezf AS recordId, to_char(r.\"time\", 'YYYY-MM-DD HH24:MI:SS') AS recordTime " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.id = i.site " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON d.id = i.device " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON u.id = i.\"user\" " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" w ON w.id = i.work_order_id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r ON r.id = i.abqezf " +
            "WHERE i.id = #{id} AND i.corp_code = 'hltgq'")
    PatrolIssueAdminDetailVO selectAdminDetail(@Param("id") String id);

    // ==================== 管理页：状态流转 ====================

    /** 作废：待处理 #1# → 已作废 #5#（条件更新，返回 0 表示状态已变化）。 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "SET status = '#5#', updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#1#'")
    int voidIssue(@Param("id") String id,
                  @Param("operatorId") String operatorId,
                  @Param("now") LocalDateTime now);

    /** 关闭问题：处理中 #2# → 已关闭 #4#。 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "SET status = '#4#', updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#2#'")
    int closeIssue(@Param("id") String id,
                   @Param("operatorId") String operatorId,
                   @Param("now") LocalDateTime now);

    /** 直接处理：待处理 #1# → 处理中 #2#（handle_status 置为直接处理 #3#）。 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "SET handle_status = '#3#', status = '#2#', updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#1#'")
    int startHandle(@Param("id") String id,
                    @Param("operatorId") String operatorId,
                    @Param("now") LocalDateTime now);

    /** 填写处理结果：仅处理中 #2# 可写（状态不变，等待关闭）。 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "SET ezuvil = #{result}, updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#2#'")
    int saveResult(@Param("id") String id,
                   @Param("result") String result,
                   @Param("operatorId") String operatorId,
                   @Param("now") LocalDateTime now);

    /** 转工单：待处理 #1# → 已转工单 #3#（handle_status 置为转工单 #2#，回写工单 id）。 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "SET handle_status = '#2#', status = '#3#', work_order_id = #{workOrderId}, " +
            "updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#1#'")
    int transferIssue(@Param("id") String id,
                      @Param("workOrderId") String workOrderId,
                      @Param("operatorId") String operatorId,
                      @Param("now") LocalDateTime now);

    /**
     * 工单取消/删除后回退关联问题：清空 work_order_id，已转工单 #3# → 待处理 #1#，
     * handle_status 回到待确认 #1#（与新增问题默认一致）。
     */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "SET work_order_id = NULL, handle_status = '#1#', status = '#1#', " +
            "updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE corp_code = 'hltgq' AND work_order_id = #{workOrderId} AND status = '#3#'")
    int resetIssuesByWorkOrder(@Param("workOrderId") String workOrderId,
                               @Param("operatorId") String operatorId,
                               @Param("now") LocalDateTime now);
}
