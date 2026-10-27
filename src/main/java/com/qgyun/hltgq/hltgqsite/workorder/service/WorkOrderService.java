package com.qgyun.hltgq.hltgqsite.workorder.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.mapper.ArchiveLookupMapper;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DictUtils;
import com.qgyun.hltgq.hltgqsite.model.util.LocalFileStorageUtils;
import com.qgyun.hltgq.hltgqsite.model.util.OperatorUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.model.vo.FileDownloadVO;
import com.qgyun.hltgq.hltgqsite.patrol.mapper.PatrolIssueMapper;
import com.qgyun.hltgq.hltgqsite.workorder.mapper.WorkOrderMapper;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderAssignRequest;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderAttachmentVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderCreateVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderDetailVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderListVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderOrgVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderResultRequest;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderRowVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderSaveRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单管理服务（/work-order/*，work-order-list.html + work-order-export.html）。
 *
 * <p>状态机（与平台表单一致）：#1# 待指派 →（指派）#2# 处理中 →（提交验收）#3# 已关闭；
 * 待指派还可（取消）#4# 已取消；#iizl# 已逾期为存量状态。
 * <ul>
 *   <li>编辑/删除/指派/取消：仅待指派 #1#（条件更新，状态已变返回 400 提示刷新）</li>
 *   <li>填写处理结果：仅处理中 #2#，写 result + pyxcen（可重复保存，不改状态）</li>
 *   <li>提交验收：仅处理中 #2# 且已填写处理结果 → 已关闭 #3#；同步把关联问题（#3# 已转工单）置为已关闭 #4#</li>
 *   <li>删除/取消时，关联问题（问题表 work_order_id = 本工单且 #3# 已转工单）回退为 #1# 待处理</li>
 * </ul>
 *
 * <p>关联问题：工单表无 azgquf 物理列，统一由问题记录表 work_order_id 反查；
 * 工单附件/图片（file/xdkvyp）无物理列，处理结果附件改用独立表
 * t_auto_hltgq_water_work_order_attachment（本地存储 file-dir/work-order/{yyyy-MM}/{uuid}_{原名}）：
 * 仅收图片（jpg/jpeg/png/gif/bmp/webp），单张 ≤5MB，每张工单最多 5 张；
 * 上传时行 order_id 为空，保存处理结果时回填归属，不在保留集合内的行与文件一并清理。
 */
@Service
public class WorkOrderService {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderService.class);

    /** 附件存储根目录名（file-dir 下的工单专属目录） */
    private static final String ATTACH_DIR = "work-order";

    /** 处理结果附件扩展名白名单（只收图片，不收 PDF/Office） */
    private static final List<String> ATTACH_IMAGE_EXTENSIONS =
            Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp");

    /** 处理结果附件单张大小上限（5MB；全局 multipart 20MB 仅作兜底） */
    private static final long ATTACH_MAX_SIZE = 5L * 1024 * 1024;

    /** 处理结果附件数量上限（每张工单最多 5 张） */
    private static final int ATTACH_MAX_COUNT = 5;

    /** 工单标题长度上限 */
    private static final int TITLE_MAX_LENGTH = 200;

    /** 内容描述长度上限（平台字段 0~500） */
    private static final int CONTENT_MAX_LENGTH = 500;

    /** 处理结果长度上限 */
    private static final int RESULT_MAX_LENGTH = 1000;

    /** 状态：待指派 */
    private static final String STATUS_ASSIGNING = "#1#";

    /** 工单编号前缀（GD + 日期；与存量 19 位编号同形态） */
    private static final String CODE_PREFIX = "GD";

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_PART = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter TIME_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 工单类型编码 → 名称（qjulvf，9 档） */
    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>();

    /** 工单状态编码 → 名称（status，5 档，含 #iizl# 已逾期） */
    private static final Map<String, String> STATUS_LABELS = new LinkedHashMap<>();

    /** 旧筛选词兼容（导出页历史文案「待处理」即待指派 #1#） */
    private static final Map<String, String> STATUS_ALIASES = new LinkedHashMap<>();

    static {
        TYPE_LABELS.put("#1#", "日常巡检工单");
        TYPE_LABELS.put("#yjje#", "设备维护工单");
        TYPE_LABELS.put("#hxqm#", "设备故障抢修工单");
        TYPE_LABELS.put("#zopb#", "水工建筑物维护工单");
        TYPE_LABELS.put("#kjua#", "渠道及附属设施养护工单");
        TYPE_LABELS.put("#enak#", "计量与监测维护工单");
        TYPE_LABELS.put("#bgtg#", "信息化与通信维护工单");
        TYPE_LABELS.put("#pfqj#", "安全隐患整改工单");
        TYPE_LABELS.put("#uvxb#", "应急处置工单");

        STATUS_LABELS.put("#1#", "待指派");
        STATUS_LABELS.put("#2#", "处理中");
        STATUS_LABELS.put("#3#", "已关闭");
        STATUS_LABELS.put("#4#", "已取消");
        STATUS_LABELS.put("#iizl#", "已逾期");

        STATUS_ALIASES.put("待处理", "#1#");
    }

    @Autowired
    private WorkOrderMapper mapper;

    /** 站点/设备/人员/部门档案存在性校验（跨模块唯一实现） */
    @Autowired
    private ArchiveLookupMapper lookupMapper;

    /** 工单取消/删除后回退关联问题 */
    @Autowired
    private PatrolIssueMapper issueMapper;

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    @Value("${hltgq.corp-code}")
    private String corpCode;

    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    /** 附件本地存储根目录（宿主机挂载 ./file-dir；与用水计划/库存盘点同模式） */
    @Value("${hltgq.file-dir:./file-dir}")
    private String fileDir;

    // ==================== 字典（跨模块展示复用） ====================

    /** 工单类型编码 → 名称（null/未知原样返回）。 */
    public static String typeLabel(String code) {
        return DictUtils.label(TYPE_LABELS, code);
    }

    /** 工单状态编码 → 名称（null/未知原样返回）。 */
    public static String statusLabel(String code) {
        return DictUtils.label(STATUS_LABELS, code);
    }

    /** 工单类型是否合法（9 档）。 */
    public static boolean isValidType(String code) {
        return code != null && TYPE_LABELS.containsKey(code);
    }

    // ==================== 列表 / 详情 ====================

    /**
     * 工单列表（导出页，裸 List）。
     *
     * @param site      所属站点：站点 id / 编号 / 名称，可选
     * @param startDate 处理时间起（yyyy-MM-dd，含当日），可选
     * @param endDate   处理时间止（yyyy-MM-dd，含当日），可选
     * @param status    状态：待指派/处理中/已关闭/已取消/已逾期 或编码，可选
     */
    public List<WorkOrderListVO> list(String site, LocalDate startDate, LocalDate endDate, String status) {
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endTime = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        List<WorkOrderListVO> rows = mapper.selectWorkOrderList(CheckUtils.trim(site), startTime, endTime,
                resolveStatusCode(status));
        for (WorkOrderListVO row : rows) {
            row.setType(typeLabel(row.getTypeCode()));
            row.setStatus(statusLabel(row.getStatusCode()));
        }
        return rows;
    }

    /** 工单分页（站点 + 处理时间区间 + 状态）；状态已按处理时间排序口径过滤。 */
    public Page<WorkOrderRowVO> page(String site, LocalDate startDate, LocalDate endDate, String status,
                                     long page, long size) {
        long p = Math.max(1, page);
        long s = Math.min(Math.max(1, size), 100);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("处理时间起不能晚于处理时间止");
        }
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endTime = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        String statusCode = resolveStatusCode(status);
        Page<WorkOrderRowVO> out = new Page<>(p, s);
        long total = mapper.countPage(CheckUtils.trim(site), startTime, endTime, statusCode);
        out.setTotal(total);
        if (total == 0) {
            out.setRecords(Collections.emptyList());
            return out;
        }
        List<WorkOrderRowVO> rows = mapper.selectPage(CheckUtils.trim(site), startTime, endTime, statusCode,
                s, (p - 1) * s);
        for (WorkOrderRowVO row : rows) {
            row.setType(typeLabel(row.getTypeCode()));
            row.setStatus(statusLabel(row.getStatusCode()));
        }
        out.setRecords(rows);
        return out;
    }

    /** 工单详情（查看弹窗 / 编辑回填）：类型与状态翻译为中文，附处理结果附件清单。 */
    public WorkOrderDetailVO detail(String id) {
        String key = CheckUtils.trim(id);
        if (key == null) {
            throw new IllegalArgumentException("工单 id 不能为空");
        }
        WorkOrderDetailVO vo = mapper.selectDetail(key);
        if (vo == null) {
            throw new IllegalArgumentException("工单不存在：" + key);
        }
        vo.setType(typeLabel(vo.getTypeCode()));
        vo.setStatus(statusLabel(vo.getStatusCode()));
        vo.setAttachments(mapper.selectAttachments(key));
        return vo;
    }

    /** 负责部门候选（工单编辑/指派页下拉）。 */
    public List<WorkOrderOrgVO> orgs() {
        return mapper.selectOrgOptions();
    }

    // ==================== 编辑 / 删除 ====================

    /** 编辑工单（仅待指派）：全字段覆盖；设备须属于所选站点，部门/人员须存在。 */
    public void update(WorkOrderSaveRequest req) {
        SaveData d = validateSave(req);
        if (d.id == null) {
            throw new IllegalArgumentException("工单 id 不能为空");
        }
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        int rows = mapper.updateWorkOrder(d.id, d.title, d.typeCode, d.content, d.siteId, d.deviceId,
                d.orgId, d.userId, d.time, operatorId, LocalDateTime.now());
        if (rows == 0) {
            throw new IllegalArgumentException("仅待指派的工单可编辑，状态已变化，请刷新后重试");
        }
        log.info("[工单] 编辑：id={}, 类型={}, 站点={}, 操作人={}", d.id, d.typeCode, d.siteId, operatorId);
    }

    /** 新增工单（手工创建）：状态默认待指派，编号自动生成。 */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderCreateVO create(WorkOrderSaveRequest req) {
        SaveData d = validateSave(req);
        return insertOrder(d.title, d.typeCode, d.content, d.siteId, d.deviceId, d.orgId, d.userId, d.time,
                "新增");
    }

    /** 工单编辑/新增的公共校验（长度 + 站点/设备归属/部门/人员存在性 + 时间格式）。 */
    private SaveData validateSave(WorkOrderSaveRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        SaveData d = new SaveData();
        d.id = CheckUtils.trim(req.getId());
        d.title = CheckUtils.requireText(req.getTitle(), "工单标题不能为空");
        CheckUtils.maxLength(d.title, TITLE_MAX_LENGTH, "工单标题");
        d.typeCode = CheckUtils.trim(req.getType());
        if (!isValidType(d.typeCode)) {
            throw new IllegalArgumentException("工单类型无效，请重新选择");
        }
        d.content = CheckUtils.maxLength(CheckUtils.trim(req.getContent()), CONTENT_MAX_LENGTH, "内容描述");
        d.siteId = CheckUtils.requireText(req.getSiteId(), "所属站点不能为空");
        if (lookupMapper.countSite(d.siteId) == 0) {
            throw new IllegalArgumentException("所属站点不存在：" + d.siteId);
        }
        d.deviceId = CheckUtils.trim(req.getDeviceId());
        if (d.deviceId != null && lookupMapper.countDeviceInSite(d.siteId, d.deviceId) == 0) {
            throw new IllegalArgumentException("所选设备不属于该站点");
        }
        d.orgId = CheckUtils.trim(req.getOrgId());
        if (d.orgId != null && lookupMapper.countOrg(d.orgId) == 0) {
            throw new IllegalArgumentException("负责部门不存在：" + d.orgId);
        }
        d.userId = CheckUtils.trim(req.getUserId());
        if (d.userId != null && lookupMapper.countUser(d.userId) == 0) {
            throw new IllegalArgumentException("处理人员不存在：" + d.userId);
        }
        d.time = parseTime(req.getTime(), "要求完成时间");
        return d;
    }

    /**
     * 删除工单（仅待指派）：删除后把关联问题回退为待处理
     * （问题表 work_order_id 清空、handle_status/status 复位），整单事务；
     * 若存在处理结果附件，行随事务删除、文件在提交后清理。
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(String id) {
        String key = CheckUtils.requireText(id, "工单 id 不能为空");
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();
        int rows = mapper.deleteWorkOrder(key);
        if (rows == 0) {
            throw new IllegalArgumentException("仅待指派的工单可删除，状态已变化，请刷新后重试");
        }
        List<String> removedPaths = new ArrayList<>();
        for (WorkOrderAttachmentVO att : mapper.selectAttachmentPaths(key)) {
            removedPaths.add(att.getFilePath());
        }
        int attachments = mapper.deleteAttachmentsByOrder(key);
        LocalFileStorageUtils.deleteAfterCommit(fileDirPath(), removedPaths);
        int reset = issueMapper.resetIssuesByWorkOrder(key, operatorId, now);
        log.info("[工单] 删除：id={}, 回退关联问题 {} 条, 清理附件 {} 个, 操作人={}",
                key, reset, attachments, operatorId);
    }

    // ==================== 状态流转 ====================

    /** 指派工单：处理人员必填，负责部门/要求完成时间可选；待指派 → 处理中。 */
    public void assign(WorkOrderAssignRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String id = CheckUtils.requireText(req.getId(), "工单 id 不能为空");
        String userId = CheckUtils.requireText(req.getUserId(), "请选择处理人员");
        if (lookupMapper.countUser(userId) == 0) {
            throw new IllegalArgumentException("处理人员不存在：" + userId);
        }
        String orgId = CheckUtils.trim(req.getOrgId());
        if (orgId != null && lookupMapper.countOrg(orgId) == 0) {
            throw new IllegalArgumentException("负责部门不存在：" + orgId);
        }
        LocalDateTime time = parseTime(req.getTime(), "要求完成时间");
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        int rows = mapper.assignWorkOrder(id, orgId, userId, time, operatorId, LocalDateTime.now());
        if (rows == 0) {
            throw new IllegalArgumentException("仅待指派的工单可指派，状态已变化，请刷新后重试");
        }
        log.info("[工单] 指派：id={}, 部门={}, 处理人={}, 要求完成时间={}, 操作人={}", id, orgId, userId, time, operatorId);
    }

    /**
     * 取消工单（仅待指派）：置已取消，并把关联问题回退为待处理（与删除同口径）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String id) {
        String key = CheckUtils.requireText(id, "工单 id 不能为空");
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();
        int rows = mapper.cancelWorkOrder(key, operatorId, now);
        if (rows == 0) {
            throw new IllegalArgumentException("仅待指派的工单可取消，状态已变化，请刷新后重试");
        }
        int reset = issueMapper.resetIssuesByWorkOrder(key, operatorId, now);
        log.info("[工单] 取消：id={}, 回退关联问题 {} 条, 操作人={}", key, reset, operatorId);
    }

    /**
     * 填写处理结果（仅处理中）：写 result 与处理时间 pyxcen，可重复保存，不改状态。
     * <p>附件：attachmentIds 为 null 表示不调整；非 null 时以集合为准
     * （新上传绑定本工单，已绑定本工单但不在集合内的行与文件一并清理）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveResult(WorkOrderResultRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String id = CheckUtils.requireText(req.getId(), "工单 id 不能为空");
        String result = CheckUtils.requireText(req.getResult(), "处理结果不能为空");
        CheckUtils.maxLength(result, RESULT_MAX_LENGTH, "处理结果");
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();
        int rows = mapper.saveResult(id, result, operatorId, now);
        if (rows == 0) {
            throw new IllegalArgumentException("仅处理中的工单可填写处理结果，状态已变化，请刷新后重试");
        }
        int kept = bindAttachments(id, req.getAttachmentIds(), now, operatorId);
        log.info("[工单] 填写处理结果：id={}, 长度={}, 附件={} 个, 操作人={}",
                id, result.length(), kept, operatorId);
    }

    /** 提交验收（仅处理中且已填写处理结果）：置已关闭，并同步关闭关联问题（验收闭环）。 */
    @Transactional(rollbackFor = Exception.class)
    public void submitAccept(String id) {
        String key = CheckUtils.requireText(id, "工单 id 不能为空");
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();
        int rows = mapper.submitAccept(key, operatorId, now);
        if (rows == 0) {
            throw new IllegalArgumentException("仅已填写处理结果的处理中工单可提交验收，状态已变化，请刷新后重试");
        }
        int issues = mapper.closeIssuesByWorkOrder(key, operatorId, now);
        log.info("[工单] 提交验收：id={}, 操作人={}, 同步关闭关联问题={} 条", key, operatorId, issues);
    }

    // ==================== 处理结果附件（上传 / 下载 / 移除） ====================

    /** 上传附件图片：图片白名单 + 单张大小上限 + 落盘（work-order/yyyy-MM/uuid_原名） + 行入库（order_id 为空待回填）。 */
    public WorkOrderAttachmentVO uploadAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > ATTACH_MAX_SIZE) {
            throw new IllegalArgumentException("单张图片不能超过 5MB");
        }
        String originalName = LocalFileStorageUtils.sanitizeFileName(file.getOriginalFilename());
        String ext = LocalFileStorageUtils.extOf(originalName);
        if (ext == null || !ATTACH_IMAGE_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("仅支持上传图片（jpg/jpeg/png/gif/bmp/webp）：" + originalName);
        }
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();
        String relPath = LocalFileStorageUtils.relPath(ATTACH_DIR, now, shortIdGenerator.nextUUID(null), originalName);
        LocalFileStorageUtils.write(fileDirPath(), relPath, file);

        String attachmentId = shortIdGenerator.nextUUID(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", attachmentId);
        row.put("order_id", null);
        row.put("file_name", originalName);
        row.put("file_path", relPath);
        row.put("file_size", file.getSize());
        row.put("file_ext", ext);
        row.put("now", now);
        row.put("operator", operatorId);
        try {
            mapper.insertAttachment(row);
        } catch (RuntimeException e) {
            // 行写入失败：清理已落盘文件，避免孤儿文件
            LocalFileStorageUtils.deleteQuietly(fileDirPath(), relPath);
            throw e;
        }
        WorkOrderAttachmentVO vo = new WorkOrderAttachmentVO();
        vo.setId(attachmentId);
        vo.setFileName(originalName);
        vo.setFileSize(file.getSize());
        vo.setFileExt(ext);
        log.info("[工单] 附件上传：id={}, 文件={}, 大小={}, 操作人={}",
                attachmentId, originalName, file.getSize(), operatorId);
        return vo;
    }

    /** 下载附件：路径穿越校验 + 文件读取（缺失提示已被清理）。 */
    public FileDownloadVO downloadAttachment(String id) {
        WorkOrderAttachmentVO att = mapper.selectAttachmentById(
                CheckUtils.requireText(id, "附件 id 不能为空"));
        if (att == null) {
            throw new IllegalArgumentException("附件不存在或已删除");
        }
        byte[] bytes = LocalFileStorageUtils.readBytes(fileDirPath(),
                CheckUtils.requireText(att.getFilePath(), "附件路径缺失"));
        FileDownloadVO vo = new FileDownloadVO();
        vo.setContent(bytes);
        vo.setFileName(att.getFileName());
        vo.setFileExt(att.getFileExt());
        return vo;
    }

    /** 移除附件（上传后取消清理 / 弹出窗内移除新上传）：原子删行（未归属守卫） + 物理删文件（best-effort）。 */
    public void removeAttachment(String id) {
        String key = CheckUtils.requireText(id, "附件 id 不能为空");
        WorkOrderAttachmentVO att = mapper.selectAttachmentById(key);
        if (att == null) {
            throw new IllegalArgumentException("附件不存在或已删除");
        }
        // 仅允许删除未归属附件：DELETE 带 order_id IS NULL 守卫，并发绑定后返回 0 行 → 拒绝移除且不删文件
        if (mapper.deleteUnboundAttachment(key) == 0) {
            throw new IllegalArgumentException("附件已保存到工单，不能直接移除");
        }
        LocalFileStorageUtils.deleteQuietly(fileDirPath(), att.getFilePath());
    }

    /**
     * 处理结果附件绑定/清理（保存处理结果时调用）：
     * attachmentIds 为 null → 不调整（兼容不带附件的旧调用）；
     * 非 null → 以集合为准：去重后不得超过 5 张，新上传的绑定本工单，
     * 现属本工单但不在集合内的删行（文件事务提交后删）。
     *
     * @return 保留附件个数（null 入参返回 -1，表示未调整）
     */
    private int bindAttachments(String orderId, List<String> attachmentIds, LocalDateTime now, String operator) {
        if (attachmentIds == null) {
            return -1;
        }
        List<String> keepIds = new ArrayList<>();
        for (String raw : attachmentIds) {
            String aid = CheckUtils.trim(raw);
            if (aid == null || keepIds.contains(aid)) {
                continue;
            }
            WorkOrderAttachmentVO att = mapper.selectAttachmentById(aid);
            if (att == null) {
                throw new IllegalArgumentException("附件不存在或已移除：" + aid);
            }
            if (att.getOrderId() != null && !att.getOrderId().equals(orderId)) {
                throw new IllegalArgumentException("附件已被其他工单占用：" + att.getFileName());
            }
            if (att.getOrderId() == null && mapper.bindAttachment(aid, orderId, operator, now) == 0) {
                throw new IllegalArgumentException("附件绑定失败，请刷新后重试：" + att.getFileName());
            }
            keepIds.add(aid);
            if (keepIds.size() > ATTACH_MAX_COUNT) {
                throw new IllegalArgumentException("处理结果附件最多上传 " + ATTACH_MAX_COUNT + " 张");
            }
        }
        List<String> removedPaths = new ArrayList<>();
        for (WorkOrderAttachmentVO att : mapper.selectAttachmentPaths(orderId)) {
            if (!keepIds.contains(att.getId()) && mapper.deleteAttachment(att.getId()) > 0) {
                removedPaths.add(att.getFilePath());
            }
        }
        LocalFileStorageUtils.deleteAfterCommit(fileDirPath(), removedPaths);
        return keepIds.size();
    }

    /** 附件存储根目录（绝对路径归一，用作穿越校验基准；文件操作见 LocalFileStorageUtils）。 */
    private Path fileDirPath() {
        return LocalFileStorageUtils.root(fileDir);
    }

    // ==================== 转工单（问题记录调用） ====================

    /**
     * 由问题记录创建工单（问题「转工单」）：状态 #1# 待指派，编号 GD+日期+时分秒+3 位流水。
     * <p>站点/设备取问题记录本身；设备档案已删除或换绑时降级为不关联设备并留日志。
     *
     * @return 新工单 id 与编号（调用方负责把问题记录置为已转工单并回写 work_order_id）
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrderCreateVO createFromIssue(String title, String typeCode, String content,
                                             String siteId, String deviceId, String orgId, String userId,
                                             LocalDateTime time) {
        String t = CheckUtils.requireText(title, "工单标题不能为空");
        CheckUtils.maxLength(t, TITLE_MAX_LENGTH, "工单标题");
        if (!isValidType(typeCode)) {
            throw new IllegalArgumentException("工单类型无效，请重新选择");
        }
        String c = CheckUtils.maxLength(CheckUtils.trim(content), CONTENT_MAX_LENGTH, "内容描述");
        String site = CheckUtils.requireText(siteId, "工单所属站点不能为空");
        if (lookupMapper.countSite(site) == 0) {
            throw new IllegalArgumentException("所属站点不存在：" + site);
        }
        String dev = CheckUtils.trim(deviceId);
        if (dev != null && lookupMapper.countDeviceInSite(site, dev) == 0) {
            log.warn("[工单] 问题关联设备已删除或换绑，转工单不关联设备：deviceId={}, site={}", dev, site);
            dev = null;
        }
        String org = CheckUtils.trim(orgId);
        if (org != null && lookupMapper.countOrg(org) == 0) {
            throw new IllegalArgumentException("负责部门不存在：" + org);
        }
        String user = CheckUtils.trim(userId);
        if (user != null && lookupMapper.countUser(user) == 0) {
            throw new IllegalArgumentException("处理人员不存在：" + user);
        }
        return insertOrder(t, typeCode, c, site, dev, org, user, time, "转工单");
    }

    // ==================== 工具 ====================

    /** 新建工单公共落库：状态 #1# 待指派、编号自动生成；scene 仅用于日志。 */
    private WorkOrderCreateVO insertOrder(String title, String typeCode, String content, String siteId,
                                          String deviceId, String orgId, String userId, LocalDateTime time,
                                          String scene) {
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();
        String id = shortIdGenerator.nextUUID(null);
        String code = generateCode(now);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("corpCode", corpCode);
        row.put("now", now);
        row.put("operatorId", operatorId);
        row.put("code", code);
        row.put("title", title);
        row.put("typeCode", typeCode);
        row.put("content", content);
        row.put("siteId", siteId);
        row.put("deviceId", deviceId);
        row.put("orgId", orgId);
        row.put("userId", userId);
        row.put("time", time);
        row.put("status", STATUS_ASSIGNING);
        mapper.insertWorkOrder(row);
        log.info("[工单] {}成功：id={}, code={}, 标题={}, 类型={}, 站点={}, 设备={}, 操作人={}",
                scene, id, code, title, typeCode, siteId, deviceId, operatorId);
        WorkOrderCreateVO vo = new WorkOrderCreateVO();
        vo.setId(id);
        vo.setCode(code);
        vo.setTitle(title);
        return vo;
    }

    /** 工单编号：GD + yyyyMMdd + HHmmss + 3 位流水（19 位，与存量同形态）；撞号顺延。 */
    private String generateCode(LocalDateTime now) {
        String base = CODE_PREFIX + now.format(DATE_PART) + now.format(TIME_PART);
        for (int seq = 1; seq <= 999; seq++) {
            String candidate = base + String.format("%03d", seq);
            if (mapper.countByCode(candidate) == 0) {
                return candidate;
            }
        }
        throw new IllegalStateException("工单编号生成失败，请重试");
    }

    /** 筛选状态归一：旧词「待处理」→ #1#；中文/编码均兼容；未知原样下传。 */
    private static String resolveStatusCode(String status) {
        String v = CheckUtils.trim(status);
        if (v == null) {
            return null;
        }
        String alias = STATUS_ALIASES.get(v);
        return alias != null ? alias : DictUtils.resolveCode(STATUS_LABELS, v);
    }

    /** 日期时间入参解析（yyyy-MM-dd HH:mm 或 yyyy-MM-dd HH:mm:ss，可空）。 */
    public static LocalDateTime parseTime(String raw, String label) {
        String v = CheckUtils.trim(raw);
        if (v == null) {
            return null;
        }
        try {
            return v.length() > 16
                    ? LocalDateTime.parse(v, TIME_SECONDS)
                    : LocalDateTime.parse(v, TIME_MINUTES);
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "格式应为 yyyy-MM-dd HH:mm 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    /** 编辑/新增校验后的字段集（新增时 id 为 null）。 */
    private static class SaveData {
        private String id;
        private String title;
        private String typeCode;
        private String content;
        private String siteId;
        private String deviceId;
        private String orgId;
        private String userId;
        private LocalDateTime time;
    }
}
