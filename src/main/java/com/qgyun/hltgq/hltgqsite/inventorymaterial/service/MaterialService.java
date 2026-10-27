package com.qgyun.hltgq.hltgqsite.inventorymaterial.service;

import com.qgyun.hltgq.hltgqsite.inventorymaterial.mapper.InventoryMaterialMapper;
import com.qgyun.hltgq.hltgqsite.inventorymaterial.vo.MaterialPageItemVO;
import com.qgyun.hltgq.hltgqsite.inventorymaterial.vo.MaterialSaveVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.InventoryCategory;
import com.qgyun.hltgq.hltgqsite.inventoryorder.mapper.InventoryLedgerMapper;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.clampPage;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.clampSize;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.currentOperatorId;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.pageResult;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.requireText;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.trim;

/**
 * 物资目录（slaifi）服务：库存管理 / 应急物资管理同页双模式。
 * <p>类别口径见 {@link InventoryCategory}：
 * <ul>
 *   <li>scope 未传或 common → 库存管理视角：列表/新增只处理常用物资，
 *       新增时「类别」默认常用物资、可选择应急物资（用于把物资归入应急侧）；</li>
 *   <li>scope=emergency → 应急物资管理视角：列表只显示应急物资，新增/编辑类别强制应急物资，
 *       前端不展示该字段（后端忽略传入值）；</li>
 *   <li>编辑/删除前校验记录类别与当前视角一致，避免跨视角按 id 越权操作。</li>
 * </ul>
 * <p>物资编号留空自动生成 {@code WZ + 毫秒时间戳}（与库内历史编号口径一致）。
 */
@Service
public class MaterialService {

    private static final Logger log = LoggerFactory.getLogger(MaterialService.class);

    /** 编号前缀（与库内历史数据 WZ1792563011910 一致） */
    private static final String CODE_PREFIX = "WZ";

    private static final int CODE_MAX_LENGTH = 64;

    private static final int NAME_MAX_LENGTH = 255;

    private static final int BRAND_MAX_LENGTH = 255;

    private static final int SPEC_MAX_LENGTH = 255;

    private static final int UNIT_MAX_LENGTH = 64;

    private static final int REMARK_MAX_LENGTH = 500;

    /** 安全库存数字文本（可带负号与小数，历史存在 -9 与 100 两种写法） */
    private static final String SAFETY_STOCK_PATTERN = "^-?[0-9]+(\\.[0-9]+)?$";

    private final InventoryMaterialMapper materialMapper;

    /** 仓库候选（与台账/出入库同一查询口径） */
    private final InventoryLedgerMapper ledgerMapper;

    private final ShortIdGenerator shortIdGenerator;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public MaterialService(InventoryMaterialMapper materialMapper,
                           InventoryLedgerMapper ledgerMapper,
                           ShortIdGenerator shortIdGenerator) {
        this.materialMapper = materialMapper;
        this.ledgerMapper = ledgerMapper;
        this.shortIdGenerator = shortIdGenerator;
    }

    // ==================== 列表 / 元数据 / 详情 ====================

    /** 目录分页（按页面 scope 隔离类别；编号/名称/品牌关键字 + 默认仓库筛选）。 */
    public Map<String, Object> page(int page, int size, String keyword, String warehouseId, String scope) {
        int p = clampPage(page);
        int s = clampSize(size);
        String kw = trim(keyword);
        String wid = trim(warehouseId);
        boolean emergency = InventoryCategory.isEmergency(scope);
        long total = materialMapper.countPage(emergency, !emergency, kw, wid);
        List<MaterialPageItemVO> records = materialMapper.selectPage(emergency, !emergency, kw, wid, s, (long) (p - 1) * s);
        return pageResult(total, p, s, records);
    }

    /** 表单元数据：启用仓库、类别选项、单位候选、当前视角应有类别。 */
    public Map<String, Object> meta(String scope) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("warehouses", ledgerMapper.selectWarehouses(true));
        out.put("categories", InventoryCategory.options());
        out.put("units", materialMapper.selectUnits());
        out.put("scope", InventoryCategory.isEmergency(scope)
                ? InventoryCategory.SCOPE_EMERGENCY : InventoryCategory.SCOPE_COMMON);
        out.put("defaultCategory", InventoryCategory.scopeCategory(scope));
        out.put("categoryEditable", InventoryCategory.isCommon(scope));
        return out;
    }

    /** 单条（编辑回显；类别与当前视角不一致时拒绝，防跨视角越权）。 */
    public MaterialPageItemVO detail(String id, String scope) {
        MaterialPageItemVO vo = materialMapper.selectById(requireText(id, "物资 id 不能为空"));
        if (vo == null) {
            throw new IllegalArgumentException("物资不存在或已删除");
        }
        InventoryCategory.requireCategory(scope, vo.getCategory(),
                "该物资不属于当前页面类别，无法查看");
        return vo;
    }

    /** 导出（同列表条件，全量无分页；前端生成 Excel）。 */
    public List<MaterialPageItemVO> exportRows(String keyword, String warehouseId, String scope) {
        boolean emergency = InventoryCategory.isEmergency(scope);
        return materialMapper.selectExportRows(emergency, !emergency, trim(keyword), trim(warehouseId));
    }

    // ==================== 新增 / 编辑 / 删除 ====================

    /** 新增（类别：应急视角强制应急物资；库存管理默认常用物资，可按选择归入应急）。 */
    @Transactional(rollbackFor = Exception.class)
    public MaterialPageItemVO save(MaterialSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String scope = req.getScope();
        String code = trim(req.getCode());
        if (code == null) {
            code = CODE_PREFIX + System.currentTimeMillis();
        }
        code = CheckUtils.maxLength(code, CODE_MAX_LENGTH, "物资编号");
        String name = CheckUtils.maxLength(
                requireText(req.getName(), "请填写物资名称"), NAME_MAX_LENGTH, "物资名称");
        String unit = CheckUtils.maxLength(
                requireText(req.getUnit(), "请填写计量单位"), UNIT_MAX_LENGTH, "计量单位");
        String brand = CheckUtils.maxLength(trim(req.getBrand()), BRAND_MAX_LENGTH, "品牌");
        String spec = CheckUtils.maxLength(trim(req.getSpec()), SPEC_MAX_LENGTH, "规格型号");
        String remark = CheckUtils.maxLength(trim(req.getRemark()), REMARK_MAX_LENGTH, "备注");
        String safetyStock = normalizeSafetyStock(req.getSafetyStock());
        String warehouseId = requireWarehouseIfPresent(req.getDefaultWarehouseId());
        String category = InventoryCategory.resolveCategory(scope, req.getCategory());

        if (materialMapper.countByCode(code, null) > 0) {
            throw new IllegalArgumentException("物资编号已存在：" + code);
        }

        String id = shortIdGenerator.nextUUID(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("code", code);
        row.put("name", name);
        row.put("brand", brand);
        row.put("spec", spec);
        row.put("unit", unit);
        row.put("safety_stock", safetyStock);
        row.put("default_warehouse", warehouseId);
        row.put("remark", remark);
        row.put("category", category);
        row.put("now", LocalDateTime.now());
        row.put("operator", currentOperatorId(fallbackOperator));
        materialMapper.insert(row);

        log.info("[物资目录] 新增：{}-{}（{}），类别={}，操作人={}",
                code, name, id, category, row.get("operator"));
        return materialMapper.selectById(id);
    }

    /** 编辑（类别按当前视角归一；编辑不允许把应急物资改成常用物资）。 */
    @Transactional(rollbackFor = Exception.class)
    public MaterialPageItemVO update(MaterialSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String id = requireText(req.getId(), "物资 id 不能为空");
        MaterialPageItemVO existing = materialMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("物资不存在或已删除");
        }
        String scope = req.getScope();
        InventoryCategory.requireCategory(scope, existing.getCategory(),
                "该物资不属于当前页面类别，无法编辑");

        String code = CheckUtils.maxLength(
                requireText(req.getCode(), "请填写物资编号"), CODE_MAX_LENGTH, "物资编号");
        String name = CheckUtils.maxLength(
                requireText(req.getName(), "请填写物资名称"), NAME_MAX_LENGTH, "物资名称");
        String unit = CheckUtils.maxLength(
                requireText(req.getUnit(), "请填写计量单位"), UNIT_MAX_LENGTH, "计量单位");
        String brand = CheckUtils.maxLength(trim(req.getBrand()), BRAND_MAX_LENGTH, "品牌");
        String spec = CheckUtils.maxLength(trim(req.getSpec()), SPEC_MAX_LENGTH, "规格型号");
        String remark = CheckUtils.maxLength(trim(req.getRemark()), REMARK_MAX_LENGTH, "备注");
        String safetyStock = normalizeSafetyStock(req.getSafetyStock());
        String warehouseId = requireWarehouseIfPresent(req.getDefaultWarehouseId());
        String category = InventoryCategory.resolveCategory(scope, req.getCategory());
        // 类别决定台账/单据的所属视角：已有业务数据时改写类别会让历史单据与流水整体翻到另一侧（不可逆）
        if (!category.equals(InventoryCategory.normalizeCategory(existing.getCategory()))
                && (materialMapper.countLedgerRefs(id) > 0 || materialMapper.countChangeRefs(id) > 0
                || materialMapper.countOrderItemRefs(id) > 0 || materialMapper.countStocktakeItemRefs(id) > 0)) {
            throw new IllegalArgumentException("该物资已有库存或单据数据，不能调整物资类别");
        }

        if (materialMapper.countByCode(code, id) > 0) {
            throw new IllegalArgumentException("物资编号已存在：" + code);
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("code", code);
        row.put("name", name);
        row.put("brand", brand);
        row.put("spec", spec);
        row.put("unit", unit);
        row.put("safety_stock", safetyStock);
        row.put("default_warehouse", warehouseId);
        row.put("remark", remark);
        row.put("category", category);
        row.put("now", LocalDateTime.now());
        row.put("operator", currentOperatorId(fallbackOperator));
        if (materialMapper.update(row) == 0) {
            throw new IllegalArgumentException("物资不存在或已删除");
        }

        log.info("[物资目录] 编辑：{}-{}（{}），类别={}，操作人={}",
                code, name, id, category, row.get("operator"));
        return materialMapper.selectById(id);
    }

    /** 删除（有台账或出入库流水引用时拒绝，保护历史数据）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id, String scope) {
        String key = requireText(id, "物资 id 不能为空");
        MaterialPageItemVO existing = materialMapper.selectById(key);
        if (existing == null) {
            throw new IllegalArgumentException("物资不存在或已删除");
        }
        InventoryCategory.requireCategory(scope, existing.getCategory(),
                "该物资不属于当前页面类别，无法删除");
        if (materialMapper.countLedgerRefs(key) > 0) {
            throw new IllegalArgumentException("该物资已有库存台账记录，不能删除");
        }
        if (materialMapper.countChangeRefs(key) > 0) {
            throw new IllegalArgumentException("该物资已有出入库流水，不能删除");
        }
        // 单据视角由明细引用决定：被出入库/盘点明细引用时删除会让单据翻到另一侧视角
        if (materialMapper.countOrderItemRefs(key) > 0) {
            throw new IllegalArgumentException("该物资已被出入库单据引用，不能删除");
        }
        if (materialMapper.countStocktakeItemRefs(key) > 0) {
            throw new IllegalArgumentException("该物资已被盘点单引用，不能删除");
        }
        materialMapper.delete(key);
        log.info("[物资目录] 删除：{}-{}（{}），类别={}，操作人={}",
                existing.getCode(), existing.getName(), key, existing.getCategory(),
                currentOperatorId(fallbackOperator));
    }

    // ==================== 校验与工具 ====================

    /** 安全库存：空 → null；填写必须为数字（允许负号与小数，与目录历史写法一致）。 */
    private static String normalizeSafetyStock(String raw) {
        String value = trim(raw);
        if (value == null) {
            return null;
        }
        if (!value.matches(SAFETY_STOCK_PATTERN)) {
            throw new IllegalArgumentException("安全库存必须为数字：" + value);
        }
        return value;
    }

    /** 默认仓库：空 → null；填写则必须存在（停用仓库不阻断历史数据编辑）。 */
    private String requireWarehouseIfPresent(String warehouseId) {
        String value = trim(warehouseId);
        if (value == null) {
            return null;
        }
        if (ledgerMapper.selectWarehouseById(value) == null) {
            throw new IllegalArgumentException("默认仓库不存在或已删除");
        }
        return value;
    }
}
