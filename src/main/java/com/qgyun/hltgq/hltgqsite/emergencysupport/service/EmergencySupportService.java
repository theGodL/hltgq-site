package com.qgyun.hltgq.hltgqsite.emergencysupport.service;

import com.qgyun.hltgq.hltgqsite.emergencysupport.EmergencySupportDict;
import com.qgyun.hltgq.hltgqsite.emergencysupport.mapper.EmergencySupportMapper;
import com.qgyun.hltgq.hltgqsite.emergencysupport.vo.EmergencyAttachmentVO;
import com.qgyun.hltgq.hltgqsite.emergencysupport.vo.EmergencyMaterialOptionVO;
import com.qgyun.hltgq.hltgqsite.emergencysupport.vo.EmergencySupportSaveVO;
import com.qgyun.hltgq.hltgqsite.emergencysupport.vo.EmergencySupportVO;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.LocalFileStorageUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.model.vo.FileDownloadVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.clampPage;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.clampSize;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.currentOperatorId;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.normalizeDay;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.pageResult;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.requireText;
import static com.qgyun.hltgq.hltgqsite.inventoryorder.InventorySupport.trim;

/**
 * 应急保障服务（自研页面：列表 / 新增 / 编辑 / 删除 / 附件 / 物资装备联动）。
 * <p>口径：
 * <ul>
 *   <li>与低代码共用主表 ifjcmy：编号 diziai 由 {@link EmergencySupportNoService} 生成（只读、唯一），
 *       编码列（pxsefg/gmypct）经 {@link EmergencySupportDict} 双向换算，前端只认中文；</li>
 *   <li>「物资装备」选择应急物资台账行后，名称（haqjle）与数量/能力（obhwqq）由前端带出，
 *       后端只做必填与长度校验（允许按实际情况改写数量描述）；</li>
 *   <li>附件本地存储：file-dir/emergency/{yyyy-MM}/{短id}_{原名}，上传时行 support_id 为空，
 *       保存/编辑回填；编辑删除/整条删除时物理文件在事务提交后清理；下载前做目录穿越校验。</li>
 * </ul>
 */
@Service
public class EmergencySupportService {

    private static final Logger log = LoggerFactory.getLogger(EmergencySupportService.class);

    /** 附件存储根目录名（file-dir 下的应急保障专属目录） */
    private static final String SUPPORT_DIR = "emergency";

    /** 保障措施/资源名称长度上限 */
    private static final int NAME_MAX_LENGTH = 255;

    /** 保障内容长度上限 */
    private static final int CONTENT_MAX_LENGTH = 500;

    /** 数量/能力长度上限 */
    private static final int CAPACITY_MAX_LENGTH = 100;

    /** 责任单位/负责人长度上限 */
    private static final int OWNER_MAX_LENGTH = 255;

    /** 适用场景长度上限 */
    private static final int SCENE_MAX_LENGTH = 255;

    /** 响应使用方式长度上限 */
    private static final int USAGE_MAX_LENGTH = 500;

    private final EmergencySupportMapper supportMapper;

    /** 编号取号（独立事务，跳号属预期） */
    private final EmergencySupportNoService noService;

    private final ShortIdGenerator shortIdGenerator;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    /** 附件本地存储根目录（宿主机挂载 ./file-dir；与设备盘点同模式） */
    @Value("${hltgq.file-dir:./file-dir}")
    private String fileDir;

    /** 附件表可用性缓存：只缓存「存在」；缺表环境每次请求重探（执行 DDL 后无需重启） */
    private volatile Boolean attachmentTableExists;

    public EmergencySupportService(EmergencySupportMapper supportMapper,
                                   EmergencySupportNoService noService,
                                   ShortIdGenerator shortIdGenerator) {
        this.supportMapper = supportMapper;
        this.noService = noService;
        this.shortIdGenerator = shortIdGenerator;
    }

    // ==================== 列表 / 元数据 / 详情 ====================

    /** 分页（保障类别 / 状态 / 编号或名称关键字，创建时间倒序）。 */
    public Map<String, Object> page(int page, int size, String category, String status, String keyword) {
        int p = clampPage(page);
        int s = clampSize(size);
        String cat = resolveFilterCategory(category);
        String st = resolveFilterStatus(status);
        String kw = trim(keyword);
        long total = supportMapper.countPage(cat, st, kw);
        List<EmergencySupportVO> records = supportMapper.selectPage(cat, st, kw, s, (long) (p - 1) * s);
        decorate(records);
        return pageResult(total, p, s, records);
    }

    /**
     * 列表行装扮：编码列翻译 + 附件批量装配。
     * <p>列表附件列需要展示首个文件名与数量；逐行查附件会产生 N+1，改为一次 IN 查询后分组回填。
     */
    private void decorate(List<EmergencySupportVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>(records.size());
        for (EmergencySupportVO row : records) {
            translate(row);
            ids.add(row.getId());
        }
        List<EmergencyAttachmentVO> attachments;
        if (!attachmentTableReady()) {
            attachments = new ArrayList<>();
        } else {
            try {
                attachments = supportMapper.selectAttachmentsBySupportIds(ids);
            } catch (DataAccessException e) {
                // 列表页无事务：查询异常可安全降级（主表列表照常可用）
                log.warn("[应急保障] 附件查询失败，列表按无附件处理：{}", e.getMessage());
                attachments = new ArrayList<>();
            }
        }
        Map<String, List<EmergencyAttachmentVO>> grouped = new LinkedHashMap<>();
        for (EmergencyAttachmentVO att : attachments) {
            String key = att.getSupportId();
            if (key == null) {
                continue;
            }
            List<EmergencyAttachmentVO> list = grouped.get(key);
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(key, list);
            }
            list.add(att);
        }
        for (EmergencySupportVO row : records) {
            List<EmergencyAttachmentVO> list = grouped.get(row.getId());
            row.setAttachments(list == null ? new ArrayList<EmergencyAttachmentVO>() : list);
        }
    }

    /** 表单元数据：保障类别 / 当前状态下拉、物资装备联动类别编码、当天日期（有效期默认值）。 */
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", EmergencySupportDict.categoryOptions());
        out.put("statuses", EmergencySupportDict.statusOptions());
        out.put("materialCategory", EmergencySupportDict.CATEGORY_MATERIAL);
        out.put("defaultStatus", EmergencySupportDict.STATUS_USABLE);
        out.put("today", LocalDate.now().toString());
        return out;
    }

    /** 单条详情（含附件清单）。 */
    public EmergencySupportVO detail(String id) {
        EmergencySupportVO vo = supportMapper.selectById(requireText(id, "应急保障 id 不能为空"));
        if (vo == null) {
            throw new IllegalArgumentException("应急保障记录不存在或已删除");
        }
        translate(vo);
        vo.setAttachments(safeAttachments(vo.getId()));
        return vo;
    }

    /** 附件清单（容错）：附件表未初始化时降级为空，保证主表详情/保存回显可用 */
    private List<EmergencyAttachmentVO> safeAttachments(String supportId) {
        if (!attachmentTableReady()) {
            return new ArrayList<>();
        }
        try {
            return supportMapper.selectAttachments(supportId);
        } catch (DataAccessException e) {
            log.warn("[应急保障] 附件查询失败，按无附件处理：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 附件表是否已建（缺表环境降级判断）。
     * <p>必须在执行附件 SQL 之前判断：主事务内一旦有语句因缺表失败，KES/PG 会把事务标记为 aborted，
     * 后续主表 SQL 与最终 COMMIT 全部失效（表现为「保存成功但没数据」或「删除/编辑报错」）。
     * <p>只缓存「存在」结论，缺表环境每次重探，执行 DDL 后无需重启即可恢复。
     */
    private boolean attachmentTableReady() {
        if (Boolean.TRUE.equals(attachmentTableExists)) {
            return true;
        }
        try {
            boolean ready = supportMapper.countAttachmentTable() > 0;
            attachmentTableExists = ready;
            if (!ready) {
                log.warn("[应急保障] 附件表未初始化：附件相关能力降级，执行 emergency-support-ddl.sql 后自动恢复");
            }
            return ready;
        } catch (DataAccessException e) {
            log.warn("[应急保障] 附件表探测失败，附件相关能力降级：{}", e.getMessage());
            return false;
        }
    }

    // ==================== 新增 / 编辑 / 删除 ====================

    /** 新增（编号服务端生成；附件回填归属）。 */
    @Transactional(rollbackFor = Exception.class)
    public EmergencySupportVO save(EmergencySupportSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Validated form = validate(req);
        String id = shortIdGenerator.nextUUID(null);
        String code = noService.nextCode();
        String operator = currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("code", code);
        row.put("category", form.category);
        row.put("name", form.name);
        row.put("content", form.content);
        row.put("capacity", form.capacity);
        row.put("owner", form.owner);
        row.put("scene", form.scene);
        row.put("status", form.status);
        row.put("check_date", form.checkDate);
        row.put("usage", form.usage);
        row.put("now", now);
        row.put("operator", operator);
        supportMapper.insertSupport(row);
        bindAttachments(id, req.getAttachmentIds(), operator, now);

        log.info("[应急保障] 新增：{}（{}），类别={}，附件 {} 个，操作人={}",
                code, form.name, form.category,
                req.getAttachmentIds() == null ? 0 : req.getAttachmentIds().size(), operator);
        return detail(id);
    }

    /**
     * 编辑（编号与创建审计列不变；附件按保留集合重绑，移除项提交后删文件）。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmergencySupportVO update(EmergencySupportSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String id = requireText(req.getId(), "应急保障 id 不能为空");
        if (supportMapper.selectById(id) == null) {
            throw new IllegalArgumentException("应急保障记录不存在或已删除");
        }
        Validated form = validate(req);
        String operator = currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();

        // 附件先绑定到本记录（无归属或已属本记录才允许），再按保留集合清理
        List<String> keepIds = bindAttachments(id, req.getAttachmentIds(), operator, now);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("category", form.category);
        row.put("name", form.name);
        row.put("content", form.content);
        row.put("capacity", form.capacity);
        row.put("owner", form.owner);
        row.put("scene", form.scene);
        row.put("status", form.status);
        row.put("check_date", form.checkDate);
        row.put("usage", form.usage);
        row.put("now", now);
        row.put("operator", operator);
        if (supportMapper.updateSupport(row) == 0) {
            throw new IllegalArgumentException("应急保障记录不存在或已删除");
        }

        // 移除：现属本记录但不在保留集合内的附件（仅删行；物理文件提交后删，避免回滚后行在文件缺）
        List<String> removedPaths = new ArrayList<>();
        for (EmergencyAttachmentVO att : safeAttachments(id)) {
            if (!keepIds.contains(att.getId())) {
                EmergencyAttachmentVO full = supportMapper.selectAttachmentById(att.getId());
                if (full != null && supportMapper.deleteAttachment(full.getId()) > 0) {
                    removedPaths.add(full.getFilePath());
                }
            }
        }
        LocalFileStorageUtils.deleteAfterCommit(fileDirPath(), removedPaths);

        log.info("[应急保障] 编辑：{}（{}），保留附件 {} 个，移除 {} 个，操作人={}",
                id, form.name, keepIds.size(), removedPaths.size(), operator);
        return detail(id);
    }

    /** 删除（主表物理删除 + 附件行清理，物理文件提交后删）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        String key = requireText(id, "应急保障 id 不能为空");
        EmergencySupportVO existing = supportMapper.selectById(key);
        if (existing == null) {
            throw new IllegalArgumentException("应急保障记录不存在或已删除");
        }
        List<String> paths = new ArrayList<>();
        if (attachmentTableReady()) {
            for (EmergencyAttachmentVO att : supportMapper.selectAttachmentsForCleanup(key)) {
                paths.add(att.getFilePath());
            }
            supportMapper.deleteAttachmentsBySupport(key);
        } else {
            // 附件表未初始化：无附件可清，不阻断主表删除
            log.warn("[应急保障] 附件表不可用，跳过附件清理：{}", existing.getCode());
        }
        supportMapper.deleteSupport(key);
        LocalFileStorageUtils.deleteAfterCommit(fileDirPath(), paths);
        log.info("[应急保障] 删除：{}（{}），附件 {} 个", existing.getCode(), existing.getName(), paths.size());
    }

    // ==================== 物资装备联动 ====================

    /** 应急物资台账候选（物资装备类别专用：选择后自动带出数量/能力）。 */
    public List<EmergencyMaterialOptionVO> materialOptions(String keyword) {
        return supportMapper.selectEmergencyMaterialOptions(trim(keyword));
    }

    // ==================== 附件（上传 / 下载 / 移除） ====================

    /** 上传附件：白名单 + 落盘（emergency/yyyy-MM/短id_原名） + 行入库（support_id 为空待回填）。 */
    public EmergencyAttachmentVO uploadAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (!attachmentTableReady()) {
            throw new IllegalArgumentException("附件功能未初始化，请先执行 emergency-support-ddl.sql");
        }
        String originalName = LocalFileStorageUtils.sanitizeFileName(file.getOriginalFilename());
        String ext = LocalFileStorageUtils.extOf(originalName);
        if (!LocalFileStorageUtils.isAllowedExt(ext)) {
            throw new IllegalArgumentException("不支持的文件类型（仅支持图片、PDF、Word、Excel）：" + originalName);
        }
        String operator = currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();
        String relPath = LocalFileStorageUtils.relPath(SUPPORT_DIR, now, shortIdGenerator.nextUUID(null), originalName);
        LocalFileStorageUtils.write(fileDirPath(), relPath, file);

        String attachmentId = shortIdGenerator.nextUUID(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", attachmentId);
        row.put("support_id", null);
        row.put("file_name", originalName);
        row.put("file_path", relPath);
        row.put("file_size", file.getSize());
        row.put("file_ext", ext);
        row.put("now", now);
        row.put("operator", operator);
        try {
            supportMapper.insertAttachment(row);
        } catch (RuntimeException e) {
            // 行写入失败：清理已落盘文件，避免孤儿文件。缺表已由 attachmentTableReady 前置拦截，
            // 此处是真实系统异常（连接/磁盘/表空间等），原样上抛交给全局处理器按 5xx 登记，避免被误报成 400 参数问题
            LocalFileStorageUtils.deleteQuietly(fileDirPath(), relPath);
            throw e;
        }

        EmergencyAttachmentVO vo = new EmergencyAttachmentVO();
        vo.setId(attachmentId);
        vo.setFileName(originalName);
        vo.setFileSize(file.getSize());
        vo.setFileExt(ext);
        log.info("[应急保障] 附件上传成功：{}（{} 字节），存储路径={}，操作人={}",
                originalName, file.getSize(), relPath, operator);
        return vo;
    }

    /** 下载附件：路径穿越校验 + 文件读取（缺失提示已被清理）。 */
    public FileDownloadVO downloadAttachment(String id) {
        EmergencyAttachmentVO att = supportMapper.selectAttachmentById(requireText(id, "附件 id 不能为空"));
        if (att == null) {
            throw new IllegalArgumentException("附件不存在或已删除");
        }
        byte[] bytes = LocalFileStorageUtils.readBytes(fileDirPath(), requireText(att.getFilePath(), "附件路径缺失"));
        FileDownloadVO vo = new FileDownloadVO();
        vo.setContent(bytes);
        vo.setFileName(att.getFileName());
        vo.setFileExt(att.getFileExt());
        return vo;
    }

    /** 移除附件（仅限未归属的新上传附件：新增取消清理 / 编辑态移除新上传）：删行（未归属守卫） + 物理删文件。 */
    public void removeAttachment(String id) {
        String key = requireText(id, "附件 id 不能为空");
        EmergencyAttachmentVO att = supportMapper.selectAttachmentById(key);
        if (att == null) {
            throw new IllegalArgumentException("附件不存在或已删除");
        }
        // 仅允许删除未归属附件：DELETE 带 support_id IS NULL 守卫，并发绑定后返回 0 行 → 拒绝移除且不删文件
        if (supportMapper.deleteUnboundAttachment(key) == 0) {
            throw new IllegalArgumentException("附件已保存到应急保障记录，不能直接移除");
        }
        LocalFileStorageUtils.deleteQuietly(fileDirPath(), att.getFilePath());
        log.info("[应急保障] 附件已移除：{}（{}）", att.getFileName(), att.getId());
    }

    // ==================== 校验与工具 ====================

    /**
     * 表单校验与归一（新增/编辑共用）：类别、状态必填且合法，名称必填，
     * 其余按列宽上限拦截；日期可空，填写必须 yyyy-MM-dd。
     */
    private static Validated validate(EmergencySupportSaveVO req) {
        Validated form = new Validated();
        form.category = EmergencySupportDict.resolveCategory(req.getCategory());
        if (!EmergencySupportDict.isValidCategory(form.category)) {
            throw new IllegalArgumentException("请选择保障类别");
        }
        form.name = CheckUtils.maxLength(
                requireText(req.getName(), "请填写保障措施/资源名称"), NAME_MAX_LENGTH, "保障措施/资源名称");
        form.content = CheckUtils.maxLength(
                trim(req.getContent()), CONTENT_MAX_LENGTH, "保障内容");
        form.capacity = CheckUtils.maxLength(
                trim(req.getCapacity()), CAPACITY_MAX_LENGTH, "数量/能力");
        form.owner = CheckUtils.maxLength(
                trim(req.getOwner()), OWNER_MAX_LENGTH, "责任单位/负责人");
        form.scene = CheckUtils.maxLength(
                trim(req.getScene()), SCENE_MAX_LENGTH, "适用场景");
        form.status = EmergencySupportDict.resolveStatus(req.getStatus());
        if (!EmergencySupportDict.isValidStatus(form.status)) {
            throw new IllegalArgumentException("请选择当前状态");
        }
        form.checkDate = normalizeDay(req.getCheckDate(), "最近检查/有效期");
        form.usage = CheckUtils.maxLength(
                trim(req.getUsage()), USAGE_MAX_LENGTH, "响应使用方式");
        return form;
    }

    /**
     * 附件绑定（新增/编辑共用）：逐个校验存在性与归属，未归属的当场回填；
     * 返回本次保留的附件 ID 集合（编辑据此清理被移除项）。
     */
    private List<String> bindAttachments(String supportId, List<String> attachmentIds,
                                         String operator, LocalDateTime now) {
        List<String> keepIds = new ArrayList<>();
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return keepIds;
        }
        if (!attachmentTableReady()) {
            // 附件表未初始化：上传接口已先行拦截，正常不会带附件 id；忽略以免整单失败
            log.warn("[应急保障] 附件表未初始化，忽略 {} 个附件绑定", attachmentIds.size());
            return keepIds;
        }
        for (String raw : attachmentIds) {
            String aid = trim(raw);
            if (aid == null) {
                continue;
            }
            EmergencyAttachmentVO att = supportMapper.selectAttachmentById(aid);
            if (att == null) {
                throw new IllegalArgumentException("附件不存在或已移除：" + aid);
            }
            if (att.getSupportId() != null && !att.getSupportId().equals(supportId)) {
                throw new IllegalArgumentException("附件已被其他应急保障记录占用：" + att.getFileName());
            }
            if (att.getSupportId() == null
                    && supportMapper.bindAttachmentSupport(aid, supportId, operator, now) == 0) {
                throw new IllegalArgumentException("附件绑定失败，请刷新后重试：" + att.getFileName());
            }
            keepIds.add(aid);
        }
        return keepIds;
    }

    /** 编码翻译（列表/详情共用）。 */
    private static void translate(EmergencySupportVO vo) {
        vo.setCategoryName(EmergencySupportDict.categoryName(vo.getCategoryCode()));
        vo.setStatusName(EmergencySupportDict.statusName(vo.getStatusCode()));
    }

    /** 列表筛选类别归一（可空；中文或编码均可，非法值直接报错便于定位） */
    private static String resolveFilterCategory(String category) {
        String value = trim(category);
        if (value == null) {
            return null;
        }
        String code = EmergencySupportDict.resolveCategory(value);
        if (!EmergencySupportDict.isValidCategory(code)) {
            throw new IllegalArgumentException("保障类别不存在：" + value);
        }
        return code;
    }

    /** 列表筛选状态归一（可空；中文或编码均可） */
    private static String resolveFilterStatus(String status) {
        String value = trim(status);
        if (value == null) {
            return null;
        }
        String code = EmergencySupportDict.resolveStatus(value);
        if (!EmergencySupportDict.isValidStatus(code)) {
            throw new IllegalArgumentException("当前状态不存在：" + value);
        }
        return code;
    }

    /** 附件存储根目录（绝对路径归一，用作穿越校验基准；文件操作见 LocalFileStorageUtils）。 */
    private Path fileDirPath() {
        return LocalFileStorageUtils.root(fileDir);
    }

    /** 校验归一后的表单值（避免落库前再次 trim/翻译）。 */
    private static final class Validated {
        private String category;
        private String name;
        private String content;
        private String capacity;
        private String owner;
        private String scene;
        private String status;
        private String checkDate;
        private String usage;
    }
}
