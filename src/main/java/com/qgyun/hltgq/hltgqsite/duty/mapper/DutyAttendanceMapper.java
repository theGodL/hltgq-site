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
 * <p>记录归属（2026-10 二次改造）：值班记录 = 一个人的值班记录——每人签到各自生成一条记录，
 * 归属人落主表 ahygpx（单关联用户，与 atfzxl 同物理形态）；今日判定/签到幂等/报告归属/交班权限/
 * 交班收口范围均以 ahygpx 为准。首版「班维度签到 + 按人补签」的 leader_checkin_at（主表）与
 * rel.checkin_at 列已弃用（保留不删、不再读写）。
 * <p>读侧新列优先 + 旧列兜底：班次时间 egkivk_min/max 优先（lzcjgq 旧文本兜底）、
 * 单位 cfszln 联表（sdcgli 旧文本兜底）；时间列一律 to_char 定长文本进出（见 DutySupport），
 * 时间比较与匹配全部在文本层完成（不依赖金仓跨类型隐式转换）。
 * <p>写侧主写当前模型字段并兼容双写 igahxz 旧列（lzcjgq=班次时间文本、sdcgli=单位名称文本，
 * 未升级的旧列表 SQL 仍可显示新记录）；人员关系表（rel）不再写入（历史数据仅读）；kwzwga 不双写（§3.2）。
 * <p>历史记录（ahygpx 为空）为改造前「班记录」形态：值班人员展示回退 rel 聚合；
 * 多人在途记录并存时一次交班按班组批量收口（历史记录一并置 #3#），无需数据回填。
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
            "d.ahygpx AS memberId, mu.name AS memberName, " +
            "to_char(d.jlailj, 'YYYY-MM-DD HH24:MI:SS') AS actualStartRaw, " +
            "to_char(d.yuyitx, 'YYYY-MM-DD HH24:MI:SS') AS actualEndRaw, " +
            "d.xggayo AS events, d.rdammr AS summary, d.ylncfw AS statusCode, " +
            "(SELECT string_agg(COALESCE(ru.name, rel.rel_id), '、' ORDER BY rel.nature_order) " +
            "   FROM " + T_RECORD_MEMBER_REL + " rel LEFT JOIN " + T_USER + " ru ON ru.id = rel.rel_id " +
            "  WHERE rel.biz_id = d.id) AS membersRaw ";

    /** 记录行公共连表（RECORD_COLS 依赖：单位 / 带班领导 / 归属人三表 LEFT JOIN）。 */
    String RECORD_JOINS =
            "LEFT JOIN " + T_ORG + " o ON o.id = d.cfszln " +
            "LEFT JOIN " + T_USER + " u ON u.id = d.atfzxl " +
            "LEFT JOIN " + T_USER + " mu ON mu.id = d.ahygpx ";

    // ==================== 今日判定 / 校验（内部行） ====================

    /**
     * 我的进行中记录（值班中 #2#）：归属人 = 我（ahygpx）、班次时间与今日相交
     * （跨天班在结束日仍可见；陈旧跨日未交班记录不遮挡当日判定），实际开始时间倒序取一条。
     * <p>改造前历史记录（ahygpx 为空）不命中——其参与人按排班维度回到「待签到」，
     * 各自签到生成自己的记录后即恢复正常流转。
     */
    @Select("SELECT " + RECORD_COLS + " FROM " + T_RECORD + " d " + RECORD_JOINS +
            "WHERE d.corp_code = 'hltgq' AND d.ylncfw = '#2#' AND d.ahygpx = #{userId} " +
            "AND to_char(COALESCE(d.egkivk_min, d.yaaebo), 'YYYY-MM-DD HH24:MI:SS') < #{dayEnd} " +
            "AND to_char(COALESCE(d.egkivk_max, d.egkivk_min, d.yaaebo), 'YYYY-MM-DD HH24:MI:SS') >= #{dayStart} " +
            "ORDER BY d.jlailj DESC NULLS LAST, d.id DESC LIMIT 1")
    AttendanceRecordRowVO selectOngoingRecord(@Param("userId") String userId,
                                              @Param("dayStart") String dayStart,
                                              @Param("dayEnd") String dayEnd);

    /**
     * 我的今日已完成记录（今日交班的 #3#）：yuyitx ∈ [dayStart, dayEnd)，倒序取一条。
     * <p>归属匹配：优先本人记录（ahygpx = 我）；历史记录（ahygpx 为空）回退班参与人
     * （带班领导或 rel 人员），保证改造前已交班的参与者仍可见「已完成」卡片（只读展示，无操作）。
     */
    @Select("SELECT " + RECORD_COLS + " FROM " + T_RECORD + " d " + RECORD_JOINS +
            "WHERE d.corp_code = 'hltgq' AND d.ylncfw = '#3#' " +
            "AND to_char(d.yuyitx, 'YYYY-MM-DD HH24:MI:SS') >= #{dayStart} " +
            "AND to_char(d.yuyitx, 'YYYY-MM-DD HH24:MI:SS') < #{dayEnd} " +
            "AND (d.ahygpx = #{userId} " +
            "     OR (d.ahygpx IS NULL AND (d.atfzxl = #{userId} OR EXISTS (SELECT 1 FROM " + T_RECORD_MEMBER_REL + " rel " +
            "     WHERE rel.biz_id = d.id AND rel.rel_id = #{userId})))) " +
            "ORDER BY d.yuyitx DESC NULLS LAST, d.id DESC LIMIT 1")
    AttendanceRecordRowVO selectCompletedTodayRecord(@Param("userId") String userId,
                                                     @Param("dayStart") String dayStart,
                                                     @Param("dayEnd") String dayEnd);

    /** 单条记录内部行（报告/交班校验与数据源；按 id 精确取）。 */
    @Select("SELECT " + RECORD_COLS + " FROM " + T_RECORD + " d " + RECORD_JOINS +
            "WHERE d.id = #{id} AND d.corp_code = 'hltgq'")
    AttendanceRecordRowVO selectRecordRow(@Param("id") String id);

    /**
     * 我的签到幂等检查：该排班我已生成的「值班中」记录 id（重复签到直接复用，不重复创建）。
     * <p>按「归属人 + 单位 + 班次」匹配：正常路径按班次起止时间精确匹配（记录值从排班复制、
     * to_char 到秒必然相等）；排班无班次时间（历史数据）时退化为「同单位 + 值班日期相同」。
     * <p>与 DutyScheduleService 今日判定共用（该排班我已有记录 = 我已签到）。
     */
    @Select("<script>" +
            "SELECT d.id FROM " + T_RECORD + " d " +
            "WHERE d.corp_code = 'hltgq' AND d.ylncfw = '#2#' AND d.ahygpx = #{operator} " +
            "AND d.cfszln = #{orgId} " +
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
    String selectMyOngoingIdBySchedule(@Param("orgId") String orgId,
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

    // ==================== 写入（主写新列 + igahxz 兼容双写旧列） ====================

    /**
     * 新建值班记录（签到）：写编号/值班日期/班次时间/单位/领导/归属人/实际开始/状态（#2#）；
     * 兼容双写 lzcjgq（班次时间文本）/sdcgli（单位名称文本）；人员关系表（rel）不再写入
     * （归属人落 ahygpx；历史 rel 数据仅用于展示兜底）。
     */
    @Insert("INSERT INTO " + T_RECORD + " " +
            "(id, corp_code, created_at, created_by, updated_at, updated_by, " +
            " pttmxx, yaaebo, egkivk_min, egkivk_max, cfszln, atfzxl, ahygpx, jlailj, ylncfw, " +
            " lzcjgq, sdcgli) " +
            "VALUES (#{id}, #{corpCode}, #{now}, #{operator}, #{now}, #{operator}, " +
            " #{recordNo}, #{dutyDate}, #{shiftMin}, #{shiftMax}, #{orgId}, #{leaderId}, #{memberId}, #{actualStart}, '#2#', " +
            " #{shiftTimeText}, #{orgName})")
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
                     @Param("memberId") String memberId,
                     @Param("actualStart") LocalDateTime actualStart,
                     @Param("shiftTimeText") String shiftTimeText,
                     @Param("orgName") String orgName);

    /**
     * 填写报告（提交字段更新：events/summary 非 null 才写，未提交或为空的字段保持原值；
     * 仅值班中记录，行数 0 表示状态已变；归属校验见服务层）。
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

    /** 交班（单条记录兜底：记录无单位信息无法按班组收口时使用）：写实际交班时间 yuyitx 与状态 #3#。 */
    @Update("UPDATE " + T_RECORD + " SET yuyitx = #{now}, ylncfw = '#3#', " +
            "updated_at = #{now}, updated_by = #{operator} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND ylncfw = '#2#'")
    int updateRecordHandover(@Param("id") String id,
                             @Param("operator") String operator,
                             @Param("now") LocalDateTime now);

    /**
     * 交班收口（班组批量）：同单位 + 班次起止时间（排班无班次时间降级同单位 + 值班日期）的
     * 全部「值班中」记录一并置已完成、写同一实际交班时间——一次交班全班个人记录组一并收口
     * （含改造前历史班记录，ahygpx 为空也一并收口）。行数 0 = 无可收口记录（并发已收口/条件不匹配）。
     */
    @Update("<script>" +
            "UPDATE " + T_RECORD + " SET yuyitx = #{now}, ylncfw = '#3#', " +
            "updated_at = #{now}, updated_by = #{operator} " +
            "WHERE corp_code = 'hltgq' AND ylncfw = '#2#' AND cfszln = #{orgId} " +
            "<choose>" +
            "<when test='startRaw != null and endRaw != null'>" +
            "AND to_char(egkivk_min, 'YYYY-MM-DD HH24:MI:SS') = #{startRaw} " +
            "AND to_char(egkivk_max, 'YYYY-MM-DD HH24:MI:SS') = #{endRaw} " +
            "</when>" +
            "<otherwise>" +
            "AND to_char(yaaebo, 'YYYY-MM-DD') = #{dutyDate} " +
            "</otherwise>" +
            "</choose>" +
            "</script>")
    int updateGroupHandover(@Param("orgId") String orgId,
                            @Param("startRaw") String startRaw,
                            @Param("endRaw") String endRaw,
                            @Param("dutyDate") String dutyDate,
                            @Param("operator") String operator,
                            @Param("now") LocalDateTime now);

    /**
     * 交班人员取数（收口组内各记录归属人；改造前历史记录回退 rel 人员）：
     * 交班收口前调用（记录仍在途态），用于交接班记录的交班人员关系写入。
     */
    @Select("<script>" +
            "SELECT DISTINCT COALESCE(d.ahygpx, rel.rel_id) AS memberId " +
            "FROM " + T_RECORD + " d LEFT JOIN " + T_RECORD_MEMBER_REL + " rel ON rel.biz_id = d.id " +
            "WHERE d.corp_code = 'hltgq' AND d.ylncfw = '#2#' AND d.cfszln = #{orgId} " +
            "<choose>" +
            "<when test='startRaw != null and endRaw != null'>" +
            "AND to_char(d.egkivk_min, 'YYYY-MM-DD HH24:MI:SS') = #{startRaw} " +
            "AND to_char(d.egkivk_max, 'YYYY-MM-DD HH24:MI:SS') = #{endRaw} " +
            "</when>" +
            "<otherwise>" +
            "AND to_char(d.yaaebo, 'YYYY-MM-DD') = #{dutyDate} " +
            "</otherwise>" +
            "</choose>" +
            "AND COALESCE(d.ahygpx, rel.rel_id) IS NOT NULL " +
            "ORDER BY memberId" +
            "</script>")
    List<String> selectGroupMemberIds(@Param("orgId") String orgId,
                                      @Param("startRaw") String startRaw,
                                      @Param("endRaw") String endRaw,
                                      @Param("dutyDate") String dutyDate);

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

    /** 交接班·交班人员关系写入（biz_id = 交接班记录 id，rel_id = 本班各记录归属人）。 */
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

    /**
     * 今日值守中行（当日 #2# 记录，按班组聚合：同单位 + 同班次起止时间合并为一行，
     * orgKey/membersRaw = 组内各记录人员聚合（新记录归属人；历史记录回退 rel 人员），
     * 服务层据此反查排班名册并标注各人签到状态；按最早实际开始时间升序取组）。
     */
    @Select("SELECT min(x.id) AS id, min(x.actualStartRaw) AS actualStartRaw, " +
            "x.shiftMinRaw AS shiftMinRaw, x.shiftMaxRaw AS shiftMaxRaw, " +
            "min(x.legacyShift) AS legacyShift, min(x.orgKey) AS orgKey, " +
            "string_agg(x.mem, '、' ORDER BY x.actualStartRaw NULLS LAST, x.id) AS membersRaw " +
            "FROM (SELECT d.id AS id, " +
            "to_char(d.jlailj, 'YYYY-MM-DD HH24:MI:SS') AS actualStartRaw, " +
            "to_char(d.egkivk_min, 'YYYY-MM-DD HH24:MI:SS') AS shiftMinRaw, " +
            "to_char(d.egkivk_max, 'YYYY-MM-DD HH24:MI:SS') AS shiftMaxRaw, " +
            "d.lzcjgq AS legacyShift, d.cfszln AS orgKey, " +
            "COALESCE(u.name, (SELECT string_agg(COALESCE(ru.name, rel.rel_id), '、' ORDER BY rel.nature_order) " +
            "   FROM " + T_RECORD_MEMBER_REL + " rel LEFT JOIN " + T_USER + " ru ON ru.id = rel.rel_id " +
            "  WHERE rel.biz_id = d.id)) AS mem " +
            "FROM " + T_RECORD + " d LEFT JOIN " + T_USER + " u ON u.id = d.ahygpx " +
            "WHERE d.corp_code = 'hltgq' AND d.ylncfw = '#2#' " +
            "AND to_char(d.yaaebo, 'YYYY-MM-DD') >= #{dayStart} " +
            "AND to_char(d.yaaebo, 'YYYY-MM-DD') < #{dayEnd}) x " +
            "GROUP BY x.orgKey, x.shiftMinRaw, x.shiftMaxRaw " +
            "ORDER BY min(x.actualStartRaw) ASC NULLS LAST")
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
