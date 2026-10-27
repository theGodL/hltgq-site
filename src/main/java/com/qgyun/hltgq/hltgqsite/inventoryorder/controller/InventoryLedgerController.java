package com.qgyun.hltgq.hltgqsite.inventoryorder.controller;

import com.qgyun.hltgq.hltgqsite.inventoryorder.service.InventoryLedgerService;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.LedgerPageItemVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.StockChangeVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.WarehouseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 物资台账（P4 台账列表/导出、P5 出入库记录/导出、左树仓库）。
 * <p>只读接口：台账与流水由出入库单据写入，本页不提供任何修改能力。
 * <p>权限：本期所有登录用户可用（与阈值设置同口径）。
 */
@RestController
@RequestMapping("/inventory-ledger")
public class InventoryLedgerController {

    @Autowired
    private InventoryLedgerService ledgerService;

    /**
     * P4 台账分页（左树选中仓库过滤 + 编号/名称模糊 + 状态筛选）。
     *
     * @param page        页码（默认 1）
     * @param size        每页条数（默认 20，上限 100）
     * @param warehouseId 仓库 ID（可选；左树选中「全部」时不传）
     * @param keyword     物资编号/名称关键字（可选）
     * @param status      库存状态（可选）：正常/偏低 或编码 #1#/#2#（Service 归一）
     * @param scope       页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     * @return total / page / size / pages / records
     */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String warehouseId,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String scope) {
        return ledgerService.page(warehouseId, keyword, status, scope, page, size);
    }

    /**
     * P4 台账导出（条件与列表一致，全量无分页，前端生成 Excel）。
     */
    @GetMapping("/export")
    public List<LedgerPageItemVO> export(@RequestParam(required = false) String warehouseId,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String scope) {
        return ledgerService.ledgerExport(warehouseId, keyword, status, scope);
    }

    /**
     * P5 出入库记录分页（台账行 = 物资 + 仓库；created_at 倒序逐笔追溯）。
     *
     * @param materialId  物资 ID（必填）
     * @param warehouseId 仓库 ID（必填）
     * @param page        页码（默认 1）
     * @param size        每页条数（默认 20，上限 100）
     * @param scope       页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     */
    @GetMapping("/changes")
    public Map<String, Object> changes(@RequestParam String materialId,
                                       @RequestParam String warehouseId,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String scope) {
        return ledgerService.changes(materialId, warehouseId, page, size, scope);
    }

    /**
     * P5 出入库记录导出（同记录条件全量，前端生成 Excel）。
     */
    @GetMapping("/changes-export")
    public List<StockChangeVO> changesExport(@RequestParam String materialId,
                                             @RequestParam String warehouseId,
                                             @RequestParam(required = false) String scope) {
        return ledgerService.changesExport(materialId, warehouseId, scope);
    }

    /**
     * P4 左树仓库清单：全部仓库（含停用，停用节点 disabled=true，点击仍可查历史台账）。
     */
    @GetMapping("/warehouses")
    public List<WarehouseVO> warehouses() {
        return ledgerService.warehouses();
    }
}
