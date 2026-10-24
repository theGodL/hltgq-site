package com.qgyun.hltgq.hltgqsite.workorder.mapper;

import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderDetailVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderListVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderOrgVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderRowVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工单管理（/work-order/*，work-order-list.html + work-order-export.html）。
 * <p>表与列均已实证：工单主表 t_auto_hltgq_water_work_order 物理列含
 * code/title/qjulvf/content/site/device/org/user/time/result/status/alert/pyxcen，
 * <b>无 azgquf（关联问题）/file（附件）/xdkvyp（图片）物理列</b>；
 * 关联问题改由问题记录表 work_order_id 反查（与站点详情同口径）。
 * <p>枚举口径：qjulvf 9 档；status #1# 待指派、#2# 处理中、#3# 已关闭、#4# 已取消、#iizl# 已逾期。
 * <p>"time"/"user" 为关键字列需双引号；org 存量兼容部门 id 与短码 code 双写法。
 */
public interface WorkOrderMapper {

    /**
     * 工单列表（导出页，裸 List）：按创建时间倒序（id 兜底）。
     * <p>site 参数支持 站点 id / 站点编号 iofhpi / 站点名称 zzkaec 三写法；
     * 处理时间过滤按 pyxcen 半开区间 [startTime, endTime)。
     */
    @Select("<script>" +
            "SELECT w.id AS id, w.code AS code, w.title AS title, w.qjulvf AS typeCode, " +
            "w.content AS content, w.site AS siteId, s.zzkaec AS siteName, " +
            "w.device AS deviceId, d.name AS deviceName, w.org AS orgId, o.name AS orgName, " +
            "w.\"user\" AS userId, u.name AS userName, w.\"time\" AS \"time\", " +
            "w.pyxcen AS handleTime, w.result AS result, w.status AS statusCode, " +
            "(SELECT i.id FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i WHERE i.work_order_id = w.id LIMIT 1) AS issueId, " +
            "(SELECT string_agg(i.title, '、') FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i WHERE i.work_order_id = w.id) AS issueTitle " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" w " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON w.site = s.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON w.device = d.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o ON (o.id = w.org OR o.code = w.org) AND o.corp_code = 'hltgq' " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON w.\"user\" = u.id " +
            "WHERE w.corp_code = 'hltgq' " +
            "<if test='site != null and site != \"\"'>AND (w.site = #{site} OR s.zzkaec = #{site} OR s.iofhpi = #{site}) </if>" +
            "<if test='startTime != null'>AND w.pyxcen &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND w.pyxcen &lt; #{endTime} </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND w.status = #{statusCode} </if>" +
            "ORDER BY w.created_at DESC, w.id DESC" +
            "</script>")
    List<WorkOrderListVO> selectWorkOrderList(@Param("site") String site,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime,
                                              @Param("statusCode") String statusCode);

    // ==================== 管理页：分页 / 详情 ====================

    /** 工单分页总数（站点 id/编号/名称 + 处理时间区间 + 状态）。 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" w " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON w.site = s.id " +
            "WHERE w.corp_code = 'hltgq' " +
            "<if test='site != null and site != \"\"'>AND (w.site = #{site} OR s.zzkaec = #{site} OR s.iofhpi = #{site}) </if>" +
            "<if test='startTime != null'>AND w.pyxcen &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND w.pyxcen &lt; #{endTime} </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND w.status = #{statusCode} </if>" +
            "</script>")
    long countPage(@Param("site") String site,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime,
                   @Param("statusCode") String statusCode);

    /**
     * 工单分页（按创建时间倒序）：站点/设备/部门/人员 LEFT JOIN，关联问题按问题表反查。
     * <p>时间列统一 to_char 为 yyyy-MM-dd HH:mm:ss，前端直接展示。
     */
    @Select("<script>" +
            "SELECT w.id AS id, w.code AS code, w.title AS title, w.qjulvf AS typeCode, " +
            "w.content AS content, w.site AS siteId, s.zzkaec AS siteName, " +
            "w.device AS deviceId, d.name AS deviceName, w.org AS orgId, o.name AS orgName, " +
            "w.\"user\" AS userId, u.name AS userName, " +
            "to_char(w.\"time\", 'YYYY-MM-DD HH24:MI:SS') AS \"time\", " +
            "to_char(w.pyxcen, 'YYYY-MM-DD HH24:MI:SS') AS handleTime, w.result AS result, " +
            "w.status AS statusCode, " +
            "(SELECT i.id FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i WHERE i.work_order_id = w.id LIMIT 1) AS issueId, " +
            "(SELECT string_agg(i.title, '、') FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i WHERE i.work_order_id = w.id) AS issueTitle " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" w " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON w.site = s.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON w.device = d.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o ON (o.id = w.org OR o.code = w.org) AND o.corp_code = 'hltgq' " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON w.\"user\" = u.id " +
            "WHERE w.corp_code = 'hltgq' " +
            "<if test='site != null and site != \"\"'>AND (w.site = #{site} OR s.zzkaec = #{site} OR s.iofhpi = #{site}) </if>" +
            "<if test='startTime != null'>AND w.pyxcen &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND w.pyxcen &lt; #{endTime} </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND w.status = #{statusCode} </if>" +
            "ORDER BY w.created_at DESC, w.id DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<WorkOrderRowVO> selectPage(@Param("site") String site,
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime,
                                    @Param("statusCode") String statusCode,
                                    @Param("limit") long limit,
                                    @Param("offset") long offset);

    /** 工单详情（查看弹窗 / 编辑回填）。 */
    @Select("SELECT w.id AS id, w.code AS code, w.title AS title, w.qjulvf AS typeCode, " +
            "w.content AS content, w.site AS siteId, s.zzkaec AS siteName, " +
            "w.device AS deviceId, d.name AS deviceName, w.org AS orgId, o.name AS orgName, " +
            "w.\"user\" AS userId, u.name AS userName, " +
            "to_char(w.\"time\", 'YYYY-MM-DD HH24:MI:SS') AS \"time\", " +
            "w.result AS result, w.status AS statusCode, " +
            "to_char(w.pyxcen, 'YYYY-MM-DD HH24:MI:SS') AS handleTime, " +
            "to_char(w.created_at, 'YYYY-MM-DD HH24:MI:SS') AS createdAt, " +
            "(SELECT i.id FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i WHERE i.work_order_id = w.id LIMIT 1) AS issueId, " +
            "(SELECT string_agg(i.title, '、') FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i WHERE i.work_order_id = w.id) AS issueTitle " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" w " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON w.site = s.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON w.device = d.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o ON (o.id = w.org OR o.code = w.org) AND o.corp_code = 'hltgq' " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON w.\"user\" = u.id " +
            "WHERE w.id = #{id} AND w.corp_code = 'hltgq'")
    WorkOrderDetailVO selectDetail(@Param("id") String id);

    // ==================== 下拉选项 ====================

    /** 负责部门候选（按短码、名称排序）。 */
    @Select("SELECT id AS id, code AS code, name AS name FROM \"qixiao-apaas\".\"t_apaas_uc_org\" " +
            "WHERE corp_code = 'hltgq' ORDER BY code NULLS LAST, name")
    List<WorkOrderOrgVO> selectOrgOptions();

    // ==================== 前置校验 ====================

    /** 工单编号是否已存在（生成时防重）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" " +
            "WHERE code = #{code} AND corp_code = 'hltgq'")
    long countByCode(@Param("code") String code);

    // ==================== 写入 / 状态流转 ====================

    /**
     * 新建工单（转工单）：result/pyxcen 为空，状态由 Service 固定 #1# 待指派。
     * <p>row 键：id/corpCode/now/operatorId/code/title/typeCode/content/siteId/deviceId/orgId/userId/time/status。
     */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" " +
            "(\"id\", \"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\", " +
            "\"code\", \"title\", \"qjulvf\", \"content\", \"site\", \"device\", \"org\", \"user\", " +
            "\"time\", \"result\", \"status\", \"pyxcen\") " +
            "VALUES (#{row.id}, #{row.corpCode}, #{row.now}, #{row.operatorId}, #{row.now}, #{row.operatorId}, " +
            "#{row.code}, #{row.title}, #{row.typeCode}, #{row.content}, #{row.siteId}, #{row.deviceId}, " +
            "#{row.orgId}, #{row.userId}, #{row.time}, NULL, #{row.status}, NULL)")
    int insertWorkOrder(@Param("row") Map<String, Object> row);

    /** 编辑工单：仅待指派 #1# 可改；org/user/time 可清空（传 null）。 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" " +
            "SET title = #{title}, qjulvf = #{typeCode}, content = #{content}, site = #{siteId}, " +
            "device = #{deviceId}, org = #{orgId}, \"user\" = #{userId}, \"time\" = #{time}, " +
            "updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#1#'")
    int updateWorkOrder(@Param("id") String id,
                        @Param("title") String title,
                        @Param("typeCode") String typeCode,
                        @Param("content") String content,
                        @Param("siteId") String siteId,
                        @Param("deviceId") String deviceId,
                        @Param("orgId") String orgId,
                        @Param("userId") String userId,
                        @Param("time") LocalDateTime time,
                        @Param("operatorId") String operatorId,
                        @Param("now") LocalDateTime now);

    /** 删除工单：仅待指派 #1# 可删。 */
    @Delete("DELETE FROM \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#1#'")
    int deleteWorkOrder(@Param("id") String id);

    /** 指派工单：待指派 #1# → 处理中 #2#（写负责部门/处理人员/要求完成时间）。 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" " +
            "SET org = #{orgId}, \"user\" = #{userId}, \"time\" = #{time}, status = '#2#', " +
            "updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#1#'")
    int assignWorkOrder(@Param("id") String id,
                        @Param("orgId") String orgId,
                        @Param("userId") String userId,
                        @Param("time") LocalDateTime time,
                        @Param("operatorId") String operatorId,
                        @Param("now") LocalDateTime now);

    /** 取消工单：待指派 #1# → 已取消 #4#。 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" " +
            "SET status = '#4#', updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#1#'")
    int cancelWorkOrder(@Param("id") String id,
                        @Param("operatorId") String operatorId,
                        @Param("now") LocalDateTime now);

    /** 填写处理结果：仅处理中 #2#；同时把 pyxcen 置为保存时刻（可重复保存）。 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" " +
            "SET result = #{result}, pyxcen = #{now}, updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#2#'")
    int saveResult(@Param("id") String id,
                   @Param("result") String result,
                   @Param("operatorId") String operatorId,
                   @Param("now") LocalDateTime now);

    /** 提交验收：处理中 #2# → 已关闭 #3#（要求已填写处理结果；pyxcen 缺失时补当前时刻）。 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" " +
            "SET status = '#3#', pyxcen = COALESCE(pyxcen, #{now}), " +
            "updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND status = '#2#' " +
            "AND result IS NOT NULL AND TRIM(result) <> ''")
    int submitAccept(@Param("id") String id,
                     @Param("operatorId") String operatorId,
                     @Param("now") LocalDateTime now);

    /**
     * 验收闭环：工单关闭后把关联问题（已转工单 #3#）同步置为已关闭 #4#。
     * <p>按 work_order_id 反查；工单取消/删除走 resetIssuesByWorkOrder 回退为待处理。
     */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "SET status = '#4#', updated_at = #{now}, updated_by = #{operatorId} " +
            "WHERE corp_code = 'hltgq' AND work_order_id = #{id} AND status = '#3#'")
    int closeIssuesByWorkOrder(@Param("id") String id,
                               @Param("operatorId") String operatorId,
                               @Param("now") LocalDateTime now);
}
