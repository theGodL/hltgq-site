package com.qgyun.hltgq.hltgqsite.h5.mapper;

import com.qgyun.hltgq.hltgqsite.h5.vo.MessagePageVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * H5 消息中心：消息分页 JOIN 查询 + 按需同步补建接收记录 + 当前登录人维度编码解析（企效平台 UC 表只读查询）。
 * <p>三类消息均以接收表为驱动（只回当前登录人的接收记录），JOIN 业务表取展示字段：
 * 业务表行被删时接收记录自然消失（JOIN 不产出孤儿行），与列表/未读数口径一致。
 * <p>同步模型（无定时任务）：summary/page 被调用时按规则补建当前登录人可见消息的接收记录
 * （NOT EXISTS 幂等、绝不重复）；未读=接收记录 is_read=#1#，阅读后 UPDATE 为 #2#。
 * <p>维度编码解析口径（corp_code 一律限定 hltgq）：部门/岗位/角色取 code、人员取 login_name；
 * 角色仅直接指派（field_id='USER'），与 RoleMapper 系统管理员判定一致，黑名单关联不参与。
 * <p>列别名与 VO 属性同名（map-underscore-to-camel-case=false 下依赖同名自动映射）；
 * level 为 KingbaseES 层次查询伪列保留字，需双引号转义；
 * isRead 由 CASE 表达式映射为 boolean。
 */
public interface MessageQueryMapper {

    /** 告警总数：接收表 JOIN 告警表，仅已确认/处理中（status IN #2#/#3#） */
    @Select("SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_alert\" a ON r.message_id = a.id " +
            "WHERE r.user_id = #{userId} AND r.message_type = '#1#' AND a.status IN ('#2#', '#3#')")
    long countAlert(@Param("userId") String userId);

    /** 告警分页：按发生时间倒序，id 降序兜底分页稳定 */
    @Select("SELECT a.id AS messageId, a.code, a.content, a.\"level\", a.status, a.type, a.time, " +
            "a.site AS siteId, s.zzkaec AS siteName, a.device AS deviceId, d.name AS deviceName, " +
            "CASE WHEN r.is_read = '#2#' THEN true ELSE false END AS isRead " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_alert\" a ON r.message_id = a.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON a.site = s.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON a.device = d.id " +
            "WHERE r.user_id = #{userId} AND r.message_type = '#1#' AND a.status IN ('#2#', '#3#') " +
            "ORDER BY a.time DESC, a.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<MessagePageVO.AlertMessage> selectAlertPage(@Param("userId") String userId,
                                                     @Param("limit") int limit,
                                                     @Param("offset") int offset);

    /** 举报投诉总数（全状态，无过滤字段） */
    @Select("SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_plmmdh\" c ON r.message_id = c.id " +
            "WHERE r.user_id = #{userId} AND r.message_type = '#2#'")
    long countComplaint(@Param("userId") String userId);

    /** 举报投诉分页：按提交时间倒序 */
    @Select("SELECT c.id AS messageId, c.jllmfa AS complaintType, c.humzvp AS content, " +
            "c.eoitvf AS contact, c.sgloiw AS phone, c.created_at AS createdAt, " +
            "CASE WHEN r.is_read = '#2#' THEN true ELSE false END AS isRead " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_plmmdh\" c ON r.message_id = c.id " +
            "WHERE r.user_id = #{userId} AND r.message_type = '#2#' " +
            "ORDER BY c.created_at DESC, c.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<MessagePageVO.ComplaintMessage> selectComplaintPage(@Param("userId") String userId,
                                                             @Param("limit") int limit,
                                                             @Param("offset") int offset);

    /** 意见征集总数（全状态，无过滤字段） */
    @Select("SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_snvday\" s ON r.message_id = s.id " +
            "WHERE r.user_id = #{userId} AND r.message_type = '#3#'")
    long countSuggestion(@Param("userId") String userId);

    /** 意见征集分页：按提交时间倒序 */
    @Select("SELECT s.id AS messageId, s.ywitii AS suggestionType, s.iiyomw AS content, " +
            "s.ymkxgm AS contact, s.nxziie AS phone, s.created_at AS createdAt, " +
            "CASE WHEN r.is_read = '#2#' THEN true ELSE false END AS isRead " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_snvday\" s ON r.message_id = s.id " +
            "WHERE r.user_id = #{userId} AND r.message_type = '#3#' " +
            "ORDER BY s.created_at DESC, s.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<MessagePageVO.SuggestionMessage> selectSuggestionPage(@Param("userId") String userId,
                                                               @Param("limit") int limit,
                                                               @Param("offset") int offset);

    /** 当前登录人 login_name（#user# 规则命中判定用） */
    @Select("SELECT login_name FROM \"qixiao-apaas\".\"t_apaas_uc_user\" " +
            "WHERE id = #{userId} AND corp_code = 'hltgq'")
    String selectLoginNameById(@Param("userId") String userId);

    /** 当前登录人姓名（值班人员多选列的姓名形态匹配用） */
    @Select("SELECT name FROM \"qixiao-apaas\".\"t_apaas_uc_user\" " +
            "WHERE id = #{userId} AND corp_code = 'hltgq'")
    String selectUserNameById(@Param("userId") String userId);

    /** 当前登录人所属部门编码集合（t_apaas_uc_user_org_rel.biz_id 即用户 id） */
    @Select("SELECT DISTINCT o.code " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_user_org_rel\" rel " +
            "JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o ON rel.rel_id = o.id AND o.corp_code = 'hltgq' " +
            "WHERE rel.corp_code = 'hltgq' AND rel.biz_id = #{userId}")
    List<String> selectDeptCodesByUserId(@Param("userId") String userId);

    /** 当前登录人岗位编码集合（t_apaas_uc_user_position_rel.biz_id 即用户 id） */
    @Select("SELECT DISTINCT p.code " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_user_position_rel\" rel " +
            "JOIN \"qixiao-apaas\".\"t_apaas_uc_position\" p ON rel.rel_id = p.id AND p.corp_code = 'hltgq' " +
            "WHERE rel.corp_code = 'hltgq' AND rel.biz_id = #{userId}")
    List<String> selectPositionCodesByUserId(@Param("userId") String userId);

    /** 当前登录人角色编码集合（t_apaas_auth_role_assign_rel.rel_id 即用户 id，仅直接指派） */
    @Select("SELECT DISTINCT r.code " +
            "FROM \"qixiao-apaas\".\"t_apaas_auth_role_assign_rel\" rel " +
            "JOIN \"qixiao-apaas\".\"t_apaas_auth_role\" r ON rel.biz_id = r.id AND r.corp_code = 'hltgq' " +
            "WHERE rel.corp_code = 'hltgq' AND rel.field_id = 'USER' AND rel.rel_id = #{userId}")
    List<String> selectRoleCodesByUserId(@Param("userId") String userId);

    /**
     * 按需同步告警接收记录：仅已确认/处理中的告警，为当前登录人补建未读记录。
     * <p>NOT EXISTS 幂等（已建记录绝不重复）；可见性判定在 Service 层完成（规则命中才调用），
     * 故此处无规则条件；id 取 userId_messageId 确定性拼接（≤64 字符）。
     * <p>注意：线上 Kingbase 的 || 按逻辑 OR 语义处理（会产生布尔值），拼接必须用 CONCAT。
     */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" " +
            "(id, user_id, message_type, message_id, is_read, corp_code, created_at, created_by) " +
            "SELECT CONCAT(#{userId}, '_', a.id), #{userId}, '#1#', a.id, '#1#', 'hltgq', CURRENT_TIMESTAMP, #{userId} " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_alert\" a " +
            "WHERE a.status IN ('#2#', '#3#') " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "  WHERE r.user_id = #{userId} AND r.message_type = '#1#' AND r.message_id = a.id" +
            ")")
    int syncAlertReceives(@Param("userId") String userId);

    /** 按需同步举报投诉接收记录（全量无状态过滤），NOT EXISTS 幂等 */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" " +
            "(id, user_id, message_type, message_id, is_read, corp_code, created_at, created_by) " +
            "SELECT CONCAT(#{userId}, '_', c.id), #{userId}, '#2#', c.id, '#1#', 'hltgq', CURRENT_TIMESTAMP, #{userId} " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_plmmdh\" c " +
            "WHERE NOT EXISTS (" +
            "  SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "  WHERE r.user_id = #{userId} AND r.message_type = '#2#' AND r.message_id = c.id" +
            ")")
    int syncComplaintReceives(@Param("userId") String userId);

    /** 按需同步意见征集接收记录（全量无状态过滤），NOT EXISTS 幂等 */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" " +
            "(id, user_id, message_type, message_id, is_read, corp_code, created_at, created_by) " +
            "SELECT CONCAT(#{userId}, '_', s.id), #{userId}, '#3#', s.id, '#1#', 'hltgq', CURRENT_TIMESTAMP, #{userId} " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_snvday\" s " +
            "WHERE NOT EXISTS (" +
            "  SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "  WHERE r.user_id = #{userId} AND r.message_type = '#3#' AND r.message_id = s.id" +
            ")")
    int syncSuggestionReceives(@Param("userId") String userId);

    /** 值班提醒总数（接收表 JOIN 值班排班表，带班领导定向） */
    @Select("SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "JOIN \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_hdbzyd\" s ON r.message_id = s.id " +
            "WHERE r.user_id = #{userId} AND r.message_type = '#4#'")
    long countDuty(@Param("userId") String userId);

    /** 值班提醒分页：按值班日期倒序，id 兜底分页稳定 */
    @Select("SELECT s.id AS messageId, s.owcvsv AS dutyDate, s.itxmyy AS shiftTime, " +
            "s.ihdflq AS dutyUnit, s.peuzwi AS scheduleStatus, " +
            "CASE WHEN s.alidpq = #{userId} THEN 'leader' ELSE 'staff' END AS dutyRole, " +
            "CASE WHEN r.is_read = '#2#' THEN true ELSE false END AS isRead " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "JOIN \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_hdbzyd\" s ON r.message_id = s.id " +
            "WHERE r.user_id = #{userId} AND r.message_type = '#4#' " +
            "ORDER BY s.owcvsv DESC, s.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<MessagePageVO.DutyMessage> selectDutyPage(@Param("userId") String userId,
                                                   @Param("limit") int limit,
                                                   @Param("offset") int offset);

    /**
     * 按需同步值班提醒接收记录（带班领导侧）：alidpq（单选人员列，存 UC 用户 id）= 当前登录人，
     * 且排班已进入「值班中」（peuzwi='#cmiu#'，即已点击开始值班；待值班阶段不提醒）、
     * 提醒状态未提醒（hfxuuk='#papu#'，2026-09 实网样例核对；早期按 '#1#' 假设导致补建 0 条）、
     * 值班日期不早于昨日（覆盖跨天夜班，同时挡住陈旧的「值班中」残留记录）；NOT EXISTS 幂等。
     */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" " +
            "(id, user_id, message_type, message_id, is_read, corp_code, created_at, created_by) " +
            "SELECT CONCAT(#{userId}, '_', s.id), #{userId}, '#4#', s.id, '#1#', 'hltgq', CURRENT_TIMESTAMP, #{userId} " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_hdbzyd\" s " +
            "WHERE s.corp_code = 'hltgq' " +
            "AND s.alidpq = #{userId} " +
            "AND s.peuzwi = '#cmiu#' " +
            "AND s.hfxuuk = '#papu#' " +
            "AND s.owcvsv >= #{since} " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "  WHERE r.user_id = #{userId} AND r.message_type = '#4#' AND r.message_id = s.id" +
            ")")
    int syncDutyReceives(@Param("userId") String userId, @Param("since") String since);

    /**
     * 按需同步值班提醒接收记录（值班人员侧）：值班人员多选列 cogxjx 命中当前登录人。
     * <p>多选列双路 LIKE（用户 id 与姓名，兼容内联文本存储形态）；姓名分支仅在取到姓名时参与，
     * 避免平台 CONCAT 忽略 NULL 产生 LIKE '%%' 恒真（会误给全员补建）。
     * <p>与领导侧同条件（排班已进入值班中 + 未提醒 + 值班日期不早于昨日）；两侧都命中时由 NOT EXISTS 天然去重（接收 id 一致）。
     * <p>列形态若为平台关联中间表，本 SQL 静默 0 行（日志 duty staffCol=0 即该信号），调用方 try/catch 降级。
     */
    @Insert("<script>" +
            "INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" " +
            "(id, user_id, message_type, message_id, is_read, corp_code, created_at, created_by) " +
            "SELECT CONCAT(#{userId}, '_', s.id), #{userId}, '#4#', s.id, '#1#', 'hltgq', CURRENT_TIMESTAMP, #{userId} " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_hdbzyd\" s " +
            "WHERE s.corp_code = 'hltgq' " +
            "AND s.peuzwi = '#cmiu#' " +
            "AND s.hfxuuk = '#papu#' " +
            "AND s.owcvsv >= #{since} " +
            "AND (s.cogxjx LIKE CONCAT('%', #{userId}, '%') " +
            "<if test='userName != null and userName != \"\"'>OR s.cogxjx LIKE CONCAT('%', #{userName}, '%') </if>" +
            ") " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "  WHERE r.user_id = #{userId} AND r.message_type = '#4#' AND r.message_id = s.id" +
            ")" +
            "</script>")
    int syncDutyStaffReceives(@Param("userId") String userId,
                              @Param("userName") String userName,
                              @Param("since") String since);

    /**
     * 按需同步值班提醒接收记录（值班人员侧·关系表形态）：平台多选关联字段落关系中间表
     * （列 id/corp_code/created_at/created_by/updated_at/updated_by/biz_id/rel_id/nature_order，
     * biz_id = 排班记录 id，rel_id = 用户 id）。
     * <p>表名按已实测同型关系表规律推得（t_auto_<corp>_<appCode>_<主表code>_<关系名>_rel；
     * 同型对照 t_auto_hltgq_knc3g_nlbdju_user_rel、t_auto_hltgq_yn8cm_igahxz_ahygpx_rel），
     * 未经库实测；与主表列形态互为兼容，调用方 try/catch 独立降级。
     * <p>与领导侧同条件（排班已进入值班中 + 未提醒 + 值班日期不早于昨日）。
     */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" " +
            "(id, user_id, message_type, message_id, is_read, corp_code, created_at, created_by) " +
            "SELECT CONCAT(#{userId}, '_', s.id), #{userId}, '#4#', s.id, '#1#', 'hltgq', CURRENT_TIMESTAMP, #{userId} " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_hdbzyd\" s " +
            "WHERE s.corp_code = 'hltgq' " +
            "AND s.peuzwi = '#cmiu#' " +
            "AND s.hfxuuk = '#papu#' " +
            "AND s.owcvsv >= #{since} " +
            "AND EXISTS (" +
            "  SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_hdbzyd_cogxjx_rel\" rel " +
            "  WHERE rel.biz_id = s.id AND rel.rel_id = #{userId}" +
            ")" +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "  WHERE r.user_id = #{userId} AND r.message_type = '#4#' AND r.message_id = s.id" +
            ")")
    int syncDutyStaffRelReceives(@Param("userId") String userId, @Param("since") String since);

    /**
     * 值班诊断（补建 0 条时调用）：取当前登录人作为带班领导的排班原值，
     * 用于核对 hfxuuk（提醒状态）/peuzwi（排班状态）字典与定向条件是否相符。
     */
    @Select("SELECT s.id AS id, s.owcvsv AS dutyDate, s.hfxuuk AS remindStatus, " +
            "s.peuzwi AS scheduleStatus, s.alidpq AS leaderId " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_hdbzyd\" s " +
            "WHERE s.alidpq = #{userId} " +
            "ORDER BY s.owcvsv DESC LIMIT 5")
    List<Map<String, Object>> selectDutyDiag(@Param("userId") String userId);

    // ==================== 模型计算消息（#5#，提交人定向） ====================

    /** 模型计算消息总数：接收表纯计数不 JOIN 主表（方案软删不物理删，接收记录保留，与列表口径一致） */
    @Select("SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" " +
            "WHERE user_id = #{userId} AND message_type = '#5#'")
    long countModelCalc(@Param("userId") String userId);

    /**
     * 模型计算消息分页第一段：接收表按 created_at 倒序取本页（同步时写入方案完成时间，即排序键）。
     * <p>只取 messageId/isRead，方案展示字段由 Service 按模块前缀拆分后第二段反查（避免 7 表 UNION 大排序）。
     */
    @Select("SELECT r.message_id AS messageId, " +
            "CASE WHEN r.is_read = '#2#' THEN true ELSE false END AS isRead " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "WHERE r.user_id = #{userId} AND r.message_type = '#5#' " +
            "ORDER BY r.created_at DESC, r.message_id DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<MessagePageVO.ModelCalcMessage> selectModelCalcPage(@Param("userId") String userId,
                                                             @Param("limit") int limit,
                                                             @Param("offset") int offset);

    /**
     * 按需同步模型计算接收记录：提交人（created_by）= 当前登录人且状态已完成的方案，7 张主表 UNION ALL。
     * <p>message_id 带模块前缀（防跨表主键冲突，也是分页第二段拆表反查依据）；
     * created_at 写方案完成时间（updated_at），供列表按完成时间倒序；
     * 墒情主表 status 为平台字典 #2#=已完成，其余 6 张为纯值 completed；
     * 系统触发任务（预跑/恢复）created_by 为固定账号，不匹配任何真实用户，天然不产生消息。
     */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" " +
            "(id, user_id, message_type, message_id, is_read, corp_code, created_at, created_by) " +
            "SELECT CONCAT(#{userId}, '_', x.message_id), #{userId}, '#5#', x.message_id, '#1#', 'hltgq', x.finished_at, #{userId} " +
            "FROM (" +
            "SELECT CONCAT('short:', id) AS message_id, updated_at AS finished_at " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_short_forecast_record\" " +
            "WHERE created_by = #{userId} AND status = 'completed' AND del_flag = '#2#' " +
            "UNION ALL " +
            "SELECT CONCAT('long:', id), updated_at " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_long_predict_record\" " +
            "WHERE created_by = #{userId} AND status = 'completed' AND del_flag = '#2#' " +
            "UNION ALL " +
            "SELECT CONCAT('loss:', id), updated_at " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_loss_record\" " +
            "WHERE created_by = #{userId} AND status = 'completed' AND del_flag = '#2#' " +
            "UNION ALL " +
            "SELECT CONCAT('demand:', id), updated_at " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_demand_record\" " +
            "WHERE created_by = #{userId} AND status = 'completed' AND del_flag = '#2#' " +
            "UNION ALL " +
            "SELECT CONCAT('moisture:', id), updated_at " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_moisture_detail\" " +
            "WHERE created_by = #{userId} AND status = '#2#' AND del_flag = '#2#' " +
            "UNION ALL " +
            "SELECT CONCAT('allocation:', id), updated_at " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_allocate_record\" " +
            "WHERE created_by = #{userId} AND status = 'completed' AND del_flag = '#2#' " +
            "UNION ALL " +
            "SELECT CONCAT('decision:', id), updated_at " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_decision_record\" " +
            "WHERE created_by = #{userId} AND status = 'completed' AND del_flag = '#2#' " +
            ") x " +
            "WHERE NOT EXISTS (" +
            "SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "WHERE r.user_id = #{userId} AND r.message_type = '#5#' AND r.message_id = x.message_id" +
            ")")
    int syncModelCalcReceives(@Param("userId") String userId);

    /** 模型计算消息第二段反查：短期预报方案详情（主键 IN，每模块独立查询） */
    @Select("<script>SELECT CONCAT('short:', id) AS messageId, scheme_name AS schemeName, updated_at AS finishedAt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_short_forecast_record\" WHERE id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    List<MessagePageVO.ModelCalcMessage> selectShortCalcDetails(@Param("ids") List<String> ids);

    /** 模型计算消息第二段反查：长期预测方案详情 */
    @Select("<script>SELECT CONCAT('long:', id) AS messageId, scheme_name AS schemeName, updated_at AS finishedAt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_long_predict_record\" WHERE id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    List<MessagePageVO.ModelCalcMessage> selectLongCalcDetails(@Param("ids") List<String> ids);

    /** 模型计算消息第二段反查：水量损失方案详情 */
    @Select("<script>SELECT CONCAT('loss:', id) AS messageId, scheme_name AS schemeName, updated_at AS finishedAt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_loss_record\" WHERE id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    List<MessagePageVO.ModelCalcMessage> selectLossCalcDetails(@Param("ids") List<String> ids);

    /** 模型计算消息第二段反查：需水预测方案详情 */
    @Select("<script>SELECT CONCAT('demand:', id) AS messageId, scheme_name AS schemeName, updated_at AS finishedAt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_demand_record\" WHERE id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    List<MessagePageVO.ModelCalcMessage> selectDemandCalcDetails(@Param("ids") List<String> ids);

    /** 模型计算消息第二段反查：墒情预测方案详情（主表 moisture_detail，status 平台字典） */
    @Select("<script>SELECT CONCAT('moisture:', id) AS messageId, scheme_name AS schemeName, updated_at AS finishedAt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_moisture_detail\" WHERE id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    List<MessagePageVO.ModelCalcMessage> selectMoistureCalcDetails(@Param("ids") List<String> ids);

    /** 模型计算消息第二段反查：配水方案详情 */
    @Select("<script>SELECT CONCAT('allocation:', id) AS messageId, scheme_name AS schemeName, updated_at AS finishedAt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_allocate_record\" WHERE id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    List<MessagePageVO.ModelCalcMessage> selectAllocationCalcDetails(@Param("ids") List<String> ids);

    /** 模型计算消息第二段反查：配水决策方案详情 */
    @Select("<script>SELECT CONCAT('decision:', id) AS messageId, scheme_name AS schemeName, updated_at AS finishedAt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_decision_record\" WHERE id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach></script>")
    List<MessagePageVO.ModelCalcMessage> selectDecisionCalcDetails(@Param("ids") List<String> ids);
}
