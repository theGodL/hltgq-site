package com.qgyun.hltgq.hltgqsite.inventoryorder.service;

import com.qgyun.hltgq.hltgqsite.inventoryorder.InventoryTypeDict;
import com.qgyun.hltgq.hltgqsite.inventoryorder.mapper.InventoryLedgerMapper;
import com.qgyun.hltgq.hltgqsite.inventoryorder.mapper.InventoryOrderMapper;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.InboundOrderSaveVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.MaterialVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.OrderDetailVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.OrderExportRowVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.OrderPageItemVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.OutboundOrderSaveVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.PickerOptionVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.StockLedgerOptionVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.WarehouseVO;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.buildNewLedgerRow;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.clampPage;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.clampSize;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.currentOperatorId;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.normalizeDay;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.normalizeRemark;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.nullToEmpty;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.pageResult;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.requireDate;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.requireEnabledWarehouse;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.requireText;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.safeItems;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.trim;

/**
 * 出入库单据服务（P1 列表/导出、详情、P2/P3 新增保存、表单元数据与选择器）。
 * <p>保存事务（设计方案 5.2）单事务执行：校验 → 独立事务取号 → 写主表+明细 →
 * 逐行台账（建账/累加/乐观锁扣减，影响行数为最终判定）→ 逐行流水；任一步失败整体回滚，
 * 编号不回滚（跳号属预期）。
 * <p>入库明细快照与台账建账要素后端回查 slaifi 固化（编号/名称/品牌/单位不接受前端传入；
 * 规格型号支持前端手工覆写，为空回退目录值）；出库明细快照取当前仓库台账行
 * （限可用库存 &gt; 0 的物资），规格型号同样支持前端覆写。
 */
@Service
public class InventoryOrderService {

    private static final Logger log = LoggerFactory.getLogger(InventoryOrderService.class);

    private final InventoryOrderMapper orderMapper;

    private final InventoryLedgerMapper ledgerMapper;

    private final InventoryOrderNoService orderNoService;

    private final ShortIdGenerator shortIdGenerator;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public InventoryOrderService(InventoryOrderMapper orderMapper,
                                 InventoryLedgerMapper ledgerMapper,
                                 InventoryOrderNoService orderNoService,
                                 ShortIdGenerator shortIdGenerator) {
        this.orderMapper = orderMapper;
        this.ledgerMapper = ledgerMapper;
        this.orderNoService = orderNoService;
        this.shortIdGenerator = shortIdGenerator;
    }

    // ==================== P1 列表 / 导出 ====================

    /**
     * 单据列表分页（全部/入库/出库 + 9 项查询条件，见 {@link InventoryOrderMapper#LIST_CONDITIONS}）。
     */
    public Map<String, Object> page(int page, int size, String source, String orderNo, String bizType,
                                    String warehouseId, String departmentId, String sourceUnit,
                                    String receiveUnit, String materialCode, String materialName,
                                    String startDate, String endDate) {
        int p = clampPage(page);
        int s = clampSize(size);
        String src = normalizeSource(source);
        String[] biz = InventoryTypeDict.parseBizType(trim(bizType));
        String bizSource = biz == null ? null : biz[0];
        String bizCode = biz == null ? null : biz[1];
        String no = trim(orderNo);
        String wid = trim(warehouseId);
        String did = trim(departmentId);
        String su = trim(sourceUnit);
        String ru = trim(receiveUnit);
        String mc = trim(materialCode);
        String mn = trim(materialName);
        String sd = normalizeDay(startDate, "起始日期");
        String ed = normalizeDay(endDate, "结束日期");

        long total = orderMapper.countOrders(src, no, bizSource, bizCode, wid, did, su, ru, mc, mn, sd, ed);
        List<OrderPageItemVO> records = orderMapper.selectOrderPage(src, no, bizSource, bizCode, wid, did, su, ru,
                mc, mn, sd, ed, s, (long) (p - 1) * s);
        for (OrderPageItemVO row : records) {
            row.setBizTypeName(InventoryTypeDict.bizTypeName(row.getSource(), row.getBizType()));
        }
        return pageResult(total, p, s, records);
    }

    /** 明细级导出（同列表条件全量，Service 翻译中文，前端生成 Excel）。 */
    public List<OrderExportRowVO> export(String source, String orderNo, String bizType,
                                         String warehouseId, String departmentId, String sourceUnit,
                                         String receiveUnit, String materialCode, String materialName,
                                         String startDate, String endDate) {
        String src = normalizeSource(source);
        String[] biz = InventoryTypeDict.parseBizType(trim(bizType));
        String bizSource = biz == null ? null : biz[0];
        String bizCode = biz == null ? null : biz[1];
        List<OrderExportRowVO> rows = orderMapper.selectOrderExportRows(src, trim(orderNo), bizSource, bizCode,
                trim(warehouseId), trim(departmentId), trim(sourceUnit), trim(receiveUnit),
                trim(materialCode), trim(materialName),
                normalizeDay(startDate, "起始日期"), normalizeDay(endDate, "结束日期"));
        for (OrderExportRowVO row : rows) {
            row.setSourceName(InventoryTypeDict.sourceName(row.getSource()));
            row.setBizTypeName(InventoryTypeDict.bizTypeName(row.getSource(), row.getBizType()));
        }
        return rows;
    }

    // ==================== 详情 / 元数据 ====================

    /** 单据详情（source=RK|CK 必填；主表 + 明细，含仓库名/科室名/业务类型中文）。 */
    public OrderDetailVO detail(String source, String id) {
        String src = normalizeSource(source);
        if (src == null) {
            throw new IllegalArgumentException("请指定单据来源（RK 或 CK）");
        }
        String key = requireText(id, "单据 id 不能为空");
        OrderDetailVO vo = InventoryTypeDict.SOURCE_INBOUND.equals(src)
                ? orderMapper.selectInboundHeader(key)
                : orderMapper.selectOutboundHeader(key);
        if (vo == null) {
            throw new IllegalArgumentException("单据不存在");
        }
        vo.setBizTypeName(InventoryTypeDict.bizTypeName(src, vo.getBizType()));
        vo.setItems(InventoryTypeDict.SOURCE_INBOUND.equals(src)
                ? orderMapper.selectInboundItems(key)
                : orderMapper.selectOutboundItems(key));
        return vo;
    }

    /** 表单元数据（业务类型选项、启用仓库含默认科室/经办人、当天日期）。 */
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("types", InventoryTypeDict.typeOptions());
        out.put("warehouses", ledgerMapper.selectWarehouses(true));
        out.put("today", LocalDate.now().toString());
        return out;
    }

    /** 物资目录候选（P2/P3「从目录选择」，不按默认仓库硬过滤）。 */
    public List<MaterialVO> materials(String keyword) {
        return ledgerMapper.selectMaterials(trim(keyword));
    }

    /** 出库台账候选（当前仓库可用库存 &gt; 0 的物资）。 */
    public List<StockLedgerOptionVO> stockLedgers(String warehouseId, String keyword) {
        return ledgerMapper.selectStockLedgerOptions(requireText(warehouseId, "请先选择仓库"), trim(keyword));
    }

    /** 归口科室候选。 */
    public List<PickerOptionVO> departments(String keyword) {
        return ledgerMapper.selectDepartments(trim(keyword));
    }

    /** 经办人候选（sub = 手机号）。 */
    public List<PickerOptionVO> users(String keyword) {
        return ledgerMapper.selectUsers(trim(keyword));
    }

    // ==================== P2 新增入库单 ====================

    /**
     * 新增入库单（保存即生效）。流程：校验 → 取号（独立事务）→ 主表 → 逐行（明细快照 +
     * 台账建账/累加 + 流水）。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailVO createInbound(InboundOrderSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String inboundType = trim(req.getInboundType());
        if (!InventoryTypeDict.isValidBizType(InventoryTypeDict.SOURCE_INBOUND, inboundType)) {
            throw new IllegalArgumentException("入库类型无效");
        }
        LocalDate orderDate = requireDate(req.getOrderDate(), "单据日期");
        String warehouseId = requireText(req.getWarehouseId(), "请选择仓库");
        String sourceUnit = requireText(req.getSourceUnit(), "请填写供应商/来源单位");
        requireMaxLength(sourceUnit, 255, "供应商/来源单位");
        String departmentId = requireText(req.getDepartmentId(), "请选择归口科室");
        String operatorId = requireText(req.getOperatorId(), "请选择经办人");
        String remark = normalizeRemark(req.getRemark(), 500);

        WarehouseVO warehouse = requireEnabledWarehouse(ledgerMapper, warehouseId);
        if (ledgerMapper.selectDepartmentName(departmentId) == null) {
            throw new IllegalArgumentException("归口科室不存在或已删除");
        }
        String operatorName = ledgerMapper.selectUserName(operatorId);
        if (operatorName == null) {
            throw new IllegalArgumentException("经办人不存在或已删除");
        }

        // 明细校验 + 后端回查物资固化快照/建账要素（规格型号可被前端手工覆写）
        List<MaterialVO> materials = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<String> specOverrides = new ArrayList<>();
        int lineNo = 1;
        for (InboundOrderSaveVO.Item item : safeItems(req.getItems(), "请至少添加一行物资明细")) {
            String materialId = requireText(item.getMaterialId(), "第 " + lineNo + " 行：请选择物资");
            MaterialVO material = ledgerMapper.selectMaterialById(materialId);
            if (material == null) {
                throw new IllegalArgumentException("第 " + lineNo + " 行：物资不存在或已删除");
            }
            materials.add(material);
            quantities.add(requirePositiveQty(item.getQuantity(), lineNo));
            specOverrides.add(normalizeSpecOverride(item.getSpec(), lineNo));
            lineNo++;
        }

        String orderNo = orderNoService.nextOrderNo(InventoryTypeDict.SOURCE_INBOUND);
        String orderId = shortIdGenerator.nextUUID(null);
        String operator = currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();
        String dateText = orderDate.toString();

        Map<String, Object> orderRow = new LinkedHashMap<>();
        orderRow.put("id", orderId);
        orderRow.put("order_no", orderNo);
        orderRow.put("inbound_type", inboundType);
        orderRow.put("order_date", dateText);
        orderRow.put("warehouse_id", warehouseId);
        orderRow.put("source_unit", sourceUnit);
        orderRow.put("department_id", departmentId);
        orderRow.put("operator_id", operatorId);
        orderRow.put("operator_name", operatorName);
        orderRow.put("remark", remark);
        orderRow.put("now", now);
        orderRow.put("operator", operator);
        orderMapper.insertInboundOrder(orderRow);

        for (int i = 0; i < materials.size(); i++) {
            MaterialVO material = materials.get(i);
            int qty = quantities.get(i);

            Map<String, Object> detailRow = new LinkedHashMap<>();
            detailRow.put("id", shortIdGenerator.nextUUID(null));
            detailRow.put("order_id", orderId);
            detailRow.put("material_id", material.getId());
            detailRow.put("material_code", nullToEmpty(material.getCode()));
            detailRow.put("material_name", nullToEmpty(material.getName()));
            detailRow.put("material_brand", material.getBrand());
            detailRow.put("material_spec", specOverrides.get(i) != null ? specOverrides.get(i) : material.getSpec());
            detailRow.put("material_unit", material.getUnit());
            detailRow.put("quantity", qty);
            detailRow.put("now", now);
            detailRow.put("operator", operator);
            orderMapper.insertInboundDetail(detailRow);

            // 台账：无行建账（含初始量），有行累加；并发建账冲突则重查后累加
            String ledgerId = ledgerMapper.selectLedgerIdByMaterialWarehouse(material.getId(), warehouseId);
            Integer qtyAfter = null;
            if (ledgerId == null) {
                // 建账要素统一由 InventorySupport.buildNewLedgerRow 组装（与盘点纯盘盈建账共用一个维护点）
                Map<String, Object> ledgerRow = buildNewLedgerRow(shortIdGenerator.nextUUID(null), material,
                        warehouseId, departmentId, qty, now, operator);
                if (ledgerMapper.insertLedgerIfAbsent(ledgerRow) > 0) {
                    ledgerId = (String) ledgerRow.get("id");
                    qtyAfter = qty;
                } else {
                    ledgerId = ledgerMapper.selectLedgerIdByMaterialWarehouse(material.getId(), warehouseId);
                    if (ledgerId == null) {
                        throw new IllegalStateException("台账建账冲突且未查到既有记录，请重试");
                    }
                }
            }
            if (qtyAfter == null) {
                qtyAfter = ledgerMapper.addLedgerStock(ledgerId, qty, operator, now);
                if (qtyAfter == null) {
                    throw new IllegalStateException("台账更新失败，请重试");
                }
            }

            Map<String, Object> changeRow = new LinkedHashMap<>();
            changeRow.put("id", shortIdGenerator.nextUUID(null));
            changeRow.put("ledger_id", ledgerId);
            changeRow.put("material_id", material.getId());
            changeRow.put("warehouse_id", warehouseId);
            changeRow.put("change_type", InventoryTypeDict.CHANGE_TYPE_IN);
            changeRow.put("order_kind", InventoryTypeDict.ORDER_KIND_INBOUND);
            changeRow.put("order_id", orderId);
            changeRow.put("order_no", orderNo);
            changeRow.put("biz_type", inboundType);
            changeRow.put("change_qty", qty);
            changeRow.put("qty_before", qtyAfter - qty);
            changeRow.put("qty_after", qtyAfter);
            changeRow.put("counterparty", sourceUnit);
            changeRow.put("operator_name", operatorName);
            changeRow.put("order_date", dateText);
            changeRow.put("now", now);
            changeRow.put("operator", operator);
            ledgerMapper.insertChangeLog(changeRow);
        }

        log.info("[出入库] 新增入库单：{}，仓库={}（{}），明细 {} 行，操作人={}",
                orderNo, warehouse.getName(), warehouseId, materials.size(), operator);
        return detail(InventoryTypeDict.SOURCE_INBOUND, orderId);
    }

    // ==================== P3 新增出库单 ====================

    /**
     * 新增出库单（保存即生效）。逐行台账乐观锁扣减（影响行数为最终判定，失败整单回滚
     * 提示「库存不足，请刷新后重试」），明细 stock_before 与流水 qty_before 取扣减后回算值。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailVO createOutbound(OutboundOrderSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String outboundType = trim(req.getOutboundType());
        if (!InventoryTypeDict.isValidBizType(InventoryTypeDict.SOURCE_OUTBOUND, outboundType)) {
            throw new IllegalArgumentException("出库类型无效");
        }
        LocalDate orderDate = requireDate(req.getOrderDate(), "单据日期");
        String warehouseId = requireText(req.getWarehouseId(), "请选择仓库");
        String receiveUnit = requireText(req.getReceiveUnit(), "请填写领用单位/工程名称");
        requireMaxLength(receiveUnit, 255, "领用单位/工程名称");
        String departmentId = requireText(req.getDepartmentId(), "请选择归口科室");
        String operatorId = requireText(req.getOperatorId(), "请选择经办人");
        String remark = normalizeRemark(req.getRemark(), 500);

        WarehouseVO warehouse = requireEnabledWarehouse(ledgerMapper, warehouseId);
        if (ledgerMapper.selectDepartmentName(departmentId) == null) {
            throw new IllegalArgumentException("归口科室不存在或已删除");
        }
        String operatorName = ledgerMapper.selectUserName(operatorId);
        if (operatorName == null) {
            throw new IllegalArgumentException("经办人不存在或已删除");
        }

        // 明细校验 + 台账行快照（限当前仓库；数量前置校验 ≤ 可用库存，最终以 UPDATE 影响行数为准）
        List<StockLedgerOptionVO> ledgers = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<String> specOverrides = new ArrayList<>();
        int lineNo = 1;
        for (OutboundOrderSaveVO.Item item : safeItems(req.getItems(), "请至少添加一行物资明细")) {
            String ledgerId = requireText(item.getLedgerId(), "第 " + lineNo + " 行：请选择物资");
            StockLedgerOptionVO ledger = ledgerMapper.selectStockLedgerRowById(ledgerId);
            if (ledger == null) {
                throw new IllegalArgumentException("第 " + lineNo + " 行：台账记录不存在，请重新选择物资");
            }
            if (!warehouseId.equals(trim(ledger.getWarehouseId()))) {
                throw new IllegalArgumentException("第 " + lineNo + " 行：所选物资与当前仓库不一致，请重新选择");
            }
            int qty = requirePositiveQty(item.getQuantity(), lineNo);
            if (ledger.getQuantity() != null && qty > ledger.getQuantity()) {
                throw new IllegalArgumentException("第 " + lineNo + " 行：出库数量超过当前可用库存（"
                        + ledger.getQuantity() + "）");
            }
            ledgers.add(ledger);
            quantities.add(qty);
            specOverrides.add(normalizeSpecOverride(item.getSpec(), lineNo));
            lineNo++;
        }

        String orderNo = orderNoService.nextOrderNo(InventoryTypeDict.SOURCE_OUTBOUND);
        String orderId = shortIdGenerator.nextUUID(null);
        String operator = currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();
        String dateText = orderDate.toString();

        Map<String, Object> orderRow = new LinkedHashMap<>();
        orderRow.put("id", orderId);
        orderRow.put("order_no", orderNo);
        orderRow.put("outbound_type", outboundType);
        orderRow.put("order_date", dateText);
        orderRow.put("warehouse_id", warehouseId);
        orderRow.put("receive_unit", receiveUnit);
        orderRow.put("department_id", departmentId);
        orderRow.put("operator_id", operatorId);
        orderRow.put("operator_name", operatorName);
        orderRow.put("remark", remark);
        orderRow.put("now", now);
        orderRow.put("operator", operator);
        orderMapper.insertOutboundOrder(orderRow);

        for (int i = 0; i < ledgers.size(); i++) {
            StockLedgerOptionVO ledger = ledgers.get(i);
            int qty = quantities.get(i);

            // 乐观锁扣减：返回 null = 库存不足 / 行不存在 → 整单回滚
            Integer qtyAfter = ledgerMapper.deductLedgerStock(ledger.getLedgerId(), qty, operator, now);
            if (qtyAfter == null) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行：库存不足，请刷新后重试");
            }
            int qtyBefore = qtyAfter + qty;

            Map<String, Object> detailRow = new LinkedHashMap<>();
            detailRow.put("id", shortIdGenerator.nextUUID(null));
            detailRow.put("order_id", orderId);
            detailRow.put("material_id", ledger.getMaterialId());
            detailRow.put("material_code", nullToEmpty(ledger.getCode()));
            detailRow.put("material_name", nullToEmpty(ledger.getName()));
            detailRow.put("material_brand", ledger.getBrand());
            detailRow.put("material_spec", specOverrides.get(i) != null ? specOverrides.get(i) : ledger.getSpec());
            detailRow.put("material_unit", ledger.getUnit());
            detailRow.put("quantity", qty);
            detailRow.put("stock_before", qtyBefore);
            detailRow.put("now", now);
            detailRow.put("operator", operator);
            orderMapper.insertOutboundDetail(detailRow);

            Map<String, Object> changeRow = new LinkedHashMap<>();
            changeRow.put("id", shortIdGenerator.nextUUID(null));
            changeRow.put("ledger_id", ledger.getLedgerId());
            changeRow.put("material_id", ledger.getMaterialId());
            changeRow.put("warehouse_id", warehouseId);
            changeRow.put("change_type", InventoryTypeDict.CHANGE_TYPE_OUT);
            changeRow.put("order_kind", InventoryTypeDict.ORDER_KIND_OUTBOUND);
            changeRow.put("order_id", orderId);
            changeRow.put("order_no", orderNo);
            changeRow.put("biz_type", outboundType);
            changeRow.put("change_qty", qty);
            changeRow.put("qty_before", qtyBefore);
            changeRow.put("qty_after", qtyAfter);
            changeRow.put("counterparty", receiveUnit);
            changeRow.put("operator_name", operatorName);
            changeRow.put("order_date", dateText);
            changeRow.put("now", now);
            changeRow.put("operator", operator);
            ledgerMapper.insertChangeLog(changeRow);
        }

        log.info("[出入库] 新增出库单：{}，仓库={}（{}），明细 {} 行，操作人={}",
                orderNo, warehouse.getName(), warehouseId, ledgers.size(), operator);
        return detail(InventoryTypeDict.SOURCE_OUTBOUND, orderId);
    }

    // ==================== 校验与工具 ====================

    /** 来源归一：空 → null（全部）；RK/CK → 自身；其它 → 400。 */
    private static String normalizeSource(String source) {
        String s = trim(source);
        if (s == null) {
            return null;
        }
        if (!InventoryTypeDict.SOURCE_INBOUND.equals(s) && !InventoryTypeDict.SOURCE_OUTBOUND.equals(s)) {
            throw new IllegalArgumentException("来源参数无效（应为 RK 或 CK）：" + s);
        }
        return s;
    }

    /** 长度上限校验（列宽约束前置拦截） */
    private static void requireMaxLength(String value, int max, String label) {
        if (value.length() > max) {
            throw new IllegalArgumentException(label + "不能超过 " + max + " 字");
        }
    }

    /** 明细行规格型号（选填）归一：去首尾空白，空 → null（回退快照）；超 255 字拦截 */
    private static String normalizeSpecOverride(String spec, int lineNo) {
        String s = trim(spec);
        if (s == null || s.isEmpty()) {
            return null;
        }
        if (s.length() > 255) {
            throw new IllegalArgumentException("第 " + lineNo + " 行：规格型号不能超过 255 字");
        }
        return s;
    }

    /** 数量必填校验（正整数） */
    private static int requirePositiveQty(Integer quantity, int lineNo) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("第 " + lineNo + " 行：数量必须为正整数");
        }
        return quantity;
    }

}
