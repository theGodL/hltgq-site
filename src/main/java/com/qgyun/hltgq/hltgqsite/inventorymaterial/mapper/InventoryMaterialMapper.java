package com.qgyun.hltgq.hltgqsite.inventorymaterial.mapper;

import com.qgyun.hltgq.hltgqsite.inventorymaterial.vo.MaterialPageItemVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.InventoryCategory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 物资目录（slaifi）维护：列表/导出/详情、编号唯一校验、新增/编辑/删除、引用检查。
 * <p>类别列 {@link InventoryCategory#COLUMN}（type）为低代码物资类别字段（#1# 常用 / #2# 应急），页面保存时原样写回。
 * <p>查询条件口径：emergency=true 仅应急物资；commonOnly=true 仅常用物资（历史 null 视为常用）。
 * <p>KES 注意：模糊匹配一律 {@code CONCAT('%', #{x}, '%')}，禁用 {@code ||}。
 */
public interface InventoryMaterialMapper {

    String T_MATERIAL = "\"qixiao-apaas\".\"t_auto_hltgq_knc3g_slaifi\"";
    String T_LEDGER = "\"qixiao-apaas\".\"t_auto_hltgq_knc3g_tzkioe\"";
    String T_CHANGE = "\"qixiao-apaas\".\"t_auto_hltgq_water_stock_change_log\"";
    String T_WAREHOUSE = "\"qixiao-apaas\".\"t_auto_hltgq_knc3g_twufyx\"";
    String T_INBOUND_DETAIL = "\"qixiao-apaas\".\"t_auto_hltgq_water_inbound_order_detail\"";
    String T_OUTBOUND_DETAIL = "\"qixiao-apaas\".\"t_auto_hltgq_water_outbound_order_detail\"";
    String T_STOCKTAKE_ITEM = "\"qixiao-apaas\".\"t_auto_hltgq_water_stocktake_order_detail\"";

    /** 类别归一表达式（与列表过滤条件共用同一份 SQL，避免展示与过滤口径不一致） */
    String CATEGORY_EXPR = InventoryCategory.NORMALIZED_EXPR;

    /** 列表查询条件（page/count/export 共用，要求别名 m） */
    String LIST_CONDITIONS =
            "<if test='emergency'>AND " + InventoryCategory.CONDITION_EMERGENCY + " </if>" +
            "<if test='commonOnly'>AND " + InventoryCategory.CONDITION_COMMON + " </if>" +
            "<if test='keyword != null and keyword != \"\"'>AND (m.higsdd LIKE CONCAT('%', #{keyword}, '%') " +
            "OR m.avlsdv LIKE CONCAT('%', #{keyword}, '%') OR m.fqztqm LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='warehouseId != null and warehouseId != \"\"'>AND m.fmewia = #{warehouseId} </if>";

    String SELECT_COLUMNS =
            "SELECT m.id AS id, m.higsdd AS code, m.avlsdv AS name, m.fqztqm AS brand, m.iwnyuk AS spec, " +
            "m.boipik AS unit, m.unqvqe AS safetyStockRaw, m.fmewia AS defaultWarehouseId, " +
            "w.xoysox AS defaultWarehouseName, m.epvuta AS remark, " + CATEGORY_EXPR + " AS category, " +
            "to_char(m.created_at, 'YYYY-MM-DD HH24:MI') AS createdAt " +
            "FROM " + T_MATERIAL + " m " +
            "LEFT JOIN " + T_WAREHOUSE + " w ON w.id = m.fmewia AND w.corp_code = 'hltgq' ";

    /** 目录分页（创建时间倒序） */
    @Select("<script>" + SELECT_COLUMNS +
            "WHERE m.corp_code = 'hltgq' " + LIST_CONDITIONS +
            "ORDER BY m.created_at DESC, m.id DESC " +
            "LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<MaterialPageItemVO> selectPage(@Param("emergency") boolean emergency,
                                        @Param("commonOnly") boolean commonOnly,
                                        @Param("keyword") String keyword,
                                        @Param("warehouseId") String warehouseId,
                                        @Param("size") int size,
                                        @Param("offset") long offset);

    /** 目录计数（条件与 {@link #selectPage} 一致） */
    @Select("<script>SELECT COUNT(*) FROM " + T_MATERIAL + " m WHERE m.corp_code = 'hltgq' " +
            LIST_CONDITIONS + "</script>")
    long countPage(@Param("emergency") boolean emergency,
                   @Param("commonOnly") boolean commonOnly,
                   @Param("keyword") String keyword,
                   @Param("warehouseId") String warehouseId);

    /** 导出（同列表条件，全量无分页；前端生成 Excel） */
    @Select("<script>" + SELECT_COLUMNS +
            "WHERE m.corp_code = 'hltgq' " + LIST_CONDITIONS +
            "ORDER BY m.created_at DESC, m.id DESC" +
            "</script>")
    List<MaterialPageItemVO> selectExportRows(@Param("emergency") boolean emergency,
                                              @Param("commonOnly") boolean commonOnly,
                                              @Param("keyword") String keyword,
                                              @Param("warehouseId") String warehouseId);

    /** 单个物资（编辑回显） */
    @Select(SELECT_COLUMNS + "WHERE m.id = #{id} AND m.corp_code = 'hltgq'")
    MaterialPageItemVO selectById(@Param("id") String id);

    /** 编号唯一校验（excludeId 为空表示新增） */
    @Select("<script>SELECT COUNT(*) FROM " + T_MATERIAL + " m " +
            "WHERE m.corp_code = 'hltgq' AND m.higsdd = #{code} " +
            "<if test='excludeId != null and excludeId != \"\"'>AND m.id != #{excludeId} </if>" +
            "</script>")
    long countByCode(@Param("code") String code, @Param("excludeId") String excludeId);

    /** 台账引用数（删除前端校验：有台账不允许删） */
    @Select("SELECT COUNT(*) FROM " + T_LEDGER + " t WHERE t.corp_code = 'hltgq' AND t.jzikio = #{materialId}")
    long countLedgerRefs(@Param("materialId") String materialId);

    /** 出入库流水引用数（删除前端校验） */
    @Select("SELECT COUNT(*) FROM " + T_CHANGE + " c WHERE c.corp_code = 'hltgq' AND c.material_id = #{materialId}")
    long countChangeRefs(@Param("materialId") String materialId);

    /**
     * 出入库明细引用数（删除前置校验）。
     * <p>单据的类别视角由「明细 material_id JOIN 目录 type」判定，物资被删后 JOIN 落空会把单据翻到另一视角，
     * 因此被单据明细引用的物资一律不允许删除。
     */
    @Select("SELECT (SELECT COUNT(*) FROM " + T_INBOUND_DETAIL + " d " +
            "WHERE d.corp_code = 'hltgq' AND d.material_id = #{materialId}) + " +
            "(SELECT COUNT(*) FROM " + T_OUTBOUND_DETAIL + " d " +
            "WHERE d.corp_code = 'hltgq' AND d.material_id = #{materialId})")
    long countOrderItemRefs(@Param("materialId") String materialId);

    /** 盘点明细引用数（删除前置校验，口径同上：明细引用决定盘点单视角）。 */
    @Select("SELECT COUNT(*) FROM " + T_STOCKTAKE_ITEM + " d " +
            "WHERE d.corp_code = 'hltgq' AND d.material_id = #{materialId}")
    long countStocktakeItemRefs(@Param("materialId") String materialId);

    /** 单位候选（目录去重，供表单下拉建议） */
    @Select("SELECT DISTINCT m.boipik AS u FROM " + T_MATERIAL + " m " +
            "WHERE m.corp_code = 'hltgq' AND m.boipik IS NOT NULL AND TRIM(m.boipik) != '' " +
            "ORDER BY 1")
    List<String> selectUnits();

    /**
     * 新增目录。
     * row key：id/code/name/brand/spec/unit/safety_stock/default_warehouse/remark/category/now/operator。
     */
    @Insert("INSERT INTO " + T_MATERIAL + " " +
            "(\"id\", \"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\", " +
            "\"" + InventoryCategory.COLUMN + "\", \"higsdd\", \"avlsdv\", \"fqztqm\", \"iwnyuk\", \"boipik\", " +
            "\"unqvqe\", \"fmewia\", \"epvuta\") " +
            "VALUES (#{row.id}, 'hltgq', #{row.now}, #{row.operator}, #{row.now}, #{row.operator}, " +
            "#{row.category}, #{row.code}, #{row.name}, #{row.brand}, #{row.spec}, #{row.unit}, " +
            "#{row.safety_stock}, #{row.default_warehouse}, #{row.remark})")
    int insert(@Param("row") Map<String, Object> row);

    /**
     * 编辑目录（不触碰分类 jwimko 等低代码字段）。
     * row key：id/code/name/brand/spec/unit/safety_stock/default_warehouse/remark/category/now/operator。
     */
    @Update("UPDATE " + T_MATERIAL + " SET " +
            "\"higsdd\" = #{row.code}, \"avlsdv\" = #{row.name}, \"fqztqm\" = #{row.brand}, " +
            "\"iwnyuk\" = #{row.spec}, \"boipik\" = #{row.unit}, \"unqvqe\" = #{row.safety_stock}, " +
            "\"fmewia\" = #{row.default_warehouse}, \"epvuta\" = #{row.remark}, " +
            "\"" + InventoryCategory.COLUMN + "\" = #{row.category}, " +
            "updated_at = #{row.now}, updated_by = #{row.operator} " +
            "WHERE id = #{row.id} AND corp_code = 'hltgq'")
    int update(@Param("row") Map<String, Object> row);

    /** 删除目录（调用前已校验无台账/流水引用） */
    @Delete("DELETE FROM " + T_MATERIAL + " WHERE id = #{id} AND corp_code = 'hltgq'")
    int delete(@Param("id") String id);

    /** 存在性（编辑/删除前置校验） */
    @Select("SELECT id FROM " + T_MATERIAL + " WHERE id = #{id} AND corp_code = 'hltgq'")
    String selectIdById(@Param("id") String id);
}
