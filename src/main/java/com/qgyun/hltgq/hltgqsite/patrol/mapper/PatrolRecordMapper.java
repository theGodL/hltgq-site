package com.qgyun.hltgq.hltgqsite.patrol.mapper;

import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolOptionVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordDetailVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordIssueItemVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordPhotoFileVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordPhotoItemVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordRowVO;
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
 * 巡检记录管理页查询/写入（/patrol-record/*，patrol-record-list.html + patrol-record-add.html）。
 * <p>表与列均已实证：
 * <ul>
 *   <li>记录主表 {@code t_auto_hltgq_water_inspection_record}：物理列 time/patrol_schedule/site/
 *       "_d_10497_device"/"user"/result/content/status/szeedc（平台多值字段 imjjpx、tzxlun、site_image 无物理列）</li>
 *   <li>计划 {@code t_auto_hltgq_water_patrol_schedule}、站点 {@code t_auto_hltgq_5nw74_vnqqef}、
 *       设备 {@code t_auto_hltgq_water_device}、人员 {@code t_apaas_uc_user}</li>
 *   <li>现场照片自建表 {@code t_auto_hltgq_water_patrol_record_photo}（不写平台文件关系表）</li>
 *   <li>关联问题 {@code t_auto_hltgq_knc3g_bpzjoh}（abqezf = 记录 id）</li>
 * </ul>
 */
@Mapper
public interface PatrolRecordMapper {

    // ==================== 列表 / 详情 ====================

    /** 记录分页总数（与 selectPage 同条件；时间区间按自然日，endTime 含当天）；空白占位草稿不计入。 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "WHERE r.corp_code = 'hltgq' " +
            "AND NOT (r.status = '#1#' AND r.site IS NULL AND r.\"_d_10497_device\" IS NULL " +
            "AND r.result IS NULL AND r.content IS NULL) " +
            "<if test='startTime != null and startTime != \"\"'>" +
            "AND r.\"time\" &gt;= CAST(#{startTime} AS timestamp) </if>" +
            "<if test='endTime != null and endTime != \"\"'>" +
            "AND r.\"time\" &lt; CAST(#{endTime} AS timestamp) + interval '1 day' </if>" +
            "<if test='siteId != null and siteId != \"\"'>AND r.site = #{siteId} </if>" +
            "</script>")
    long countPage(@Param("startTime") String startTime,
                   @Param("endTime") String endTime,
                   @Param("siteId") String siteId);

    /**
     * 记录分页：按巡检时间倒序、id 降序兜底；空白占位草稿（H5 开始巡检写入、站点/设备/结果/内容全空）不计入。
     * <p>计划/站点/设备/人员均 LEFT JOIN（关联行被删时本行不丢，名称列为 null）；
     * 照片数与问题数走聚合子查询，避免 JOIN 放大行数。
     */
    @Select("<script>" +
            "SELECT r.id AS id, to_char(r.\"time\", 'YYYY-MM-DD HH24:MI') AS \"time\", " +
            "r.patrol_schedule AS scheduleId, p.title AS scheduleTitle, " +
            "r.site AS siteId, s.zzkaec AS siteName, " +
            "r.\"_d_10497_device\" AS deviceId, d.name AS deviceName, " +
            "r.\"user\" AS userId, u.name AS userName, " +
            "r.result AS resultCode, r.content AS content, r.status AS statusCode, " +
            "COALESCE(ph.cnt, 0) AS photoCount, COALESCE(isu.cnt, 0) AS issueCount " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" p ON p.id = r.patrol_schedule " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.id = r.site " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON d.id = r.\"_d_10497_device\" " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON u.id = r.\"user\" " +
            "LEFT JOIN (SELECT record_id, COUNT(*) AS cnt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_record_photo\" " +
            "WHERE corp_code = 'hltgq' AND record_id IS NOT NULL GROUP BY record_id) ph ON ph.record_id = r.id " +
            "LEFT JOIN (SELECT abqezf, COUNT(*) AS cnt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "WHERE corp_code = 'hltgq' AND abqezf IS NOT NULL GROUP BY abqezf) isu ON isu.abqezf = r.id " +
            "WHERE r.corp_code = 'hltgq' " +
            "AND NOT (r.status = '#1#' AND r.site IS NULL AND r.\"_d_10497_device\" IS NULL " +
            "AND r.result IS NULL AND r.content IS NULL) " +
            "<if test='startTime != null and startTime != \"\"'>" +
            "AND r.\"time\" &gt;= CAST(#{startTime} AS timestamp) </if>" +
            "<if test='endTime != null and endTime != \"\"'>" +
            "AND r.\"time\" &lt; CAST(#{endTime} AS timestamp) + interval '1 day' </if>" +
            "<if test='siteId != null and siteId != \"\"'>AND r.site = #{siteId} </if>" +
            "ORDER BY r.\"time\" DESC, r.id DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<PatrolRecordRowVO> selectPage(@Param("startTime") String startTime,
                                       @Param("endTime") String endTime,
                                       @Param("siteId") String siteId,
                                       @Param("limit") long limit,
                                       @Param("offset") long offset);

    /** 记录详情（编辑回填 / 查看弹窗；不存在返回 null）。 */
    @Select("SELECT r.id AS id, to_char(r.\"time\", 'YYYY-MM-DD HH24:MI') AS \"time\", " +
            "r.patrol_schedule AS scheduleId, p.title AS scheduleTitle, " +
            "r.site AS siteId, s.zzkaec AS siteName, " +
            "r.\"_d_10497_device\" AS deviceId, d.name AS deviceName, " +
            "r.\"user\" AS userId, u.name AS userName, " +
            "r.result AS resultCode, r.content AS content, r.szeedc AS cost, r.status AS statusCode " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" p ON p.id = r.patrol_schedule " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.id = r.site " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON d.id = r.\"_d_10497_device\" " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON u.id = r.\"user\" " +
            "WHERE r.id = #{id} AND r.corp_code = 'hltgq'")
    PatrolRecordDetailVO selectDetail(@Param("id") String id);

    /** 记录下的关联问题（问题表 abqezf；按发现时间倒序，上限 100 条）。 */
    @Select("SELECT id, code, title, content, to_char(\"time\", 'YYYY-MM-DD HH24:MI') AS \"time\", " +
            "status AS statusCode, handle_status AS handleStatusCode, risk_level AS riskLevel " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "WHERE corp_code = 'hltgq' AND abqezf = #{recordId} " +
            "ORDER BY \"time\" DESC, id DESC LIMIT 100")
    List<PatrolRecordIssueItemVO> selectIssuesByRecord(@Param("recordId") String recordId);

    // ==================== 前置校验 ====================

    /** 计划是否存在（编辑时允许保持原计划，不要求仍为已发布）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" " +
            "WHERE id = #{id} AND corp_code = 'hltgq'")
    long countSchedule(@Param("id") String id);

    /** 计划是否为已发布（#2# 进行中；新增记录仅可选此类计划）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#2#'")
    long countPublishedSchedule(@Param("id") String id);

    /** 记录下的问题数（>0 时禁止删除记录，保护已上报问题）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "WHERE corp_code = 'hltgq' AND abqezf = #{recordId}")
    long countIssueByRecord(@Param("recordId") String recordId);

    /** 记录下已绑定的现场照片数（提交前完整性校验）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_record_photo\" " +
            "WHERE corp_code = 'hltgq' AND record_id = #{recordId}")
    long countPhotosByRecord(@Param("recordId") String recordId);

    // ==================== 空白占位草稿（幂等提交） ====================

    /**
     * 计划下最早一条空白占位草稿 id：草稿 #1# 且站点/设备/结果/内容全空（H5 开始巡检写入的占位行）。
     * <p>提交入口优先填充该行，避免「草稿 + 正式」两条；无空白草稿返回 null。
     */
    @Select("SELECT id FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "WHERE corp_code = 'hltgq' AND patrol_schedule = #{scheduleId} AND status = '#1#' " +
            "AND site IS NULL AND \"_d_10497_device\" IS NULL AND result IS NULL AND content IS NULL " +
            "ORDER BY created_at ASC NULLS LAST, id ASC LIMIT 1")
    String selectBlankDraftId(@Param("scheduleId") String scheduleId);

    /**
     * 认领并填充空白占位草稿（幂等提交）：带 status = '#1#' + 四项全空条件，
     * 仅当该行仍是未被填充的占位草稿时更新成功；返回 0 表示已被并发认领（调用方退回新增）。
     */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "SET \"time\" = #{time}, \"patrol_schedule\" = #{scheduleId}, \"site\" = #{siteId}, " +
            "\"_d_10497_device\" = #{deviceId}, \"user\" = #{userId}, \"result\" = #{result}, " +
            "\"content\" = #{content}, \"szeedc\" = #{cost}, \"status\" = #{statusCode}, " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#1#' " +
            "AND site IS NULL AND \"_d_10497_device\" IS NULL AND result IS NULL AND content IS NULL")
    int claimBlankDraft(@Param("id") String id,
                        @Param("operatorId") String operatorId,
                        @Param("now") LocalDateTime now,
                        @Param("time") LocalDateTime time,
                        @Param("scheduleId") String scheduleId,
                        @Param("siteId") String siteId,
                        @Param("deviceId") String deviceId,
                        @Param("userId") String userId,
                        @Param("result") String result,
                        @Param("content") String content,
                        @Param("statusCode") String statusCode,
                        @Param("cost") Double cost);

    /**
     * 清理计划下其余空白占位草稿（保留 keepId）：只删仍为 #1# 且四项全空的行，
     * 已有照片/问题关联的不动（防误删异常数据）。
     */
    @Delete("DELETE FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "WHERE r.corp_code = 'hltgq' AND r.patrol_schedule = #{scheduleId} AND r.status = '#1#' " +
            "AND r.site IS NULL AND r.\"_d_10497_device\" IS NULL AND r.result IS NULL AND r.content IS NULL " +
            "AND r.id <> #{keepId} " +
            "AND NOT EXISTS (SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_record_photo\" ph " +
            "WHERE ph.record_id = r.id) " +
            "AND NOT EXISTS (SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" isu " +
            "WHERE isu.abqezf = r.id)")
    int deleteBlankDrafts(@Param("scheduleId") String scheduleId, @Param("keepId") String keepId);

    // ==================== 写入 ====================

    /**
     * 新增巡检记录（状态由 Service 固定 #1# 草稿）：巡检对象写平台动态列 "_d_10497_device"，
     * 成本金额写 szeedc，审计列取当前操作人。
     */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "(\"id\", \"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\", " +
            "\"time\", \"patrol_schedule\", \"site\", \"_d_10497_device\", \"user\", \"result\", \"content\", \"status\", \"szeedc\") " +
            "VALUES (#{id}, #{corpCode}, #{now}, #{operatorId}, #{now}, #{operatorId}, " +
            "#{time}, #{scheduleId}, #{siteId}, #{deviceId}, #{userId}, #{result}, #{content}, #{statusCode}, #{cost})")
    int insertRecord(@Param("id") String id,
                     @Param("corpCode") String corpCode,
                     @Param("operatorId") String operatorId,
                     @Param("now") LocalDateTime now,
                     @Param("time") LocalDateTime time,
                     @Param("scheduleId") String scheduleId,
                     @Param("siteId") String siteId,
                     @Param("deviceId") String deviceId,
                     @Param("userId") String userId,
                     @Param("result") String result,
                     @Param("content") String content,
                     @Param("statusCode") String statusCode,
                     @Param("cost") Double cost);

    /**
     * 编辑记录（仅草稿）：带 status = '#1#' 条件，返回 0 表示记录不存在或已提交。
     */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "SET \"time\" = #{time}, \"patrol_schedule\" = #{scheduleId}, \"site\" = #{siteId}, " +
            "\"_d_10497_device\" = #{deviceId}, \"user\" = #{userId}, \"result\" = #{result}, " +
            "\"content\" = #{content}, \"szeedc\" = #{cost}, " +
            "updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#1#'")
    int updateDraft(@Param("id") String id,
                    @Param("operatorId") String operatorId,
                    @Param("now") LocalDateTime now,
                    @Param("time") LocalDateTime time,
                    @Param("scheduleId") String scheduleId,
                    @Param("siteId") String siteId,
                    @Param("deviceId") String deviceId,
                    @Param("userId") String userId,
                    @Param("result") String result,
                    @Param("content") String content,
                    @Param("cost") Double cost);

    /** 提交记录：草稿 #1# → 已提交 #2#；返回 0 表示记录不存在或已提交。 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "SET status = '#2#', updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#1#'")
    int submit(@Param("id") String id,
               @Param("operatorId") String operatorId,
               @Param("now") LocalDateTime now);

    /** 删除记录（仅草稿）。 */
    @Delete("DELETE FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#1#'")
    int deleteDraft(@Param("id") String id);

    // ==================== 下拉候选 ====================

    /**
     * 所属计划候选：仅已发布 #2#（进行中）；includeId 用于编辑时保留当前计划
     * （原计划可能已完成/取消，不能因为不在候选中而丢掉回填）。
     */
    @Select("<script>" +
            "SELECT p.id AS value, p.title AS label, p.code AS sub " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" p " +
            "WHERE p.corp_code = 'hltgq' AND (p.status = '#2#' " +
            "<if test='includeId != null and includeId != \"\"'>OR p.id = #{includeId} </if>) " +
            "ORDER BY p.start_time DESC, p.id DESC LIMIT 500" +
            "</script>")
    List<PatrolOptionVO> selectPlanOptions(@Param("includeId") String includeId);

    // ==================== 现场照片（自建表） ====================

    /** 上传成功即写照片行（record_id / nature_order 为空，保存记录时回填）。 */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_record_photo\" " +
            "(\"id\", \"record_id\", \"file_name\", \"file_path\", \"file_size\", \"file_ext\", \"nature_order\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.recordId}, #{row.fileName}, #{row.filePath}, #{row.fileSize}, " +
            "#{row.fileExt}, #{row.natureOrder}, #{row.corpCode}, #{row.now}, #{row.operatorId}, " +
            "#{row.now}, #{row.operatorId})")
    int insertPhoto(@Param("row") Map<String, Object> row);

    /** 按 id 取照片行（预览 / 归属校验 / 移除）。 */
    @Select("SELECT id, record_id AS recordId, file_name AS fileName, file_path AS filePath, " +
            "file_size AS fileSize, file_ext AS fileExt, nature_order AS natureOrder, created_by AS createdBy " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_record_photo\" WHERE id = #{id}")
    PatrolRecordPhotoFileVO selectPhoto(@Param("id") String id);

    /** 按记录 id 取照片列表（详情回显 / 编辑保留判断，按保存时的顺序）。 */
    @Select("SELECT id, record_id AS recordId, file_name AS \"name\", file_size AS \"size\", " +
            "file_ext AS \"ext\", nature_order AS natureOrder " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_record_photo\" " +
            "WHERE record_id = #{recordId} ORDER BY nature_order NULLS LAST, created_at")
    List<PatrolRecordPhotoItemVO> selectPhotosByRecord(@Param("recordId") String recordId);

    /** 按记录 id 取照片行（编辑差异比对 + 删除时物理清文件用，含 filePath/createdBy）。 */
    @Select("SELECT id, record_id AS recordId, file_name AS fileName, file_path AS filePath, " +
            "file_size AS fileSize, file_ext AS fileExt, nature_order AS natureOrder, created_by AS createdBy " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_record_photo\" " +
            "WHERE record_id = #{recordId} ORDER BY nature_order NULLS LAST, created_at")
    List<PatrolRecordPhotoFileVO> selectPhotoRowsByRecord(@Param("recordId") String recordId);

    /**
     * 绑定照片到记录并写排序：带 created_by = 当前操作人、record_id IS NULL 条件，
     * 防止绑定他人照片或重复绑定；返回 0 表示校验失败（事务整体回滚）。
     */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_record_photo\" " +
            "SET record_id = #{recordId}, nature_order = #{order}, updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND created_by = #{operatorId} AND record_id IS NULL")
    int bindPhoto(@Param("id") String id,
                  @Param("recordId") String recordId,
                  @Param("order") int order,
                  @Param("operatorId") String operatorId,
                  @Param("now") LocalDateTime now);

    /** 重排已绑定照片顺序（编辑保存后顺序按前端列表）。 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_record_photo\" " +
            "SET nature_order = #{order}, updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND record_id = #{recordId}")
    int updatePhotoOrder(@Param("id") String id,
                         @Param("recordId") String recordId,
                         @Param("order") int order,
                         @Param("operatorId") String operatorId,
                         @Param("now") LocalDateTime now);

    /** 移除记录下已绑定的照片行（编辑去掉的照片 / 删除记录时清理；文件由 Service 物理删除）。 */
    @Delete("DELETE FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_record_photo\" " +
            "WHERE id = #{id} AND record_id = #{recordId}")
    int deleteBoundPhoto(@Param("id") String id, @Param("recordId") String recordId);

    /** 移除未绑定的照片行（误传清理；已绑定记录的不允许移除）。 */
    @Delete("DELETE FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_record_photo\" " +
            "WHERE id = #{id} AND created_by = #{operatorId} AND record_id IS NULL")
    int deleteUnboundPhoto(@Param("id") String id, @Param("operatorId") String operatorId);
}
