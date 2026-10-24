package com.qgyun.hltgq.hltgqsite.patrol.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.mapper.ArchiveLookupMapper;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DictUtils;
import com.qgyun.hltgq.hltgqsite.model.util.LocalFileStorageUtils;
import com.qgyun.hltgq.hltgqsite.model.util.OperatorUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.patrol.mapper.PatrolIssueMapper;
import com.qgyun.hltgq.hltgqsite.patrol.mapper.PatrolPlanMapper;
import com.qgyun.hltgq.hltgqsite.patrol.support.PatrolDicts;
import com.qgyun.hltgq.hltgqsite.patrol.support.PatrolPhotoSupport;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueAdminDetailVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueAdminRequest;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueCreateRequest;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueCreateVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssuePhotoFileVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssuePhotoItemVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssuePhotoVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueRecordContextVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueRowVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolSiteVO;
import com.qgyun.hltgq.hltgqsite.workorder.service.WorkOrderService;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderCreateVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 巡检问题服务（/patrol-issue/*，PC 巡检记录抽屉、H5 与问题记录管理页共用）。
 *
 * <p>落库口径（问题表 t_auto_hltgq_knc3g_bpzjoh）：
 * <ul>
 *   <li>code：QST-毫秒时间戳（与存量 8 条数据同形态；毫秒级查重，重复时重试）</li>
 *   <li>time：发现时间，取服务端当前时间；handle_status=#1# 待确认、status=#1# 待处理（均为只读默认）</li>
 *   <li>site/device/"user" 为单值列；abqezf 存关联巡检记录 id；risk_level 可空</li>
 * </ul>
 *
 * <p>管理页状态机：作废 #1#→#5#；关闭 #2#→#4#；直接处理 #1#→#2#（handle=#3#）；
 * 填写处理结果只写 ezuvil；转工单 #1#→#3#（handle=#2#、回写 work_order_id，并新建 #1# 待指派工单）。
 * 全部流转为条件更新，状态已变化返回 400 提示刷新。
 *
 * <p>现场照片：自建表 t_auto_hltgq_water_patrol_issue_photo + 本地 file-dir（patrol-issue/yyyy-MM/），
 * 上传时仅记录上传人，提交问题时绑定并写排序；<b>不写平台 jmltds 关系表</b>
 * （平台文件服务上传契约未接入，详见 H5接口.md）。
 */
@Service
public class PatrolIssueService {

    private static final Logger log = LoggerFactory.getLogger(PatrolIssueService.class);

    /** 照片存储子目录（相对 hltgq.file-dir） */
    private static final String PHOTO_DIR = "patrol-issue";

    /** 问题标题长度上限 */
    private static final int TITLE_MAX_LENGTH = 200;

    /** 问题描述长度上限（平台字段 0~500） */
    private static final int CONTENT_MAX_LENGTH = 500;

    /** 处理结果长度上限 */
    private static final int RESULT_MAX_LENGTH = 1000;

    /** 单次提交照片上限 */
    private static final int PHOTO_MAX_COUNT = 9;

    /** 单张照片大小上限：10MB（multipart 全局上限 20MB） */
    private static final long PHOTO_MAX_SIZE = 10L * 1024 * 1024;

    /** 照片扩展名白名单（仅图片） */
    private static final Set<String> PHOTO_EXTENSIONS = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp"));

    /** 处理方式默认：待确认（平台只读默认，新增不开放选择） */
    private static final String HANDLE_STATUS_PENDING = "#1#";

    /** 状态默认：待处理（表单隐藏字段，SQL 有对应列） */
    private static final String STATUS_PENDING = "#1#";

    /** 问题编号前缀（QST- + 毫秒时间戳，与存量数据一致） */
    private static final String CODE_PREFIX = "QST-";

    /** 风险等级填写值 → 落库编码（选项组 Tzocc3TligWY99Y7QBo） */
    private static final Map<String, String> RISK_LEVEL_CODES = new LinkedHashMap<>();

    static {
        RISK_LEVEL_CODES.put("低风险", "#1#");
        RISK_LEVEL_CODES.put("中风险", "#2#");
        RISK_LEVEL_CODES.put("高风险", "#3#");
        RISK_LEVEL_CODES.put("#1#", "#1#");
        RISK_LEVEL_CODES.put("#2#", "#2#");
        RISK_LEVEL_CODES.put("#3#", "#3#");
    }

    @Autowired
    private PatrolIssueMapper mapper;

    /** 站点/设备/人员档案存在性校验（跨模块唯一实现） */
    @Autowired
    private ArchiveLookupMapper lookupMapper;

    /** 复用计划巡查范围查询（与「添加巡检记录」同一口径） */
    @Autowired
    private PatrolPlanMapper planMapper;

    /** 转工单复用工单服务（编号生成 + 工单落库，与问题标记同一事务） */
    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    @Value("${hltgq.corp-code}")
    private String corpCode;

    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    @Value("${hltgq.file-dir:./file-dir}")
    private String fileDir;

    // ==================== 巡检记录上下文 ====================

    /**
     * 巡检记录上下文：站点/设备/时间以记录为准（新增问题页回填）。
     * <p>记录已带站点时 siteLocked=true；sites 为计划巡查范围，仅供 H5 补录记录时参考。
     */
    public PatrolIssueRecordContextVO recordContext(String recordId) {
        String key = CheckUtils.trim(recordId);
        if (key == null) {
            throw new IllegalArgumentException("巡检记录 id 不能为空");
        }
        PatrolIssueRecordContextVO vo = mapper.selectRecordContext(key);
        if (vo == null) {
            throw new IllegalArgumentException("巡检记录不存在：" + key);
        }
        vo.setSiteLocked(vo.getSiteId() != null);
        List<PatrolSiteVO> sites = planMapper.selectPlanSites(vo.getScheduleId());
        vo.setSites(sites == null ? Collections.emptyList() : sites);
        return vo;
    }

    // ==================== 新增问题 ====================

    /**
     * 新增问题（巡检过程中上报）：校验站点/设备/发现人/关联记录后写问题表，
     * 并按数组顺序绑定现场照片；整单事务，任一照片绑定失败整体回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public PatrolIssueCreateVO create(PatrolIssueCreateRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String title = CheckUtils.trim(req.getTitle());
        if (title == null) {
            throw new IllegalArgumentException("问题标题不能为空");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("问题标题超长（≤" + TITLE_MAX_LENGTH + " 字）");
        }
        String siteId = CheckUtils.trim(req.getSiteId());
        if (siteId == null) {
            throw new IllegalArgumentException("所属站点不能为空");
        }
        if (lookupMapper.countSite(siteId) == 0) {
            throw new IllegalArgumentException("所属站点不存在：" + siteId);
        }
        String deviceId = CheckUtils.trim(req.getDeviceId());
        if (deviceId != null && lookupMapper.countDeviceInSite(siteId, deviceId) == 0) {
            throw new IllegalArgumentException("所选设备不属于该站点");
        }
        String content = CheckUtils.trim(req.getContent());
        if (content == null) {
            throw new IllegalArgumentException("问题描述不能为空");
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("问题描述超长（≤" + CONTENT_MAX_LENGTH + " 字）");
        }
        String riskLevel = null;
        String riskRaw = CheckUtils.trim(req.getRiskLevel());
        if (riskRaw != null) {
            riskLevel = RISK_LEVEL_CODES.get(riskRaw);
            if (riskLevel == null) {
                throw new IllegalArgumentException("风险等级无效（应为 低风险/中风险/高风险）");
            }
        }
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        String userId = CheckUtils.trim(req.getUserId());
        if (userId == null) {
            userId = operatorId;
        }
        if (lookupMapper.countUser(userId) == 0) {
            throw new IllegalArgumentException("发现人不存在：" + userId);
        }
        String recordId = CheckUtils.trim(req.getRecordId());
        if (recordId != null) {
            PatrolIssueRecordContextVO record = mapper.selectRecordContext(recordId);
            if (record == null) {
                throw new IllegalArgumentException("关联的巡检记录不存在：" + recordId);
            }
            // 站点必填且以记录为准：记录缺站点属数据异常，先补录记录再上报
            if (record.getSiteId() == null) {
                throw new IllegalArgumentException("巡检记录缺少站点，请先补录巡检记录后再上报问题");
            }
            if (!record.getSiteId().equals(siteId)) {
                throw new IllegalArgumentException("所属站点须与巡检记录一致");
            }
        }
        List<String> photoIds = PatrolPhotoSupport.normalizePhotoIds(req.getPhotoIds(), PHOTO_MAX_COUNT);
        // 预校验：照片必须存在、属于当前上传人且未绑定过其他问题
        for (String photoId : photoIds) {
            PatrolIssuePhotoFileVO photo = mapper.selectPhoto(photoId);
            if (photo == null) {
                throw new IllegalArgumentException("现场照片不存在或已失效，请重新上传");
            }
            if (!operatorId.equals(photo.getCreatedBy()) || photo.getIssueId() != null) {
                throw new IllegalArgumentException("现场照片不可用（非本人上传或已提交），请重新上传");
            }
        }

        String code = generateCode();
        String id = shortIdGenerator.nextUUID(null);
        LocalDateTime now = LocalDateTime.now();
        mapper.insertIssue(id, corpCode, operatorId, now, code, title, siteId, deviceId, content,
                userId, now, HANDLE_STATUS_PENDING, STATUS_PENDING, riskLevel, recordId);
        for (int i = 0; i < photoIds.size(); i++) {
            int updated = mapper.bindPhoto(photoIds.get(i), id, i + 1, operatorId, now);
            if (updated == 0) {
                throw new IllegalArgumentException("现场照片状态已变化，请重新上传后提交");
            }
        }
        log.info("[巡检问题] 新增问题：id={}, code={}, 站点={}, 设备={}, 关联记录={}, 照片={} 张, 发现人={}, 操作人={}",
                id, code, siteId, deviceId, recordId, photoIds.size(), userId, operatorId);
        PatrolIssueCreateVO vo = new PatrolIssueCreateVO();
        vo.setId(id);
        vo.setCode(code);
        vo.setPhotoCount(photoIds.size());
        return vo;
    }

    // ==================== 管理页：分页 / 详情 ====================

    /** 问题分页（标题模糊 + 所属站点 + 状态）；状态支持中文与编码。 */
    public Page<PatrolIssueRowVO> page(String title, String siteId, String status, long page, long size) {
        long p = Math.max(1, page);
        long s = Math.min(Math.max(1, size), 100);
        String t = CheckUtils.trim(title);
        String sid = CheckUtils.trim(siteId);
        String statusCode = DictUtils.resolveCode(PatrolDicts.ISSUE_STATUS_LABELS, status);
        Page<PatrolIssueRowVO> out = new Page<>(p, s);
        long total = mapper.countAdminPage(t, sid, statusCode);
        out.setTotal(total);
        if (total == 0) {
            out.setRecords(Collections.emptyList());
            return out;
        }
        List<PatrolIssueRowVO> rows = mapper.selectAdminPage(t, sid, statusCode, s, (p - 1) * s);
        for (PatrolIssueRowVO row : rows) {
            row.setHandleLabel(DictUtils.label(PatrolDicts.HANDLE_STATUS_LABELS, row.getHandleCode()));
            row.setStatusLabel(DictUtils.label(PatrolDicts.ISSUE_STATUS_LABELS, row.getStatusCode()));
        }
        out.setRecords(rows);
        return out;
    }

    /** 问题详情（查看弹窗）：含关联工单编号/标题/状态；现场照片走 /patrol-issue/photos。 */
    public PatrolIssueAdminDetailVO detail(String id) {
        String key = CheckUtils.trim(id);
        if (key == null) {
            throw new IllegalArgumentException("问题 id 不能为空");
        }
        PatrolIssueAdminDetailVO vo = mapper.selectAdminDetail(key);
        if (vo == null) {
            throw new IllegalArgumentException("问题记录不存在：" + key);
        }
        vo.setHandleLabel(DictUtils.label(PatrolDicts.HANDLE_STATUS_LABELS, vo.getHandleCode()));
        vo.setStatusLabel(DictUtils.label(PatrolDicts.ISSUE_STATUS_LABELS, vo.getStatusCode()));
        vo.setRiskLabel(DictUtils.label(PatrolDicts.RISK_LABELS, vo.getRiskCode()));
        vo.setWorkOrderStatusLabel(WorkOrderService.statusLabel(vo.getWorkOrderStatusCode()));
        return vo;
    }

    // ==================== 管理页：状态流转 ====================

    /** 作废问题：待处理 #1# → 已作废 #5#。 */
    public void voidIssue(String id) {
        String key = requireIssueId(id);
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        int rows = mapper.voidIssue(key, operatorId, LocalDateTime.now());
        if (rows == 0) {
            throw new IllegalArgumentException("仅待处理的问题可作废，状态已变化，请刷新后重试");
        }
        log.info("[巡检问题] 作废：id={}, 操作人={}", key, operatorId);
    }

    /** 关闭问题：处理中 #2# → 已关闭 #4#。 */
    public void closeIssue(String id) {
        String key = requireIssueId(id);
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        int rows = mapper.closeIssue(key, operatorId, LocalDateTime.now());
        if (rows == 0) {
            throw new IllegalArgumentException("仅处理中的问题可关闭，状态已变化，请刷新后重试");
        }
        log.info("[巡检问题] 关闭：id={}, 操作人={}", key, operatorId);
    }

    /** 直接处理：待处理 #1# → 处理中 #2#（处理方式置为直接处理 #3#）。 */
    public void directHandle(String id) {
        String key = requireIssueId(id);
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        int rows = mapper.startHandle(key, operatorId, LocalDateTime.now());
        if (rows == 0) {
            throw new IllegalArgumentException("仅待处理的问题可直接处理，状态已变化，请刷新后重试");
        }
        log.info("[巡检问题] 直接处理：id={}, 操作人={}", key, operatorId);
    }

    /** 填写处理结果：仅处理中 #2#，只写处理结果，不改状态（可重复保存）。 */
    public void saveResult(PatrolIssueAdminRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String key = requireIssueId(req.getId());
        String result = CheckUtils.requireText(req.getResult(), "处理结果不能为空");
        CheckUtils.maxLength(result, RESULT_MAX_LENGTH, "处理结果");
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        int rows = mapper.saveResult(key, result, operatorId, LocalDateTime.now());
        if (rows == 0) {
            throw new IllegalArgumentException("仅处理中的问题可填写处理结果，状态已变化，请刷新后重试");
        }
        log.info("[巡检问题] 填写处理结果：id={}, 长度={}, 操作人={}", key, result.length(), operatorId);
    }

    /**
     * 转工单：待处理 #1# → 已转工单 #3#，并新建 #1# 待指派工单；整单事务。
     * <p>站点/设备取问题记录本身；标题/内容未填时默认取问题标题/描述。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderCreateVO transfer(PatrolIssueAdminRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String key = requireIssueId(req.getId());
        PatrolIssueAdminDetailVO issue = mapper.selectAdminDetail(key);
        if (issue == null) {
            throw new IllegalArgumentException("问题记录不存在：" + key);
        }
        if (!STATUS_PENDING.equals(issue.getStatusCode())) {
            throw new IllegalArgumentException("仅待处理的问题可转工单，状态已变化，请刷新后重试");
        }
        if (issue.getSiteId() == null) {
            throw new IllegalArgumentException("问题缺少所属站点，无法转工单，请先补录站点");
        }
        String type = CheckUtils.trim(req.getType());
        if (!WorkOrderService.isValidType(type)) {
            throw new IllegalArgumentException("请选择工单类型");
        }
        String title = CheckUtils.trim(req.getTitle());
        if (title == null) {
            title = issue.getTitle();
        }
        String content = CheckUtils.trim(req.getContent());
        if (content == null) {
            content = issue.getContent();
        }
        LocalDateTime time = WorkOrderService.parseTime(req.getTime(), "要求完成时间");
        WorkOrderCreateVO order = workOrderService.createFromIssue(title, type, content, issue.getSiteId(),
                issue.getDeviceId(), req.getOrgId(), req.getUserId(), time);
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        int rows = mapper.transferIssue(key, order.getId(), operatorId, LocalDateTime.now());
        if (rows == 0) {
            throw new IllegalArgumentException("问题状态已变化，请刷新后重试");
        }
        log.info("[巡检问题] 转工单：问题 id={}, 工单 id={}, code={}, 操作人={}",
                key, order.getId(), order.getCode(), operatorId);
        return order;
    }

    /** 管理操作的问题 id 必填校验。 */
    private static String requireIssueId(String id) {
        return CheckUtils.requireText(id, "问题 id 不能为空");
    }

    // ==================== 现场照片 ====================

    /** 上传现场照片：图片白名单 + 落盘（patrol-issue/yyyy-MM/uuid_原名） + 写照片行（待提交绑定）。 */
    public PatrolIssuePhotoVO uploadPhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > PHOTO_MAX_SIZE) {
            throw new IllegalArgumentException("单张照片不能超过 10MB");
        }
        String originalName = LocalFileStorageUtils.sanitizeFileName(file.getOriginalFilename());
        String ext = LocalFileStorageUtils.extOf(originalName);
        if (ext == null || !PHOTO_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("仅支持图片格式（jpg/jpeg/png/gif/bmp/webp）：" + originalName);
        }
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();
        String photoId = shortIdGenerator.nextUUID(null);
        String relPath = PHOTO_DIR + "/" + now.format(LocalFileStorageUtils.MONTH_DIR) + "/" + photoId + "_" + originalName;
        Path base = LocalFileStorageUtils.root(fileDir);
        LocalFileStorageUtils.write(base, relPath, file);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", photoId);
        row.put("issueId", null);
        row.put("fileName", originalName);
        row.put("filePath", relPath);
        row.put("fileSize", file.getSize());
        row.put("fileExt", ext);
        row.put("natureOrder", null);
        row.put("corpCode", corpCode);
        row.put("now", now);
        row.put("operatorId", operatorId);
        try {
            mapper.insertPhoto(row);
        } catch (RuntimeException e) {
            // 行写入失败：清理已落盘文件，避免孤儿文件
            LocalFileStorageUtils.deleteQuietly(base, relPath);
            throw e;
        }
        log.info("[巡检问题] 照片上传成功：{}（{} 字节），id={}，路径={}，上传人={}",
                originalName, file.getSize(), photoId, relPath, operatorId);
        PatrolIssuePhotoVO vo = new PatrolIssuePhotoVO();
        vo.setId(photoId);
        vo.setName(originalName);
        vo.setSize(file.getSize());
        vo.setExt(ext);
        vo.setUrl("/patrol-issue/photo/" + photoId);
        return vo;
    }

    /** 读取照片字节（预览；路径穿越校验由存储工具兜底）。 */
    public PatrolPhotoSupport.PhotoContent readPhoto(String id) {
        String photoId = CheckUtils.trim(id);
        if (photoId == null) {
            throw new IllegalArgumentException("照片 id 不能为空");
        }
        PatrolIssuePhotoFileVO photo = mapper.selectPhoto(photoId);
        if (photo == null) {
            throw new IllegalArgumentException("照片不存在或已删除");
        }
        String relPath = CheckUtils.trim(photo.getFilePath());
        if (relPath == null) {
            throw new IllegalArgumentException("照片路径缺失");
        }
        byte[] bytes = LocalFileStorageUtils.readBytes(LocalFileStorageUtils.root(fileDir), relPath);
        String ext = photo.getFileExt() == null ? LocalFileStorageUtils.extOf(photo.getFileName()) : photo.getFileExt();
        return new PatrolPhotoSupport.PhotoContent(bytes, ext == null ? "" : ext.toLowerCase(Locale.ROOT));
    }

    /**
     * 按问题 id 查现场照片（H5 / PC 回显）：排序与上传时一致，url 为站点内相对路径
     * （前端拼 API 前缀后直接用于 img）。
     */
    public List<PatrolIssuePhotoItemVO> listPhotos(String issueId) {
        String key = CheckUtils.trim(issueId);
        if (key == null) {
            throw new IllegalArgumentException("问题 id 不能为空");
        }
        if (mapper.countIssue(key) == 0) {
            throw new IllegalArgumentException("问题记录不存在：" + key);
        }
        List<PatrolIssuePhotoItemVO> rows = mapper.selectPhotosByIssue(key);
        for (PatrolIssuePhotoItemVO row : rows) {
            row.setUrl("/patrol-issue/photo/" + row.getId());
        }
        return rows;
    }

    /** 移除未提交的照片（仅本人上传、尚未绑定问题），删行 + 物理删文件。 */
    public void removePhoto(String id) {
        String photoId = CheckUtils.trim(id);
        if (photoId == null) {
            throw new IllegalArgumentException("照片 id 不能为空");
        }
        PatrolIssuePhotoFileVO photo = mapper.selectPhoto(photoId);
        if (photo == null) {
            throw new IllegalArgumentException("照片不存在或已删除");
        }
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        if (mapper.deleteUnboundPhoto(photoId, operatorId) == 0) {
            throw new IllegalArgumentException("照片已提交到问题记录，不能移除");
        }
        LocalFileStorageUtils.deleteQuietly(LocalFileStorageUtils.root(fileDir), photo.getFilePath());
        log.info("[巡检问题] 照片已移除：{}（{}），操作人={}", photo.getFileName(), photoId, operatorId);
    }

    // ==================== 工具 ====================

    /** 问题编号生成：QST-毫秒时间戳；同毫秒撞号时重试（最多 5 次）。 */
    private String generateCode() {
        for (int i = 0; i < 5; i++) {
            String candidate = CODE_PREFIX + System.currentTimeMillis();
            if (mapper.countByCode(candidate) == 0) {
                return candidate;
            }
            try {
                Thread.sleep(2L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("问题编号生成失败，请重试");
    }
}
