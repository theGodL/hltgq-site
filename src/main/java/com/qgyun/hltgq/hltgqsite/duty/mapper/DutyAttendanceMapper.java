package com.qgyun.hltgq.hltgqsite.duty.mapper;

import com.qgyun.hltgq.hltgqsite.duty.vo.AttendanceRecordRowVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.HandoverTimelineRowVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.OngoingTimelineRowVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.PendingTimelineRowVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 值班记录（igahxz）与交接班记录（kwzwga）读写 Mapper。
 * <p>读侧新列优先 + 旧列兜底：班次时间 egkivk_min/max 优先（lzcjgq 旧文本兜底）、
 * 单位 cfszln 联表（sdcgli 旧文本兜底）；时间列一律 to_char 定长文本进出（见 DutySupport），
 * 时间比较与匹配全部在文本层完成（不依赖金仓跨类型隐式转换）。
 * <p>写侧主写当前模型字段并兼容双写 igahxz 旧列（lzcjgq=班次时间文本、sdcgli=单位名称文本，
 * 未升级的旧列表 SQL 仍可显示新记录）；kwzwga 不双写（§3.2）。
 */
public interface DutyAttendanceMapper {

    String T_RECORD = "\"qixiao-apaas\".\"t_auto_hltgq_yn8cm_igahxz\"";
    String T_RECORD_MEMBER_REL = "\"qixiao-apaas\".\"t_auto_hltgq_yn8cm_igahxz_ahygpx_rel\"";
    String T_HANDOVER = "\"qixiao-apaas\".\"t_auto_hltgq_yn8cm_kwzwga\"";
    String T_HANDOVER_FROM_REL = "\"qixiao-apaas\".\"t_auto_hltgq_yn8cm_kwzwga_ezvhop_rel\"";
    String T_HANDOVER_TO_REL = "\"qixiao-apaas\".\"t_auto_hltgq_yn8cm_kwzwga_igkyeu_rel\"";
    String T_SCHEDULE = "\"qixiao-apaas\".\"t_auto_hltgq_yn8cm_hdbzyd\"";
    String T_SCHEDULE_MEMBER_REL = "\"qixiao-apaas\".\"t_auto_hltgq_yn8cm_hdbzyd_cogxjx_rel\"";
    String T_ORG = "\"qixiao-apaas\".\"t_apaas_uc_org\"";
    String T_USER = "\"qixiao-apaas\".\"t_apaas_uc_user\"";

    /** 记录行公共列（别名与 AttendanceRecordRowVO 字段精确对齐；时间列 to_char 定长）。 */
    String RECORD_COLS =
            "d.id AS id, d.pttmxx AS recordNo, " +
            "to_char(d.yaaebo, 'YYYY-MM-DD HH24:MI:SS') AS dutyDateRaw, " +
            "to_char(d.egkivk_min, 'YYYY-MM-DD HH24:MI:SS') AS shiftMinRaw, " +
            "to_char(d.egkivk_max, 'YYYY-MM-DD HH24:MI:SS') AS shiftMaxRaw, " +
            "d.lzcjgq AS legacyShift, " +
            "d.cfszln AS orgId, o.name AS orgName, d.sdcgli AS legacyUnit, " +
            "d.atfzxl AS leaderId, u.name AS leaderName, " +
            "to_char(d.jlailj, 'YYYY-MM-DD HH24:MI:SS') AS actualStartRaw, " +
            "to_char(d.yuyitx, 'YYYY-MM-DD HH24:MI:SS') AS actualEndRaw, " +
            "d.rdammr AS summary, d.ylncfw AS statusCode, " +
            "(SELECT string_agg(COALESCE(ru.name, rel.rel_id), '、' ORDER BY rel.nature_order) " +
            "   FROM " + T_RECORD_MEMBER_REL + " rel LEFT JOIN " + T_USER + " ru ON ru.id = rel.rel_id " +
            "  WHERE rel.biz_id = d.id) AS membersRaw, " +
            "(SELECT string_agg(rel.rel_id, ',' ORDER BY rel.nature_order) " +
            "   FROM " + T_RECORD_MEMBER_REL + " rel WHERE rel.biz_id = d.id) AS memberIdsRaw ";

    // ==================== 今日判定 / 校验（内部行） ====================

    /**
     * 我的进行中记录（值班中 #2#）：带我参与（带班领导或值班人员）且本人已签到，
     * 班次时间与今日相交（跨天班在结束日仍可见；陈旧跨日未交班记录不遮挡当日判定），
     * 实际开始时间倒序取一条。
     * <p>签到按人判定：领导看主表 leader_checkin_at，成员看本人 rel 行 checkin_at；
     * 本人未签到的在途记录走 selectUnsignedOngoingRecord（待签到态）。
     */
    @Select("SELECT " + RECORD_COLS + " FROM " + T_RECORD + " d " +
            "LEFT JOIN " + T_ORG + " o ON o.id = d.cfszln " +
            "LEFT JOIN " + T_USER + " u ON u.id = d.atfzxl " +
            "WHERE d.corp_code = 'hltgq' AND d.ylncfw = '#2#' " +
            "AND to_char(COALESCE(d.egkivk_min, d.yaaebo), 'YYYY-MM-DD HH24:MI:SS') < #{dayEnd} " +
            "AND to_char(COALESCE(d.egkivk_max, d.egkivk_min, d.yaaebo), 'YYYY-MM-DD HH24:MI:SS') >= #{dayStart} " +
            "AND ((d.atfzxl = #{userId} AND d.leader_checkin_at IS NOT NULL) " +
            "     OR EXISTS (SELECT 1 FROM " + T_RECORD_MEMBER_REL + " rel " +
            "     WHERE rel.biz_id = d.id AND rel.rel_id = #{userId} AND rel.checkin_at IS NOT NULL)) " +
            "ORDER BY d.jlailj DESC NULLS LAST, d.id DESC LIMIT 1")
    AttendanceRecordRowVO selectOngoingRecord(@Param("userId") String userId,
                                              @Param("dayStart") String dayStart,
                                              @Param("dayEnd") String dayEnd);

    /**
     * 我的进行中记录但本人尚未签到（#2# 记录已由同班他人创建、本人签到位为空、班次时间与今日相交）：
     * 「待签到」态数据源——提示并引导本人前往值班交接班页补签，补签前不显示「值班中」。
     * <p>时间窗与 {@link #selectOngoingRecord} 一致：陈旧跨日未交班记录不判为待签到。
     */
    @Select("SELECT " + RECORD_COLS + " FROM " + T_RECORD + " d " +
            "LEFT JOIN " + T_ORG + " o ON o.id = d.cfszln " +
            "LEFT JOIN " + T_USER + " u ON u.id = d.atfzxl " +
            "WHERE d.corp_code = 'hltgq' AND d.ylncfw = '#2#' " +
            "AND to_char(COALESCE(d.egkivk_min, d.yaaebo), 'YYYY-MM-DD HH24:MI:SS') < #{dayEnd} " +
            "AND to_char(COALESCE(d.egkivk_max, d.egkivk_min, d.yaaebo), 'YYYY-MM-DD HH24:MI:SS') >= #{dayStart} " +
            "AND ((d.atfzxl = #{userId} AND d.leader_checkin_at IS NULL) " +
            "     OR EXISTS (SELECT 1 FROM " + T_RECORD_MEMBER_REL + " rel " +
            "     WHERE rel.biz_id = d.id AND rel.rel_id = #{userId} AND rel.checkin_at IS NULL)) " +
            "ORDER BY d.jlailj DESC NULLS LAST, d.id DESC LIMIT 1")
    AttendanceRecordRowVO selectUnsignedOngoingRecord(@Param("userId") String userId,
                                                      @Param("dayStart") String dayStart,
                                                      @Param("dayEnd") String dayEnd);

    /**
     * 我的今日已完成记录（今日交班的 #3#）：yuyitx ∈ [dayStart, dayEnd)，倒序取一条。
     */
    @Select("SELECT " + RECORD_COLS + " FROM " + T_RECORD + " d " +
            "LEFT JOIN " + T_ORG + " o ON o.id = d.cfszln " +
            "LEFT JOIN " + T_USER + " u ON u.id = d.atfzxl " +
            "WHERE d.corp_code = 'hltgq' AND d.ylncfw = '#3#' " +
            "AND to_char(d.yuyitx, 'YYYY-MM-DD HH24:MI:SS') >= #{dayStart} " +
            "AND to_char(d.yuyitx, 'YYYY-MM-DD HH24:MI:SS') < #{dayEnd} " +
            "AND (d.atfzxl = #{userId} OR EXISTS (SELECT 1 FROM " + T_RECORD_MEMBER_REL + " rel " +
            "     WHERE rel.biz_id = d.id AND rel.rel_id = #{userId})) " +
            "ORDER BY d.yuyitx DESC NULLS LAST, d.id DESC LIMIT 1")
    AttendanceRecordRowVO selectCompletedTodayRecord(@Param("userId") String userId,
                                                     @Param("dayStart") String dayStart,
                                                     @Param("dayEnd") String dayEnd);

    /** 单条记录内部行（报告/交班校验与数据源；按 id 精确取）。 */
    @Select("SELECT " + RECORD_COLS + " FROM " + T_RECORD + " d " +
            "LEFT JOIN " + T_ORG + " o ON o.id = d.cfszln " +
            "LEFT JOIN " + T_USER + " u ON u.id = d.atfzxl " +
            "WHERE d.id = #{id} AND d.corp_code = 'hltgq'")
    AttendanceRecordRowVO selectRecordRow(@Param("id") String id);

    /**
     * 本人在指定记录上的签到时间（to_char 定长文本；领导读主表列、成员读本人 rel 行；null=未签到）。
     */
    @Select("SELECT CASE WHEN d.atfzxl = #{userId} " +
            "THEN to_char(d.leader_checkin_at, 'YYYY-MM-DD HH24:MI:SS') " +
            "ELSE (SELECT to_char(rel.checkin_at, 'YYYY-MM-DD HH24:MI:SS') " +
            "        FROM " + T_RECORD_MEMBER_REL + " rel " +
            "       WHERE rel.biz_id = d.id AND rel.rel_id = #{userId}) END " +
            "FROM " + T_RECORD + " d WHERE d.id = #{recordId}")
    String selectMyCheckinRaw(@Param("recordId") String recordId, @Param("userId") String userId);

    /**
     * 幂等检查：该排班已存在的、且我参与的「值班中」记录 id（开始值班重复点击时直接复用，不重复创建）。
     * <p>正常路径按单位 + 班次起止时间精确匹配（记录值从排班复制、to_char 到秒必然相等）；
     * 排班无班次时间（历史数据）时退化为「同单位 + 值班日期相同」；
     * 参与人条件防同段双班（人员时间唯一、两班人员不重叠）时复用到他人记录。
     */
    @Select("<script>" +
            "SELECT d.id FROM " + T_RECORD + " d " +
            "WHERE d.corp_code = 'hltgq' AND d.ylncfw = '#2#' AND d.cfszln = #{orgId} " +
            "AND (d.atfzxl = #{operator} OR EXISTS (SELECT 1 FROM " + T_RECORD_MEMBER_REL + " mr " +
            "     WHERE mr.biz_id = d.id AND mr.rel_id = #{operator})) " +
            "<choose>" +
            "<when test='startRaw != null and endRaw != null'>" +
            "AND to_char(d.egkivk_min, 'YYYY-MM-DD HH24:MI:SS') = #{startRaw} " +
            "AND to_char(d.egkivk_max, 'YYYY-MM-DD HH24:MI:SS') = #{endRaw} " +
            "</when>" +
            "<otherwise>" +
            "AND to_char(d.yaaebo, 'YYYY-MM-DD') = #{dutyDate} " +
            "</otherwise>" +
            "</choose>" +
            "LIMIT 1" +
            "</script>")
    String selectOngoingIdBySchedule(@Param("orgId") String orgId,
                                     @Param("startRaw") String startRaw,
                                     @Param("endRaw") String endRaw,
                                     @Param("dutyDate") String dutyDate,
                                     @Param("operator") String operator);

    /**
     * 该排班已存在的、且本人已签到的「值班中」记录 id（today-status 的本人签到判定）。
     * <p>匹配规则同 selectOngoingIdBySchedule，仅追加「本人已签到」条件。
     */
    @Select("<script>" +
            "SELECT d.id FROM " + T_RECORD + " d " +
            "WHERE d.corp_code = 'hltgq' AND d.ylncfw = '#2#' AND d.cfszln = #{orgId} " +
            "AND ((d.atfzxl = #{operator} AND d.leader_checkin_at IS NOT NULL) " +
            "     OR EXISTS (SELECT 1 FROM " + T_RECORD_MEMBER_REL + " mr " +
            "     WHERE mr.biz_id = d.id AND mr.rel_id = #{operator} AND mr.checkin_at IS NOT NULL)) " +
            "<choose>" +
            "<when test='startRaw != null and endRaw != null'>" +
            "AND to_char(d.egkivk_min, 'YYYY-MM-DD HH24:MI:SS') = #{startRaw} " +
            "AND to_char(d.egkivk_max, 'YYYY-MM-DD HH24:MI:SS') = #{endRaw} " +
            "</when>" +
            "<otherwise>" +
            "AND to_char(d.yaaebo, 'YYYY-MM-DD') = #{dutyDate} " +
            "</otherwise>" +
            "</choose>" +
            "LIMIT 1" +
            "</script>")
    String selectSignedIdBySchedule(@Param("orgId") String orgId,
                                    @Param("startRaw") String startRaw,
                                    @Param("endRaw") String endRaw,
                                    @Param("dutyDate") String dutyDate,
                                    @Param("operator") String operator);

    // ==================== 编号生成（LIKE 当日前缀 + Java 解析最大序号） ====================

    /**
     * 编号取号互斥锁（KingbaseES 事务级建议锁，key=86199613 固定常量）：
     * 在「查最大序号 → 插入」前调用，锁随所在事务提交/回滚自动释放（无事务时瞬时释放）；
     * 并发签到/交班在数据库层串行化，防跨实例重号。
     */
    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(86199613)) t")
    int lockDutyNoSerial();

    /** 值班记录编号前缀查询（ZBJL-yyyyMMdd-%）。 */
    @Select("SELECT d.pttmxx FROM " + T_RECORD + " d " +
            "WHERE d.corp_code = 'hltgq' AND d.pttmxx LIKE #{prefix}")
    List<String> selectRecordNosByPrefix(@Param("prefix") String prefix);

    /** 交接班编号前缀查询（JJB-yyyyMMdd-%）。 */
    @Select("SELECT k.nizxsv FROM " + T_HANDOVER + " k " +
            "WHERE k.corp_code = 'hltgq' AND k.nizxsv LIKE #{prefix}")
    List<String> selectHandoverNosByPrefix(@Param("prefix") String prefix);

    /** 记录的值班人员 id（nature_order 序；交班时复制为交接班记录的交班人员）。 */
    @Select("SELECT rel.rel_id FROM " + T_RECORD_MEMBER_REL + " rel " +
            "WHERE rel.biz_id = #{bizId} ORDER BY rel.nature_order")
    List<String> selectRecordMemberIds(@Param("bizId") String bizId);

    // ==================== 写入（主写新列 + igahxz 兼容双写旧列） ====================

    /**
     * 新建值班记录：写编号/值班日期/班次时间/单位/领导/实际开始/状态（#2#）；
     * 兼容双写 lzcjgq（班次时间文本）/sdcgli（单位名称文本）；人员不写已弃用文本列，走 rel 表。
     */
    @Insert("INSERT INTO " + T_RECORD + " " +
            "(id, corp_code, created_at, created_by, updated_at, updated_by, " +
            " pttmxx, yaaebo, egkivk_min, egkivk_max, cfszln, atfzxl, jlailj, ylncfw, " +
            " lzcjgq, sdcgli, leader_checkin_at) " +
            "VALUES (#{id}, #{corpCode}, #{now}, #{operator}, #{now}, #{operator}, " +
            " #{recordNo}, #{dutyDate}, #{shiftMin}, #{shiftMax}, #{orgId}, #{leaderId}, #{actualStart}, '#2#', " +
            " #{shiftTimeText}, #{orgName}, #{leaderCheckinAt})")
    int insertRecord(@Param("id") String id,
                     @Param("corpCode") String corpCode,
                     @Param("operator") String operator,
                     @Param("now") LocalDateTime now,
                     @Param("recordNo") String recordNo,
                     @Param("dutyDate") LocalDateTime dutyDate,
                     @Param("shiftMin") LocalDateTime shiftMin,
                     @Param("shiftMax") LocalDateTime shiftMax,
                     @Param("orgId") String orgId,
                     @Param("leaderId") String leaderId,
                     @Param("actualStart") LocalDateTime actualStart,
                     @Param("shiftTimeText") String shiftTimeText,
                     @Param("orgName") String orgName,
                     @Param("leaderCheckinAt") LocalDateTime leaderCheckinAt);

    /**
     * 带班领导签到写入（首签建记录时落 insertRecord，此处为同班补签）：
     * 仅本人（atfzxl）且签到列为空的在途记录可写（行数 0 = 已签/非本人/已交班）。
     */
    @Update("UPDATE " + T_RECORD + " SET leader_checkin_at = #{now}, " +
            "updated_at = #{now}, updated_by = #{operator} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND ylncfw = '#2#' " +
            "AND atfzxl = #{operator} AND leader_checkin_at IS NULL")
    int updateLeaderCheckin(@Param("id") String id,
                            @Param("operator") String operator,
                            @Param("now") LocalDateTime now);

    /**
     * 值班人员签到写入（首签建记录时落 insertRecordMember，此处为同班补签）：
     * 仅本人 rel 行且签到列为空、记录仍在途（#2#）的行可写（行数 0 = 已签/不在名单/已交班）。
     */
    @Update("UPDATE " + T_RECORD_MEMBER_REL + " SET checkin_at = #{now}, " +
            "updated_at = #{now}, updated_by = #{operator} " +
            "WHERE biz_id = #{id} AND rel_id = #{operator} AND checkin_at IS NULL " +
            "AND EXISTS (SELECT 1 FROM " + T_RECORD + " d " +
            "     WHERE d.id = biz_id AND d.corp_code = 'hltgq' AND d.ylncfw = '#2#')")
    int updateMemberCheckin(@Param("id") String id,
                            @Param("operator") String operator,
                            @Param("now") LocalDateTime now);

    /**
     * 填写报告（提交字段更新：events/summary 非 null 才写，未提交或为空的字段保持原值；
     * 仅值班中记录，行数 0 表示状态已变）。
     */
    @Update("<script>UPDATE " + T_RECORD + " <set>" +
            "<if test='events != null'>xggayo = #{events}, </if>" +
            "<if test='summary != null'>rdammr = #{summary}, </if>" +
            "updated_at = #{now}, updated_by = #{operator} </set>" +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND ylncfw = '#2#'</script>")
    int updateRecordReport(@Param("id") String id,
                           @Param("events") String events,
                           @Param("summary") String summary,
                           @Param("operator") String operator,
                           @Param("now") LocalDateTime now);

    /** 交班：写实际交班时间 yuyitx 与状态 #3#（仅值班中记录，行数 0 表示并发重复交班）。 */
    @Update("UPDATE " + T_RECORD + " SET yuyitx = #{now}, ylncfw = '#3#', " +
            "updated_at = #{now}, updated_by = #{operator} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND ylncfw = '#2#'")
    int updateRecordHandover(@Param("id") String id,
                             @Param("operator") String operator,
                             @Param("now") LocalDateTime now);

    /** 新建交接班记录（主写新列；无兼容双写，§3.2）。 */
    @Insert("INSERT INTO " + T_HANDOVER + " " +
            "(id, corp_code, created_at, created_by, updated_at, updated_by, " +
            " nizxsv, tavpmi_min, tavpmi_max, whthxi, qwiioz, gdtjny) " +
            "VALUES (#{id}, #{corpCode}, #{now}, #{operator}, #{now}, #{operator}, " +
            " #{handoverNo}, #{shiftMin}, #{shiftMax}, #{orgId}, #{handoverTime}, #{matters})")
    int insertHandover(@Param("id") String id,
                       @Param("corpCode") String corpCode,
                       @Param("operator") String operator,
                       @Param("now") LocalDateTime now,
                       @Param("handoverNo") String handoverNo,
                       @Param("shiftMin") LocalDateTime shiftMin,
                       @Param("shiftMax") LocalDateTime shiftMax,
                       @Param("orgId") String orgId,
                       @Param("handoverTime") LocalDateTime handoverTime,
                       @Param("matters") String matters);

    // ==================== 关系表写入（多选中间表：9 列通用结构） ====================

    /**
     * 值班记录人员关系写入（开始值班时从排班成员复制，nature_order 从 1 递增）。
     * checkin_at：首签人本人那行落签到时间，其余为 null（待各自补签）。
     */
    @Insert("INSERT INTO " + T_RECORD_MEMBER_REL + " " +
            "(id, corp_code, created_at, created_by, updated_at, updated_by, biz_id, rel_id, nature_order, checkin_at) " +
            "VALUES (#{id}, #{corpCode}, #{now}, #{operator}, #{now}, #{operator}, #{bizId}, #{relId}, #{natureOrder}, #{checkinAt})")
    int insertRecordMember(@Param("id") String id,
                           @Param("bizId") String bizId,
                           @Param("relId") String relId,
                           @Param("natureOrder") String natureOrder,
                           @Param("corpCode") String corpCode,
                           @Param("now") LocalDateTime now,
                           @Param("operator") String operator,
                           @Param("checkinAt") LocalDateTime checkinAt);

    /** 交接班·交班人员关系写入（biz_id = 交接班记录 id，rel_id = 本班值班人员）。 */
    @Insert("INSERT INTO " + T_HANDOVER_FROM_REL + " " +
            "(id, corp_code, created_at, created_by, updated_at, updated_by, biz_id, rel_id, nature_order) " +
            "VALUES (#{id}, #{corpCode}, #{now}, #{operator}, #{now}, #{operator}, #{bizId}, #{relId}, #{natureOrder})")
    int insertHandoverFromMember(@Param("id") String id,
                                 @Param("bizId") String bizId,
                                 @Param("relId") String relId,
                                 @Param("natureOrder") String natureOrder,
                                 @Param("corpCode") String corpCode,
                                 @Param("now") LocalDateTime now,
                                 @Param("operator") String operator);

    /** 交接班·接班人员关系写入（biz_id = 交接班记录 id，rel_id = 提交的 receiverIds）。 */
    @Insert("INSERT INTO " + T_HANDOVER_TO_REL + " " +
            "(id, corp_code, created_at, created_by, updated_at, updated_by, biz_id, rel_id, nature_order) " +
            "VALUES (#{id}, #{corpCode}, #{now}, #{operator}, #{now}, #{operator}, #{bizId}, #{relId}, #{natureOrder})")
    int insertHandoverToMember(@Param("id") String id,
                               @Param("bizId") String bizId,
                               @Param("relId") String relId,
                               @Param("natureOrder") String natureOrder,
                               @Param("corpCode") String corpCode,
                               @Param("now") LocalDateTime now,
                               @Param("operator") String operator);

    // ==================== 今日交接班动态（timeline） ====================

    /** 今日已交班行（qwiioz 当天；按交班时间升序；含待办移交事项 gdtjny）。 */
    @Select("SELECT k.id AS id, k.nizxsv AS handoverNo, " +
            "to_char(k.qwiioz, 'YYYY-MM-DD HH24:MI:SS') AS handoverTimeRaw, " +
            "to_char(k.tavpmi_min, 'YYYY-MM-DD HH24:MI:SS') AS shiftMinRaw, " +
            "to_char(k.tavpmi_max, 'YYYY-MM-DD HH24:MI:SS') AS shiftMaxRaw, " +
            "k.gdtjny AS matters, " +
            "(SELECT string_agg(COALESCE(fu.name, frel.rel_id), '、' ORDER BY frel.nature_order) " +
            "   FROM " + T_HANDOVER_FROM_REL + " frel LEFT JOIN " + T_USER + " fu ON fu.id = frel.rel_id " +
            "  WHERE frel.biz_id = k.id) AS fromNamesRaw, " +
            "(SELECT string_agg(COALESCE(tu.name, trel.rel_id), '、' ORDER BY trel.nature_order) " +
            "   FROM " + T_HANDOVER_TO_REL + " trel LEFT JOIN " + T_USER + " tu ON tu.id = trel.rel_id " +
            "  WHERE trel.biz_id = k.id) AS toNamesRaw " +
            "FROM " + T_HANDOVER + " k " +
            "WHERE k.corp_code = 'hltgq' " +
            "AND to_char(k.qwiioz, 'YYYY-MM-DD HH24:MI:SS') >= #{dayStart} " +
            "AND to_char(k.qwiioz, 'YYYY-MM-DD HH24:MI:SS') < #{dayEnd} " +
            "ORDER BY k.qwiioz ASC")
    List<HandoverTimelineRowVO> selectTimelineHandovers(@Param("dayStart") String dayStart,
                                                        @Param("dayEnd") String dayEnd);

    /** 今日值守中行（#2# 记录、值班日期当天；按实际开始时间升序）。 */
    @Select("SELECT d.id AS id, " +
            "to_char(d.jlailj, 'YYYY-MM-DD HH24:MI:SS') AS actualStartRaw, " +
            "to_char(d.egkivk_min, 'YYYY-MM-DD HH24:MI:SS') AS shiftMinRaw, " +
            "to_char(d.egkivk_max, 'YYYY-MM-DD HH24:MI:SS') AS shiftMaxRaw, " +
            "d.lzcjgq AS legacyShift, " +
            "(SELECT string_agg(COALESCE(ru.name, rel.rel_id), '、' ORDER BY rel.nature_order) " +
            "   FROM " + T_RECORD_MEMBER_REL + " rel LEFT JOIN " + T_USER + " ru ON ru.id = rel.rel_id " +
            "  WHERE rel.biz_id = d.id) AS membersRaw " +
            "FROM " + T_RECORD + " d " +
            "WHERE d.corp_code = 'hltgq' AND d.ylncfw = '#2#' " +
            "AND to_char(d.yaaebo, 'YYYY-MM-DD') >= #{dayStart} " +
            "AND to_char(d.yaaebo, 'YYYY-MM-DD') < #{dayEnd} " +
            "ORDER BY d.jlailj ASC NULLS LAST")
    List<OngoingTimelineRowVO> selectTimelineOngoing(@Param("dayStart") String dayStart,
                                                     @Param("dayEnd") String dayEnd);

    /**
     * 今日待交接行（#zzkl# 待值班、或 #1# 已到点未开始（读侧纠偏为待值班）、值班日期当天；
     * 按开始时间升序）。
     */
    @Select("SELECT s.id AS id, s.fjyyhu AS shiftCode, " +
            "to_char(s.tneakp_min, 'YYYY-MM-DD HH24:MI:SS') AS shiftMinRaw, " +
            "(SELECT string_agg(COALESCE(ru.name, rel.rel_id), '、' ORDER BY rel.nature_order) " +
            "   FROM " + T_SCHEDULE_MEMBER_REL + " rel LEFT JOIN " + T_USER + " ru ON ru.id = rel.rel_id " +
            "  WHERE rel.biz_id = s.id) AS membersRaw " +
            "FROM " + T_SCHEDULE + " s " +
            "WHERE s.corp_code = 'hltgq' " +
            "AND (s.peuzwi = '#zzkl#' OR (s.peuzwi = '#1#' AND COALESCE(s.tneakp_min, s.owcvsv) <= #{now})) " +
            "AND to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD') >= #{dayStart} " +
            "AND to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD') < #{dayEnd} " +
            "ORDER BY s.tneakp_min ASC NULLS LAST")
    List<PendingTimelineRowVO> selectTimelinePending(@Param("dayStart") String dayStart,
                                                     @Param("dayEnd") String dayEnd,
                                                     @Param("now") LocalDateTime now);
}
