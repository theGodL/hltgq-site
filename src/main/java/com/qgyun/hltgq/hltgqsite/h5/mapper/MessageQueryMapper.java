package com.qgyun.hltgq.hltgqsite.h5.mapper;

import com.qgyun.hltgq.hltgqsite.h5.vo.MessagePageVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * H5 消息中心：消息分页 JOIN 查询 + 未分发消息扫描 + 接收人解析（企效平台 UC 表只读查询）。
 * <p>三类消息均以接收表为驱动（只回当前登录人的分发记录），JOIN 业务表取展示字段：
 * 业务表行被删时接收记录自然消失（JOIN 不产出孤儿行），与列表/未读数口径一致。
 * <p>接收人解析口径（corp_code 一律限定 hltgq）：规则 target_id 与平台表匹配，
 * 部门/岗位/角色取 code、人员取 login_name；匹配结果统一为 t_apaas_uc_user.id 列表；
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

    /**
     * 未分发告警扫描：仅已确认/处理中且时间窗口内的告警、接收表无任何接收记录（NOT EXISTS）。
     * <p>窗口防御：老告警不参与首次全量分发（由 message.alert-window-days 控制）。
     */
    @Select("SELECT a.id FROM \"qixiao-apaas\".\"t_auto_hltgq_water_alert\" a " +
            "WHERE a.status IN ('#2#', '#3#') AND a.time >= #{since} " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "  WHERE r.message_type = '#1#' AND r.message_id = a.id" +
            ") " +
            "ORDER BY a.id")
    List<String> selectUndispatchedAlertIds(@Param("since") LocalDateTime since);

    /** 未分发举报投诉扫描（全量，无状态过滤） */
    @Select("SELECT c.id FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_plmmdh\" c " +
            "WHERE NOT EXISTS (" +
            "  SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "  WHERE r.message_type = '#2#' AND r.message_id = c.id" +
            ") " +
            "ORDER BY c.id")
    List<String> selectUndispatchedComplaintIds();

    /** 未分发意见征集扫描（全量，无状态过滤） */
    @Select("SELECT s.id FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_snvday\" s " +
            "WHERE NOT EXISTS (" +
            "  SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "  WHERE r.message_type = '#3#' AND r.message_id = s.id" +
            ") " +
            "ORDER BY s.id")
    List<String> selectUndispatchedSuggestionIds();

    /** 部门编码 → 用户主键集合（t_apaas_uc_user_org_rel.biz_id 即用户 id） */
    @Select("<script>" +
            "SELECT DISTINCT rel.biz_id AS userId " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_user_org_rel\" rel " +
            "JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o " +
            "  ON rel.rel_id = o.id AND o.corp_code = 'hltgq' " +
            "WHERE rel.corp_code = 'hltgq' AND o.code IN " +
            "<foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach>" +
            "</script>")
    List<String> selectUserIdsByOrgCodes(@Param("codes") List<String> codes);

    /** 岗位编码 → 用户主键集合（t_apaas_uc_user_position_rel.biz_id 即用户 id） */
    @Select("<script>" +
            "SELECT DISTINCT rel.biz_id AS userId " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_user_position_rel\" rel " +
            "JOIN \"qixiao-apaas\".\"t_apaas_uc_position\" p " +
            "  ON rel.rel_id = p.id AND p.corp_code = 'hltgq' " +
            "WHERE rel.corp_code = 'hltgq' AND p.code IN " +
            "<foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach>" +
            "</script>")
    List<String> selectUserIdsByPositionCodes(@Param("codes") List<String> codes);

    /** 角色编码 → 用户主键集合（t_apaas_auth_role_assign_rel.rel_id 即用户 id，仅直接指派） */
    @Select("<script>" +
            "SELECT DISTINCT rel.rel_id AS userId " +
            "FROM \"qixiao-apaas\".\"t_apaas_auth_role_assign_rel\" rel " +
            "JOIN \"qixiao-apaas\".\"t_apaas_auth_role\" r " +
            "  ON rel.biz_id = r.id AND r.corp_code = 'hltgq' " +
            "WHERE rel.corp_code = 'hltgq' AND rel.field_id = 'USER' AND r.code IN " +
            "<foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach>" +
            "</script>")
    List<String> selectUserIdsByRoleCodes(@Param("codes") List<String> codes);

    /** 登录名 → 用户主键集合 */
    @Select("<script>" +
            "SELECT id AS userId FROM \"qixiao-apaas\".\"t_apaas_uc_user\" " +
            "WHERE corp_code = 'hltgq' AND login_name IN " +
            "<foreach collection='loginNames' item='n' open='(' separator=',' close=')'>#{n}</foreach>" +
            "</script>")
    List<String> selectUserIdsByLoginNames(@Param("loginNames") List<String> loginNames);

    /** 全员用户主键（无规则兜底分发） */
    @Select("SELECT id AS userId FROM \"qixiao-apaas\".\"t_apaas_uc_user\" WHERE corp_code = 'hltgq'")
    List<String> selectAllUserIds();
}
