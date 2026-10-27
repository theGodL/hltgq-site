package com.qgyun.hltgq.hltgqsite.inventorystocktake.controller;

import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.StockLedgerOptionVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.service.StocktakeService;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakeAttachmentVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakeDetailVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakeDownloadVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakeSaveVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakeUpdateVO;
import com.qgyun.hltgq.hltgqsite.model.util.DownloadHeaderUtils;
import com.qgyun.hltgq.hltgqsite.model.util.MediaTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备盘点（P6 列表、P7 新增/查看/编辑、台账带出、附件上传下载）。
 * <p>权限：本期所有登录用户可用（与出入库同口径）；保存即生效，不提供删除；
 * 编辑仅限盘点人/备注/附件。
 */
@RestController
@RequestMapping("/inventory-stocktake")
public class InventoryStocktakeController {

    private static final Logger log = LoggerFactory.getLogger(InventoryStocktakeController.class);

    @Autowired
    private StocktakeService stocktakeService;

    /**
     * 表单元数据：启用仓库（含默认科室/经办人）、当天日期、当前登录人（盘点人默认值）。
     */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return stocktakeService.meta();
    }

    /**
     * 盘点单列表分页。
     *
     * @param page         页码（默认 1）
     * @param size         每页条数（默认 20，上限 100）
     * @param orderNo      盘点单号（模糊，可选）
     * @param warehouseId  盘点仓库 ID（可选）
     * @param operatorId   盘点人 ID（可选）
     * @param materialCode 物资编码（明细级模糊，可选）
     * @param materialName 物资名称（明细级模糊，可选）
     * @param startDate    盘点日期起（yyyy-MM-dd，可选，含当日）
     * @param endDate      盘点日期止（yyyy-MM-dd，可选，含当日）
     * @param scope        页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     * @return total / page / size / pages / records（records 行含附件摘要）
     */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String orderNo,
                                    @RequestParam(required = false) String warehouseId,
                                    @RequestParam(required = false) String operatorId,
                                    @RequestParam(required = false) String materialCode,
                                    @RequestParam(required = false) String materialName,
                                    @RequestParam(required = false) String startDate,
                                    @RequestParam(required = false) String endDate,
                                    @RequestParam(required = false) String scope) {
        return stocktakeService.page(page, size, orderNo, warehouseId, operatorId,
                materialCode, materialName, startDate, endDate, scope);
    }

    /**
     * 盘点单详情（主表 + 明细 + 附件；跨类别单据按 id 直达也拒绝）。
     *
     * @param id    盘点单 ID（必填）
     * @param scope 页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     */
    @GetMapping("/detail")
    public StocktakeDetailVO detail(@RequestParam String id,
                                    @RequestParam(required = false) String scope) {
        return stocktakeService.detail(id, scope);
    }

    /**
     * 新增盘点单（保存即生效；账面与台账实时值不一致整单拒绝）。
     *
     * @return 保存后的盘点单详情
     */
    @PostMapping("/save")
    public StocktakeDetailVO save(@RequestBody StocktakeSaveVO body) {
        log.info("收到新增盘点单请求：warehouse={}，items={}，attachments={}",
                body == null ? null : body.getWarehouseId(),
                body == null || body.getItems() == null ? 0 : body.getItems().size(),
                body == null || body.getAttachmentIds() == null ? 0 : body.getAttachmentIds().size());
        return stocktakeService.save(body);
    }

    /**
     * 编辑盘点单（仅盘点人/备注/附件；不触碰台账、明细与流水）。
     *
     * @return 编辑后的盘点单详情
     */
    @PostMapping("/update")
    public StocktakeDetailVO update(@RequestBody StocktakeUpdateVO body) {
        log.info("收到编辑盘点单请求：id={}，attachments={}",
                body == null ? null : body.getId(),
                body == null || body.getAttachmentIds() == null ? 0 : body.getAttachmentIds().size());
        return stocktakeService.update(body);
    }

    /**
     * 盘点带出：当前仓库台账全量行（含 0 库存；quantity 即账面）。
     *
     * @param warehouseId 仓库 ID（必填）
     * @param keyword     物资编号/名称关键字（可选）
     * @param scope       页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     */
    @GetMapping("/stock-ledgers")
    public List<StockLedgerOptionVO> stockLedgers(@RequestParam String warehouseId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String scope) {
        return stocktakeService.stockLedgers(warehouseId, keyword, scope);
    }

    /**
     * 手动加行：物资目录全量 + 该仓库实时账面（无台账记 0，含纯盘盈场景）。
     *
     * @param warehouseId 仓库 ID（必填）
     * @param keyword     物资编号/名称关键字（可选）
     * @param scope       页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     */
    @GetMapping("/material-options")
    public List<StockLedgerOptionVO> materialOptions(@RequestParam String warehouseId,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String scope) {
        return stocktakeService.materialOptions(warehouseId, keyword, scope);
    }

    /**
     * 附件上传（multipart，单文件 ≤20MB；白名单：图片/PDF/Word/Excel）。
     *
     * @return {id, fileName, fileSize, fileExt}
     */
    @PostMapping("/attachment/upload")
    public StocktakeAttachmentVO uploadAttachment(@RequestParam("file") MultipartFile file) {
        log.info("收到盘点附件上传请求：{}（{} 字节）",
                file == null ? null : file.getOriginalFilename(),
                file == null ? 0 : file.getSize());
        return stocktakeService.uploadAttachment(file);
    }

    /**
     * 附件下载（Content-Disposition 带原始文件名，前端 fetch + blob 使用）。
     *
     * @param id 附件 ID（必填）
     */
    @GetMapping("/attachment/download")
    public ResponseEntity<byte[]> downloadAttachment(@RequestParam String id) {
        StocktakeDownloadVO file = stocktakeService.downloadAttachment(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, DownloadHeaderUtils.attachment(file.getFileName()))
                .contentType(MediaTypeUtils.of(file.getFileExt()))
                .body(file.getContent());
    }

    /**
     * 附件移除（编辑态移除 / 新增取消时清理未关联附件；物理删文件 + 删行）。
     */
    @PostMapping("/attachment/remove")
    public Map<String, Object> removeAttachment(@RequestParam String id) {
        stocktakeService.removeAttachment(id);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }
}
