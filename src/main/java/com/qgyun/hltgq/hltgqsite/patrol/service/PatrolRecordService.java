package com.qgyun.hltgq.hltgqsite.patrol.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DictUtils;
import com.qgyun.hltgq.hltgqsite.model.util.LocalFileStorageUtils;
import com.qgyun.hltgq.hltgqsite.model.util.OperatorUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.patrol.mapper.PatrolPlanMapper;
import com.qgyun.hltgq.hltgqsite.patrol.mapper.PatrolRecordMapper;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolOptionVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordDetailVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordIssueItemVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordPhotoFileVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordPhotoItemVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordRowVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordSaveRequest;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolSiteVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 巡检记录管理服务（/patrol-record/*，patrol-record-list.html + patrol-record-add.html）。
 *
 * <p>落库口径（记录表 t_auto_hltgq_water_inspection_record）：
 * <ul>
 *   <li>time/patrol_schedule/site/"user"/result/content/status 为物理列；巡检对象写动态列 "_d_10497_device"</li>
 *   <li>成本金额写 szeedc（2 位小数）；新增固定 status=#1# 草稿，列表「提交」后置 #2# 已提交</li>
 *   <li>平台多值字段（imjjpx 可选站点、tzxlun 问题子表、site_image 现场照片）无物理列：
 *       站点候选按计划巡查范围推导；问题走新增问题页（abqezf 关联）；照片落自建表</li>
 * </ul>
 *
 * <p>现场照片：自建表 t_auto_hltgq_water_patrol_record_photo + 本地 file-dir（patrol-record/yyyy-MM/），
 * 上传时仅记录上传人，保存记录时绑定并按列表顺序排序；<b>不写平台文件关系表</b>
 * （平台文件服务上传契约未接入，H5 提交的记录照片在 PC 端不可见）。
 */
@Service
public class PatrolRecordService {

    private static final Logger log = LoggerFactory.getLogger(PatrolRecordService.class);

    /** 照片存储子目录（相对 hltgq.file-dir） */
    private static final String PHOTO_DIR = "patrol-record";

    /** 现场照片上限（平台定义：最多 3 张） */
    private static final int PHOTO_MAX_COUNT = 3;

    /** 单张照片大小上限：5MB（multipart 全局上限 20MB） */
    private static final long PHOTO_MAX_SIZE = 5L * 1024 * 1024;

    /** 照片扩展名白名单（仅图片） */
    private static final Set<String> PHOTO_EXTENSIONS = new HashSet<>(
            Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp"));

    /** 巡检说明长度上限 */
    private static final int CONTENT_MAX_LENGTH = 1000;

    /** 记录状态：草稿 */
    private static final String STATUS_DRAFT = "#1#";

    /** 记录状态：已提交 */
    private static final String STATUS_SUBMITTED = "#2#";

    /** 巡检结果编码 → 名称（选项组 IYBBDt3IqXJL71kqrYE） */
    private static final Map<String, String> RESULT_LABELS = new LinkedHashMap<>();

    /** 记录状态编码 → 名称（选项组 mITE3MttOBlt5Hb83Yw） */
    private static final Map<String, String> RECORD_STATUS_LABELS = new LinkedHashMap<>();

    /** 问题状态编码 → 名称 */
    private static final Map<String, String> ISSUE_STATUS_LABELS = new LinkedHashMap<>();

    /** 问题处理方式编码 → 名称 */
    private static final Map<String, String> HANDLE_STATUS_LABELS = new LinkedHashMap<>();

    /** 风险等级编码 → 名称（选项组 Tzocc3TligWY99Y7QBo） */
    private static final Map<String, String> RISK_LABELS = new LinkedHashMap<>();

    private static final DateTimeFormatter TIME_INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static {
        RESULT_LABELS.put("#2#", "正常");
        RESULT_LABELS.put("#3#", "异常");
        RESULT_LABELS.put("#4#", "隐患");
        RESULT_LABELS.put("#5#", "缺陷");
        RESULT_LABELS.put("#6#", "故障");

        RECORD_STATUS_LABELS.put("#1#", "草稿");
        RECORD_STATUS_LABELS.put("#2#", "已提交");

        ISSUE_STATUS_LABELS.put("#1#", "待处理");

        HANDLE_STATUS_LABELS.put("#1#", "待确认");

        RISK_LABELS.put("#1#", "低风险");
        RISK_LABELS.put("#2#", "中风险");
        RISK_LABELS.put("#3#", "高风险");
    }

    @Autowired
    private PatrolRecordMapper mapper;

    /** 复用计划巡查范围查询（与添加巡检记录同一口径） */
    @Autowired
    private PatrolPlanMapper planMapper;

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    @Value("${hltgq.corp-code}")
    private String corpCode;

    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    @Value("${hltgq.file-dir:./file-dir}")
    private String fileDir;

    // ==================== 列表 / 详情 ====================

    /** 记录分页（巡检时间区间 + 巡检站点筛选）；照片数/问题数随行返回。 */
    public Page<PatrolRecordRowVO> page(String startTime, String endTime, String siteId, long page, long size) {
        long p = Math.max(1, page);
        long s = Math.min(Math.max(1, size), 100);
        String st = normalizeDate(startTime, "开始日期");
        String et = normalizeDate(endTime, "结束日期");
        if (st != null && et != null && st.compareTo(et) > 0) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        String sid = CheckUtils.trim(siteId);
        Page<PatrolRecordRowVO> out = new Page<>(p, s);
        long total = mapper.countPage(st, et, sid);
        out.setTotal(total);
        if (total == 0) {
            out.setRecords(Collections.emptyList());
            return out;
        }
        List<PatrolRecordRowVO> rows = mapper.selectPage(st, et, sid, s, (p - 1) * s);
        for (PatrolRecordRowVO row : rows) {
            row.setResultLabel(DictUtils.label(RESULT_LABELS, row.getResultCode()));
            row.setStatusLabel(DictUtils.label(RECORD_STATUS_LABELS, row.getStatusCode()));
            row.setPhotoCount(row.getPhotoCount() == null ? 0 : row.getPhotoCount());
            row.setIssueCount(row.getIssueCount() == null ? 0 : row.getIssueCount());
        }
        out.setRecords(rows);
        return out;
    }

    /** 记录详情：基本信息 + 现场照片（自建表）+ 关联问题。 */
    public PatrolRecordDetailVO detail(String id) {
        String key = CheckUtils.trim(id);
        if (key == null) {
            throw new IllegalArgumentException("巡检记录 id 不能为空");
        }
        PatrolRecordDetailVO vo = mapper.selectDetail(key);
        if (vo == null) {
            throw new IllegalArgumentException("巡检记录不存在：" + key);
        }
        vo.setResultLabel(DictUtils.label(RESULT_LABELS, vo.getResultCode()));
        vo.setStatusLabel(DictUtils.label(RECORD_STATUS_LABELS, vo.getStatusCode()));
        List<PatrolRecordPhotoItemVO> photos = mapper.selectPhotosByRecord(key);
        for (PatrolRecordPhotoItemVO photo : photos) {
            photo.setUrl("/patrol-record/photo/" + photo.getId());
        }
        vo.setPhotos(photos);
        List<PatrolRecordIssueItemVO> issues = mapper.selectIssuesByRecord(key);
        for (PatrolRecordIssueItemVO issue : issues) {
            issue.setStatusLabel(DictUtils.label(ISSUE_STATUS_LABELS, issue.getStatusCode()));
            issue.setHandleStatusLabel(DictUtils.label(HANDLE_STATUS_LABELS, issue.getHandleStatusCode()));
            issue.setRiskLabel(DictUtils.label(RISK_LABELS, issue.getRiskLevel()));
        }
        vo.setIssues(issues);
        return vo;
    }

    /** 所属计划候选（仅已发布；includeId 用于编辑时保留当前计划）。 */
    public List<PatrolOptionVO> planOptions(String includeId) {
        return mapper.selectPlanOptions(CheckUtils.trim(includeId));
    }

    // ==================== 新增 / 编辑 ====================

    /**
     * 新增记录（草稿）：校验计划/站点/设备/结果/照片后写主表并按顺序绑定照片，整单事务。
     * <p>幂等：优先“认领”该计划下的空白占位草稿（H5 开始巡检写入、四项全空）并填充，
     * 仅在无空白草稿或并发被认领时新增；保存后清理同计划其余空白草稿，
     * 保证任何客户端都不会出现「草稿 + 正式」两条。
     * <p>站点须在计划巡查范围内（计划未配范围时不限）；设备须属于该站点。
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(PatrolRecordSaveRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        LocalDateTime time = parseTime(req.getTime());
        String scheduleId = requireSchedule(req.getScheduleId(), true);
        String siteId = requireSite(scheduleId, req.getSiteId());
        String deviceId = requireDevice(siteId, req.getDeviceId());
        String result = requireResult(req.getResult());
        String content = normalizeContent(req.getContent());
        Double cost = normalizeCost(req.getCost());
        String userId = requireUser(req.getUserId(), operatorId);
        List<String> photoIds = normalizePhotoIds(req.getPhotoIds());
        if (photoIds.isEmpty()) {
            throw new IllegalArgumentException("现场照片不能为空（至少上传 1 张）");
        }
        precheckNewPhotos(photoIds, operatorId);

        LocalDateTime now = LocalDateTime.now();
        String id = mapper.selectBlankDraftId(scheduleId);
        boolean reused = false;
        if (id != null) {
            // 认领式填充：仅当该行仍是空白占位草稿时更新成功；被并发认领则退回新增
            if (mapper.claimBlankDraft(id, operatorId, now, time, scheduleId, siteId,
                    deviceId, userId, result, content, STATUS_DRAFT, cost) > 0) {
                reused = true;
            } else {
                id = null;
            }
        }
        if (id == null) {
            id = shortIdGenerator.nextUUID(null);
            mapper.insertRecord(id, corpCode, operatorId, now, time, scheduleId, siteId, deviceId,
                    userId, result, content, STATUS_DRAFT, cost);
        }
        for (int i = 0; i < photoIds.size(); i++) {
            if (mapper.bindPhoto(photoIds.get(i), id, i + 1, operatorId, now) == 0) {
                throw new IllegalArgumentException("现场照片状态已变化，请重新上传后保存");
            }
        }
        int cleaned = mapper.deleteBlankDrafts(scheduleId, id);
        log.info("[巡检记录] {}记录：id={}, 计划={}, 站点={}, 设备={}, 结果={}, 照片={} 张, 清理空草稿={} 条, 操作人={}",
                reused ? "填充草稿" : "新增", id, scheduleId, siteId, deviceId, result, photoIds.size(), cleaned, operatorId);
        return id;
    }

    /**
     * 编辑记录（仅草稿）：字段校验同新增；照片以入参列表为准（不在列表中的旧照片删行 + 删文件）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String update(PatrolRecordSaveRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String id = CheckUtils.trim(req.getId());
        if (id == null) {
            throw new IllegalArgumentException("巡检记录 id 不能为空");
        }
        PatrolRecordDetailVO record = mapper.selectDetail(id);
        if (record == null) {
            throw new IllegalArgumentException("巡检记录不存在：" + id);
        }
        if (!STATUS_DRAFT.equals(record.getStatusCode())) {
            throw new IllegalArgumentException("已提交的巡检记录不可编辑");
        }
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        LocalDateTime time = parseTime(req.getTime());
        String scheduleId = requireSchedule(req.getScheduleId(), false);
        // 编辑允许保持原计划（原计划可能已完成/取消），换计划时仍要求已发布
        if (!scheduleId.equals(record.getScheduleId()) && mapper.countPublishedSchedule(scheduleId) == 0) {
            throw new IllegalArgumentException("所属计划不存在或不是进行中状态");
        }
        String siteId = requireSite(scheduleId, req.getSiteId());
        String deviceId = requireDevice(siteId, req.getDeviceId());
        String result = requireResult(req.getResult());
        String content = normalizeContent(req.getContent());
        Double cost = normalizeCost(req.getCost());
        String userId = requireUser(req.getUserId(), operatorId);
        List<String> photoIds = normalizePhotoIds(req.getPhotoIds());
        if (photoIds.isEmpty()) {
            throw new IllegalArgumentException("现场照片不能为空（至少上传 1 张）");
        }
        List<PatrolRecordPhotoFileVO> existing = mapper.selectPhotoRowsByRecord(id);
        Map<String, PatrolRecordPhotoFileVO> existingMap = new LinkedHashMap<>();
        for (PatrolRecordPhotoFileVO row : existing) {
            existingMap.put(row.getId(), row);
        }
        List<String> newPhotoIds = new ArrayList<>();
        for (String photoId : photoIds) {
            if (!existingMap.containsKey(photoId)) {
                newPhotoIds.add(photoId);
            }
        }
        precheckNewPhotos(newPhotoIds, operatorId);

        LocalDateTime now = LocalDateTime.now();
        int updated = mapper.updateDraft(id, operatorId, now, time, scheduleId, siteId, deviceId,
                userId, result, content, cost);
        if (updated == 0) {
            throw new IllegalArgumentException("巡检记录不存在或已提交");
        }
        List<String> removedPaths = new ArrayList<>();
        for (PatrolRecordPhotoFileVO old : existing) {
            if (!photoIds.contains(old.getId())) {
                mapper.deleteBoundPhoto(old.getId(), id);
                removedPaths.add(old.getFilePath());
            }
        }
        for (int i = 0; i < photoIds.size(); i++) {
            String photoId = photoIds.get(i);
            if (existingMap.containsKey(photoId)) {
                mapper.updatePhotoOrder(photoId, id, i + 1, operatorId, now);
            } else if (mapper.bindPhoto(photoId, id, i + 1, operatorId, now) == 0) {
                throw new IllegalArgumentException("现场照片状态已变化，请重新上传后保存");
            }
        }
        Path base = LocalFileStorageUtils.root(fileDir);
        for (String path : removedPaths) {
            LocalFileStorageUtils.deleteQuietly(base, path);
        }
        log.info("[巡检记录] 编辑记录：id={}, 计划={}, 站点={}, 设备={}, 照片={} 张（移除 {} 张）, 操作人={}",
                id, scheduleId, siteId, deviceId, photoIds.size(), removedPaths.size(), operatorId);
        return id;
    }

    /** 删除记录（仅草稿）：记录下已上报问题时拒绝；照片行与文件一并清理。 */
    @Transactional(rollbackFor = Exception.class)
    public void remove(String id) {
        String key = CheckUtils.trim(id);
        if (key == null) {
            throw new IllegalArgumentException("巡检记录 id 不能为空");
        }
        PatrolRecordDetailVO record = mapper.selectDetail(key);
        if (record == null) {
            throw new IllegalArgumentException("巡检记录不存在：" + key);
        }
        if (!STATUS_DRAFT.equals(record.getStatusCode())) {
            throw new IllegalArgumentException("已提交的巡检记录不可删除");
        }
        if (mapper.countIssueByRecord(key) > 0) {
            throw new IllegalArgumentException("该巡检记录下已存在问题记录，不能删除");
        }
        List<PatrolRecordPhotoFileVO> photos = mapper.selectPhotoRowsByRecord(key);
        if (mapper.deleteDraft(key) == 0) {
            throw new IllegalArgumentException("巡检记录不存在或已提交");
        }
        for (PatrolRecordPhotoFileVO photo : photos) {
            mapper.deleteBoundPhoto(photo.getId(), key);
        }
        Path base = LocalFileStorageUtils.root(fileDir);
        for (PatrolRecordPhotoFileVO photo : photos) {
            LocalFileStorageUtils.deleteQuietly(base, photo.getFilePath());
        }
        log.info("[巡检记录] 删除记录：id={}, 计划={}, 照片={} 张", key, record.getScheduleId(), photos.size());
    }

    /** 提交记录（草稿 → 已提交）：脏草稿（缺站点/设备/结果/照片）拒绝提交，先编辑补全；
     *  提交成功后兜底清理同计划其余空白占位草稿，避免残留「正式 + 空草稿」。 */
    @Transactional(rollbackFor = Exception.class)
    public void submit(String id) {
        String key = CheckUtils.trim(id);
        if (key == null) {
            throw new IllegalArgumentException("巡检记录 id 不能为空");
        }
        PatrolRecordDetailVO record = mapper.selectDetail(key);
        if (record == null) {
            throw new IllegalArgumentException("巡检记录不存在：" + key);
        }
        if (!STATUS_DRAFT.equals(record.getStatusCode())) {
            throw new IllegalArgumentException("巡检记录已提交，请勿重复提交");
        }
        if (record.getSiteId() == null) {
            throw new IllegalArgumentException("巡检记录缺少站点，请先编辑补全后再提交");
        }
        if (record.getDeviceId() == null) {
            throw new IllegalArgumentException("巡检对象不能为空，请先编辑补全后再提交");
        }
        if (record.getResultCode() == null) {
            throw new IllegalArgumentException("巡检结果不能为空，请先编辑补全后再提交");
        }
        if (mapper.countPhotosByRecord(key) == 0) {
            throw new IllegalArgumentException("现场照片不能为空，请先编辑补全后再提交");
        }
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        if (mapper.submit(key, operatorId, LocalDateTime.now()) == 0) {
            throw new IllegalArgumentException("巡检记录不存在或已提交");
        }
        int cleaned = record.getScheduleId() == null ? 0 : mapper.deleteBlankDrafts(record.getScheduleId(), key);
        log.info("[巡检记录] 提交记录：id={}, 操作人={}, 清理同计划空草稿={} 条", key, operatorId, cleaned);
    }

    // ==================== 现场照片 ====================

    /** 上传现场照片：图片白名单 + 落盘（patrol-record/yyyy-MM/uuid_原名） + 写照片行（待保存绑定）。 */
    public PatrolRecordPhotoItemVO uploadPhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > PHOTO_MAX_SIZE) {
            throw new IllegalArgumentException("单张照片不能超过 5MB");
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
        row.put("recordId", null);
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
            LocalFileStorageUtils.deleteQuietly(base, relPath);
            throw e;
        }
        log.info("[巡检记录] 照片上传成功：{}（{} 字节），id={}，路径={}，上传人={}",
                originalName, file.getSize(), photoId, relPath, operatorId);
        PatrolRecordPhotoItemVO vo = new PatrolRecordPhotoItemVO();
        vo.setId(photoId);
        vo.setName(originalName);
        vo.setSize(file.getSize());
        vo.setExt(ext);
        vo.setUrl("/patrol-record/photo/" + photoId);
        return vo;
    }

    /** 读取照片字节（预览；路径穿越校验由存储工具兜底）。 */
    public PhotoContent readPhoto(String id) {
        String photoId = CheckUtils.trim(id);
        if (photoId == null) {
            throw new IllegalArgumentException("照片 id 不能为空");
        }
        PatrolRecordPhotoFileVO photo = mapper.selectPhoto(photoId);
        if (photo == null) {
            throw new IllegalArgumentException("照片不存在或已删除");
        }
        String relPath = CheckUtils.trim(photo.getFilePath());
        if (relPath == null) {
            throw new IllegalArgumentException("照片路径缺失");
        }
        byte[] bytes = LocalFileStorageUtils.readBytes(LocalFileStorageUtils.root(fileDir), relPath);
        String ext = photo.getFileExt() == null ? LocalFileStorageUtils.extOf(photo.getFileName()) : photo.getFileExt();
        return new PhotoContent(bytes, ext == null ? "" : ext.toLowerCase(Locale.ROOT));
    }

    /** 移除未保存的照片（仅本人上传、尚未绑定记录），删行 + 物理删文件。 */
    public void removePhoto(String id) {
        String photoId = CheckUtils.trim(id);
        if (photoId == null) {
            throw new IllegalArgumentException("照片 id 不能为空");
        }
        PatrolRecordPhotoFileVO photo = mapper.selectPhoto(photoId);
        if (photo == null) {
            throw new IllegalArgumentException("照片不存在或已删除");
        }
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        if (mapper.deleteUnboundPhoto(photoId, operatorId) == 0) {
            throw new IllegalArgumentException("照片已保存到巡检记录，不能移除");
        }
        LocalFileStorageUtils.deleteQuietly(LocalFileStorageUtils.root(fileDir), photo.getFilePath());
        log.info("[巡检记录] 照片已移除：{}（{}），操作人={}", photo.getFileName(), photoId, operatorId);
    }

    // ==================== 校验 / 工具 ====================

    /** 计划校验：存在 +（新增时）必须已发布 #2#。 */
    private String requireSchedule(String raw, boolean mustPublished) {
        String scheduleId = CheckUtils.trim(raw);
        if (scheduleId == null) {
            throw new IllegalArgumentException("所属计划不能为空");
        }
        if (mapper.countSchedule(scheduleId) == 0) {
            throw new IllegalArgumentException("所属计划不存在：" + scheduleId);
        }
        if (mustPublished && mapper.countPublishedSchedule(scheduleId) == 0) {
            throw new IllegalArgumentException("所属计划不存在或不是进行中状态");
        }
        return scheduleId;
    }

    /** 站点校验：存在 + 在计划巡查范围内（计划未配范围时不限）。 */
    private String requireSite(String scheduleId, String raw) {
        String siteId = CheckUtils.trim(raw);
        if (siteId == null) {
            throw new IllegalArgumentException("巡检站点不能为空");
        }
        if (mapper.countSite(siteId) == 0) {
            throw new IllegalArgumentException("巡检站点不存在：" + siteId);
        }
        List<PatrolSiteVO> scope = planMapper.selectPlanSites(scheduleId);
        if (scope != null && !scope.isEmpty()
                && scope.stream().noneMatch(s -> siteId.equals(s.getId()))) {
            throw new IllegalArgumentException("巡检站点不在该计划的巡查范围内");
        }
        return siteId;
    }

    /** 设备校验：必填 + 属于站点。 */
    private String requireDevice(String siteId, String raw) {
        String deviceId = CheckUtils.trim(raw);
        if (deviceId == null) {
            throw new IllegalArgumentException("巡检对象不能为空");
        }
        if (mapper.countDeviceInSite(siteId, deviceId) == 0) {
            throw new IllegalArgumentException("巡检对象不属于所选站点");
        }
        return deviceId;
    }

    /** 巡检结果校验：必填 + 编码（#2#~#6#）。 */
    private String requireResult(String raw) {
        String value = CheckUtils.trim(raw);
        if (value == null) {
            throw new IllegalArgumentException("巡检结果不能为空");
        }
        String code = DictUtils.resolveCode(RESULT_LABELS, value);
        if (!RESULT_LABELS.containsKey(code)) {
            throw new IllegalArgumentException("巡检结果无效（应为 正常/异常/隐患/缺陷/故障）");
        }
        return code;
    }

    /** 巡检人员校验：可空（默认当前登录人），填了必须存在。 */
    private String requireUser(String raw, String operatorId) {
        String userId = CheckUtils.trim(raw);
        if (userId == null) {
            userId = operatorId;
        }
        if (mapper.countUser(userId) == 0) {
            throw new IllegalArgumentException("巡检人员不存在：" + userId);
        }
        return userId;
    }

    /** 巡检说明：可空，长度 ≤1000。 */
    private String normalizeContent(String raw) {
        String content = CheckUtils.trim(raw);
        if (content != null && content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("巡检说明超长（≤" + CONTENT_MAX_LENGTH + " 字）");
        }
        return content;
    }

    /** 成本金额：可空，非负，保留 2 位小数。 */
    private static Double normalizeCost(Double cost) {
        if (cost == null) {
            return null;
        }
        if (cost < 0) {
            throw new IllegalArgumentException("成本金额不能为负");
        }
        return BigDecimal.valueOf(cost).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** 巡检时间解析：兼容 yyyy-MM-dd HH:mm / yyyy-MM-ddTHH:mm / 带秒。 */
    private static LocalDateTime parseTime(String raw) {
        String value = CheckUtils.trim(raw);
        if (value == null) {
            throw new IllegalArgumentException("巡检时间不能为空");
        }
        value = value.replace('T', ' ');
        if (value.length() == 16) {
            value = value + ":00";
        }
        try {
            return LocalDateTime.parse(value, TIME_INPUT_FORMAT);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("巡检时间格式不正确（yyyy-MM-dd HH:mm）");
        }
    }

    /** 日期筛选参数：可空，格式 yyyy-MM-dd。 */
    private static String normalizeDate(String raw, String name) {
        String value = CheckUtils.trim(raw);
        if (value == null) {
            return null;
        }
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException(name + "格式不正确（yyyy-MM-dd）");
        }
        return value;
    }

    /** 照片 id 归一：去空、去重（保持顺序），并校验数量上限。 */
    private static List<String> normalizePhotoIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String v : raw) {
            String id = CheckUtils.trim(v);
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.size() > PHOTO_MAX_COUNT) {
            throw new IllegalArgumentException("现场照片最多上传 " + PHOTO_MAX_COUNT + " 张");
        }
        return new ArrayList<>(unique);
    }

    /** 新照片预校验：存在、属于当前上传人且未绑定过其他记录。 */
    private void precheckNewPhotos(List<String> photoIds, String operatorId) {
        for (String photoId : photoIds) {
            PatrolRecordPhotoFileVO photo = mapper.selectPhoto(photoId);
            if (photo == null) {
                throw new IllegalArgumentException("现场照片不存在或已失效，请重新上传");
            }
            if (!operatorId.equals(photo.getCreatedBy()) || photo.getRecordId() != null) {
                throw new IllegalArgumentException("现场照片不可用（非本人上传或已保存），请重新上传");
            }
        }
    }

    /** 照片字节与扩展名（Controller 组装 Content-Type）。 */
    public static class PhotoContent {

        private final byte[] content;
        private final String ext;

        public PhotoContent(byte[] content, String ext) {
            this.content = content;
            this.ext = ext;
        }

        public byte[] getContent() {
            return content;
        }

        public String getExt() {
            return ext;
        }
    }
}
