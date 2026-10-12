package com.qgyun.hltgq.hltgqsite.inventoryorder.mapper;

import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.OrderDetailVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.OrderExportRowVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.OrderPageItemVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 出入库单据（入库单/出库单主表 + 明细表写入；P1 列表、详情、明细级导出查询）。
 * <p>表名/列名口径见 init.sql 3.1~3.4（英文列名与设计方案中文列一一对应，关联收敛为单列 ID）。
 * <p>写入走 {@code @Param("row") Map}：key 为 snake_case 列名（Service 组装），
 * 审计列 corp_code 恒 'hltgq'、时间由 Java 传入（与 patrol 模块写库风格一致）。
 * <p>P1 列表为「入库 UNION ALL 出库」子查询（外层别名 t），物资编码/名称按单据级 EXISTS
 * 回查对应来源明细快照表（两来源分别判定，避免 id 跨表误命中）；
 * 条件片段 {@link #LIST_CONDITIONS} 由 page/count/export 三处共用。
 * <p>KES 注意：模糊匹配一律 {@code CONCAT('%', #{x}, '%')}，禁用 {@code ||}。
 */
public interface InventoryOrderMapper {

    String T_INBOUND = "\"qixiao-apaas\".\"t_auto_hltgq_water_inbound_order\"";
    String T_INBOUND_DETAIL = "\"qixiao-apaas\".\"t_auto_hltgq_water_inbound_order_detail\"";
    String T_OUTBOUND = "\"qixiao-apaas\".\"t_auto_hltgq_water_outbound_order\"";
    String T_OUTBOUND_DETAIL = "\"qixiao-apaas\".\"t_auto_hltgq_water_outbound_order_detail\"";
    String T_WAREHOUSE = "\"qixiao-apaas\".\"t_auto_hltgq_knc3g_twufyx\"";
    String T_ORG = "\"qixiao-apaas\".\"t_apaas_uc_org\"";

    /** P1 单据来源子查询：两表同构输出 + 来源列 source（RK/CK）；del_flag 恒 #2#（本期无删除）。 */
    String ORDERS_UNION = "(SELECT 'RK' AS source, h.id AS id, h.order_no AS order_no, h.inbound_type AS biz_type, " +
            "h.order_date AS order_date, h.warehouse_id AS warehouse_id, h.source_unit AS counterparty, " +
            "h.department_id AS department_id, h.operator_id AS operator_id, h.operator_name AS operator_name, " +
            "h.remark AS remark, h.created_at AS created_at " +
            "FROM " + T_INBOUND + " h WHERE h.corp_code = 'hltgq' AND h.del_flag = '#2#' " +
            "UNION ALL " +
            "SELECT 'CK' AS source, h.id AS id, h.order_no AS order_no, h.outbound_type AS biz_type, " +
            "h.order_date AS order_date, h.warehouse_id AS warehouse_id, h.receive_unit AS counterparty, " +
            "h.department_id AS department_id, h.operator_id AS operator_id, h.operator_name AS operator_name, " +
            "h.remark AS remark, h.created_at AS created_at " +
            "FROM " + T_OUTBOUND + " h WHERE h.corp_code = 'hltgq' AND h.del_flag = '#2#')";

    /**
     * P1 动态查询条件（page/count/export 共用，作用于 {@link #ORDERS_UNION} 外层别名 t）。
     * <p>sourceUnit/receiveUnit 为供应商/来源、领用单位两个独立条件，按 t.source 配对只命中对应来源；
     * bizSource/bizCode 为业务类型联合键（RK#1#/CK#1#）拆解结果；日期含当日边界。
     */
    String LIST_CONDITIONS =
            "<if test='source != null and source != \"\"'>AND t.source = #{source} </if>" +
            "<if test='orderNo != null and orderNo != \"\"'>AND t.order_no LIKE CONCAT('%', #{orderNo}, '%') </if>" +
            "<if test='bizSource != null and bizSource != \"\"'>AND t.source = #{bizSource} AND t.biz_type = #{bizCode} </if>" +
            "<if test='warehouseId != null and warehouseId != \"\"'>AND t.warehouse_id = #{warehouseId} </if>" +
            "<if test='departmentId != null and departmentId != \"\"'>AND t.department_id = #{departmentId} </if>" +
            "<if test='sourceUnit != null and sourceUnit != \"\"'>AND t.source = 'RK' AND t.counterparty LIKE CONCAT('%', #{sourceUnit}, '%') </if>" +
            "<if test='receiveUnit != null and receiveUnit != \"\"'>AND t.source = 'CK' AND t.counterparty LIKE CONCAT('%', #{receiveUnit}, '%') </if>" +
            "<if test='materialCode != null and materialCode != \"\"'>AND ((t.source = 'RK' AND EXISTS (SELECT 1 FROM " + T_INBOUND_DETAIL + " d WHERE d.order_id = t.id AND d.material_code LIKE CONCAT('%', #{materialCode}, '%'))) OR (t.source = 'CK' AND EXISTS (SELECT 1 FROM " + T_OUTBOUND_DETAIL + " d WHERE d.order_id = t.id AND d.material_code LIKE CONCAT('%', #{materialCode}, '%')))) </if>" +
            "<if test='materialName != null and materialName != \"\"'>AND ((t.source = 'RK' AND EXISTS (SELECT 1 FROM " + T_INBOUND_DETAIL + " d WHERE d.order_id = t.id AND d.material_name LIKE CONCAT('%', #{materialName}, '%'))) OR (t.source = 'CK' AND EXISTS (SELECT 1 FROM " + T_OUTBOUND_DETAIL + " d WHERE d.order_id = t.id AND d.material_name LIKE CONCAT('%', #{materialName}, '%')))) </if>" +
            "<if test='startDate != null and startDate != \"\"'>AND t.order_date &gt;= CAST(#{startDate} AS date) </if>" +
            "<if test='endDate != null and endDate != \"\"'>AND t.order_date &lt;= CAST(#{endDate} AS date) </if>";

    // ==================== 写入 ====================

    /**
     * 入库单主表。row key：id/order_no/inbound_type/order_date/warehouse_id/source_unit/
     * department_id/operator_id/operator_name/remark/now/operator。
     */
    @Insert("INSERT INTO " + T_INBOUND + " " +
            "(\"id\", \"order_no\", \"inbound_type\", \"order_date\", \"warehouse_id\", \"source_unit\", " +
            "\"department_id\", \"operator_id\", \"operator_name\", \"remark\", \"del_flag\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.order_no}, #{row.inbound_type}, CAST(#{row.order_date} AS date), " +
            "#{row.warehouse_id}, #{row.source_unit}, #{row.department_id}, #{row.operator_id}, " +
            "#{row.operator_name}, #{row.remark}, '#2#', 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertInboundOrder(@Param("row") Map<String, Object> row);

    /**
     * 入库单明细（物资六字段快照由 Service 回查 slaifi 固化）。
     * row key：id/order_id/material_id/material_code/material_name/material_brand/
     * material_spec/material_unit/quantity/now/operator。
     */
    @Insert("INSERT INTO " + T_INBOUND_DETAIL + " " +
            "(\"id\", \"order_id\", \"material_id\", \"material_code\", \"material_name\", \"material_brand\", " +
            "\"material_spec\", \"material_unit\", \"quantity\", \"corp_code\", \"created_at\", \"created_by\", " +
            "\"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.order_id}, #{row.material_id}, #{row.material_code}, #{row.material_name}, " +
            "#{row.material_brand}, #{row.material_spec}, #{row.material_unit}, #{row.quantity}, 'hltgq', " +
            "#{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertInboundDetail(@Param("row") Map<String, Object> row);

    /**
     * 出库单主表。row key 同入库单（outbound_type/receive_unit 替代 inbound_type/source_unit）。
     */
    @Insert("INSERT INTO " + T_OUTBOUND + " " +
            "(\"id\", \"order_no\", \"outbound_type\", \"order_date\", \"warehouse_id\", \"receive_unit\", " +
            "\"department_id\", \"operator_id\", \"operator_name\", \"remark\", \"del_flag\", " +
            "\"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.order_no}, #{row.outbound_type}, CAST(#{row.order_date} AS date), " +
            "#{row.warehouse_id}, #{row.receive_unit}, #{row.department_id}, #{row.operator_id}, " +
            "#{row.operator_name}, #{row.remark}, '#2#', 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertOutboundOrder(@Param("row") Map<String, Object> row);

    /**
     * 出库单明细（含 stock_before 变动前快照，台账扣减成功后由 Service 回填）。
     * row key：入库明细 key + stock_before。
     */
    @Insert("INSERT INTO " + T_OUTBOUND_DETAIL + " " +
            "(\"id\", \"order_id\", \"material_id\", \"material_code\", \"material_name\", \"material_brand\", " +
            "\"material_spec\", \"material_unit\", \"quantity\", \"stock_before\", \"corp_code\", \"created_at\", " +
            "\"created_by\", \"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.order_id}, #{row.material_id}, #{row.material_code}, #{row.material_name}, " +
            "#{row.material_brand}, #{row.material_spec}, #{row.material_unit}, #{row.quantity}, #{row.stock_before}, " +
            "'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator})")
    int insertOutboundDetail(@Param("row") Map<String, Object> row);

    // ==================== P1 列表 / 详情 / 导出 ====================

    /**
     * 单据列表分页（先 UNION 再过滤排序，业务日期倒序；仓库名/科室名 LEFT JOIN 不丢行）。
     */
    @Select("<script>" +
            "SELECT t.source AS source, t.id AS id, t.order_no AS orderNo, t.biz_type AS bizType, " +
            "to_char(t.order_date, 'YYYY-MM-DD') AS orderDate, " +
            "t.warehouse_id AS warehouseId, w.xoysox AS warehouseName, " +
            "t.department_id AS departmentId, g.name AS departmentName, " +
            "t.counterparty AS counterparty, t.operator_id AS operatorId, t.operator_name AS operatorName, " +
            "t.remark AS remark " +
            "FROM " + ORDERS_UNION + " t " +
            "LEFT JOIN " + T_WAREHOUSE + " w ON w.id = t.warehouse_id AND w.corp_code = 'hltgq' " +
            "LEFT JOIN " + T_ORG + " g ON g.id = t.department_id AND g.corp_code = 'hltgq' " +
            "WHERE 1 = 1 " + LIST_CONDITIONS +
            "ORDER BY t.order_date DESC, t.created_at DESC, t.order_no DESC " +
            "LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<OrderPageItemVO> selectOrderPage(@Param("source") String source,
                                          @Param("orderNo") String orderNo,
                                          @Param("bizSource") String bizSource,
                                          @Param("bizCode") String bizCode,
                                          @Param("warehouseId") String warehouseId,
                                          @Param("departmentId") String departmentId,
                                          @Param("sourceUnit") String sourceUnit,
                                          @Param("receiveUnit") String receiveUnit,
                                          @Param("materialCode") String materialCode,
                                          @Param("materialName") String materialName,
                                          @Param("startDate") String startDate,
                                          @Param("endDate") String endDate,
                                          @Param("size") int size,
                                          @Param("offset") long offset);

    /** 单据列表计数（条件与 {@link #selectOrderPage} 完全一致）。 */
    @Select("<script>SELECT COUNT(*) FROM " + ORDERS_UNION + " t WHERE 1 = 1 " + LIST_CONDITIONS + "</script>")
    long countOrders(@Param("source") String source,
                     @Param("orderNo") String orderNo,
                     @Param("bizSource") String bizSource,
                     @Param("bizCode") String bizCode,
                     @Param("warehouseId") String warehouseId,
                     @Param("departmentId") String departmentId,
                     @Param("sourceUnit") String sourceUnit,
                     @Param("receiveUnit") String receiveUnit,
                     @Param("materialCode") String materialCode,
                     @Param("materialName") String materialName,
                     @Param("startDate") String startDate,
                     @Param("endDate") String endDate);

    /** 入库单详情头（bizType=inbound_type，counterparty=source_unit）。 */
    @Select("SELECT 'RK' AS source, h.id AS id, h.order_no AS orderNo, h.inbound_type AS bizType, " +
            "to_char(h.order_date, 'YYYY-MM-DD') AS orderDate, " +
            "h.warehouse_id AS warehouseId, w.xoysox AS warehouseName, " +
            "h.department_id AS departmentId, g.name AS departmentName, " +
            "h.source_unit AS counterparty, h.operator_id AS operatorId, h.operator_name AS operatorName, " +
            "h.remark AS remark " +
            "FROM " + T_INBOUND + " h " +
            "LEFT JOIN " + T_WAREHOUSE + " w ON w.id = h.warehouse_id AND w.corp_code = 'hltgq' " +
            "LEFT JOIN " + T_ORG + " g ON g.id = h.department_id AND g.corp_code = 'hltgq' " +
            "WHERE h.id = #{id} AND h.corp_code = 'hltgq' AND h.del_flag = '#2#'")
    OrderDetailVO selectInboundHeader(@Param("id") String id);

    /** 出库单详情头（bizType=outbound_type，counterparty=receive_unit）。 */
    @Select("SELECT 'CK' AS source, h.id AS id, h.order_no AS orderNo, h.outbound_type AS bizType, " +
            "to_char(h.order_date, 'YYYY-MM-DD') AS orderDate, " +
            "h.warehouse_id AS warehouseId, w.xoysox AS warehouseName, " +
            "h.department_id AS departmentId, g.name AS departmentName, " +
            "h.receive_unit AS counterparty, h.operator_id AS operatorId, h.operator_name AS operatorName, " +
            "h.remark AS remark " +
            "FROM " + T_OUTBOUND + " h " +
            "LEFT JOIN " + T_WAREHOUSE + " w ON w.id = h.warehouse_id AND w.corp_code = 'hltgq' " +
            "LEFT JOIN " + T_ORG + " g ON g.id = h.department_id AND g.corp_code = 'hltgq' " +
            "WHERE h.id = #{id} AND h.corp_code = 'hltgq' AND h.del_flag = '#2#'")
    OrderDetailVO selectOutboundHeader(@Param("id") String id);

    /** 入库单明细（stockBefore 恒空）。 */
    @Select("SELECT d.material_id AS materialId, d.material_code AS materialCode, d.material_name AS materialName, " +
            "d.material_brand AS materialBrand, d.material_spec AS materialSpec, d.material_unit AS materialUnit, " +
            "d.quantity AS quantity " +
            "FROM " + T_INBOUND_DETAIL + " d " +
            "WHERE d.order_id = #{orderId} AND d.corp_code = 'hltgq' " +
            "ORDER BY d.created_at ASC, d.id ASC")
    List<OrderDetailVO.Item> selectInboundItems(@Param("orderId") String orderId);

    /** 出库单明细（含 stockBefore 变动前快照）。 */
    @Select("SELECT d.material_id AS materialId, d.material_code AS materialCode, d.material_name AS materialName, " +
            "d.material_brand AS materialBrand, d.material_spec AS materialSpec, d.material_unit AS materialUnit, " +
            "d.quantity AS quantity, d.stock_before AS stockBefore " +
            "FROM " + T_OUTBOUND_DETAIL + " d " +
            "WHERE d.order_id = #{orderId} AND d.corp_code = 'hltgq' " +
            "ORDER BY d.created_at ASC, d.id ASC")
    List<OrderDetailVO.Item> selectOutboundItems(@Param("orderId") String orderId);

    /**
     * 明细级导出（一行 = 单据 × 物资明细；筛选条件与列表完全一致：单据级命中后展开其全部明细）。
     * <p>无分页按条件全量返回，Service 翻译中文后由前端生成 Excel。
     */
    @Select("<script>" +
            "SELECT t.order_no AS orderNo, t.source AS source, t.biz_type AS bizType, " +
            "to_char(t.order_date, 'YYYY-MM-DD') AS orderDate, " +
            "w.xoysox AS warehouseName, g.name AS departmentName, " +
            "t.counterparty AS counterparty, t.operator_name AS operatorName, t.remark AS remark, " +
            "t.material_code AS materialCode, t.material_name AS materialName, t.material_brand AS materialBrand, " +
            "t.material_spec AS materialSpec, t.material_unit AS materialUnit, t.quantity AS quantity " +
            "FROM (" +
            "SELECT h.id AS id, h.order_no AS order_no, 'RK' AS source, h.inbound_type AS biz_type, " +
            "h.order_date AS order_date, h.warehouse_id AS warehouse_id, h.department_id AS department_id, " +
            "h.source_unit AS counterparty, h.operator_name AS operator_name, h.remark AS remark, " +
            "h.created_at AS created_at, d.material_code AS material_code, d.material_name AS material_name, " +
            "d.material_brand AS material_brand, d.material_spec AS material_spec, " +
            "d.material_unit AS material_unit, d.quantity AS quantity " +
            "FROM " + T_INBOUND + " h JOIN " + T_INBOUND_DETAIL + " d ON d.order_id = h.id " +
            "WHERE h.corp_code = 'hltgq' AND h.del_flag = '#2#' " +
            "UNION ALL " +
            "SELECT h.id AS id, h.order_no AS order_no, 'CK' AS source, h.outbound_type AS biz_type, " +
            "h.order_date AS order_date, h.warehouse_id AS warehouse_id, h.department_id AS department_id, " +
            "h.receive_unit AS counterparty, h.operator_name AS operator_name, h.remark AS remark, " +
            "h.created_at AS created_at, d.material_code AS material_code, d.material_name AS material_name, " +
            "d.material_brand AS material_brand, d.material_spec AS material_spec, " +
            "d.material_unit AS material_unit, d.quantity AS quantity " +
            "FROM " + T_OUTBOUND + " h JOIN " + T_OUTBOUND_DETAIL + " d ON d.order_id = h.id " +
            "WHERE h.corp_code = 'hltgq' AND h.del_flag = '#2#'" +
            ") t " +
            "LEFT JOIN " + T_WAREHOUSE + " w ON w.id = t.warehouse_id AND w.corp_code = 'hltgq' " +
            "LEFT JOIN " + T_ORG + " g ON g.id = t.department_id AND g.corp_code = 'hltgq' " +
            "WHERE 1 = 1 " + LIST_CONDITIONS +
            "ORDER BY t.order_date DESC, t.order_no DESC, t.material_code ASC" +
            "</script>")
    List<OrderExportRowVO> selectOrderExportRows(@Param("source") String source,
                                                 @Param("orderNo") String orderNo,
                                                 @Param("bizSource") String bizSource,
                                                 @Param("bizCode") String bizCode,
                                                 @Param("warehouseId") String warehouseId,
                                                 @Param("departmentId") String departmentId,
                                                 @Param("sourceUnit") String sourceUnit,
                                                 @Param("receiveUnit") String receiveUnit,
                                                 @Param("materialCode") String materialCode,
                                                 @Param("materialName") String materialName,
                                                 @Param("startDate") String startDate,
                                                 @Param("endDate") String endDate);
}
