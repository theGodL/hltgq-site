package com.qgyun.hltgq.hltgqsite.inventoryorder;

import com.qgyun.hltgq.hltgqsite.inventoryorder.mapper.InventoryLedgerMapper;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.MaterialVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.WarehouseVO;
import com.qgyun.hltgq.hltgqsite.model.util.ApiResultUtils;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.OperatorUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存业务（出入库/盘点）共用支撑（复用的单一实现点，供 OrderService / LedgerService /
 * OrderNoService / StocktakeService 共享）。
 * <ul>
 *   <li>文本归一：{@link #trim(String)} / {@link #requireText(String, String)}（委托 CheckUtils）；</li>
 *   <li>分页参数与结果：{@link #clampPage(int)} / {@link #clampSize(int)} /
 *       {@link #pageResult(long, int, int, List)}（委托 ApiResultUtils，全站口径 total/page/size/pages/records）；</li>
 *   <li>审计操作人：{@link #currentOperatorId(String)}（委托 OperatorUtils，会话缺失时用配置兜底，审计列 NOT NULL）；</li>
 *   <li>业务校验：{@link #requireDate(String, String)} / {@link #normalizeDay(String, String)}
 *       （委托 CheckUtils） / {@link #normalizeRemark(String, int)} / {@link #safeItems(List, String)} /
 *       {@link #requireEnabledWarehouse(InventoryLedgerMapper, String)}；</li>
 *   <li>台账建账要素：{@link #buildNewLedgerRow} / {@link #parseSafetyStock(String)} /
 *       {@link #nullToEmpty(String)}。</li>
 * </ul>
 */
public final class InventorySupport {

    /** 明细行数防御上限（保存事务逐行写台账，出入库与盘点共用口径） */
    public static final int MAX_ITEMS = 500;

    private InventorySupport() {
    }

    /** 去首尾空白，空串按 null 处理（委托 CheckUtils） */
    public static String trim(String value) {
        return CheckUtils.trim(value);
    }

    /** 必填文本（trim 后为空报 400；委托 CheckUtils） */
    public static String requireText(String value, String message) {
        return CheckUtils.requireText(value, message);
    }

    /** 页码收敛（1 ~ 100000；委托 ApiResultUtils） */
    public static int clampPage(int page) {
        return ApiResultUtils.clampPage(page);
    }

    /** 页大小收敛（1 ~ 100；委托 ApiResultUtils） */
    public static int clampSize(int size) {
        return ApiResultUtils.clampSize(size);
    }

    /** 分页结果（全站口径 total/page/size/pages/records；委托 ApiResultUtils） */
    public static Map<String, Object> pageResult(long total, int page, int size, List<?> records) {
        return ApiResultUtils.pageResult(total, page, size, records);
    }

    /** 当前操作人（会话缺失时用配置兜底；审计列 NOT NULL；委托 OperatorUtils） */
    public static String currentOperatorId(String fallbackOperator) {
        return OperatorUtils.currentOperatorId(fallbackOperator);
    }

    /** 日期必填校验（yyyy-MM-dd；label 如「单据日期」「盘点日期」；委托 CheckUtils） */
    public static LocalDate requireDate(String value, String label) {
        return CheckUtils.requireDate(value, label);
    }

    /** 查询日期参数校验（可空；yyyy-MM-dd；委托 CheckUtils） */
    public static String normalizeDay(String value, String label) {
        return CheckUtils.normalizeDay(value, label);
    }

    /** 备注归一（trim；超过 maxLength 字报 400） */
    public static String normalizeRemark(String remark, int maxLength) {
        String s = trim(remark);
        if (s != null && s.length() > maxLength) {
            throw new IllegalArgumentException("备注不能超过 " + maxLength + " 字");
        }
        return s;
    }

    /** 明细行数校验（非空、不超过 {@link #MAX_ITEMS} 行；emptyMessage 如「请至少添加一行物资明细」） */
    public static <T> List<T> safeItems(List<T> items, String emptyMessage) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("明细行数不能超过 " + MAX_ITEMS + " 行");
        }
        return items;
    }

    /** 仓库校验：存在且未停用（停用仓库不可选） */
    public static WarehouseVO requireEnabledWarehouse(InventoryLedgerMapper ledgerMapper, String warehouseId) {
        WarehouseVO warehouse = ledgerMapper.selectWarehouseById(warehouseId);
        if (warehouse == null) {
            throw new IllegalArgumentException("仓库不存在或已删除");
        }
        if (Boolean.TRUE.equals(warehouse.getDisabled())) {
            throw new IllegalArgumentException("仓库已停用，不可选择");
        }
        return warehouse;
    }

    /** 安全库存文本（unqvqe）转整数：空/非法恒 null（建账 vhvhja 允许为空） */
    public static Integer parseSafetyStock(String raw) {
        String s = trim(raw);
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s).intValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 快照必填列兜底（列 NOT NULL，源字段可空时用空串） */
    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 台账建账行组装（建账要素单一维护点：出入库建账与盘点纯盘盈建账共用）。
     * row key：id/material_id/warehouse_id/department_id/brand/name/spec/unit/quantity/safety_stock/now/operator。
     */
    public static Map<String, Object> buildNewLedgerRow(String id, MaterialVO material, String warehouseId,
                                                        String departmentId, int quantity,
                                                        LocalDateTime now, String operator) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("material_id", material.getId());
        row.put("warehouse_id", warehouseId);
        row.put("department_id", departmentId);
        row.put("brand", material.getBrand());
        row.put("name", nullToEmpty(material.getName()));
        row.put("spec", material.getSpec());
        row.put("unit", material.getUnit());
        row.put("quantity", quantity);
        row.put("safety_stock", parseSafetyStock(material.getSafetyStockRaw()));
        row.put("now", now);
        row.put("operator", operator);
        return row;
    }
}
