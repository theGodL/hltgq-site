package com.qgyun.hltgq.hltgqsite.inventoryorder.controller;

import com.qgyun.hltgq.hltgqsite.inventoryorder.service.InventoryOrderService;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.InboundOrderSaveVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.MaterialVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.OrderDetailVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.OrderExportRowVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.OutboundOrderSaveVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.PickerOptionVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.StockLedgerOptionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 出入库单据（P1 列表/详情/导出、P2/P3 新增保存、表单选择器）。
 * <p>权限：本期所有登录用户可用（与阈值设置同口径），后续如需收紧为指定角色，在
 * AuthInterceptor 的角色校验基础上对 /inbound、/outbound 加角色断言即可（预留扩展点）。
 * <p>保存即生效：入库/出库无编辑与删除（纠错由线下处理，见设计方案 5.3）。
 */
@RestController
@RequestMapping("/inventory-order")
public class InventoryOrderController {

    private static final Logger log = LoggerFactory.getLogger(InventoryOrderController.class);

    @Autowired
    private InventoryOrderService orderService;

    /**
     * 表单元数据：业务类型选项（联合键 RK#1#/CK#1#，group 区分 in/out）、启用仓库
     * （含默认归口科室/经办人）、当天日期。
     */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return orderService.meta();
    }

    /**
     * 单据列表分页（全部/入库/出库 Tab + 查询条件）。
     *
     * @param page         页码（默认 1）
     * @param size         每页条数（默认 20，上限 100）
     * @param source       来源（可选）：RK 入库 / CK 出库，不传为全部
     * @param orderNo      单据编号（模糊，可选）
     * @param bizType      业务类型联合键（可选）：RK#1# / CK#1#（前端按当前 Tab 过滤选项）
     * @param warehouseId  仓库 ID（可选）
     * @param departmentId 归口科室 ID（可选）
     * @param sourceUnit   供应商/来源单位（模糊，仅入库单，可选）
     * @param receiveUnit  领用单位/工程名称（模糊，仅出库单，可选）
     * @param materialCode 物资编号（明细级模糊，可选）
     * @param materialName 物资名称（明细级模糊，可选）
     * @param startDate    单据日期起（yyyy-MM-dd，可选，含当日）
     * @param endDate      单据日期止（yyyy-MM-dd，可选，含当日）
     * @param scope        页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     * @return total / page / size / pages / records
     */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String source,
                                    @RequestParam(required = false) String orderNo,
                                    @RequestParam(required = false) String bizType,
                                    @RequestParam(required = false) String warehouseId,
                                    @RequestParam(required = false) String departmentId,
                                    @RequestParam(required = false) String sourceUnit,
                                    @RequestParam(required = false) String receiveUnit,
                                    @RequestParam(required = false) String materialCode,
                                    @RequestParam(required = false) String materialName,
                                    @RequestParam(required = false) String startDate,
                                    @RequestParam(required = false) String endDate,
                                    @RequestParam(required = false) String scope) {
        return orderService.page(page, size, source, orderNo, bizType, warehouseId, departmentId,
                sourceUnit, receiveUnit, materialCode, materialName, startDate, endDate, scope);
    }

    /**
     * 单据详情（主表 + 明细；业务类型与来源中文均已翻译；跨类别单据按 id 直达也拒绝）。
     *
     * @param source RK 入库 / CK 出库（必填）
     * @param id     单据 ID（必填）
     * @param scope  页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     */
    @GetMapping("/detail")
    public OrderDetailVO detail(@RequestParam String source, @RequestParam String id,
                                @RequestParam(required = false) String scope) {
        return orderService.detail(source, id, scope);
    }

    /**
     * 新增入库单（保存即生效）。
     *
     * @return 保存后的单据详情
     */
    @PostMapping("/inbound")
    public OrderDetailVO createInbound(@RequestBody InboundOrderSaveVO body) {
        log.info("收到新增入库单请求：type={}，warehouse={}，items={}",
                body == null ? null : body.getInboundType(),
                body == null ? null : body.getWarehouseId(),
                body == null || body.getItems() == null ? 0 : body.getItems().size());
        return orderService.createInbound(body);
    }

    /**
     * 新增出库单（保存即生效；台账乐观锁扣减，库存不足整单回滚）。
     *
     * @return 保存后的单据详情
     */
    @PostMapping("/outbound")
    public OrderDetailVO createOutbound(@RequestBody OutboundOrderSaveVO body) {
        log.info("收到新增出库单请求：type={}，warehouse={}，items={}",
                body == null ? null : body.getOutboundType(),
                body == null ? null : body.getWarehouseId(),
                body == null || body.getItems() == null ? 0 : body.getItems().size());
        return orderService.createOutbound(body);
    }

    /**
     * 物资目录候选（P2/P3「从目录选择」；不按默认仓库硬过滤）。
     *
     * @param keyword 物资编号/名称关键字（可选）
     * @param scope   页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     */
    @GetMapping("/materials")
    public List<MaterialVO> materials(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String scope) {
        return orderService.materials(keyword, scope);
    }

    /**
     * 出库台账候选（当前仓库可用库存 &gt; 0 的物资；出库明细只能用这里的行）。
     *
     * @param warehouseId 仓库 ID（必填）
     * @param keyword     物资编号/名称关键字（可选）
     * @param scope       页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     */
    @GetMapping("/stock-ledgers")
    public List<StockLedgerOptionVO> stockLedgers(@RequestParam String warehouseId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String scope) {
        return orderService.stockLedgers(warehouseId, keyword, scope);
    }

    /**
     * 归口科室候选（平台组织表，按名称模糊）。
     *
     * @param keyword 科室名称关键字（可选）
     */
    @GetMapping("/departments")
    public List<PickerOptionVO> departments(@RequestParam(required = false) String keyword) {
        return orderService.departments(keyword);
    }

    /**
     * 经办人候选（平台用户表，按姓名/手机号模糊；sub = 手机号）。
     *
     * @param keyword 姓名/手机号关键字（可选）
     */
    @GetMapping("/users")
    public List<PickerOptionVO> users(@RequestParam(required = false) String keyword) {
        return orderService.users(keyword);
    }

    /**
     * 单据明细级导出（一行 = 单据 × 物资明细；条件与列表一致，全量无分页，前端生成 Excel）。
     * <p>参数同 {@link #page}（不含分页参数）。
     */
    @GetMapping("/export")
    public List<OrderExportRowVO> export(@RequestParam(required = false) String source,
                                         @RequestParam(required = false) String orderNo,
                                         @RequestParam(required = false) String bizType,
                                         @RequestParam(required = false) String warehouseId,
                                         @RequestParam(required = false) String departmentId,
                                         @RequestParam(required = false) String sourceUnit,
                                         @RequestParam(required = false) String receiveUnit,
                                         @RequestParam(required = false) String materialCode,
                                         @RequestParam(required = false) String materialName,
                                         @RequestParam(required = false) String startDate,
                                         @RequestParam(required = false) String endDate,
                                         @RequestParam(required = false) String scope) {
        return orderService.export(source, orderNo, bizType, warehouseId, departmentId,
                sourceUnit, receiveUnit, materialCode, materialName, startDate, endDate, scope);
    }
}
