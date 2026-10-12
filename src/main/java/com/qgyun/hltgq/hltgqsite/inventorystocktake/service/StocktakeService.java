package com.qgyun.hltgq.hltgqsite.inventorystocktake.service;

import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.inventoryorder.InventoryTypeDict;
import com.qgyun.hltgq.hltgqsite.inventoryorder.mapper.InventoryLedgerMapper;
import com.qgyun.hltgq.hltgqsite.inventoryorder.service.InventoryOrderNoService;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.MaterialVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.StockLedgerOptionVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.WarehouseVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.mapper.InventoryStocktakeMapper;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakeAttachmentVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakeDetailVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakeDownloadVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakePageItemVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakeSaveVO;
import com.qgyun.hltgq.hltgqsite.inventorystocktake.vo.StocktakeUpdateVO;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
 * 设备盘点服务（P6 列表、P7 新增/查看/编辑、台账带出、附件）。
 * <p>保存事务（方案 4.1）单事务执行：校验 → 独立事务取号（PDD+yyyyMMdd+4位）→ 写主表 →
 * 逐行台账 FOR UPDATE 重读（与页面账面快照不一致一律拒绝保存，不允许强制覆盖）→
 * 逐行绝对校正台账（ejgxlq=实盘，重算 mojktw；无台账行按纯盘盈建账）→ 仅差异行写
 * 盘盈 #3# / 盘亏 #4# 流水（order_kind #3# 盘点单）→ 回填附件归属；任一步失败整体回滚，编号不回滚。
 * <p>编辑仅限盘点人/备注/附件（方案 1.1、4.3）：不触碰台账、明细与流水；历史流水经手人不变。
 * <p>附件本地存储：file-dir/stocktake/{yyyy-MM}/{uuid}_{原名}，上传时行 order_id 为空，
 * 保存/编辑回填；移除（或编辑删除）时物理删文件 + 删行；下载前做目录穿越校验。
 */
@Service
public class StocktakeService {

    private static final Logger log = LoggerFactory.getLogger(StocktakeService.class);

    /** 盘点单编号前缀（serial_type 复用 water_order_no_seq；列宽 8 容得下） */
    private static final String ORDER_SERIAL_TYPE = "PDD";

    /** 备注长度上限（方案口径 50 字） */
    private static final int REMARK_MAX_LENGTH = 50;

    /** 附件存储根目录名（file-dir 下的盘点专属目录） */
    private static final String STOCKTAKE_DIR = "stocktake";

    /** 附件扩展名白名单（图片 / PDF / Word / Excel） */
    private static final Set<String> ALLOWED_EXT = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "pdf", "doc", "docx", "xls", "xlsx"));

    /** 附件按月分桶目录（yyyy-MM） */
    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyy-MM");

    private final InventoryStocktakeMapper stocktakeMapper;

    /** 复用出入库写入组件：台账建账（insertLedgerIfAbsent）/定位（selectLedgerIdByMaterialWarehouse）/
     * 流水（insertChangeLog）/仓库/用户/物资查询（与盘点同底座，口径单一维护点） */
    private final InventoryLedgerMapper ledgerMapper;

    /** 编号取号（通用：serial_type 参数化，传 PDD 即得 PDD+日期+4位） */
    private final InventoryOrderNoService orderNoService;

    private final ShortIdGenerator shortIdGenerator;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    /** 附件本地存储根目录（宿主机挂载 ./file-dir；与 decision-excel 同模式） */
    @Value("${hltgq.file-dir:./file-dir}")
    private String fileDir;

    public StocktakeService(InventoryStocktakeMapper stocktakeMapper,
                            InventoryLedgerMapper ledgerMapper,
                            InventoryOrderNoService orderNoService,
                            ShortIdGenerator shortIdGenerator) {
        this.stocktakeMapper = stocktakeMapper;
        this.ledgerMapper = ledgerMapper;
        this.orderNoService = orderNoService;
        this.shortIdGenerator = shortIdGenerator;
    }

    // ==================== P6 列表 / 元数据 ====================

    /** 盘点单分页（单号/仓库/盘点人/物资编码/物资名称/盘点日期范围，条件见 InventoryStocktakeMapper.LIST_CONDITIONS）。 */
    public Map<String, Object> page(int page, int size, String orderNo, String warehouseId, String operatorId,
                                    String materialCode, String materialName, String startDate, String endDate) {
        int p = clampPage(page);
        int s = clampSize(size);
        String no = trim(orderNo);
        String wid = trim(warehouseId);
        String oid = trim(operatorId);
        String mc = trim(materialCode);
        String mn = trim(materialName);
        String sd = normalizeDay(startDate, "起始日期");
        String ed = normalizeDay(endDate, "结束日期");
        long total = stocktakeMapper.countPage(no, wid, oid, mc, mn, sd, ed);
        List<StocktakePageItemVO> records = stocktakeMapper.selectPage(no, wid, oid, mc, mn, sd, ed, s, (long) (p - 1) * s);
        decorateAttachments(records);
        return pageResult(total, p, s, records);
    }

    /** 表单元数据：启用仓库（含默认科室/经办人）、当天日期、当前登录人（id + 姓名反查，作盘点人默认值）。 */
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("warehouses", ledgerMapper.selectWarehouses(true));
        out.put("today", LocalDate.now().toString());
        Map<String, Object> current = new LinkedHashMap<>();
        UserContext user = UserContextHolder.currentUser();
        String userId = user == null ? null : trim(user.getUserId());
        current.put("id", userId);
        current.put("name", userId == null ? null : ledgerMapper.selectUserName(userId));
        out.put("currentUser", current);
        return out;
    }

    /** 盘点单详情（主表 + 明细 + 附件）。 */
    public StocktakeDetailVO detail(String id) {
        String key = requireText(id, "盘点单 id 不能为空");
        StocktakeDetailVO vo = stocktakeMapper.selectHeader(key);
        if (vo == null) {
            throw new IllegalArgumentException("盘点单不存在");
        }
        vo.setItems(stocktakeMapper.selectItems(key));
        vo.setAttachments(stocktakeMapper.selectAttachments(key));
        return vo;
    }

    /** 列表附件批量装配（一次查询多单附件，避免逐行查询）。 */
    private void decorateAttachments(List<StocktakePageItemVO> records) {
        if (records.isEmpty()) {
            return;
        }
        List<String> ids = records.stream().map(StocktakePageItemVO::getId).collect(Collectors.toList());
        Map<String, List<StocktakeAttachmentVO>> byOrder = new HashMap<>();
        for (StocktakeAttachmentVO att : stocktakeMapper.selectAttachmentsByOrderIds(ids)) {
            byOrder.computeIfAbsent(att.getOrderId(), k -> new ArrayList<>()).add(att);
        }
        for (StocktakePageItemVO row : records) {
            row.setAttachments(byOrder.getOrDefault(row.getId(), new ArrayList<>()));
        }
    }

    // ==================== 带出 / 加行选择器 ====================

    /** 盘点带出：当前仓库台账全量行（含 0 库存；明细默认账面 = quantity，实盘默认同账面由前端预填）。 */
    public List<StockLedgerOptionVO> stockLedgers(String warehouseId, String keyword) {
        return stocktakeMapper.selectLedgerOptions(requireText(warehouseId, "请先选择盘点仓库"), trim(keyword));
    }

    /** 手动加行：物资目录全量 + 该仓库实时账面（无台账记 0，含纯盘盈场景）。 */
    public List<StockLedgerOptionVO> materialOptions(String warehouseId, String keyword) {
        return stocktakeMapper.selectMaterialOptions(requireText(warehouseId, "请先选择盘点仓库"), trim(keyword));
    }

    // ==================== P7 新增（保存即生效） ====================

    /**
     * 新增盘点单。保存事务：校验 → 取号（独立事务，跳号属预期）→ 主表 → 逐行
     * （台账锁行校验 + 绝对校正/建账 + 明细 + 差异流水）→ 附件回填。
     */
    @Transactional(rollbackFor = Exception.class)
    public StocktakeDetailVO save(StocktakeSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        LocalDate checkDate = requireDate(req.getCheckDate(), "盘点日期");
        String warehouseId = requireText(req.getWarehouseId(), "请选择盘点仓库");
        String operatorId = requireText(req.getOperatorId(), "请选择盘点人员");
        String remark = normalizeRemark(req.getRemark(), REMARK_MAX_LENGTH);
        WarehouseVO warehouse = requireEnabledWarehouse(ledgerMapper, warehouseId);
        String operatorName = ledgerMapper.selectUserName(operatorId);
        if (operatorName == null) {
            throw new IllegalArgumentException("盘点人不存在或已删除");
        }

        // 明细校验：非空、行数上限、物资存在且不重复、账面/实盘为非负整数
        List<StocktakeSaveVO.Item> items = safeItems(req.getItems(), "请至少添加一行盘点明细");
        Set<String> seen = new HashSet<>();
        List<MaterialVO> materials = new ArrayList<>();
        List<int[]> quantities = new ArrayList<>();
        int lineNo = 1;
        for (StocktakeSaveVO.Item item : items) {
            String materialId = requireText(item.getMaterialId(), "第 " + lineNo + " 行：请选择物资");
            if (!seen.add(materialId)) {
                throw new IllegalArgumentException("第 " + lineNo + " 行：物资重复，同一物资只能出现一行");
            }
            MaterialVO material = ledgerMapper.selectMaterialById(materialId);
            if (material == null) {
                throw new IllegalArgumentException("第 " + lineNo + " 行：物资不存在或已删除");
            }
            int bookQty = requireNonNegativeInt(item.getBookQty(), lineNo, "账面库存");
            int checkQty = requireNonNegativeInt(item.getCheckQty(), lineNo, "实盘数量");
            materials.add(material);
            quantities.add(new int[]{bookQty, checkQty});
            lineNo++;
        }

        String orderNo = orderNoService.nextOrderNo(ORDER_SERIAL_TYPE);
        String orderId = shortIdGenerator.nextUUID(null);
        String operator = currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();
        String dateText = checkDate.toString();

        Map<String, Object> orderRow = new LinkedHashMap<>();
        orderRow.put("id", orderId);
        orderRow.put("order_no", orderNo);
        orderRow.put("check_date", dateText);
        orderRow.put("warehouse_id", warehouseId);
        orderRow.put("operator_id", operatorId);
        orderRow.put("operator_name", operatorName);
        orderRow.put("remark", remark);
        orderRow.put("now", now);
        orderRow.put("operator", operator);
        stocktakeMapper.insertOrder(orderRow);

        for (int i = 0; i < materials.size(); i++) {
            MaterialVO material = materials.get(i);
            int bookQty = quantities.get(i)[0];
            int checkQty = quantities.get(i)[1];
            int line = i + 1;

            // 账面一致性校验：FOR UPDATE 锁行重读台账实时值，与页面快照不一致一律拒绝
            StockLedgerOptionVO ledger = stocktakeMapper.selectLedgerRowForUpdate(material.getId(), warehouseId);
            String ledgerId = null;
            boolean created = false;
            if (ledger != null) {
                int current = ledger.getQuantity() == null ? 0 : ledger.getQuantity();
                requireBookQtyMatched(line, material.getName(), current, bookQty);
                ledgerId = ledger.getLedgerId();
            } else {
                if (bookQty != 0) {
                    requireBookQtyMatched(line, material.getName(), 0, bookQty);
                }
                if (checkQty > 0) {
                    // 纯盘盈：无台账行 → 建账（含实盘初始量）；建账要素统一由 InventorySupport.buildNewLedgerRow 组装（与出入库共用一个维护点）
                    Map<String, Object> ledgerRow = buildNewLedgerRow(shortIdGenerator.nextUUID(null), material,
                            warehouseId, warehouse.getDepartmentId(), checkQty, now, operator);
                    if (ledgerMapper.insertLedgerIfAbsent(ledgerRow) > 0) {
                        ledgerId = (String) ledgerRow.get("id");
                        created = true;
                    } else {
                        ledgerId = ledgerMapper.selectLedgerIdByMaterialWarehouse(material.getId(), warehouseId);
                        if (ledgerId == null) {
                            throw new IllegalStateException("台账建账冲突且未查到既有记录，请重试");
                        }
                        StockLedgerOptionVO again = stocktakeMapper.selectLedgerRowForUpdate(material.getId(), warehouseId);
                        int current = again == null || again.getQuantity() == null ? 0 : again.getQuantity();
                        requireBookQtyMatched(line, material.getName(), current, bookQty);
                    }
                }
                // 实盘为 0 且无台账：零库存不建账（避免产生 ejgxlq=0 的空台账行），无校正与流水
            }

            // 台账绝对校正（ejgxlq = 实盘；建账路径已在建账语句内初始化，跳过；零库存无台账行跳过）
            if (!created && ledgerId != null) {
                Integer after = stocktakeMapper.updateLedgerAbsolute(ledgerId, checkQty, operator, now);
                if (after == null) {
                    throw new IllegalStateException("台账更新失败，请重试");
                }
            }

            int profit = Math.max(checkQty - bookQty, 0);
            int loss = Math.max(bookQty - checkQty, 0);

            Map<String, Object> detailRow = new LinkedHashMap<>();
            detailRow.put("id", shortIdGenerator.nextUUID(null));
            detailRow.put("order_id", orderId);
            detailRow.put("material_id", material.getId());
            detailRow.put("material_code", nullToEmpty(material.getCode()));
            detailRow.put("material_name", nullToEmpty(material.getName()));
            detailRow.put("material_brand", material.getBrand());
            detailRow.put("material_spec", material.getSpec());
            detailRow.put("material_unit", material.getUnit());
            detailRow.put("book_qty", bookQty);
            detailRow.put("check_qty", checkQty);
            detailRow.put("profit_qty", profit);
            detailRow.put("loss_qty", loss);
            detailRow.put("now", now);
            detailRow.put("operator", operator);
            stocktakeMapper.insertItem(detailRow);

            // 仅差异行写流水（盘盈 #3# / 盘亏 #4#；qty_before=账面、qty_after=实盘；biz_type 快照恒 #3#）
            if (checkQty != bookQty) {
                Map<String, Object> changeRow = new LinkedHashMap<>();
                changeRow.put("id", shortIdGenerator.nextUUID(null));
                changeRow.put("ledger_id", ledgerId);
                changeRow.put("material_id", material.getId());
                changeRow.put("warehouse_id", warehouseId);
                changeRow.put("change_type", checkQty > bookQty
                        ? InventoryTypeDict.CHANGE_TYPE_PROFIT : InventoryTypeDict.CHANGE_TYPE_LOSS);
                changeRow.put("order_kind", InventoryTypeDict.ORDER_KIND_STOCKTAKE);
                changeRow.put("order_id", orderId);
                changeRow.put("order_no", orderNo);
                changeRow.put("biz_type", InventoryTypeDict.ORDER_KIND_STOCKTAKE);
                changeRow.put("change_qty", Math.max(profit, loss));
                changeRow.put("qty_before", bookQty);
                changeRow.put("qty_after", checkQty);
                changeRow.put("counterparty", null);
                changeRow.put("operator_name", operatorName);
                changeRow.put("order_date", dateText);
                changeRow.put("now", now);
                changeRow.put("operator", operator);
                ledgerMapper.insertChangeLog(changeRow);
            }
        }

        // 附件回填归属（上传时 order_id 为空；已被其它单占用或不存在一律拒绝）
        if (req.getAttachmentIds() != null) {
            for (String raw : req.getAttachmentIds()) {
                String aid = trim(raw);
                if (aid == null) {
                    continue;
                }
                StocktakeAttachmentVO att = stocktakeMapper.selectAttachmentById(aid);
                if (att == null) {
                    throw new IllegalArgumentException("附件不存在或已移除");
                }
                if (att.getOrderId() != null && !att.getOrderId().equals(orderId)) {
                    throw new IllegalArgumentException("附件已被其他盘点单占用：" + att.getFileName());
                }
                if (att.getOrderId() == null && stocktakeMapper.bindAttachmentOrder(aid, orderId, operator, now) == 0) {
                    throw new IllegalArgumentException("附件绑定失败，请刷新后重试：" + att.getFileName());
                }
            }
        }

        log.info("[盘点] 新增盘点单：{}，仓库={}（{}），明细 {} 行，操作人={}",
                orderNo, warehouse.getName(), warehouseId, materials.size(), operator);
        return detail(orderId);
    }

    // ==================== P7 编辑（仅盘点人/备注/附件） ====================

    /**
     * 编辑盘点单：仅更新盘点人/备注 + 附件增删（新增绑定归属，移除物理删文件+删行）；
     * 不触碰台账、明细与流水（方案 4.3）。
     */
    @Transactional(rollbackFor = Exception.class)
    public StocktakeDetailVO update(StocktakeUpdateVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String id = requireText(req.getId(), "盘点单 id 不能为空");
        if (stocktakeMapper.selectHeader(id) == null) {
            throw new IllegalArgumentException("盘点单不存在");
        }
        String operatorId = requireText(req.getOperatorId(), "请选择盘点人员");
        String operatorName = ledgerMapper.selectUserName(operatorId);
        if (operatorName == null) {
            throw new IllegalArgumentException("盘点人不存在或已删除");
        }
        String remark = normalizeRemark(req.getRemark(), REMARK_MAX_LENGTH);
        String operator = currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();

        // 保留附件集：校验存在性/归属，新上传的附件绑定到本单
        List<String> keepIds = new ArrayList<>();
        if (req.getAttachmentIds() != null) {
            for (String raw : req.getAttachmentIds()) {
                String aid = trim(raw);
                if (aid == null) {
                    continue;
                }
                StocktakeAttachmentVO att = stocktakeMapper.selectAttachmentById(aid);
                if (att == null) {
                    throw new IllegalArgumentException("附件不存在或已移除：" + aid);
                }
                if (att.getOrderId() != null && !att.getOrderId().equals(id)) {
                    throw new IllegalArgumentException("附件已被其他盘点单占用：" + att.getFileName());
                }
                if (att.getOrderId() == null && stocktakeMapper.bindAttachmentOrder(aid, id, operator, now) == 0) {
                    throw new IllegalArgumentException("附件绑定失败，请刷新后重试：" + att.getFileName());
                }
                keepIds.add(aid);
            }
        }

        int updated = stocktakeMapper.updateOrder(id, operatorId, operatorName, remark, operator, now);
        if (updated == 0) {
            throw new IllegalArgumentException("盘点单不存在或已删除");
        }

        // 移除：现属本单但不在保留集合内的附件（仅删行；物理文件在事务提交后删，避免回滚后行在文件缺）
        List<String> removedPaths = new ArrayList<>();
        for (StocktakeAttachmentVO att : stocktakeMapper.selectAttachments(id)) {
            if (!keepIds.contains(att.getId())) {
                StocktakeAttachmentVO row = stocktakeMapper.selectAttachmentById(att.getId());
                if (row != null && stocktakeMapper.deleteAttachment(row.getId()) > 0) {
                    removedPaths.add(row.getFilePath());
                }
            }
        }
        registerAfterCommitFileCleanup(removedPaths);

        log.info("[盘点] 编辑盘点单：{}（{}），盘点人={}，保留附件 {} 个，操作人={}",
                id, req.getRemark(), operatorName, keepIds.size(), operator);
        return detail(id);
    }

    // ==================== 附件（上传 / 下载 / 移除） ====================

    /** 上传附件：白名单 + 落盘（stocktake/yyyy-MM/uuid_原名） + 行入库（order_id 为空待回填）。 */
    public StocktakeAttachmentVO uploadAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String originalName = sanitizeFileName(file.getOriginalFilename());
        String ext = extOf(originalName);
        if (ext == null || !ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("不支持的文件类型（仅支持图片、PDF、Word、Excel）：" + originalName);
        }
        String operator = currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();
        String storedName = shortIdGenerator.nextUUID(null) + "_" + originalName;
        String relPath = STOCKTAKE_DIR + "/" + now.format(MONTH_DIR) + "/" + storedName;
        Path target = resolveUnderBase(fileDirPath(), relPath);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("附件保存失败: " + e.getMessage());
        }

        String attachmentId = shortIdGenerator.nextUUID(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", attachmentId);
        row.put("order_id", null);
        row.put("file_name", originalName);
        row.put("file_path", relPath);
        row.put("file_size", file.getSize());
        row.put("file_ext", ext);
        row.put("now", now);
        row.put("operator", operator);
        try {
            stocktakeMapper.insertAttachment(row);
        } catch (RuntimeException e) {
            // 行写入失败：清理已落盘文件，避免孤儿文件
            deleteFileQuietly(relPath);
            throw e;
        }

        StocktakeAttachmentVO vo = new StocktakeAttachmentVO();
        vo.setId(attachmentId);
        vo.setFileName(originalName);
        vo.setFileSize(file.getSize());
        vo.setFileExt(ext);
        log.info("[盘点] 附件上传成功：{}（{} 字节），存储路径={}，操作人={}",
                originalName, file.getSize(), relPath, operator);
        return vo;
    }

    /** 下载附件：路径穿越校验 + 文件读取（缺失提示已被清理）。 */
    public StocktakeDownloadVO downloadAttachment(String id) {
        StocktakeAttachmentVO att = stocktakeMapper.selectAttachmentById(requireText(id, "附件 id 不能为空"));
        if (att == null) {
            throw new IllegalArgumentException("附件不存在或已删除");
        }
        Path target = resolveUnderBase(fileDirPath(), requireText(att.getFilePath(), "附件路径缺失"));
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("附件文件不存在，可能已被清理");
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(target);
        } catch (IOException e) {
            throw new IllegalStateException("附件读取失败: " + e.getMessage());
        }
        StocktakeDownloadVO vo = new StocktakeDownloadVO();
        vo.setContent(bytes);
        vo.setFileName(att.getFileName());
        vo.setFileExt(att.getFileExt());
        return vo;
    }

    /** 移除附件（仅限未保存的新上传附件：新增取消清理 / 编辑态移除新上传）：原子删行（未归属守卫） + 物理删文件（best-effort）。 */
    public void removeAttachment(String id) {
        String key = requireText(id, "附件 id 不能为空");
        StocktakeAttachmentVO att = stocktakeMapper.selectAttachmentById(key);
        if (att == null) {
            throw new IllegalArgumentException("附件不存在或已删除");
        }
        // 仅允许删除未归属附件：DELETE 带 order_id IS NULL 守卫，并发绑定后返回 0 行 → 拒绝移除且不删文件
        if (stocktakeMapper.deleteUnboundAttachment(key) == 0) {
            throw new IllegalArgumentException("附件已保存到盘点单，不能直接移除");
        }
        deleteFileQuietly(att.getFilePath());
        log.info("[盘点] 附件已移除：{}（{}）", att.getFileName(), att.getId());
    }

    /** 附件存储根目录（绝对路径归一，用作穿越校验基准）。 */
    private Path fileDirPath() {
        return Paths.get(fileDir).toAbsolutePath().normalize();
    }

    /** 相对路径解析 + 目录穿越校验（normalize 后必须仍在根目录下）。 */
    private static Path resolveUnderBase(Path base, String relPath) {
        Path target = base.resolve(relPath).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("附件路径非法");
        }
        return target;
    }

    /** 物理删除附件文件（失败仅告警，不阻断业务）。 */
    private void deleteFileQuietly(String relPath) {
        if (trim(relPath) == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolveUnderBase(fileDirPath(), relPath));
        } catch (Exception e) {
            log.warn("[盘点] 附件文件删除失败（忽略）：path={}, error={}", relPath, e.getMessage());
        }
    }

    /** 事务提交后再物理删文件（回滚时附件行仍在、文件不丢）；无事务同步上下文时立即执行。 */
    private void registerAfterCommitFileCleanup(List<String> relPaths) {
        if (relPaths.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            relPaths.forEach(this::deleteFileQuietly);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                relPaths.forEach(StocktakeService.this::deleteFileQuietly);
            }
        });
    }

    /** 原始文件名清洗：去路径段与控制字符、空白兜底、超长截断（保留扩展名）。 */
    private static String sanitizeFileName(String raw) {
        String name = raw == null ? "" : raw;
        name = name.replace('\\', '/');
        int idx = name.lastIndexOf('/');
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        name = name.replaceAll("[\\r\\n\\t\\u0000]", "").trim();
        if (name.isEmpty()) {
            name = "file";
        }
        if (name.length() > 120) {
            String ext = extOf(name);
            String stem = ext == null ? name : name.substring(0, name.length() - ext.length() - 1);
            if (stem.length() > 100) {
                stem = stem.substring(0, 100);
            }
            name = ext == null ? stem : stem + "." + ext;
        }
        return name;
    }

    /** 扩展名提取（小写；无扩展名返回 null）。 */
    private static String extOf(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    // ==================== 校验与工具 ====================

    /** 账面一致性断言：页面快照与台账实时值不一致 → 拒绝保存（方案 4.1 步骤 4，不允许强制覆盖）。 */
    private static void requireBookQtyMatched(int lineNo, String materialName, int current, int snapshot) {
        if (current != snapshot) {
            throw new IllegalArgumentException("第 " + lineNo + " 行「" + materialName + "」：账面库存已发生变化（当前 "
                    + current + "，页面为 " + snapshot + "），请关闭弹窗重新盘点后再保存");
        }
    }

    /** 非负整数校验（账面/实盘数量）。 */
    private static int requireNonNegativeInt(Integer value, int lineNo, String label) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException("第 " + lineNo + " 行：" + label + "必须为不小于 0 的整数");
        }
        return value;
    }

}
