package com.qgyun.hltgq.hltgqsite.inventoryorder.service;

import com.qgyun.hltgq.hltgqsite.inventoryorder.InventoryCategory;
import com.qgyun.hltgq.hltgqsite.inventoryorder.InventoryTypeDict;
import com.qgyun.hltgq.hltgqsite.inventoryorder.mapper.InventoryLedgerMapper;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.LedgerPageItemVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.StockChangeVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.WarehouseVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.clampPage;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.clampSize;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.pageResult;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.requireText;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.trim;

/**
 * 物资台账服务（P4 台账列表/导出、P5 出入库记录/导出、左树仓库）。
 * <p>只读查询：台账/流水均为单据写入的产物，本服务不做任何写操作；
 * 安全库存与库存状态按物资目录（slaifi.unqvqe）实时计算，目录调整即时生效；
 * 类别按页面 scope 隔离：未传/common = 常用物资，emergency = 应急物资（两侧互不可见）；
 * 翻译口径统一走 {@link InventoryTypeDict}（状态：正常/偏低，与设计方案一致）。
 */
@Service
public class InventoryLedgerService {

    private final InventoryLedgerMapper ledgerMapper;

    public InventoryLedgerService(InventoryLedgerMapper ledgerMapper) {
        this.ledgerMapper = ledgerMapper;
    }

    /** P4 台账分页（左树仓库过滤 + 编号/名称模糊 + 库存状态筛选 + 物资类别隔离）。 */
    public Map<String, Object> page(String warehouseId, String keyword, String status, String scope,
                                    int page, int size) {
        int p = clampPage(page);
        int s = clampSize(size);
        String wid = trim(warehouseId);
        String kw = trim(keyword);
        String statusCode = InventoryTypeDict.resolveStockStatus(status);
        boolean emergency = InventoryCategory.isEmergency(scope);
        boolean commonOnly = InventoryCategory.isCommon(scope);
        long total = ledgerMapper.countLedgers(wid, kw, statusCode, emergency, commonOnly);
        List<LedgerPageItemVO> records = ledgerMapper.selectLedgerPage(wid, kw, statusCode,
                emergency, commonOnly, s, (long) (p - 1) * s);
        for (LedgerPageItemVO row : records) {
            row.setStatusName(InventoryTypeDict.stockStatusName(row.getStatusCode()));
        }
        return pageResult(total, p, s, records);
    }

    /** P4 台账导出（同列表条件全量；前端生成 Excel）。 */
    public List<LedgerPageItemVO> ledgerExport(String warehouseId, String keyword, String status, String scope) {
        List<LedgerPageItemVO> rows = ledgerMapper.selectLedgerExportRows(
                trim(warehouseId), trim(keyword), InventoryTypeDict.resolveStockStatus(status),
                InventoryCategory.isEmergency(scope), InventoryCategory.isCommon(scope));
        for (LedgerPageItemVO row : rows) {
            row.setStatusName(InventoryTypeDict.stockStatusName(row.getStatusCode()));
        }
        return rows;
    }

    /** P5 出入库记录分页（台账行 = materialId + warehouseId，created_at 倒序；按页面视角隔离类别）。 */
    public Map<String, Object> changes(String materialId, String warehouseId, int page, int size, String scope) {
        int p = clampPage(page);
        int s = clampSize(size);
        String mid = requireText(materialId, "请指定物资");
        String wid = requireText(warehouseId, "请指定仓库");
        boolean emergency = InventoryCategory.isEmergency(scope);
        boolean commonOnly = InventoryCategory.isCommon(scope);
        long total = ledgerMapper.countStockChanges(mid, wid, emergency, commonOnly);
        List<StockChangeVO> records = ledgerMapper.selectStockChanges(mid, wid, emergency, commonOnly, s, (long) (p - 1) * s);
        for (StockChangeVO row : records) {
            decorate(row);
        }
        return pageResult(total, p, s, records);
    }

    /** P5 出入库记录导出（同记录条件全量；前端生成 Excel）。 */
    public List<StockChangeVO> changesExport(String materialId, String warehouseId, String scope) {
        boolean emergency = InventoryCategory.isEmergency(scope);
        boolean commonOnly = InventoryCategory.isCommon(scope);
        List<StockChangeVO> rows = ledgerMapper.selectStockChangesForExport(
                requireText(materialId, "请指定物资"), requireText(warehouseId, "请指定仓库"),
                emergency, commonOnly);
        for (StockChangeVO row : rows) {
            decorate(row);
        }
        return rows;
    }

    /** P4 左树：全部仓库（含停用，停用节点 disabled=true，点击仍可查历史台账）。 */
    public List<WarehouseVO> warehouses() {
        return ledgerMapper.selectWarehouses(false);
    }

    /** 流水行翻译（变动类型 + 按单据类别解释的业务类型） */
    private static void decorate(StockChangeVO row) {
        row.setChangeTypeName(InventoryTypeDict.changeTypeName(row.getChangeType()));
        row.setBizTypeName(InventoryTypeDict.changeBizTypeName(row.getOrderKind(), row.getBizType()));
    }
}
