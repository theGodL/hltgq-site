package com.qgyun.hltgq.hltgqsite.duty.mapper;

import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleDetailVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleListRowVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleRowVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 值班排班（hdbzyd）读写 Mapper。
 * <p>读侧新列优先 + 旧列兜底：值班日期 COALESCE(idjwlq, owcvsv)、单位名回落 ihdflq、
 * 人员走关系表 hdbzyd_cogxjx_rel（string_agg 按 nature_order 聚合）。
 * <p>写侧主写当前模型字段并兼容双写旧列（owcvsv/itxmyy/ihdflq —— 保住 H5 值班提醒），
 * 「待值班」读时纠偏不落库（§3.1）；时间列一律 to_char 定长文本进出（见 DutySupport）。
 */
public interface DutyScheduleMapper {

    String T_SCHEDULE = "\"qixiao-apaas\".\"t_auto_hltgq_yn8cm_hdbzyd\"";
    String T_MEMBER_REL = "\"qixiao-apaas\".\"t_auto_hltgq_yn8cm_hdbzyd_cogxjx_rel\"";
    String T_ORG = "\"qixiao-apaas\".\"t_apaas_uc_org\"";
    String T_USER = "\"qixiao-apaas\".\"t_apaas_uc_user\"";

    // ==================== 下拉选项与存在性校验 ====================

    /** 单位候选（下拉用：id + name）。 */
    @Select("SELECT g.id AS id, g.name AS name FROM " + T_ORG + " g " +
            "WHERE g.corp_code = 'hltgq' ORDER BY g.name ASC, g.id ASC")
    List<Map<String, Object>> selectOptionsOrgs();

    /** 人员候选（下拉用：id + name；带班领导与值班人员共用）。 */
    @Select("SELECT u.id AS id, u.name AS name FROM " + T_USER + " u " +
            "WHERE u.corp_code = 'hltgq' ORDER BY u.name ASC, u.id ASC")
    List<Map<String, Object>> selectOptionsUsers();

    /** 单位名称（保存校验：null = 单位不存在）。 */
    @Select("SELECT name FROM " + T_ORG + " WHERE id = #{id} AND corp_code = 'hltgq'")
    String selectOrgNameById(@Param("id") String id);

    /** 用户姓名（保存校验/今日判定展示：null = 用户不存在）。 */
    @Select("SELECT name FROM " + T_USER + " WHERE id = #{id} AND corp_code = 'hltgq'")
    String selectUserNameById(@Param("id") String id);

    /** 批量存在性校验（返回命中的用户 id；值与入参逐一对齐由调用方比对）。 */
    @Select("<script>" +
            "SELECT id FROM " + T_USER + " WHERE corp_code = 'hltgq' AND id IN " +
            "<foreach collection='ids' item='it' open='(' separator=',' close=')'>#{it}</foreach>" +
            "</script>")
    List<String> selectExistingUserIds(@Param("ids") List<String> ids);

    /** 批量用户姓名（receiverCandidates / 今日值班人员展示用：id + name）。 */
    @Select("<script>" +
            "SELECT u.id AS id, u.name AS name FROM " + T_USER + " u " +
            "WHERE u.corp_code = 'hltgq' AND u.id IN " +
            "<foreach collection='ids' item='it' open='(' separator=',' close=')'>#{it}</foreach>" +
            "</script>")
    List<Map<String, Object>> selectUsersByIds(@Param("ids") List<String> ids);

    // ==================== 列表 / 详情（读时纠偏 + 兜底） ====================

    /** 排班列表总数（过滤条件与列表一致；status 传中文档位或编码原文）。 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM " + T_SCHEDULE + " s " +
            "LEFT JOIN " + T_ORG + " o ON o.id = s.jepbhm " +
            "LEFT JOIN " + T_USER + " u ON u.id = s.alidpq " +
            "WHERE s.corp_code = 'hltgq' " +
            "<if test='dayStart != null'>AND to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') &gt;= #{dayStart} AND to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') &lt; #{dayEnd} </if>" +
            "<if test='shiftCode != null'>AND s.fjyyhu = #{shiftCode} </if>" +
            "<if test='status != null'><choose>" +
            "<when test='status == \"未开始\"'>AND s.peuzwi = '#1#' AND (COALESCE(s.tneakp_min, s.owcvsv) IS NULL OR COALESCE(s.tneakp_min, s.owcvsv) &gt; #{now}) </when>" +
            "<when test='status == \"待值班\"'>AND (s.peuzwi = '#zzkl#' OR (s.peuzwi = '#1#' AND COALESCE(s.tneakp_min, s.owcvsv) &lt;= #{now})) </when>" +
            "<when test='status == \"值班中\"'>AND s.peuzwi = '#cmiu#' </when>" +
            "<when test='status == \"已完成\"'>AND s.peuzwi = '#qavx#' </when>" +
            "<otherwise>AND s.peuzwi = #{status} </otherwise>" +
            "</choose></if>" +
            "<if test='org != null'>AND (s.jepbhm = #{org} OR o.name LIKE CONCAT('%', #{org}, '%') OR s.ihdflq LIKE CONCAT('%', #{org}, '%')) </if>" +
            "<if test='keyword != null'>AND (u.name LIKE CONCAT('%', #{keyword}, '%') OR EXISTS (SELECT 1 FROM " + T_MEMBER_REL + " relkw " +
            "JOIN " + T_USER + " rukw ON rukw.id = relkw.rel_id AND rukw.corp_code = 'hltgq' " +
            "WHERE relkw.biz_id = s.id AND rukw.name LIKE CONCAT('%', #{keyword}, '%'))) </if>" +
            "</script>")
    long countSchedulePage(@Param("dayStart") String dayStart,
                           @Param("dayEnd") String dayEnd,
                           @Param("shiftCode") String shiftCode,
                           @Param("status") String status,
                           @Param("org") String org,
                           @Param("keyword") String keyword,
                           @Param("now") LocalDateTime now);

    /**
     * 排班分页列表：值班时间倒序（历史无时间行按旧列值班日期倒序兜底）。
     * <p>展示名聚合（成员姓名/成员 id）按 rel.nature_order 排序，与详情顺序一致。
     */
    @Select("<script>" +
            "SELECT s.id, " +
            "to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') AS dutyDateRaw, " +
            "to_char(s.tneakp_min, 'YYYY-MM-DD HH24:MI:SS') AS tneakpMinRaw, " +
            "to_char(s.tneakp_max, 'YYYY-MM-DD HH24:MI:SS') AS tneakpMaxRaw, " +
            "s.fjyyhu AS shiftCode, s.peuzwi AS statusCode, " +
            "s.jepbhm AS orgId, o.name AS orgName, s.ihdflq AS legacyUnit, " +
            "s.alidpq AS leaderId, u.name AS leaderName, " +
            "(SELECT string_agg(COALESCE(ru.name, rel.rel_id), '、' ORDER BY rel.nature_order) " +
            "   FROM " + T_MEMBER_REL + " rel LEFT JOIN " + T_USER + " ru ON ru.id = rel.rel_id " +
            "  WHERE rel.biz_id = s.id) AS members, " +
            "(SELECT string_agg(rel.rel_id, ',' ORDER BY rel.nature_order) " +
            "   FROM " + T_MEMBER_REL + " rel WHERE rel.biz_id = s.id) AS memberIdsRaw " +
            "FROM " + T_SCHEDULE + " s " +
            "LEFT JOIN " + T_ORG + " o ON o.id = s.jepbhm " +
            "LEFT JOIN " + T_USER + " u ON u.id = s.alidpq " +
            "WHERE s.corp_code = 'hltgq' " +
            "<if test='dayStart != null'>AND to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') &gt;= #{dayStart} AND to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') &lt; #{dayEnd} </if>" +
            "<if test='shiftCode != null'>AND s.fjyyhu = #{shiftCode} </if>" +
            "<if test='status != null'><choose>" +
            "<when test='status == \"未开始\"'>AND s.peuzwi = '#1#' AND (COALESCE(s.tneakp_min, s.owcvsv) IS NULL OR COALESCE(s.tneakp_min, s.owcvsv) &gt; #{now}) </when>" +
            "<when test='status == \"待值班\"'>AND (s.peuzwi = '#zzkl#' OR (s.peuzwi = '#1#' AND COALESCE(s.tneakp_min, s.owcvsv) &lt;= #{now})) </when>" +
            "<when test='status == \"值班中\"'>AND s.peuzwi = '#cmiu#' </when>" +
            "<when test='status == \"已完成\"'>AND s.peuzwi = '#qavx#' </when>" +
            "<otherwise>AND s.peuzwi = #{status} </otherwise>" +
            "</choose></if>" +
            "<if test='org != null'>AND (s.jepbhm = #{org} OR o.name LIKE CONCAT('%', #{org}, '%') OR s.ihdflq LIKE CONCAT('%', #{org}, '%')) </if>" +
            "<if test='keyword != null'>AND (u.name LIKE CONCAT('%', #{keyword}, '%') OR EXISTS (SELECT 1 FROM " + T_MEMBER_REL + " relkw " +
            "JOIN " + T_USER + " rukw ON rukw.id = relkw.rel_id AND rukw.corp_code = 'hltgq' " +
            "WHERE relkw.biz_id = s.id AND rukw.name LIKE CONCAT('%', #{keyword}, '%'))) </if>" +
            "ORDER BY COALESCE(s.tneakp_min, s.owcvsv) DESC NULLS LAST, s.id DESC " +
            "LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<ScheduleListRowVO> selectSchedulePage(@Param("dayStart") String dayStart,
                                               @Param("dayEnd") String dayEnd,
                                               @Param("shiftCode") String shiftCode,
                                               @Param("status") String status,
                                               @Param("org") String org,
                                               @Param("keyword") String keyword,
                                               @Param("size") int size,
                                               @Param("offset") int offset,
                                               @Param("now") LocalDateTime now);

    /** 排班详情（编辑/查看回显；成员 id/姓名两数组按 nature_order 对齐）。 */
    @Select("SELECT s.id, " +
            "to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') AS dutyDateRaw, " +
            "to_char(s.tneakp_min, 'YYYY-MM-DD HH24:MI:SS') AS tneakpMinRaw, " +
            "to_char(s.tneakp_max, 'YYYY-MM-DD HH24:MI:SS') AS tneakpMaxRaw, " +
            "s.fjyyhu AS shiftCode, s.peuzwi AS statusCode, " +
            "s.jepbhm AS orgId, o.name AS orgName, s.ihdflq AS legacyUnit, " +
            "s.alidpq AS leaderId, u.name AS leaderName, " +
            "(SELECT string_agg(rel.rel_id, ',' ORDER BY rel.nature_order) " +
            "   FROM " + T_MEMBER_REL + " rel WHERE rel.biz_id = s.id) AS memberIdsRaw, " +
            "(SELECT string_agg(COALESCE(ru.name, rel.rel_id), '、' ORDER BY rel.nature_order) " +
            "   FROM " + T_MEMBER_REL + " rel LEFT JOIN " + T_USER + " ru ON ru.id = rel.rel_id " +
            "  WHERE rel.biz_id = s.id) AS memberNamesRaw " +
            "FROM " + T_SCHEDULE + " s " +
            "LEFT JOIN " + T_ORG + " o ON o.id = s.jepbhm " +
            "LEFT JOIN " + T_USER + " u ON u.id = s.alidpq " +
            "WHERE s.id = #{id} AND s.corp_code = 'hltgq'")
    ScheduleDetailVO selectScheduleDetail(@Param("id") String id);

    // ==================== 内部行查询（校验 / 复制源 / 今日判定） ====================

    /** 单条排班内部行（编辑/删除校验、开始值班复制源）。 */
    @Select("SELECT s.id, s.peuzwi AS statusCode, s.fjyyhu AS shiftCode, " +
            "s.jepbhm AS orgId, o.name AS orgName, s.ihdflq AS legacyUnit, " +
            "s.alidpq AS leaderId, u.name AS leaderName, " +
            "to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') AS dutyDateRaw, " +
            "to_char(s.tneakp_min, 'YYYY-MM-DD HH24:MI:SS') AS tneakpMinRaw, " +
            "to_char(s.tneakp_max, 'YYYY-MM-DD HH24:MI:SS') AS tneakpMaxRaw " +
            "FROM " + T_SCHEDULE + " s " +
            "LEFT JOIN " + T_ORG + " o ON o.id = s.jepbhm " +
            "LEFT JOIN " + T_USER + " u ON u.id = s.alidpq " +
            "WHERE s.id = #{id} AND s.corp_code = 'hltgq'")
    ScheduleRowVO selectScheduleRow(@Param("id") String id);

    /**
     * 人员时间冲突校验（保存排班用）：查找与候选时间区间 [startRaw, endRaw) 重叠、
     * 且候选人员（带班领导或值班人员）在其中出现的其他排班（编辑时经 excludeId 排除自身）；
     * 命中返回冲突行（含 conflictNamesRaw 重叠人员姓名聚合），未命中返回 null。
     * <p>时间重叠判定：候选开始 &lt; 对方结束 且 对方开始 &lt; 候选结束（首尾相接不算重叠）；
     * 无时间的历史排班不参与；多条命中取开始时间最早一条。
     */
    @Select("<script>" +
            "SELECT s.id, " +
            "to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') AS dutyDateRaw, " +
            "s.fjyyhu AS shiftCode, " +
            "to_char(s.tneakp_min, 'YYYY-MM-DD HH24:MI:SS') AS tneakpMinRaw, " +
            "to_char(s.tneakp_max, 'YYYY-MM-DD HH24:MI:SS') AS tneakpMaxRaw, " +
            "COALESCE(o.name, s.ihdflq) AS orgName, " +
            "(SELECT string_agg(x.name, '、') FROM " + T_USER + " x " +
            "  WHERE x.corp_code = 'hltgq' AND x.id IN " +
            "<foreach collection='userIds' item='uid' open='(' separator=',' close=')'>#{uid}</foreach>" +
            "  AND (x.id = s.alidpq OR EXISTS (SELECT 1 FROM " + T_MEMBER_REL + " rc " +
            "       WHERE rc.biz_id = s.id AND rc.rel_id = x.id))) AS conflictNamesRaw " +
            "FROM " + T_SCHEDULE + " s " +
            "LEFT JOIN " + T_ORG + " o ON o.id = s.jepbhm " +
            "WHERE s.corp_code = 'hltgq' " +
            "<if test='excludeId != null'>AND s.id != #{excludeId} </if>" +
            "AND to_char(COALESCE(s.tneakp_min, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') &lt; #{endRaw} " +
            "AND #{startRaw} &lt; to_char(COALESCE(s.tneakp_max, s.tneakp_min, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') " +
            "AND (s.alidpq IN " +
            "<foreach collection='userIds' item='lid' open='(' separator=',' close=')'>#{lid}</foreach>" +
            "OR EXISTS (SELECT 1 FROM " + T_MEMBER_REL + " r WHERE r.biz_id = s.id AND r.rel_id IN " +
            "<foreach collection='userIds' item='mid' open='(' separator=',' close=')'>#{mid}</foreach>)) " +
            "ORDER BY to_char(COALESCE(s.tneakp_min, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') ASC NULLS LAST " +
            "LIMIT 1" +
            "</script>")
    ScheduleRowVO selectScheduleConflict(@Param("excludeId") String excludeId,
                                         @Param("startRaw") String startRaw,
                                         @Param("endRaw") String endRaw,
                                         @Param("userIds") List<String> userIds);

    /**
     * 今日与我相关的排班（今日值班判定 / 值班管理页签到提醒）：
     * 我参与（带班领导或值班人员）且满足其一 ——
     * ① 未开始/待值班且班次时间与今日相交；② 值班中（#cmiu#，不限时间，兜底）。
     * （start &lt; 明日零点 且 end ≧ 今日零点；coalesce 兜底历史数据，无时间行不判定）。
     * 排序：待值班（#zzkl#）→ 未开始（#1#）→ 值班中（#cmiu# 兜底），再按开始时间升序
     * （同一天多条并存时，提醒优先指向待签到的班）。
     * <p>附 memberNamesRaw（成员姓名聚合，交接班页未开始卡片直接展示，免二次查询）。
     */
    @Select("SELECT s.id, s.peuzwi AS statusCode, s.fjyyhu AS shiftCode, " +
            "s.jepbhm AS orgId, o.name AS orgName, s.ihdflq AS legacyUnit, " +
            "s.alidpq AS leaderId, u.name AS leaderName, " +
            "to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') AS dutyDateRaw, " +
            "to_char(s.tneakp_min, 'YYYY-MM-DD HH24:MI:SS') AS tneakpMinRaw, " +
            "to_char(s.tneakp_max, 'YYYY-MM-DD HH24:MI:SS') AS tneakpMaxRaw, " +
            "(SELECT string_agg(COALESCE(ru.name, rel.rel_id), '、' ORDER BY rel.nature_order) " +
            "   FROM " + T_MEMBER_REL + " rel LEFT JOIN " + T_USER + " ru ON ru.id = rel.rel_id " +
            "  WHERE rel.biz_id = s.id) AS memberNamesRaw " +
            "FROM " + T_SCHEDULE + " s " +
            "LEFT JOIN " + T_ORG + " o ON o.id = s.jepbhm " +
            "LEFT JOIN " + T_USER + " u ON u.id = s.alidpq " +
            "WHERE s.corp_code = 'hltgq' " +
            "AND (s.alidpq = #{userId} OR EXISTS (SELECT 1 FROM " + T_MEMBER_REL + " rel " +
            "     WHERE rel.biz_id = s.id AND rel.rel_id = #{userId})) " +
            "AND (s.peuzwi = '#cmiu#' OR " +
            "     (s.peuzwi IN ('#1#', '#zzkl#') " +
            "      AND to_char(COALESCE(s.tneakp_min, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') < #{dayEnd} " +
            "      AND to_char(COALESCE(s.tneakp_max, s.tneakp_min, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') >= #{dayStart})) " +
            "ORDER BY CASE s.peuzwi WHEN '#zzkl#' THEN 0 WHEN '#1#' THEN 1 ELSE 2 END, " +
            "COALESCE(s.tneakp_min, s.owcvsv) ASC NULLS LAST " +
            "LIMIT 1")
    ScheduleRowVO selectMyTodaySchedule(@Param("userId") String userId,
                                        @Param("dayStart") String dayStart,
                                        @Param("dayEnd") String dayEnd);

    /**
     * 今日待签到排班（登录提醒「待签到优先」判定：交接班卡片/登录提醒的 notstarted 态来源）：
     * 我参与（带班领导或值班人员）且未开始/待值班、班次时间与今日相交
     * （start &lt; 明日零点 且 end ≧ 今日零点；无时间行不判定）。
     * 排序：待值班（#zzkl#，上一班交班指定接班）→ 未开始（#1#），按开始时间升序取一条。
     * <p>与 {@link #selectMyTodaySchedule} 的区别：不含「值班中兜底」分支，
     * 保证同一天多条并存时待签到的班优先返回、不被值班中的班遮挡。
     */
    @Select("SELECT s.id, s.peuzwi AS statusCode, s.fjyyhu AS shiftCode, " +
            "s.jepbhm AS orgId, o.name AS orgName, s.ihdflq AS legacyUnit, " +
            "s.alidpq AS leaderId, u.name AS leaderName, " +
            "to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') AS dutyDateRaw, " +
            "to_char(s.tneakp_min, 'YYYY-MM-DD HH24:MI:SS') AS tneakpMinRaw, " +
            "to_char(s.tneakp_max, 'YYYY-MM-DD HH24:MI:SS') AS tneakpMaxRaw, " +
            "(SELECT string_agg(COALESCE(ru.name, rel.rel_id), '、' ORDER BY rel.nature_order) " +
            "   FROM " + T_MEMBER_REL + " rel LEFT JOIN " + T_USER + " ru ON ru.id = rel.rel_id " +
            "  WHERE rel.biz_id = s.id) AS memberNamesRaw " +
            "FROM " + T_SCHEDULE + " s " +
            "LEFT JOIN " + T_ORG + " o ON o.id = s.jepbhm " +
            "LEFT JOIN " + T_USER + " u ON u.id = s.alidpq " +
            "WHERE s.corp_code = 'hltgq' " +
            "AND (s.alidpq = #{userId} OR EXISTS (SELECT 1 FROM " + T_MEMBER_REL + " rel " +
            "     WHERE rel.biz_id = s.id AND rel.rel_id = #{userId})) " +
            "AND s.peuzwi IN ('#1#', '#zzkl#') " +
            "AND to_char(COALESCE(s.tneakp_min, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') < #{dayEnd} " +
            "AND to_char(COALESCE(s.tneakp_max, s.tneakp_min, s.owcvsv), 'YYYY-MM-DD HH24:MI:SS') >= #{dayStart} " +
            "ORDER BY CASE s.peuzwi WHEN '#zzkl#' THEN 0 ELSE 1 END, " +
            "COALESCE(s.tneakp_min, s.owcvsv) ASC NULLS LAST " +
            "LIMIT 1")
    ScheduleRowVO selectMyPendingTodaySchedule(@Param("userId") String userId,
                                               @Param("dayStart") String dayStart,
                                               @Param("dayEnd") String dayEnd);

    /** 排班的值班人员 id（nature_order 序；开始值班时复制到值班记录）。 */
    @Select("SELECT rel.rel_id FROM " + T_MEMBER_REL + " rel WHERE rel.biz_id = #{bizId} ORDER BY rel.nature_order")
    List<String> selectMemberIds(@Param("bizId") String bizId);

    /**
     * 交班匹配（精确）：单位 + 班次起止时间精确相等（记录值从排班复制，必然命中）。
     * 返回排班 id；未命中返回 null。
     */
    @Select("SELECT s.id FROM " + T_SCHEDULE + " s " +
            "WHERE s.corp_code = 'hltgq' AND s.jepbhm = #{orgId} " +
            "AND to_char(s.tneakp_min, 'YYYY-MM-DD HH24:MI:SS') = #{startRaw} " +
            "AND to_char(s.tneakp_max, 'YYYY-MM-DD HH24:MI:SS') = #{endRaw} LIMIT 1")
    String selectScheduleIdByMatch(@Param("orgId") String orgId,
                                   @Param("startRaw") String startRaw,
                                   @Param("endRaw") String endRaw);

    /**
     * 交班匹配（降级）：精确匹配不到（历史数据新列为空）时，用「同单位 + 进行态 + 值班日期相同」
     * 再试一次（#cmiu# 优先）。
     */
    @Select("SELECT s.id, s.peuzwi AS statusCode, " +
            "to_char(s.tneakp_max, 'YYYY-MM-DD HH24:MI:SS') AS tneakpMaxRaw " +
            "FROM " + T_SCHEDULE + " s " +
            "WHERE s.corp_code = 'hltgq' AND s.jepbhm = #{orgId} " +
            "AND s.peuzwi IN ('#cmiu#', '#zzkl#') " +
            "AND to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD') >= #{dayStart} " +
            "AND to_char(COALESCE(s.idjwlq, s.owcvsv), 'YYYY-MM-DD') < #{dayEnd} " +
            "ORDER BY CASE s.peuzwi WHEN '#cmiu#' THEN 0 ELSE 1 END, s.tneakp_min ASC NULLS LAST " +
            "LIMIT 1")
    ScheduleRowVO selectScheduleByFallback(@Param("orgId") String orgId,
                                           @Param("dayStart") String dayStart,
                                           @Param("dayEnd") String dayEnd);

    /**
     * 同单位下一班（未开始、开始时间不早于交班班次结束时间，时间最近一条）：
     * 交班时自动置「待值班」，同时作为交班弹窗接班人员的默认候选来源。
     * <p>excludeId 为空（记录未匹配到排班）时不拼接排除条件（避免 PG 对无上下文参数
     * 无法推断类型的 {@code ? IS NULL} 写法）。
     */
    @Select("<script>" +
            "SELECT s.id FROM " + T_SCHEDULE + " s " +
            "WHERE s.corp_code = 'hltgq' AND s.jepbhm = #{orgId} AND s.peuzwi = '#1#' " +
            "AND to_char(s.tneakp_min, 'YYYY-MM-DD HH24:MI:SS') >= #{afterRaw} " +
            "<if test='excludeId != null'>AND s.id != #{excludeId} </if>" +
            "ORDER BY s.tneakp_min ASC LIMIT 1" +
            "</script>")
    String selectNextScheduleId(@Param("orgId") String orgId,
                                @Param("afterRaw") String afterRaw,
                                @Param("excludeId") String excludeId);

    // ==================== 排班写入（主写新列 + 兼容双写旧列） ====================

    /**
     * 新增排班：主写 idjwlq/tneakp/自定义列与 peuzwi=#1#；
     * 兼容双写 owcvsv（值班日期)/itxmyy（班次时间文本)/ihdflq（单位名称）—— H5 值班提醒依赖（§2.3）。
     */
    @Insert("INSERT INTO " + T_SCHEDULE + " " +
            "(id, corp_code, created_at, created_by, updated_at, updated_by, " +
            " idjwlq, tneakp_min, tneakp_max, fjyyhu, jepbhm, alidpq, peuzwi, " +
            " owcvsv, itxmyy, ihdflq) " +
            "VALUES (#{id}, #{corpCode}, #{now}, #{operator}, #{now}, #{operator}, " +
            " #{dutyDate}, #{timeStart}, #{timeEnd}, #{shiftCode}, #{orgId}, #{leaderId}, '#1#', " +
            " #{dutyDate}, #{shiftTimeText}, #{orgName})")
    int insertSchedule(@Param("id") String id,
                       @Param("corpCode") String corpCode,
                       @Param("operator") String operator,
                       @Param("now") LocalDateTime now,
                       @Param("dutyDate") LocalDateTime dutyDate,
                       @Param("timeStart") LocalDateTime timeStart,
                       @Param("timeEnd") LocalDateTime timeEnd,
                       @Param("shiftCode") String shiftCode,
                       @Param("orgId") String orgId,
                       @Param("leaderId") String leaderId,
                       @Param("shiftTimeText") String shiftTimeText,
                       @Param("orgName") String orgName);

    /** 编辑排班（仅未开始/待值班可编辑，服务层校验；状态不动；同步刷新兼容双写旧列）。 */
    @Update("UPDATE " + T_SCHEDULE + " SET updated_at = #{now}, updated_by = #{operator}, " +
            "idjwlq = #{dutyDate}, tneakp_min = #{timeStart}, tneakp_max = #{timeEnd}, " +
            "fjyyhu = #{shiftCode}, jepbhm = #{orgId}, alidpq = #{leaderId}, " +
            "owcvsv = #{dutyDate}, itxmyy = #{shiftTimeText}, ihdflq = #{orgName} " +
            "WHERE id = #{id} AND corp_code = 'hltgq'")
    int updateSchedule(@Param("id") String id,
                       @Param("operator") String operator,
                       @Param("now") LocalDateTime now,
                       @Param("dutyDate") LocalDateTime dutyDate,
                       @Param("timeStart") LocalDateTime timeStart,
                       @Param("timeEnd") LocalDateTime timeEnd,
                       @Param("shiftCode") String shiftCode,
                       @Param("orgId") String orgId,
                       @Param("leaderId") String leaderId,
                       @Param("shiftTimeText") String shiftTimeText,
                       @Param("orgName") String orgName);

    /** 删除排班（仅未开始/待值班可删除，服务层校验；关系数据由调用方同事务清理）。 */
    @Delete("DELETE FROM " + T_SCHEDULE + " WHERE id = #{id} AND corp_code = 'hltgq'")
    int deleteSchedule(@Param("id") String id);

    /** 排班状态置位（交班置已完成 / 下一班置待值班，通用）。 */
    @Update("UPDATE " + T_SCHEDULE + " SET peuzwi = #{status}, updated_at = #{now}, updated_by = #{operator} " +
            "WHERE id = #{id} AND corp_code = 'hltgq'")
    int updateScheduleStatus(@Param("id") String id,
                             @Param("status") String status,
                             @Param("operator") String operator,
                             @Param("now") LocalDateTime now);

    /** 开始值班：排班置「值班中」并写 H5 提醒标记 hfxuuk=#papu#（H5 提醒过滤依赖该标记）。 */
    @Update("UPDATE " + T_SCHEDULE + " SET peuzwi = '#cmiu#', hfxuuk = '#papu#', " +
            "updated_at = #{now}, updated_by = #{operator} " +
            "WHERE id = #{id} AND corp_code = 'hltgq'")
    int markScheduleOnDuty(@Param("id") String id,
                           @Param("operator") String operator,
                           @Param("now") LocalDateTime now);

    // ==================== 值班人员关系表（多选中间表） ====================

    /** 清理排班的值班人员关系（编辑重写/删除排班时先删后插）。 */
    @Delete("DELETE FROM " + T_MEMBER_REL + " WHERE biz_id = #{bizId}")
    int deleteMembers(@Param("bizId") String bizId);

    /** 值班人员关系写入（biz_id = 排班 id，rel_id = 用户 id，nature_order 从 1 递增）。 */
    @Insert("INSERT INTO " + T_MEMBER_REL + " " +
            "(id, corp_code, created_at, created_by, updated_at, updated_by, biz_id, rel_id, nature_order) " +
            "VALUES (#{id}, #{corpCode}, #{now}, #{operator}, #{now}, #{operator}, #{bizId}, #{relId}, #{natureOrder})")
    int insertMember(@Param("id") String id,
                     @Param("bizId") String bizId,
                     @Param("relId") String relId,
                     @Param("natureOrder") String natureOrder,
                     @Param("corpCode") String corpCode,
                     @Param("now") LocalDateTime now,
                     @Param("operator") String operator);
}
