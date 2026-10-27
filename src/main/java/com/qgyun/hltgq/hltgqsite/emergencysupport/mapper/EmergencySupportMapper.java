package com.qgyun.hltgq.hltgqsite.emergencysupport.mapper;

import com.qgyun.hltgq.hltgqsite.emergencysupport.vo.EmergencyAttachmentVO;
import com.qgyun.hltgq.hltgqsite.emergencysupport.vo.EmergencyMaterialOptionVO;
import com.qgyun.hltgq.hltgqsite.emergencysupport.vo.EmergencySupportVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.InventoryCategory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 应急保障（t_auto_hltgq_bcmxdv_ifjcmy）+ 附件（t_auto_hltgq_water_emergency_attachment）持久层。
 * <p>与低代码共用主表：列名沿用平台拼音列（diziai/pxsefg/haqjle/...），编码列存选项组编码
 * （如 {@code #nkzk#}），中文翻译由 Service 经 {@code EmergencySupportDict} 完成。
 * <p>关键口径：
 * <ul>
 *   <li><b>主表无 del_flag</b>：删除为物理删除（附件行一并清理，文件由 Service 提交后删）；</li>
 *   <li><b>编号只读</b>：diziai 由取号服务生成（YJBZ-yyyyMMdd-当日序号），更新语句不触碰该列；</li>
 *   <li><b>onizsz</b> 物理类型为 timestamp，写入统一 {@code CAST(... AS date)}、读出 to_char，
 *       与既有 4 条历史数据（00:00:00）口径一致；</li>
 *   <li><b>物资装备联动</b>：候选来自应急物资台账行（目录 type = #2#），
 *       数量取台账当前库存，保证带出即最新；</li>
 *   <li>模糊匹配一律 CONCAT，禁用 {@code ||}。</li>
 * </ul>
 */
public interface EmergencySupportMapper {

    String T_SUPPORT = "\"qixiao-apaas\".\"t_auto_hltgq_bcmxdv_ifjcmy\"";
    String T_ATTACH = "\"qixiao-apaas\".\"t_auto_hltgq_water_emergency_attachment\"";
    String T_LEDGER = "\"qixiao-apaas\".\"t_auto_hltgq_knc3g_tzkioe\"";
    String T_MATERIAL = "\"qixiao-apaas\".\"t_auto_hltgq_knc3g_slaifi\"";
    String T_WAREHOUSE = "\"qixiao-apaas\".\"t_auto_hltgq_knc3g_twufyx\"";

    /** 列表条件（page/count 共用，作用于主表别名 s：类别 / 状态 / 编号或名称关键字） */
    String LIST_CONDITIONS =
            "<if test='category != null and category != \"\"'>AND s.pxsefg = #{category} </if>" +
            "<if test='status != null and status != \"\"'>AND s.gmypct = #{status} </if>" +
            "<if test='keyword != null and keyword != \"\"'>AND (s.haqjle LIKE CONCAT('%', #{keyword}, '%') " +
            "OR s.diziai LIKE CONCAT('%', #{keyword}, '%')) </if>";

    /** 列表/详情公共投影（编码中文由 Service 翻译） */
    String SUPPORT_COLUMNS =
            "s.id AS id, s.diziai AS code, s.pxsefg AS categoryCode, s.haqjle AS name, " +
            "s.fhlhmx AS content, s.obhwqq AS capacity, s.bljckc AS owner, s.btjuks AS scene, " +
            "s.gmypct AS statusCode, to_char(s.onizsz, 'YYYY-MM-DD') AS checkDate, s.zfvgfx AS usage, " +
            "to_char(s.created_at, 'YYYY-MM-DD HH24:MI') AS createdAt ";

    // ==================== 列表 / 详情 ====================

    /** 应急保障分页（创建时间倒序；类别/状态为编码等值，关键字模糊编号或名称） */
    @Select("<script>" +
            "SELECT " + SUPPORT_COLUMNS +
            "FROM " + T_SUPPORT + " s " +
            "WHERE s.corp_code = 'hltgq' " + LIST_CONDITIONS +
            "ORDER BY s.created_at DESC, s.id DESC " +
            "LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<EmergencySupportVO> selectPage(@Param("category") String category,
                                        @Param("status") String status,
                                        @Param("keyword") String keyword,
                                        @Param("size") int size,
                                        @Param("offset") long offset);

    /** 应急保障计数（条件与 {@link #selectPage} 完全一致） */
    @Select("<script>SELECT COUNT(*) FROM " + T_SUPPORT + " s WHERE s.corp_code = 'hltgq' " +
            LIST_CONDITIONS + "</script>")
    long countPage(@Param("category") String category,
                   @Param("status") String status,
                   @Param("keyword") String keyword);

    /** 单条记录（不存在返回 null；附件由 {@link #selectAttachments} 单独装配） */
    @Select("SELECT " + SUPPORT_COLUMNS +
            "FROM " + T_SUPPORT + " s " +
            "WHERE s.id = #{id} AND s.corp_code = 'hltgq'")
    EmergencySupportVO selectById(@Param("id") String id);

    // ==================== 写入 ====================

    /**
     * 新增记录（编号由取号服务生成，只读）。
     * row key：id/code/category/name/content/capacity/owner/scene/status/check_date/usage/now/operator。
     */
    @Insert("INSERT INTO " + T_SUPPORT + " " +
            "(\"id\", \"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\", " +
            "\"diziai\", \"pxsefg\", \"haqjle\", \"fhlhmx\", \"obhwqq\", \"bljckc\", \"btjuks\", \"gmypct\", " +
            "\"onizsz\", \"zfvgfx\") " +
            "VALUES (#{row.id}, 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator}, " +
            "#{row.code}, #{row.category}, #{row.name}, #{row.content}, #{row.capacity}, #{row.owner}, " +
            "#{row.scene}, #{row.status}, CAST(#{row.check_date} AS date), #{row.usage})")
    int insertSupport(@Param("row") Map<String, Object> row);

    /**
     * 编辑记录（不触碰编号 diziai 与创建审计列）。
     * row key：id/category/name/content/capacity/owner/scene/status/check_date/usage/now/operator。
     */
    @Update("UPDATE " + T_SUPPORT + " SET " +
            "pxsefg = #{row.category}, haqjle = #{row.name}, fhlhmx = #{row.content}, " +
            "obhwqq = #{row.capacity}, bljckc = #{row.owner}, btjuks = #{row.scene}, " +
            "gmypct = #{row.status}, onizsz = CAST(#{row.check_date} AS date), zfvgfx = #{row.usage}, " +
            "updated_at = #{row.now}, updated_by = #{row.operator} " +
            "WHERE id = #{row.id} AND corp_code = 'hltgq'")
    int updateSupport(@Param("row") Map<String, Object> row);

    /** 删除记录（主表无 del_flag，物理删除；附件行由 Service 先行清理） */
    @Delete("DELETE FROM " + T_SUPPORT + " WHERE id = #{id} AND corp_code = 'hltgq'")
    int deleteSupport(@Param("id") String id);

    // ==================== 附件 ====================

    /**
     * 附件表是否已建（存量环境未执行 emergency-support-ddl.sql 的降级判断）。
     * <p>查系统表本身不会因缺表报错；用于在主事务内提前跳过附件 SQL，
     * 避免缺表语句把 KES/PG 事务打成 aborted 状态（后续 COMMIT 静默变 ROLLBACK）。
     */
    @Select("SELECT COUNT(*) FROM information_schema.tables " +
            "WHERE table_schema = 'qixiao-apaas' " +
            "AND table_name = 't_auto_hltgq_water_emergency_attachment'")
    int countAttachmentTable();

    /** 记录附件清单（详情/编辑管理用；不含 file_path，下载走 {@link #selectAttachmentById}） */
    @Select("SELECT a.id AS id, a.support_id AS supportId, a.file_name AS fileName, " +
            "a.file_size AS fileSize, a.file_ext AS fileExt " +
            "FROM " + T_ATTACH + " a " +
            "WHERE a.support_id = #{supportId} AND a.corp_code = 'hltgq' " +
            "ORDER BY a.created_at ASC, a.id ASC")
    List<EmergencyAttachmentVO> selectAttachments(@Param("supportId") String supportId);

    /** 记录删除前的附件清理清单（含 file_path，供提交后物理删文件） */
    @Select("SELECT a.id AS id, a.support_id AS supportId, a.file_name AS fileName, a.file_path AS filePath " +
            "FROM " + T_ATTACH + " a " +
            "WHERE a.support_id = #{supportId} AND a.corp_code = 'hltgq' " +
            "ORDER BY a.created_at ASC, a.id ASC")
    List<EmergencyAttachmentVO> selectAttachmentsForCleanup(@Param("supportId") String supportId);

    /** 列表附件批量查询（一条 SQL 取当前页全部记录附件，按 supportId 分组由 Service 装配） */
    @Select("<script>SELECT a.id AS id, a.support_id AS supportId, a.file_name AS fileName, " +
            "a.file_size AS fileSize, a.file_ext AS fileExt " +
            "FROM " + T_ATTACH + " a " +
            "WHERE a.corp_code = 'hltgq' AND a.support_id IN " +
            "<foreach collection='supportIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach> " +
            "ORDER BY a.created_at ASC, a.id ASC</script>")
    List<EmergencyAttachmentVO> selectAttachmentsBySupportIds(@Param("supportIds") List<String> supportIds);

    /** 单个附件（下载/删除/编辑绑定前的行查询，含 file_path） */
    @Select("SELECT a.id AS id, a.support_id AS supportId, a.file_name AS fileName, a.file_path AS filePath, " +
            "a.file_size AS fileSize, a.file_ext AS fileExt " +
            "FROM " + T_ATTACH + " a " +
            "WHERE a.id = #{id} AND a.corp_code = 'hltgq'")
    EmergencyAttachmentVO selectAttachmentById(@Param("id") String id);

    /**
     * 附件行（上传成功即写入，support_id 为空；保存/编辑时由 {@link #bindAttachmentSupport} 回填）。
     * row key：id/support_id/file_name/file_path/file_size/file_ext/now/operator。
     */
    @Insert("INSERT INTO " + T_ATTACH + " " +
            "(\"id\", \"support_id\", \"file_name\", \"file_path\", \"file_size\", \"file_ext\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.support_id}, #{row.file_name}, #{row.file_path}, #{row.file_size}, " +
            "#{row.file_ext}, 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertAttachment(@Param("row") Map<String, Object> row);

    /** 附件回填归属（仅允许无归属或本记录附件，防跨记录占用；0 行 = 已被占用/不存在） */
    @Update("UPDATE " + T_ATTACH + " SET support_id = #{supportId}, updated_at = #{now}, updated_by = #{operator} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND (support_id IS NULL OR support_id = #{supportId})")
    int bindAttachmentSupport(@Param("id") String id,
                              @Param("supportId") String supportId,
                              @Param("operator") String operator,
                              @Param("now") LocalDateTime now);

    /** 附件行删除（物理删除；文件由 Service 事务提交后删） */
    @Delete("DELETE FROM " + T_ATTACH + " WHERE id = #{id} AND corp_code = 'hltgq'")
    int deleteAttachment(@Param("id") String id);

    /** 附件行删除（仅限未归属附件：并发绑定后返回 0 行，由 Service 拒绝移除且不删文件） */
    @Delete("DELETE FROM " + T_ATTACH + " WHERE id = #{id} AND corp_code = 'hltgq' AND support_id IS NULL")
    int deleteUnboundAttachment(@Param("id") String id);

    /** 附件行按记录批量删除（删除记录时使用；文件由 Service 提交后删） */
    @Delete("DELETE FROM " + T_ATTACH + " WHERE support_id = #{supportId} AND corp_code = 'hltgq'")
    int deleteAttachmentsBySupport(@Param("supportId") String supportId);

    // ==================== 物资装备联动候选 ====================

    /**
     * 应急物资台账候选（仅目录类别为应急物资的台账行；数量即当前库存，选择后自动带出）。
     * <p>按「物资 + 仓库」逐行返回：同一物资在不同仓库数量不同，带出值必须与所选行一致；
     * 关键字模糊物资编号或台账名称；LIMIT 500 防御超长下拉。
     */
    @Select("<script>" +
            "SELECT t.id AS ledgerId, t.jzikio AS materialId, m.higsdd AS code, t.wpxwwi AS name, " +
            "t.hughww AS spec, t.mddokm AS unit, CAST(t.ejgxlq AS numeric(20,0)) AS quantity, " +
            "t.oywydb AS warehouseId, w.xoysox AS warehouseName " +
            "FROM " + T_LEDGER + " t " +
            "LEFT JOIN " + T_MATERIAL + " m ON m.id = t.jzikio AND m.corp_code = 'hltgq' " +
            "LEFT JOIN " + T_WAREHOUSE + " w ON w.id = t.oywydb AND w.corp_code = 'hltgq' " +
            "WHERE t.corp_code = 'hltgq' AND " + InventoryCategory.CONDITION_EMERGENCY + " " +
            "<if test='keyword != null and keyword != \"\"'>AND (m.higsdd LIKE CONCAT('%', #{keyword}, '%') " +
            "OR t.wpxwwi LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY m.higsdd ASC, t.id ASC " +
            "LIMIT 500" +
            "</script>")
    List<EmergencyMaterialOptionVO> selectEmergencyMaterialOptions(@Param("keyword") String keyword);
}
