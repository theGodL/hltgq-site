package com.qgyun.hltgq.hltgqsite.inventorystocktake.mapper;

import com.qgyun.hltgq.hltgqsite.inventoryorder.InventoryCategory;
import com.qgyun.hltgq.hltgqsite.inventoryorder.mapper.InventoryLedgerMapper;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.StockLedgerOptionVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakeAttachmentVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakeDetailVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakeItemVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakePageItemVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 设备盘点（P6 列表 / P7 详情与保存 / 附件 / 台账带出）持久层。
 * <p>表口径见 init.sql 4.1~4.3；与出入库同底座：编号复用 water_order_no_seq（serial_type=PDD）、
 * 台账复用 tzkioe、流水复用 stock_change_log（盘盈 #3# / 盘亏 #4#，order_kind #3# 盘点单）。
 * <p>关键口径：
 * <ul>
 *   <li><b>账面一致性校验</b>：{@link #selectLedgerRowForUpdate} 以 FOR UPDATE 锁行重读台账实时值，
 *       与页面快照（明细 book_qty）不一致时由 Service 整单拒绝保存，防并发覆盖；</li>
 *   <li><b>台账绝对校正</b>：{@link #updateLedgerAbsolute} 以实盘数直接赋值 ejgxlq（盘点语义，
 *       非出入库的相对增减），并重算 mojktw（&gt; 安全库存 → #1# 正常，否则 → #2# 偏低）；</li>
 *   <li>数量列 double precision 读出统一 CAST numeric(20,0)；模糊匹配一律 CONCAT，禁用 {@code ||}。</li>
 * </ul>
 */
public interface InventoryStocktakeMapper {

    String T_ORDER = "\"qixiao-apaas\".\"t_auto_hltgq_water_stocktake_order\"";
    String T_ITEM = "\"qixiao-apaas\".\"t_auto_hltgq_water_stocktake_order_detail\"";
    String T_ATTACH = "\"qixiao-apaas\".\"t_auto_hltgq_water_stocktake_attachment\"";
    String T_WAREHOUSE = "\"qixiao-apaas\".\"t_auto_hltgq_knc3g_twufyx\"";
    String T_LEDGER = "\"qixiao-apaas\".\"t_auto_hltgq_knc3g_tzkioe\"";
    String T_MATERIAL = "\"qixiao-apaas\".\"t_auto_hltgq_knc3g_slaifi\"";

    /** 盘点单是否含应急物资明细（作用于主表别名 o）：明细按 material_id 回查目录 */
    String HAS_EMERGENCY_ITEM = "EXISTS (SELECT 1 FROM " + T_ITEM + " d JOIN " + T_MATERIAL +
            " m ON m.id = d.material_id AND m.corp_code = 'hltgq' " +
            "WHERE d.order_id = o.id AND d.corp_code = 'hltgq' AND " + InventoryCategory.CONDITION_EMERGENCY + ")";

    /** 视角类别过滤：emergency=true 只看含应急物资明细的盘点单；false 排除（明细未关联物资的历史单据保留） */
    String CATEGORY_FILTER =
            "<if test='emergency != null'><choose>" +
            "<when test='emergency'>AND " + HAS_EMERGENCY_ITEM + " </when>" +
            "<otherwise>AND NOT " + HAS_EMERGENCY_ITEM + " </otherwise>" +
            "</choose></if>";

    /** P6 动态查询条件（page/count 共用，作用于主表别名 o；物资编码/名称按明细级 EXISTS） */
    String LIST_CONDITIONS =
            "<if test='orderNo != null and orderNo != \"\"'>AND o.order_no LIKE CONCAT('%', #{orderNo}, '%') </if>" +
            "<if test='warehouseId != null and warehouseId != \"\"'>AND o.warehouse_id = #{warehouseId} </if>" +
            "<if test='operatorId != null and operatorId != \"\"'>AND o.operator_id = #{operatorId} </if>" +
            "<if test='materialCode != null and materialCode != \"\"'>AND EXISTS (SELECT 1 FROM " + T_ITEM +
            " d WHERE d.order_id = o.id AND d.material_code LIKE CONCAT('%', #{materialCode}, '%')) </if>" +
            "<if test='materialName != null and materialName != \"\"'>AND EXISTS (SELECT 1 FROM " + T_ITEM +
            " d WHERE d.order_id = o.id AND d.material_name LIKE CONCAT('%', #{materialName}, '%')) </if>" +
            "<if test='startDate != null and startDate != \"\"'>AND o.check_date &gt;= CAST(#{startDate} AS date) </if>" +
            "<if test='endDate != null and endDate != \"\"'>AND o.check_date &lt;= CAST(#{endDate} AS date) </if>" +
            CATEGORY_FILTER;

    // ==================== P6 列表 ====================

    /** 盘点单分页（盘点日期倒序；仓库名 LEFT JOIN 不丢行） */
    @Select("<script>" +
            "SELECT o.id AS id, o.order_no AS orderNo, to_char(o.check_date, 'YYYY-MM-DD') AS checkDate, " +
            "o.warehouse_id AS warehouseId, w.xoysox AS warehouseName, " +
            "o.operator_id AS operatorId, o.operator_name AS operatorName, o.remark AS remark, " +
            "to_char(o.created_at, 'YYYY-MM-DD HH24:MI:SS') AS createdAt " +
            "FROM " + T_ORDER + " o " +
            "LEFT JOIN " + T_WAREHOUSE + " w ON w.id = o.warehouse_id AND w.corp_code = 'hltgq' " +
            "WHERE o.corp_code = 'hltgq' AND o.del_flag = '#2#' " + LIST_CONDITIONS +
            "ORDER BY o.check_date DESC, o.created_at DESC, o.order_no DESC " +
            "LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<StocktakePageItemVO> selectPage(@Param("orderNo") String orderNo,
                                         @Param("warehouseId") String warehouseId,
                                         @Param("operatorId") String operatorId,
                                         @Param("materialCode") String materialCode,
                                         @Param("materialName") String materialName,
                                         @Param("startDate") String startDate,
                                         @Param("endDate") String endDate,
                                         @Param("emergency") Boolean emergency,
                                         @Param("size") int size,
                                         @Param("offset") long offset);

    /** 盘点单计数（条件与 {@link #selectPage} 完全一致） */
    @Select("<script>SELECT COUNT(*) FROM " + T_ORDER + " o WHERE o.corp_code = 'hltgq' AND o.del_flag = '#2#' " +
            LIST_CONDITIONS + "</script>")
    long countPage(@Param("orderNo") String orderNo,
                   @Param("warehouseId") String warehouseId,
                   @Param("operatorId") String operatorId,
                   @Param("materialCode") String materialCode,
                   @Param("materialName") String materialName,
                   @Param("startDate") String startDate,
                   @Param("endDate") String endDate,
                   @Param("emergency") Boolean emergency);

    // ==================== P7 详情 / 明细 / 附件 ====================

    /** 盘点单详情头（不存在返回 null） */
    @Select("SELECT o.id AS id, o.order_no AS orderNo, to_char(o.check_date, 'YYYY-MM-DD') AS checkDate, " +
            "o.warehouse_id AS warehouseId, w.xoysox AS warehouseName, " +
            "o.operator_id AS operatorId, o.operator_name AS operatorName, o.remark AS remark, " +
            "to_char(o.created_at, 'YYYY-MM-DD HH24:MI:SS') AS createdAt " +
            "FROM " + T_ORDER + " o " +
            "LEFT JOIN " + T_WAREHOUSE + " w ON w.id = o.warehouse_id AND w.corp_code = 'hltgq' " +
            "WHERE o.id = #{id} AND o.corp_code = 'hltgq' AND o.del_flag = '#2#'")
    StocktakeDetailVO selectHeader(@Param("id") String id);

    /** 盘点单明细（录入顺序） */
    @Select("SELECT d.material_id AS materialId, d.material_code AS materialCode, d.material_name AS materialName, " +
            "d.material_brand AS materialBrand, d.material_spec AS materialSpec, d.material_unit AS materialUnit, " +
            "d.book_qty AS bookQty, d.check_qty AS checkQty, d.profit_qty AS profitQty, d.loss_qty AS lossQty " +
            "FROM " + T_ITEM + " d " +
            "WHERE d.order_id = #{orderId} AND d.corp_code = 'hltgq' " +
            "ORDER BY d.created_at ASC, d.id ASC")
    List<StocktakeItemVO> selectItems(@Param("orderId") String orderId);

    /** 盘点单是否含应急物资明细（详情/编辑深链校验用，与列表 HAS_EMERGENCY_ITEM 同口径）。 */
    @Select("SELECT COUNT(*) FROM " + T_ITEM + " d JOIN " + T_MATERIAL +
            " m ON m.id = d.material_id AND m.corp_code = 'hltgq' " +
            "WHERE d.order_id = #{orderId} AND d.corp_code = 'hltgq' AND " + InventoryCategory.CONDITION_EMERGENCY)
    long countEmergencyItems(@Param("orderId") String orderId);

    /** 单据附件清单（详情/编辑管理用；不含 file_path，下载走 {@link #selectAttachmentById}） */
    @Select("SELECT a.id AS id, a.order_id AS orderId, a.file_name AS fileName, " +
            "a.file_size AS fileSize, a.file_ext AS fileExt " +
            "FROM " + T_ATTACH + " a " +
            "WHERE a.order_id = #{orderId} AND a.corp_code = 'hltgq' " +
            "ORDER BY a.created_at ASC, a.id ASC")
    List<StocktakeAttachmentVO> selectAttachments(@Param("orderId") String orderId);

    /** 列表页附件批量装配（一次查询多单附件，避免逐行查询） */
    @Select("<script>" +
            "SELECT a.id AS id, a.order_id AS orderId, a.file_name AS fileName, " +
            "a.file_size AS fileSize, a.file_ext AS fileExt " +
            "FROM " + T_ATTACH + " a " +
            "WHERE a.corp_code = 'hltgq' AND a.order_id IN " +
            "<foreach collection='orderIds' item='oid' open='(' separator=',' close=')'>#{oid}</foreach> " +
            "ORDER BY a.created_at ASC, a.id ASC" +
            "</script>")
    List<StocktakeAttachmentVO> selectAttachmentsByOrderIds(@Param("orderIds") List<String> orderIds);

    /** 单个附件（下载/删除/编辑绑定前的行查询，含 file_path） */
    @Select("SELECT a.id AS id, a.order_id AS orderId, a.file_name AS fileName, a.file_path AS filePath, " +
            "a.file_size AS fileSize, a.file_ext AS fileExt " +
            "FROM " + T_ATTACH + " a " +
            "WHERE a.id = #{id} AND a.corp_code = 'hltgq'")
    StocktakeAttachmentVO selectAttachmentById(@Param("id") String id);

    // ==================== 写入 ====================

    /**
     * 盘点单主表。row key：id/order_no/check_date/warehouse_id/operator_id/operator_name/remark/now/operator。
     */
    @Insert("INSERT INTO " + T_ORDER + " " +
            "(\"id\", \"order_no\", \"check_date\", \"warehouse_id\", \"operator_id\", \"operator_name\", " +
            "\"remark\", \"del_flag\", \"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.order_no}, CAST(#{row.check_date} AS date), #{row.warehouse_id}, " +
            "#{row.operator_id}, #{row.operator_name}, #{row.remark}, '#2#', 'hltgq', " +
            "#{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertOrder(@Param("row") Map<String, Object> row);

    /**
     * 盘点单明细（物资六字段快照由 Service 回查 slaifi 固化）。
     * row key：id/order_id/material_id/material_code/material_name/material_brand/
     * material_spec/material_unit/book_qty/check_qty/profit_qty/loss_qty/now/operator。
     */
    @Insert("INSERT INTO " + T_ITEM + " " +
            "(\"id\", \"order_id\", \"material_id\", \"material_code\", \"material_name\", \"material_brand\", " +
            "\"material_spec\", \"material_unit\", \"book_qty\", \"check_qty\", \"profit_qty\", \"loss_qty\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.order_id}, #{row.material_id}, #{row.material_code}, #{row.material_name}, " +
            "#{row.material_brand}, #{row.material_spec}, #{row.material_unit}, #{row.book_qty}, #{row.check_qty}, " +
            "#{row.profit_qty}, #{row.loss_qty}, 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertItem(@Param("row") Map<String, Object> row);

    /**
     * 附件行（上传成功即写入，order_id 为空；保存/编辑时由 {@link #bindAttachmentOrder} 回填）。
     * row key：id/order_id/file_name/file_path/file_size/file_ext/now/operator。
     */
    @Insert("INSERT INTO " + T_ATTACH + " " +
            "(\"id\", \"order_id\", \"file_name\", \"file_path\", \"file_size\", \"file_ext\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.order_id}, #{row.file_name}, #{row.file_path}, #{row.file_size}, " +
            "#{row.file_ext}, 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertAttachment(@Param("row") Map<String, Object> row);

    /** 编辑：仅盘点人/备注/审计列（不触碰台账、明细与流水） */
    @Update("UPDATE " + T_ORDER + " SET " +
            "operator_id = #{operatorId}, operator_name = #{operatorName}, remark = #{remark}, " +
            "updated_at = #{now}, updated_by = #{operator} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND del_flag = '#2#'")
    int updateOrder(@Param("id") String id,
                    @Param("operatorId") String operatorId,
                    @Param("operatorName") String operatorName,
                    @Param("remark") String remark,
                    @Param("operator") String operator,
                    @Param("now") LocalDateTime now);

    /** 附件回填归属（仅允许无归属或本单附件，防跨单占用；0 行 = 已被占用/不存在） */
    @Update("UPDATE " + T_ATTACH + " SET order_id = #{orderId}, updated_at = #{now}, updated_by = #{operator} " +
            "WHERE id = #{id} AND corp_code = 'hltgq' AND (order_id IS NULL OR order_id = #{orderId})")
    int bindAttachmentOrder(@Param("id") String id,
                            @Param("orderId") String orderId,
                            @Param("operator") String operator,
                            @Param("now") LocalDateTime now);

    /** 附件行删除（物理删除，编辑态移除已归属附件用；文件由 Service 在事务提交后删） */
    @Delete("DELETE FROM " + T_ATTACH + " WHERE id = #{id} AND corp_code = 'hltgq'")
    int deleteAttachment(@Param("id") String id);

    /** 附件行删除（仅限未归属附件：并发绑定后返回 0 行，由 Service 拒绝移除且不删文件） */
    @Delete("DELETE FROM " + T_ATTACH + " WHERE id = #{id} AND corp_code = 'hltgq' AND order_id IS NULL")
    int deleteUnboundAttachment(@Param("id") String id);

    // ==================== 台账（盘点专用读写） ====================

    /**
     * 锁行重读台账实时值（保存事务内调用）：FOR UPDATE 串行化并发出入库/盘点，
     * quantity 与页面快照（book_qty）比对不一致 → Service 整单拒绝保存。
     * <p>返回 null = 该物资在该仓库无台账（纯盘盈建账场景，页面快照必须为 0）。
     */
    @Select("SELECT t.id AS ledgerId, t.jzikio AS materialId, t.oywydb AS warehouseId, " +
            "CAST(t.ejgxlq AS numeric(20,0)) AS quantity " +
            "FROM " + T_LEDGER + " t " +
            "WHERE t.corp_code = 'hltgq' AND t.jzikio = #{materialId} AND t.oywydb = #{warehouseId} " +
            "ORDER BY t.created_at ASC, t.id ASC LIMIT 1 FOR UPDATE")
    StockLedgerOptionVO selectLedgerRowForUpdate(@Param("materialId") String materialId,
                                                 @Param("warehouseId") String warehouseId);

    /**
     * 台账绝对校正（盘点语义：ejgxlq = 实盘数量，非相对增减），重算 mojktw 并 RETURNING 新值。
     * <p>返回 null = 台账行不存在（理论不可达，防御）。
     */
    @Select("UPDATE " + T_LEDGER + " SET " +
            "ejgxlq = CAST(#{qty} AS double precision), " +
            "mojktw = CASE WHEN #{qty} > COALESCE(vhvhja, 0) THEN '#1#' ELSE '#2#' END, " +
            "updated_at = #{now}, updated_by = #{operator} " +
            "WHERE id = #{ledgerId} AND corp_code = 'hltgq' " +
            "RETURNING CAST(ejgxlq AS numeric(20,0))")
    Integer updateLedgerAbsolute(@Param("ledgerId") String ledgerId,
                                 @Param("qty") int qty,
                                 @Param("operator") String operator,
                                 @Param("now") LocalDateTime now);

    /** 盘点带出：当前仓库台账行（含 0 库存与偏低行；明细默认账面即本查询 quantity）。
     * <p>安全库存与出入库/台账同口径：实时取物资目录（{@link InventoryLedgerMapper#SAFE_STOCK_EXPR}）。
     * <p>LIMIT 501 = 明细上限 500 + 1 行截断探测（超出由前端截至 500 行并提示分批另建盘点单）。 */
    @Select("<script>" +
            "SELECT t.id AS ledgerId, t.jzikio AS materialId, m.higsdd AS code, t.wpxwwi AS name, " +
            "t.qhtxml AS brand, t.hughww AS spec, t.mddokm AS unit, " +
            "CAST(t.ejgxlq AS numeric(20,0)) AS quantity, " + InventoryLedgerMapper.SAFE_STOCK_EXPR + " AS safetyStock, " +
            "t.oywydb AS warehouseId " +
            "FROM " + T_LEDGER + " t " +
            "LEFT JOIN " + T_MATERIAL + " m ON m.id = t.jzikio AND m.corp_code = 'hltgq' " +
            "WHERE t.corp_code = 'hltgq' AND t.oywydb = #{warehouseId} " +
            InventoryLedgerMapper.CATEGORY_CONDITIONS +
            "<if test='keyword != null and keyword != \"\"'>AND (m.higsdd LIKE CONCAT('%', #{keyword}, '%') OR t.wpxwwi LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY m.higsdd ASC, t.id ASC " +
            "LIMIT 501" +
            "</script>")
    List<StockLedgerOptionVO> selectLedgerOptions(@Param("warehouseId") String warehouseId,
                                                  @Param("keyword") String keyword,
                                                  @Param("emergency") boolean emergency,
                                                  @Param("commonOnly") boolean commonOnly);

    /**
     * 手动加行：物资目录全量启用物资 + 该仓库实时账面（LEFT JOIN，无台账记 0）。
     * <p>账面随行返回，保证加行快照与保存时校验同源（有台账物资不会误按 0 盘盈）。
     */
    @Select("<script>" +
            "SELECT m.id AS materialId, m.higsdd AS code, m.avlsdv AS name, m.fqztqm AS brand, " +
            "m.iwnyuk AS spec, m.boipik AS unit, " +
            "COALESCE(CAST(t.ejgxlq AS numeric(20,0)), 0) AS quantity " +
            "FROM " + T_MATERIAL + " m " +
            "LEFT JOIN " + T_LEDGER + " t ON t.jzikio = m.id AND t.oywydb = #{warehouseId} AND t.corp_code = 'hltgq' " +
            "WHERE m.corp_code = 'hltgq' " +
            InventoryLedgerMapper.CATEGORY_CONDITIONS +
            "<if test='keyword != null and keyword != \"\"'>AND (m.higsdd LIKE CONCAT('%', #{keyword}, '%') OR m.avlsdv LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY m.higsdd ASC, m.id ASC " +
            "LIMIT 1000" +
            "</script>")
    List<StockLedgerOptionVO> selectMaterialOptions(@Param("warehouseId") String warehouseId,
                                                    @Param("keyword") String keyword,
                                                    @Param("emergency") boolean emergency,
                                                    @Param("commonOnly") boolean commonOnly);
}
