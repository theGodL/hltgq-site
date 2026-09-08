package com.qgyun.hltgq.hltgqsite.h5.mapper;

import com.qgyun.hltgq.hltgqsite.h5.vo.MessagePageVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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
}
