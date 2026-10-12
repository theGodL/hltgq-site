package com.qgyun.hltgq.hltgqsite.inventoryorder.mapper;

import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.LedgerPageItemVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.MaterialVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.PickerOptionVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.StockChangeVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.StockLedgerOptionVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.WarehouseVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 库存核心写入（编号取号、tzkioe 台账建账/增减、kcjl 流水）+ 台账页/流水查询 + 表单选择器。
 * <p>关键口径：
 * <ul>
 *   <li><b>取号</b>：KES 一步式 {@code INSERT ... ON CONFLICT (serial_type, serial_date) DO UPDATE
 *       SET current_seq = 表.current_seq + 1 RETURNING current_seq}（行锁串行，禁 SELECT MAX+1），
 *       由 Service 在 REQUIRES_NEW 独立事务中调用（主事务失败跳号属预期）；</li>
 *   <li><b>建账</b>：{@code ON CONFLICT (jzikio, oywydb) DO NOTHING}（依赖唯一索引 uk_tzkioe_material_warehouse），
 *       0 行 = 已存在 → SELECT id 后走 UPDATE 累加；</li>
 *   <li><b>出库乐观锁</b>：UPDATE 带 {@code COALESCE(ejgxlq,0) >= qty} 条件 + RETURNING；
 *       返回 null = 库存不足 → Service 整单回滚提示；</li>
 *   <li><b>mojktw 重算</b>：SET 表达式引用旧值（新量 > COALESCE(vhvhja,0) → #1# 正常，否则含等于 → #2# 偏低）；</li>
 *   <li>数量列 double precision 读出统一 CAST numeric(20,0)；模糊匹配一律 CONCAT，禁用 {@code ||}。</li>
 * </ul>
 */
public interface InventoryLedgerMapper {

    String T_SEQ = "\"qixiao-apaas\".\"t_auto_hltgq_water_order_no_seq\"";
    String T_LEDGER = "\"qixiao-apaas\".\"t_auto_hltgq_knc3g_tzkioe\"";
    String T_MATERIAL = "\"qixiao-apaas\".\"t_auto_hltgq_knc3g_slaifi\"";
    String T_WAREHOUSE = "\"qixiao-apaas\".\"t_auto_hltgq_knc3g_twufyx\"";
    String T_ORG = "\"qixiao-apaas\".\"t_apaas_uc_org\"";
    String T_USER = "\"qixiao-apaas\".\"t_apaas_uc_user\"";
    String T_CHANGE = "\"qixiao-apaas\".\"t_auto_hltgq_water_stock_change_log\"";

    // ==================== 取号 ====================

    /**
     * 一步取号：无当日行则建行（序号 1），存在则原子 +1，RETURNING 返回新序号。
     * <p>调用方必须在独立事务（REQUIRES_NEW）中执行；同一 (serial_type, serial_date) 并发由唯一索引/行锁串行。
     * <p>注意：UPDATE 中必须用表名限定引用当前值（EXCLUDED 是插入行值，不可用于自增）。
     */
    @Select("INSERT INTO " + T_SEQ + " " +
            "(\"id\", \"serial_type\", \"serial_date\", \"current_seq\", \"corp_code\", " +
            "\"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{id}, #{serialType}, #{serialDate}, 1, 'hltgq', #{now}, #{operator}, #{now}, #{operator}) " +
            "ON CONFLICT (serial_type, serial_date) DO UPDATE " +
            "SET current_seq = t_auto_hltgq_water_order_no_seq.current_seq + 1, " +
            "updated_at = #{now}, updated_by = #{operator} " +
            "RETURNING current_seq")
    Integer upsertOrderNoSeq(@Param("id") String id,
                             @Param("serialType") String serialType,
                             @Param("serialDate") String serialDate,
                             @Param("operator") String operator,
                             @Param("now") LocalDateTime now);

    // ==================== 台账（tzkioe） ====================

    /**
     * 首次入库建账（幂等）：数量/安全库存/状态一并初始化；冲突（并发或已建账）返回 0 行。
     * <p>row key：id/material_id/warehouse_id/department_id/brand/name/spec/unit/quantity/safety_stock/now/operator。
     * <p>kxhsks 同单位冗余；mojktw 由 quantity 与 safety_stock（空视 0）比较得出（等于 → #2# 偏低）。
     */
    @Insert("INSERT INTO " + T_LEDGER + " " +
            "(\"id\", \"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\", " +
            "\"jzikio\", \"oywydb\", \"czdwpj\", \"qhtxml\", \"wpxwwi\", \"hughww\", \"mddokm\", \"kxhsks\", " +
            "\"ejgxlq\", \"vhvhja\", \"mojktw\") " +
            "VALUES (#{row.id}, 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator}, " +
            "#{row.material_id}, #{row.warehouse_id}, #{row.department_id}, #{row.brand}, #{row.name}, " +
            "#{row.spec}, #{row.unit}, #{row.unit}, " +
            "CAST(#{row.quantity} AS double precision), CAST(#{row.safety_stock} AS double precision), " +
            "CASE WHEN CAST(#{row.quantity} AS double precision) > COALESCE(CAST(#{row.safety_stock} AS double precision), 0) " +
            "THEN '#1#' ELSE '#2#' END) " +
            "ON CONFLICT (jzikio, oywydb) DO NOTHING")
    int insertLedgerIfAbsent(@Param("row") Map<String, Object> row);

    /** 按（物资 + 仓库）定位台账行 id（唯一索引保证至多一行；冲突重试路径使用）。 */
    @Select("SELECT id FROM " + T_LEDGER + " " +
            "WHERE corp_code = 'hltgq' AND jzikio = #{materialId} AND oywydb = #{warehouseId} " +
            "ORDER BY created_at ASC, id ASC LIMIT 1")
    String selectLedgerIdByMaterialWarehouse(@Param("materialId") String materialId,
                                             @Param("warehouseId") String warehouseId);

    /**
     * 入库累加（幂等外的常规路径）：ejgxlq += qty 并重算 mojktw，RETURNING 新库存。
     * <p>返回 null = 台账行不存在（理论不可达，防御）。
     */
    @Select("UPDATE " + T_LEDGER + " SET " +
            "ejgxlq = COALESCE(ejgxlq, 0) + #{qty}, " +
            "mojktw = CASE WHEN (COALESCE(ejgxlq, 0) + #{qty}) > COALESCE(vhvhja, 0) THEN '#1#' ELSE '#2#' END, " +
            "updated_at = #{now}, updated_by = #{operator} " +
            "WHERE id = #{ledgerId} AND corp_code = 'hltgq' " +
            "RETURNING CAST(ejgxlq AS numeric(20,0))")
    Integer addLedgerStock(@Param("ledgerId") String ledgerId,
                           @Param("qty") int qty,
                           @Param("operator") String operator,
                           @Param("now") LocalDateTime now);

    /**
     * 出库乐观锁扣减：条件 {@code COALESCE(ejgxlq,0) >= qty} + 重算 mojktw，RETURNING 新库存。
     * <p>返回 null（0 行）= 库存不足或行不存在 → Service 抛「库存不足，请刷新后重试」整单回滚。
     */
    @Select("UPDATE " + T_LEDGER + " SET " +
            "ejgxlq = COALESCE(ejgxlq, 0) - #{qty}, " +
            "mojktw = CASE WHEN (COALESCE(ejgxlq, 0) - #{qty}) > COALESCE(vhvhja, 0) THEN '#1#' ELSE '#2#' END, " +
            "updated_at = #{now}, updated_by = #{operator} " +
            "WHERE id = #{ledgerId} AND corp_code = 'hltgq' AND COALESCE(ejgxlq, 0) >= #{qty} " +
            "RETURNING CAST(ejgxlq AS numeric(20,0))")
    Integer deductLedgerStock(@Param("ledgerId") String ledgerId,
                              @Param("qty") int qty,
                              @Param("operator") String operator,
                              @Param("now") LocalDateTime now);

    // ==================== 流水（kcjl） ====================

    /**
     * 写库存变动流水。row key：id/ledger_id/material_id/warehouse_id/change_type/order_kind/
     * order_id/order_no/biz_type/change_qty/qty_before/qty_after/counterparty/operator_name/order_date/now/operator。
     */
    @Insert("INSERT INTO " + T_CHANGE + " " +
            "(\"id\", \"ledger_id\", \"material_id\", \"warehouse_id\", \"change_type\", \"order_kind\", " +
            "\"order_id\", \"order_no\", \"biz_type\", \"change_qty\", \"qty_before\", \"qty_after\", " +
            "\"counterparty\", \"operator_name\", \"order_date\", \"corp_code\", \"created_at\", \"created_by\", " +
            "\"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.ledger_id}, #{row.material_id}, #{row.warehouse_id}, #{row.change_type}, " +
            "#{row.order_kind}, #{row.order_id}, #{row.order_no}, #{row.biz_type}, #{row.change_qty}, " +
            "#{row.qty_before}, #{row.qty_after}, #{row.counterparty}, #{row.operator_name}, " +
            "CAST(#{row.order_date} AS date), 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertChangeLog(@Param("row") Map<String, Object> row);

    /** 出入库记录分页（按台账行 material_id + warehouse_id 过滤，created_at 倒序）。 */
    @Select("SELECT c.id AS id, c.change_type AS changeType, c.order_kind AS orderKind, " +
            "c.order_id AS orderId, c.order_no AS orderNo, c.biz_type AS bizType, " +
            "c.change_qty AS changeQty, c.qty_before AS qtyBefore, c.qty_after AS qtyAfter, " +
            "c.counterparty AS counterparty, c.operator_name AS operatorName, " +
            "to_char(c.order_date, 'YYYY-MM-DD') AS orderDate, " +
            "to_char(c.created_at, 'YYYY-MM-DD HH24:MI:SS') AS createdAt, " +
            "c.warehouse_id AS warehouseId, w.xoysox AS warehouseName " +
            "FROM " + T_CHANGE + " c " +
            "LEFT JOIN " + T_WAREHOUSE + " w ON w.id = c.warehouse_id AND w.corp_code = 'hltgq' " +
            "WHERE c.corp_code = 'hltgq' AND c.material_id = #{materialId} AND c.warehouse_id = #{warehouseId} " +
            "ORDER BY c.created_at DESC, c.id DESC " +
            "LIMIT #{size} OFFSET #{offset}")
    List<StockChangeVO> selectStockChanges(@Param("materialId") String materialId,
                                           @Param("warehouseId") String warehouseId,
                                           @Param("size") int size,
                                           @Param("offset") long offset);

    /** 出入库记录计数（条件与 {@link #selectStockChanges} 一致）。 */
    @Select("SELECT COUNT(*) FROM " + T_CHANGE + " c " +
            "WHERE c.corp_code = 'hltgq' AND c.material_id = #{materialId} AND c.warehouse_id = #{warehouseId}")
    long countStockChanges(@Param("materialId") String materialId,
                           @Param("warehouseId") String warehouseId);

    /** 出入库记录导出（同列表条件，全量无分页）。 */
    @Select("SELECT c.id AS id, c.change_type AS changeType, c.order_kind AS orderKind, " +
            "c.order_id AS orderId, c.order_no AS orderNo, c.biz_type AS bizType, " +
            "c.change_qty AS changeQty, c.qty_before AS qtyBefore, c.qty_after AS qtyAfter, " +
            "c.counterparty AS counterparty, c.operator_name AS operatorName, " +
            "to_char(c.order_date, 'YYYY-MM-DD') AS orderDate, " +
            "to_char(c.created_at, 'YYYY-MM-DD HH24:MI:SS') AS createdAt, " +
            "c.warehouse_id AS warehouseId, w.xoysox AS warehouseName " +
            "FROM " + T_CHANGE + " c " +
            "LEFT JOIN " + T_WAREHOUSE + " w ON w.id = c.warehouse_id AND w.corp_code = 'hltgq' " +
            "WHERE c.corp_code = 'hltgq' AND c.material_id = #{materialId} AND c.warehouse_id = #{warehouseId} " +
            "ORDER BY c.created_at DESC, c.id DESC")
    List<StockChangeVO> selectStockChangesForExport(@Param("materialId") String materialId,
                                                    @Param("warehouseId") String warehouseId);

    // ==================== P4 台账列表 ====================

    /** 台账分页（左树仓库过滤 + 编号/名称模糊 + 状态筛选；物资编号取 slaifi.higsdd，其余取台账快照）。 */
    @Select("<script>" +
            "SELECT t.id AS id, t.jzikio AS materialId, m.higsdd AS materialCode, " +
            "t.qhtxml AS brand, t.wpxwwi AS name, t.hughww AS spec, t.mddokm AS unit, " +
            "t.oywydb AS warehouseId, w.xoysox AS warehouseName, " +
            "CAST(t.ejgxlq AS numeric(20,0)) AS quantity, CAST(t.vhvhja AS numeric(20,0)) AS safetyStock, " +
            "t.mojktw AS statusCode, t.czdwpj AS departmentId " +
            "FROM " + T_LEDGER + " t " +
            "LEFT JOIN " + T_WAREHOUSE + " w ON w.id = t.oywydb AND w.corp_code = 'hltgq' " +
            "LEFT JOIN " + T_MATERIAL + " m ON m.id = t.jzikio AND m.corp_code = 'hltgq' " +
            "WHERE t.corp_code = 'hltgq' " +
            "<if test='warehouseId != null and warehouseId != \"\"'>AND t.oywydb = #{warehouseId} </if>" +
            "<if test='keyword != null and keyword != \"\"'>AND (m.higsdd LIKE CONCAT('%', #{keyword}, '%') OR t.wpxwwi LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND t.mojktw = #{statusCode} </if>" +
            "ORDER BY t.created_at DESC, t.id DESC " +
            "LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<LedgerPageItemVO> selectLedgerPage(@Param("warehouseId") String warehouseId,
                                            @Param("keyword") String keyword,
                                            @Param("statusCode") String statusCode,
                                            @Param("size") int size,
                                            @Param("offset") long offset);

    /** 台账计数（条件与 {@link #selectLedgerPage} 一致）。 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM " + T_LEDGER + " t " +
            "LEFT JOIN " + T_MATERIAL + " m ON m.id = t.jzikio AND m.corp_code = 'hltgq' " +
            "WHERE t.corp_code = 'hltgq' " +
            "<if test='warehouseId != null and warehouseId != \"\"'>AND t.oywydb = #{warehouseId} </if>" +
            "<if test='keyword != null and keyword != \"\"'>AND (m.higsdd LIKE CONCAT('%', #{keyword}, '%') OR t.wpxwwi LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND t.mojktw = #{statusCode} </if>" +
            "</script>")
    long countLedgers(@Param("warehouseId") String warehouseId,
                      @Param("keyword") String keyword,
                      @Param("statusCode") String statusCode);

    /** 台账导出（同 P4 条件，全量无分页；前端生成 Excel）。 */
    @Select("<script>" +
            "SELECT t.id AS id, t.jzikio AS materialId, m.higsdd AS materialCode, " +
            "t.qhtxml AS brand, t.wpxwwi AS name, t.hughww AS spec, t.mddokm AS unit, " +
            "t.oywydb AS warehouseId, w.xoysox AS warehouseName, " +
            "CAST(t.ejgxlq AS numeric(20,0)) AS quantity, CAST(t.vhvhja AS numeric(20,0)) AS safetyStock, " +
            "t.mojktw AS statusCode, t.czdwpj AS departmentId " +
            "FROM " + T_LEDGER + " t " +
            "LEFT JOIN " + T_WAREHOUSE + " w ON w.id = t.oywydb AND w.corp_code = 'hltgq' " +
            "LEFT JOIN " + T_MATERIAL + " m ON m.id = t.jzikio AND m.corp_code = 'hltgq' " +
            "WHERE t.corp_code = 'hltgq' " +
            "<if test='warehouseId != null and warehouseId != \"\"'>AND t.oywydb = #{warehouseId} </if>" +
            "<if test='keyword != null and keyword != \"\"'>AND (m.higsdd LIKE CONCAT('%', #{keyword}, '%') OR t.wpxwwi LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND t.mojktw = #{statusCode} </if>" +
            "ORDER BY t.created_at DESC, t.id DESC" +
            "</script>")
    List<LedgerPageItemVO> selectLedgerExportRows(@Param("warehouseId") String warehouseId,
                                                  @Param("keyword") String keyword,
                                                  @Param("statusCode") String statusCode);

    // ==================== 表单选择器 / 校验辅助 ====================

    /**
     * 仓库候选（meta 下拉 enabledOnly=true 仅启用；P4 左树 false 全量含停用并附 disabled 标记）。
     * <p>aeleiw：#1# 启用 / #zlji# 停用（null 视作启用）；科室名/经办人名 LEFT JOIN 平台表。
     */
    @Select("<script>" +
            "SELECT w.id AS id, w.xoysox AS name, w.bcewqm AS code, w.aeleiw AS statusCode, " +
            "COALESCE(w.aeleiw = '#zlji#', false) AS disabled, " +
            "w.dosozz AS departmentId, g.name AS departmentName, " +
            "w.ivplza AS operatorId, u.name AS operatorName " +
            "FROM " + T_WAREHOUSE + " w " +
            "LEFT JOIN " + T_ORG + " g ON g.id = w.dosozz AND g.corp_code = 'hltgq' " +
            "LEFT JOIN " + T_USER + " u ON u.id = w.ivplza AND u.corp_code = 'hltgq' " +
            "WHERE w.corp_code = 'hltgq' " +
            "<if test='enabledOnly'>AND (w.aeleiw IS NULL OR w.aeleiw != '#zlji#') </if>" +
            "ORDER BY w.created_at ASC, w.id ASC" +
            "</script>")
    List<WarehouseVO> selectWarehouses(@Param("enabledOnly") boolean enabledOnly);

    /** 单个仓库（保存校验：存在 + 是否停用 + 默认科室/经办人）。 */
    @Select("SELECT w.id AS id, w.xoysox AS name, w.bcewqm AS code, w.aeleiw AS statusCode, " +
            "COALESCE(w.aeleiw = '#zlji#', false) AS disabled, " +
            "w.dosozz AS departmentId, g.name AS departmentName, " +
            "w.ivplza AS operatorId, u.name AS operatorName " +
            "FROM " + T_WAREHOUSE + " w " +
            "LEFT JOIN " + T_ORG + " g ON g.id = w.dosozz AND g.corp_code = 'hltgq' " +
            "LEFT JOIN " + T_USER + " u ON u.id = w.ivplza AND u.corp_code = 'hltgq' " +
            "WHERE w.id = #{id} AND w.corp_code = 'hltgq'")
    WarehouseVO selectWarehouseById(@Param("id") String id);

    /** 归口科室候选（按名称模糊，value/label 直用）。 */
    @Select("<script>" +
            "SELECT g.id AS value, g.name AS label " +
            "FROM " + T_ORG + " g " +
            "WHERE g.corp_code = 'hltgq' " +
            "<if test='keyword != null and keyword != \"\"'>AND g.name LIKE CONCAT('%', #{keyword}, '%') </if>" +
            "ORDER BY g.name ASC, g.id ASC" +
            "</script>")
    List<PickerOptionVO> selectDepartments(@Param("keyword") String keyword);

    /** 经办人候选（按姓名/手机号模糊，sub = 手机号）。 */
    @Select("<script>" +
            "SELECT u.id AS value, u.name AS label, u.mobile AS sub " +
            "FROM " + T_USER + " u " +
            "WHERE u.corp_code = 'hltgq' " +
            "<if test='keyword != null and keyword != \"\"'>AND (u.name LIKE CONCAT('%', #{keyword}, '%') OR u.mobile LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY u.name ASC, u.id ASC" +
            "</script>")
    List<PickerOptionVO> selectUsers(@Param("keyword") String keyword);

    /** 经办人姓名（保存时快照来源；null = 用户不存在）。 */
    @Select("SELECT name FROM " + T_USER + " WHERE id = #{id} AND corp_code = 'hltgq'")
    String selectUserName(@Param("id") String id);

    /** 科室名称（保存校验：null = 科室不存在）。 */
    @Select("SELECT name FROM " + T_ORG + " WHERE id = #{id} AND corp_code = 'hltgq'")
    String selectDepartmentName(@Param("id") String id);

    /** 物资目录候选（入库明细「从目录选择」，不按默认仓库硬过滤；safetyStockRaw 为原始文本由 Service 解析）。 */
    @Select("<script>" +
            "SELECT m.id AS id, m.higsdd AS code, m.avlsdv AS name, m.fqztqm AS brand, " +
            "m.iwnyuk AS spec, m.boipik AS unit, m.unqvqe AS safetyStockRaw, m.fmewia AS defaultWarehouseId " +
            "FROM " + T_MATERIAL + " m " +
            "WHERE m.corp_code = 'hltgq' " +
            "<if test='keyword != null and keyword != \"\"'>AND (m.higsdd LIKE CONCAT('%', #{keyword}, '%') OR m.avlsdv LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY m.higsdd ASC, m.id ASC " +
            "LIMIT 1000" +
            "</script>")
    List<MaterialVO> selectMaterials(@Param("keyword") String keyword);

    /** 单个物资（入库明细快照来源；null = 不存在）。 */
    @Select("SELECT m.id AS id, m.higsdd AS code, m.avlsdv AS name, m.fqztqm AS brand, " +
            "m.iwnyuk AS spec, m.boipik AS unit, m.unqvqe AS safetyStockRaw, m.fmewia AS defaultWarehouseId " +
            "FROM " + T_MATERIAL + " m " +
            "WHERE m.id = #{id} AND m.corp_code = 'hltgq'")
    MaterialVO selectMaterialById(@Param("id") String id);

    /** 出库台账候选（当前仓库且可用库存 &gt; 0 的物资）。 */
    @Select("<script>" +
            "SELECT t.id AS ledgerId, t.jzikio AS materialId, m.higsdd AS code, t.wpxwwi AS name, " +
            "t.qhtxml AS brand, t.hughww AS spec, t.mddokm AS unit, " +
            "CAST(t.ejgxlq AS numeric(20,0)) AS quantity, CAST(t.vhvhja AS numeric(20,0)) AS safetyStock, " +
            "t.oywydb AS warehouseId " +
            "FROM " + T_LEDGER + " t " +
            "LEFT JOIN " + T_MATERIAL + " m ON m.id = t.jzikio AND m.corp_code = 'hltgq' " +
            "WHERE t.corp_code = 'hltgq' AND t.oywydb = #{warehouseId} AND COALESCE(t.ejgxlq, 0) > 0 " +
            "<if test='keyword != null and keyword != \"\"'>AND (m.higsdd LIKE CONCAT('%', #{keyword}, '%') OR t.wpxwwi LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY m.higsdd ASC, t.id ASC" +
            "</script>")
    List<StockLedgerOptionVO> selectStockLedgerOptions(@Param("warehouseId") String warehouseId,
                                                       @Param("keyword") String keyword);

    /** 出库保存按台账行 id 取快照（校验行归属仓库、取物资六字段快照与可用库存）。 */
    @Select("SELECT t.id AS ledgerId, t.jzikio AS materialId, m.higsdd AS code, t.wpxwwi AS name, " +
            "t.qhtxml AS brand, t.hughww AS spec, t.mddokm AS unit, " +
            "CAST(t.ejgxlq AS numeric(20,0)) AS quantity, CAST(t.vhvhja AS numeric(20,0)) AS safetyStock, " +
            "t.oywydb AS warehouseId " +
            "FROM " + T_LEDGER + " t " +
            "LEFT JOIN " + T_MATERIAL + " m ON m.id = t.jzikio AND m.corp_code = 'hltgq' " +
            "WHERE t.id = #{ledgerId} AND t.corp_code = 'hltgq'")
    StockLedgerOptionVO selectStockLedgerRowById(@Param("ledgerId") String ledgerId);
}
