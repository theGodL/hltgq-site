package com.qgyun.hltgq.hltgqsite.watersaving.mapper;

import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingSchemeVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 节水方案（t_auto_hltgq_water_saving_scheme）。表结构见 init.sql 节水管理 5.2。
 * <p>名称年度内唯一（uk_water_saving_scheme_year_name 兜底并发）；"year" 为保留字，SQL 全程加引号。
 * <p>核验作废（方案A）：年度记录水量变更 / 编辑换依据 / 刷新对比依据 → status '#4#'、verify_* 保留、
 * verify_invalid '#1#'（作废）/ '#2#'（有效）；达标展示由 Service 按优先级链派生（不落库）。
 * <p>指标联动：indicator_ids 为 JSON 数组文本（["id",…]），反查用引号包裹 id 精确匹配（防子串误配）。
 */
public interface SavingSchemeMapper {

    String T_SCHEME = "\"qixiao-apaas\".\"t_auto_hltgq_water_saving_scheme\"";

    /** 列（列表/详情共用；别名 s；indicator_ids 原文由 Service 解析） */
    String BASE_COLS = "s.id AS id, s.name AS name, s.\"year\" AS year, s.evidence_id AS evidenceId, " +
            "s.baseline_volume AS baselineVolume, s.target_volume AS targetVolume, s.owner AS owner, " +
            "s.measure AS measure, s.status AS status, s.indicator_ids AS indicatorIdsJson, " +
            "s.verify_after_volume AS verifyAfterVolume, s.verify_source AS verifySource, " +
            "s.verify_note AS verifyNote, to_char(s.verify_date, 'YYYY-MM-DD') AS verifyDate, " +
            "s.verify_comparable AS verifyComparable, s.verify_invalid AS verifyInvalid, " +
            "to_char(s.last_recalc_at, 'YYYY-MM-DD HH24:MI') AS lastRecalcText, " +
            "s.last_recalc_reason AS lastRecalcReason, s.is_demo AS isDemo, " +
            "s.attachment_name AS attachmentName, " +
            "to_char(s.created_at, 'YYYY-MM-DD HH24:MI') AS createdText, " +
            "to_char(s.updated_at, 'YYYY-MM-DD HH24:MI') AS updatedText";

    /** 列表条件（count/page 共用，作用于别名 s） */
    String LIST_CONDITIONS =
            "<if test='year != null'>AND s.\"year\" = #{year} </if>" +
            "<if test='name != null and name != \"\"'>AND s.name LIKE CONCAT('%', #{name}, '%') </if>" +
            "<if test='owner != null and owner != \"\"'>AND s.owner LIKE CONCAT('%', #{owner}, '%') </if>" +
            "<if test='status != null and status != \"\"'>AND s.status = #{status} </if>";

    /** 列表分页（创建时间倒序） */
    @Select("<script>SELECT " + BASE_COLS + " FROM " + T_SCHEME + " s WHERE s.corp_code = 'hltgq' " +
            LIST_CONDITIONS + "ORDER BY s.created_at DESC, s.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<SavingSchemeVO> selectPage(@Param("year") Integer year,
                                    @Param("name") String name,
                                    @Param("owner") String owner,
                                    @Param("status") String status,
                                    @Param("size") int size,
                                    @Param("offset") long offset);

    /** 列表计数（条件与 {@link #selectPage} 一致） */
    @Select("<script>SELECT COUNT(*) FROM " + T_SCHEME + " s WHERE s.corp_code = 'hltgq' " +
            LIST_CONDITIONS + "</script>")
    long countPage(@Param("year") Integer year,
                   @Param("name") String name,
                   @Param("owner") String owner,
                   @Param("status") String status);

    /** 按 id 查 */
    @Select("SELECT " + BASE_COLS + " FROM " + T_SCHEME + " s WHERE s.id = #{id} AND s.corp_code = 'hltgq'")
    SavingSchemeVO selectById(@Param("id") String id);

    /** 名称年度内唯一性预检（编辑时排除自身） */
    @Select("<script>SELECT COUNT(*) FROM " + T_SCHEME + " WHERE corp_code = 'hltgq' AND \"year\" = #{year} " +
            "AND name = #{name} <if test='excludeId != null'>AND id != #{excludeId}</if></script>")
    int countByName(@Param("year") int year, @Param("name") String name, @Param("excludeId") String excludeId);

    /** 全量方案（导出；includeDemo=false 时排除虚拟演示数据） */
    @Select("<script>SELECT " + BASE_COLS + " FROM " + T_SCHEME + " s WHERE s.corp_code = 'hltgq' " +
            "<if test='includeDemo == false'>AND s.is_demo != '#1#' </if>" +
            "ORDER BY s.\"year\" DESC, s.created_at DESC, s.id</script>")
    List<SavingSchemeVO> selectForExport(@Param("includeDemo") boolean includeDemo);

    /** 关联指定指标的方案 id（indicator_ids JSON 数组引号包裹精确匹配；
     *  CONCAT 拼接——KingbaseES 下 || 拼接绑定参数会静默退化） */
    @Select("SELECT s.id FROM " + T_SCHEME + " s WHERE s.corp_code = 'hltgq' " +
            "AND s.indicator_ids LIKE CONCAT('%\"', #{indicatorId}, '\"%') ORDER BY s.id")
    List<String> selectIdsByIndicatorRef(@Param("indicatorId") String indicatorId);

    /** 关联指定指标的方案数（指标列表 schemeCount / 删除保护；CONCAT 拼接原因同上） */
    @Select("SELECT COUNT(*) FROM " + T_SCHEME + " WHERE corp_code = 'hltgq' " +
            "AND indicator_ids LIKE CONCAT('%\"', #{indicatorId}, '\"%')")
    int countByIndicatorRef(@Param("indicatorId") String indicatorId);

    /** 新增（row key：id/name/year/evidence_id/baseline_volume/target_volume/owner/measure/status/
     *  indicator_ids/is_demo/now/operator） */
    @Insert("INSERT INTO " + T_SCHEME + " " +
            "(\"id\", \"name\", \"year\", \"evidence_id\", \"baseline_volume\", \"target_volume\", \"owner\", " +
            "\"measure\", \"status\", \"indicator_ids\", \"is_demo\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.name}, #{row.year}, #{row.evidence_id}, #{row.baseline_volume}, " +
            "#{row.target_volume}, #{row.owner}, #{row.measure}, #{row.status}, #{row.indicator_ids}, " +
            "#{row.is_demo}, 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insert(@Param("row") Map<String, Object> row);

    /** 基础字段更新（名称/年度/依据/基线/目标/负责人/措施/指标/标记；状态与核验列由专用语句维护） */
    @Update("UPDATE " + T_SCHEME + " SET \"name\" = #{row.name}, \"year\" = #{row.year}, " +
            "\"evidence_id\" = #{row.evidence_id}, " +
            "\"baseline_volume\" = #{row.baseline_volume}, \"target_volume\" = #{row.target_volume}, " +
            "\"owner\" = #{row.owner}, \"measure\" = #{row.measure}, " +
            "\"indicator_ids\" = #{row.indicator_ids}, \"is_demo\" = #{row.is_demo}, " +
            "\"updated_at\" = #{row.now}, \"updated_by\" = #{row.operator} " +
            "WHERE \"id\" = #{row.id} AND \"corp_code\" = 'hltgq'")
    int updateBase(@Param("row") Map<String, Object> row);

    /** 状态流转（带原状态守卫；影响行数 0 = 状态已变更） */
    @Update("UPDATE " + T_SCHEME + " SET \"status\" = #{toStatus}, \"updated_at\" = #{now}, " +
            "\"updated_by\" = #{operator} WHERE \"id\" = #{id} AND \"status\" = #{fromStatus} " +
            "AND \"corp_code\" = 'hltgq'")
    int updateStatus(@Param("id") String id,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus,
                     @Param("now") LocalDateTime now,
                     @Param("operator") String operator);

    /**
     * 核验动作（写入 verify_*、复位 verify_invalid、驱动状态、留痕）：
     * comparable '#1#' → status '#3#' 已完成；'#2#' → status '#4#' 待复核。
     */
    @Update("UPDATE " + T_SCHEME + " SET \"verify_after_volume\" = #{afterVolume}, " +
            "\"verify_source\" = #{source}, \"verify_note\" = #{note}, " +
            "\"verify_date\" = CAST(#{verifyDate} AS date), \"verify_comparable\" = #{comparable}, " +
            "\"verify_invalid\" = '#2#', \"status\" = #{toStatus}, " +
            "\"last_recalc_at\" = #{now}, \"last_recalc_reason\" = #{reason}, " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"id\" = #{id} AND \"corp_code\" = 'hltgq'")
    int updateVerify(@Param("id") String id,
                     @Param("afterVolume") BigDecimal afterVolume,
                     @Param("source") String source,
                     @Param("note") String note,
                     @Param("verifyDate") String verifyDate,
                     @Param("comparable") String comparable,
                     @Param("toStatus") String toStatus,
                     @Param("reason") String reason,
                     @Param("now") LocalDateTime now,
                     @Param("operator") String operator);

    /**
     * 按年度记录批量作废核验（方案A：年度记录水量变更时）：
     * 仅处理曾核验（verify_after_volume 非空）的方案 → status '#4#'、verify_invalid '#1#'、留痕。
     */
    @Update("UPDATE " + T_SCHEME + " SET \"status\" = '#4#', \"verify_invalid\" = '#1#', " +
            "\"last_recalc_at\" = #{now}, \"last_recalc_reason\" = #{reason}, " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"evidence_id\" = #{evidenceId} AND \"verify_after_volume\" IS NOT NULL " +
            "AND \"corp_code\" = 'hltgq'")
    int invalidateVerifyByEvidence(@Param("evidenceId") String evidenceId,
                                   @Param("reason") String reason,
                                   @Param("now") LocalDateTime now,
                                   @Param("operator") String operator);

    /**
     * 单条作废核验（编辑换依据触发）：仅处理曾核验（verify_after_volume 非空）的方案。
     */
    @Update("UPDATE " + T_SCHEME + " SET \"status\" = '#4#', \"verify_invalid\" = '#1#', " +
            "\"last_recalc_at\" = #{now}, \"last_recalc_reason\" = #{reason}, " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"id\" = #{id} AND \"verify_after_volume\" IS NOT NULL AND \"corp_code\" = 'hltgq'")
    int invalidateVerifyById(@Param("id") String id,
                             @Param("reason") String reason,
                             @Param("now") LocalDateTime now,
                             @Param("operator") String operator);

    /**
     * 刷新对比依据：baseline 取当前记录实际值；曾核验 → 同时作废核验（status '#4#'、verify_invalid '#1#'）；
     * 未核验 → 仅刷新 baseline 并留痕。
     */
    @Update("UPDATE " + T_SCHEME + " SET \"baseline_volume\" = #{baseline}, " +
            "\"status\" = CASE WHEN \"verify_after_volume\" IS NOT NULL THEN '#4#' ELSE \"status\" END, " +
            "\"verify_invalid\" = CASE WHEN \"verify_after_volume\" IS NOT NULL THEN '#1#' ELSE \"verify_invalid\" END, " +
            "\"last_recalc_at\" = #{now}, \"last_recalc_reason\" = #{reason}, " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"id\" = #{id} AND \"corp_code\" = 'hltgq'")
    int refreshBaseline(@Param("id") String id,
                        @Param("baseline") BigDecimal baseline,
                        @Param("reason") String reason,
                        @Param("now") LocalDateTime now,
                        @Param("operator") String operator);

    /** 指标变更联动留痕（逐个方案；达标展示为派生计算，无需落库改状态） */
    @Update("<script>UPDATE " + T_SCHEME + " SET \"last_recalc_at\" = #{now}, " +
            "\"last_recalc_reason\" = #{reason}, \"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"corp_code\" = 'hltgq' AND \"id\" IN " +
            "<foreach collection='ids' item='sid' open='(' separator=',' close=')'>#{sid}</foreach></script>")
    int touchRecalc(@Param("ids") List<String> ids,
                    @Param("reason") String reason,
                    @Param("now") LocalDateTime now,
                    @Param("operator") String operator);

    /** 附件信息更新（上传即替换：name/url 同写） */
    @Update("UPDATE " + T_SCHEME + " SET \"attachment_name\" = #{fileName}, \"attachment_url\" = #{relPath}, " +
            "\"updated_at\" = #{now}, \"updated_by\" = #{operator} " +
            "WHERE \"id\" = #{id} AND \"corp_code\" = 'hltgq'")
    int updateAttachment(@Param("id") String id,
                         @Param("fileName") String fileName,
                         @Param("relPath") String relPath,
                         @Param("now") LocalDateTime now,
                         @Param("operator") String operator);

    /** 物理删除（附件文件由 Service 事务提交后清理） */
    @Delete("DELETE FROM " + T_SCHEME + " WHERE id = #{id} AND corp_code = 'hltgq'")
    int delete(@Param("id") String id);

    /** 附件存储相对路径（替换/删除时清理旧文件用） */
    @Select("SELECT attachment_url FROM " + T_SCHEME + " WHERE id = #{id} AND corp_code = 'hltgq'")
    String selectAttachmentUrl(@Param("id") String id);
}
