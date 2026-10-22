package com.qgyun.hltgq.hltgqsite.patrol.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.entity.PatrolSchedule;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DictUtils;
import com.qgyun.hltgq.hltgqsite.model.util.OperatorUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.patrol.mapper.PatrolPlanMapper;
import com.qgyun.hltgq.hltgqsite.patrol.mapper.PatrolRecordMapper;
import com.qgyun.hltgq.hltgqsite.patrol.mapper.PatrolScheduleMapper;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolOptionVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordPhotoFileVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordSaveVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolScheduleDetailVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolScheduleSaveVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolScheduleVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolSiteVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 巡检计划管理服务（列表页 / 新增页共用接口）。
 *
 * <p>数据口径：
 * <ul>
 *   <li>计划主表 t_auto_hltgq_water_patrol_schedule；巡检人员为<b>主表 "user" 单选列</b>（2026-10 由多选改造），
 *       存量数据的旧多选关系表 t_auto_hltgq_knc3g_nlbdju_user_rel 仅作展示回退，新写入不再使用</li>
 *   <li>巡查范围写关系表 t_auto_hltgq_knc3g_nlbdju_tkwovk_rel（多选不改造）</li>
 *   <li>巡检记录 t_auto_hltgq_water_inspection_record，巡检对象为动态列 "_d_10497_device"</li>
 * </ul>
 *
 * <p>状态流转：新增/导入落 #1# 草稿 → 发布后存储 #2#（已发布），列表/详情按<b>巡检记录</b>
 * 动态推导展示态（已有巡检记录 #2# 进行中、尚无记录 #5# 未开始）；
 * 完成 → #3# 已完成；取消 → #4# 已取消（人工终态优先，不再推导）；
 * 批量删除在存在巡检记录时拒绝（保护记录数据）。
 */
@Service
public class PatrolPlanService {

    private static final Logger log = LoggerFactory.getLogger(PatrolPlanService.class);

    /** 单页条数上限 */
    private static final long MAX_PAGE_SIZE = 100;

    /** 巡查内容长度上限（平台字段定义 ≤1000） */
    private static final int CONTENT_MAX_LENGTH = 1000;

    /** 计划编号前缀（与导入自动编号同形态） */
    private static final String CODE_PREFIX = "XCJH";

    /** 新增计划默认状态：草稿（发布后才按巡检进度流转） */
    private static final String DEFAULT_PLAN_STATUS = "#1#";

    /** 已提交状态编码（需校验现场照片） */
    private static final String RECORD_STATUS_SUBMITTED = "#2#";

    /** 巡检记录默认状态：已提交（页面可改草稿） */
    private static final String DEFAULT_RECORD_STATUS = RECORD_STATUS_SUBMITTED;

    /** 现场照片上限（与巡检记录页一致：最多 3 张） */
    private static final int PHOTO_MAX_COUNT = 3;

    /** 计划状态编码 → 名称（选项组 WWxK2R0AQzJDaDHdYP7） */
    private static final Map<String, String> STATUS_LABELS = new LinkedHashMap<>();
    /** 计划类型编码 → 名称（选项组 LV9fsnKRCbmPOaL2Hxu） */
    private static final Map<String, String> PLAN_TYPE_LABELS = new LinkedHashMap<>();
    /** 巡检结果编码 → 名称（选项组 IYBBDt3IqXJL71kqrYE） */
    private static final Map<String, String> RESULT_LABELS = new LinkedHashMap<>();
    /** 巡检记录状态编码 → 名称（选项组 mITE3MttOBlt5Hb83Yw） */
    private static final Map<String, String> RECORD_STATUS_LABELS = new LinkedHashMap<>();

    /** 新增计划：计划类型填写值 → 落库编码 */
    private static final Map<String, String> PLAN_TYPE_CODES = new LinkedHashMap<>();
    /** 添加记录：巡检结果填写值 → 落库编码 */
    private static final Map<String, String> RESULT_CODES = new LinkedHashMap<>();
    /** 添加记录：状态填写值 → 落库编码 */
    private static final Map<String, String> RECORD_STATUS_CODES = new LinkedHashMap<>();

    static {
        STATUS_LABELS.put("#1#", "草稿");
        STATUS_LABELS.put("#5#", "未开始");
        STATUS_LABELS.put("#2#", "进行中");
        STATUS_LABELS.put("#3#", "已完成");
        STATUS_LABELS.put("#4#", "已取消");

        PLAN_TYPE_LABELS.put("#1#", "年度计划");
        PLAN_TYPE_LABELS.put("#wavn#", "年度计划");
        PLAN_TYPE_LABELS.put("#zjgg#", "月度计划");

        RESULT_LABELS.put("#2#", "正常");
        RESULT_LABELS.put("#3#", "异常");
        RESULT_LABELS.put("#4#", "隐患");
        RESULT_LABELS.put("#5#", "缺陷");
        RESULT_LABELS.put("#6#", "故障");

        RECORD_STATUS_LABELS.put("#1#", "草稿");
        RECORD_STATUS_LABELS.put("#2#", "已提交");

        PLAN_TYPE_CODES.put("年度计划", "#1#");
        PLAN_TYPE_CODES.put("月度计划", "#zjgg#");
        PLAN_TYPE_CODES.put("#1#", "#1#");
        PLAN_TYPE_CODES.put("#wavn#", "#1#");
        PLAN_TYPE_CODES.put("#zjgg#", "#zjgg#");

        RESULT_CODES.put("正常", "#2#");
        RESULT_CODES.put("异常", "#3#");
        RESULT_CODES.put("隐患", "#4#");
        RESULT_CODES.put("缺陷", "#5#");
        RESULT_CODES.put("故障", "#6#");
        RESULT_CODES.put("#2#", "#2#");
        RESULT_CODES.put("#3#", "#3#");
        RESULT_CODES.put("#4#", "#4#");
        RESULT_CODES.put("#5#", "#5#");
        RESULT_CODES.put("#6#", "#6#");

        RECORD_STATUS_CODES.put("草稿", "#1#");
        RECORD_STATUS_CODES.put("已提交", "#2#");
        RECORD_STATUS_CODES.put("#1#", "#1#");
        RECORD_STATUS_CODES.put("#2#", "#2#");
    }

    @Autowired
    private PatrolPlanMapper mapper;

    @Autowired
    private PatrolScheduleMapper scheduleMapper;

    /** 添加记录时绑定现场照片（复用巡检记录自建照片表） */
    @Autowired
    private PatrolRecordMapper recordMapper;

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    @Value("${hltgq.corp-code}")
    private String corpCode;

    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    // ==================== 列表 / 详情 ====================

    /**
     * 计划分页（全状态）。
     *
     * @param name      计划名称模糊，可选
     * @param creatorId 创建人用户 id，可选
     * @param planType  计划类型：annual / monthly（或 中文/编码），可选
     * @param status    存储状态：草稿/已发布/已完成/已取消（中文或编码），可选；
     *                  展示态「未开始」为按巡检记录推导结果，不作存储过滤
     */
    public Page<PatrolScheduleVO> page(String name, String creatorId, String planType, String status,
                                       long page, long size) {
        long current = page > 0 ? page : 1;
        long pageSize = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : 10;
        String planTypeKey = normalizePlanType(planType);
        String statusCode = resolveStatusFilter(status);

        Page<PatrolScheduleVO> result = new Page<>(current, pageSize);
        long total = mapper.countPage(CheckUtils.trim(name), CheckUtils.trim(creatorId), planTypeKey, statusCode);
        result.setTotal(total);
        if (total == 0) {
            result.setRecords(Collections.emptyList());
            return result;
        }
        List<PatrolScheduleVO> rows = mapper.selectPage(CheckUtils.trim(name), CheckUtils.trim(creatorId),
                planTypeKey, statusCode, pageSize, (current - 1) * pageSize);
        fillLabels(rows);
        result.setRecords(rows);
        return result;
    }

    /** 计划详情（列表页「查看」弹窗）；不存在抛 400。 */
    public PatrolScheduleDetailVO detail(String id) {
        String scheduleId = requireId(id, "计划 id 不能为空");
        PatrolScheduleDetailVO vo = mapper.selectDetail(scheduleId);
        if (vo == null) {
            throw new IllegalArgumentException("巡检计划不存在：" + scheduleId);
        }
        vo.setPlanTypeLabel(DictUtils.label(PLAN_TYPE_LABELS, vo.getPlanType()));
        applyDisplayStatus(vo, mapper.countRecords(scheduleId) > 0);
        List<PatrolSiteVO> sites = mapper.selectPlanSites(scheduleId);
        vo.setSites(sites == null ? Collections.emptyList() : sites);
        return vo;
    }

    // ==================== 巡检记录 ====================

    /** 计划下的巡检记录分页（列表页「查看巡检记录」弹窗）。 */
    public Page<PatrolRecordVO> records(String scheduleId, long page, long size) {
        String id = requireId(scheduleId, "计划 id 不能为空");
        if (mapper.countSchedule(id) == 0) {
            throw new IllegalArgumentException("巡检计划不存在：" + id);
        }
        long current = page > 0 ? page : 1;
        long pageSize = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : 10;

        Page<PatrolRecordVO> result = new Page<>(current, pageSize);
        long total = mapper.countRecords(id);
        result.setTotal(total);
        if (total == 0) {
            result.setRecords(Collections.emptyList());
            return result;
        }
        List<PatrolRecordVO> rows = mapper.selectRecordPage(id, pageSize, (current - 1) * pageSize);
        for (PatrolRecordVO row : rows) {
            row.setResultLabel(DictUtils.label(RESULT_LABELS, row.getResultCode()));
            row.setStatusLabel(DictUtils.label(RECORD_STATUS_LABELS, row.getStatusCode()));
        }
        result.setRecords(rows);
        return result;
    }

    /**
     * 添加巡检记录（列表页「添加巡检记录」弹窗）：站点须在计划巡查范围内，设备须属于该站点。
     * <p>状态留空默认「已提交 #2#」；巡检人员留空取当前登录人。
     * <p>现场照片可选传（最多 3 张）；状态为已提交时至少 1 张（与巡检记录页提交口径一致），
     * 草稿可后续到巡检记录页补照片再提交。
     */
    @Transactional(rollbackFor = Exception.class)
    public void addRecord(PatrolRecordSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String scheduleId = requireId(req.getScheduleId(), "巡检计划不能为空");
        if (mapper.countSchedule(scheduleId) == 0) {
            throw new IllegalArgumentException("巡检计划不存在：" + scheduleId);
        }
        String siteId = requireId(req.getSiteId(), "巡检站点不能为空");
        if (mapper.countSiteInPlan(scheduleId, siteId) == 0) {
            throw new IllegalArgumentException("所选站点不在该计划的巡查范围内");
        }
        String deviceId = requireId(req.getDeviceId(), "巡检设备不能为空");
        if (mapper.countDeviceInSite(siteId, deviceId) == 0) {
            throw new IllegalArgumentException("所选设备不属于该站点");
        }
        String result = RESULT_CODES.get(CheckUtils.trim(req.getResult()));
        if (result == null) {
            throw new IllegalArgumentException("巡检结果无效（应为 正常/异常/隐患/缺陷/故障）");
        }
        String content = CheckUtils.trim(req.getContent());
        if (content == null) {
            throw new IllegalArgumentException("巡检内容不能为空");
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("巡检内容超长（≤1000 字）");
        }
        LocalDateTime time = req.getInspTime() == null || req.getInspTime().trim().isEmpty()
                ? LocalDateTime.now()
                : parseDateTime(req.getInspTime());
        if (time == null) {
            throw new IllegalArgumentException("巡检时间格式无效（应为 yyyy-MM-dd HH:mm）");
        }
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        String userId = CheckUtils.trim(req.getUserId());
        if (userId == null) {
            userId = operatorId;
        }
        String statusCode = DEFAULT_RECORD_STATUS;
        String statusRaw = CheckUtils.trim(req.getStatus());
        if (statusRaw != null) {
            statusCode = RECORD_STATUS_CODES.get(statusRaw);
            if (statusCode == null) {
                throw new IllegalArgumentException("巡检记录状态无效（应为 草稿/已提交）");
            }
        }
        List<String> photoIds = normalizePhotoIds(req.getPhotoIds());
        if (RECORD_STATUS_SUBMITTED.equals(statusCode) && photoIds.isEmpty()) {
            throw new IllegalArgumentException("已提交的巡检记录需上传现场照片（至少 1 张）");
        }
        precheckNewPhotos(photoIds, operatorId);
        String id = shortIdGenerator.nextUUID(null);
        LocalDateTime now = LocalDateTime.now();
        mapper.insertRecord(id, corpCode, operatorId, now, time, scheduleId, siteId, deviceId,
                userId, result, content, statusCode);
        for (int i = 0; i < photoIds.size(); i++) {
            if (recordMapper.bindPhoto(photoIds.get(i), id, i + 1, operatorId, now) == 0) {
                throw new IllegalArgumentException("现场照片状态已变化，请重新上传后保存");
            }
        }
        log.info("[巡检计划] 添加巡检记录：计划id={}, 记录id={}, 站点={}, 设备={}, 结果={}, 照片={} 张, 操作人={}",
                scheduleId, id, siteId, deviceId, result, photoIds.size(), operatorId);
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
            PatrolRecordPhotoFileVO photo = recordMapper.selectPhoto(photoId);
            if (photo == null) {
                throw new IllegalArgumentException("现场照片不存在或已失效，请重新上传");
            }
            if (!operatorId.equals(photo.getCreatedBy()) || photo.getRecordId() != null) {
                throw new IllegalArgumentException("现场照片不可用（非本人上传或已保存），请重新上传");
            }
        }
    }

    // ==================== 状态流转 / 删除 ====================

    /** 完成计划（批量）：状态置 #3# 已完成。 */
    public int complete(List<String> ids) {
        return updateStatus(ids, "#3#");
    }

    /** 取消计划（批量）：状态置 #4# 已取消。 */
    public int cancel(List<String> ids) {
        return updateStatus(ids, "#4#");
    }

    /**
     * 发布计划（批量）：草稿 #1# → 已发布 #2#（存储值）；
     * 发布后列表/详情的展示状态按是否已开始巡检动态推导（无巡检记录=未开始 / 有记录=进行中）。
     * <p>仅草稿可发布，且<b>结束日期已过的计划拒绝发布</b>（发布后即锁死不可编辑，
     * 避免存量过错日期的草稿发布后无法修正）；需先编辑计划时间。
     */
    @Transactional(rollbackFor = Exception.class)
    public int publish(List<String> ids) {
        List<String> uniqueIds = normalizeIds(ids, "请选择要发布的计划");
        List<PatrolScheduleVO> rows = mapper.selectForPublish(uniqueIds);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("计划不存在或已被删除");
        }
        LocalDate today = LocalDate.now();
        for (PatrolScheduleVO row : rows) {
            if (!"#1#".equals(row.getStatusCode())) {
                throw new IllegalArgumentException("计划「" + row.getTitle() + "」不是草稿，无法发布");
            }
            LocalDate endDate = row.getEndTime() == null ? null : row.getEndTime().toLocalDate();
            if (endDate != null && today.isAfter(endDate)) {
                throw new IllegalArgumentException("计划「" + row.getTitle() + "」已过结束日期（"
                        + endDate + "），请先编辑计划时间再发布");
            }
        }
        int updated = mapper.publishStatus(uniqueIds,
                OperatorUtils.currentOperatorId(fallbackOperator), LocalDateTime.now());
        if (updated == 0) {
            throw new IllegalArgumentException("所选计划不是草稿或已被删除，仅草稿状态的计划可以发布");
        }
        log.info("[巡检计划] 发布计划：计划数={}, 实际更新={}", uniqueIds.size(), updated);
        return updated;
    }

    /**
     * 批量删除计划：存在巡检记录时拒绝（保护记录数据）；
     * 否则同事务删除主表 + 巡查范围关系 + 旧巡检人员多选关系（存量清理）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int remove(List<String> ids) {
        List<String> uniqueIds = normalizeIds(ids, "请选择要删除的计划");
        long recordCount = mapper.countRecordsBySchedules(uniqueIds);
        if (recordCount > 0) {
            throw new IllegalArgumentException("所选计划下存在 " + recordCount + " 条巡检记录，无法删除");
        }
        int deleted = mapper.deleteSchedules(uniqueIds);
        mapper.deleteSiteRelations(uniqueIds);
        mapper.deleteUserRelations(uniqueIds);
        log.info("[巡检计划] 批量删除：计划数={}, 实际删除={}", uniqueIds.size(), deleted);
        return deleted;
    }

    // ==================== 新增计划 ====================

    /**
     * 新增巡检计划：主表（含巡检人员 "user" 单选列）+ 巡查范围关系表，编号自动生成。
     * <p>校验：名称/类型/内容/起止时间/巡检人员/巡查范围均必填；结束不得早于开始。
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(PatrolScheduleSaveVO req) {
        PlanForm form = validatePlanForm(req);
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        String code = generateCode();
        LocalDateTime now = LocalDateTime.now();

        PatrolSchedule entity = new PatrolSchedule();
        entity.setId(shortIdGenerator.nextUUID(null));
        entity.setCode(code);
        entity.setTitle(form.title);
        entity.setContent(form.content);
        entity.setStartTime(form.startDate.atStartOfDay());
        entity.setEndTime(form.endDate.atStartOfDay());
        entity.setPlanType(form.planType);
        entity.setStatus(DEFAULT_PLAN_STATUS);
        entity.setUser(form.inspectorId);
        entity.setCorpCode(corpCode);
        entity.setCreatedAt(now);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
        scheduleMapper.insert(entity);

        int order = 1;
        for (String siteId : form.siteIds) {
            scheduleMapper.insertSiteRelation(shortIdGenerator.nextUUID(null), siteId,
                    entity.getId(), String.valueOf(order++), corpCode, now, operatorId);
        }
        log.info("[巡检计划] 新增计划：id={}, 编号={}, 名称={}, 巡检人员={}, 巡查范围={}站, 操作人={}",
                entity.getId(), code, form.title, form.inspectorId, form.siteIds.size(), operatorId);
        return entity.getId();
    }

    /**
     * 编辑巡检计划（仅草稿）：主表字段 + 巡查范围关系整体重建，状态保持草稿。
     * <p>发布后计划进入巡检进度驱动的展示态流转（未开始 → 进行中），不允许再改；非草稿或不存在时拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public String update(PatrolScheduleSaveVO req) {
        PlanForm form = validatePlanForm(req);
        String id = requireId(req.getId(), "计划 id 不能为空");
        String operatorId = OperatorUtils.currentOperatorId(fallbackOperator);
        LocalDateTime now = LocalDateTime.now();

        int updated = mapper.updateDraft(id, form.title, form.content, form.planType,
                form.startDate.atStartOfDay(), form.endDate.atStartOfDay(), form.inspectorId,
                operatorId, now);
        if (updated == 0) {
            throw new IllegalArgumentException("计划不存在或不是草稿状态，仅草稿可以编辑");
        }
        mapper.deleteSiteRelations(Collections.singletonList(id));
        int order = 1;
        for (String siteId : form.siteIds) {
            scheduleMapper.insertSiteRelation(shortIdGenerator.nextUUID(null), siteId,
                    id, String.valueOf(order++), corpCode, now, operatorId);
        }
        log.info("[巡检计划] 编辑草稿：id={}, 名称={}, 巡检人员={}, 巡查范围={}站, 操作人={}",
                id, form.title, form.inspectorId, form.siteIds.size(), operatorId);
        return id;
    }

    // ==================== 下拉候选 ====================

    /** 站点候选（新增计划巡查范围多选）。 */
    public List<PatrolOptionVO> siteOptions(String keyword) {
        return mapper.selectSiteOptions(CheckUtils.trim(keyword));
    }

    /** 设备候选（添加巡检记录：按所选站点）。 */
    public List<PatrolOptionVO> deviceOptions(String siteId) {
        String id = requireId(siteId, "站点 id 不能为空");
        return mapper.selectDeviceOptions(id);
    }

    /** 用户候选（创建人筛选、巡检人员单选）。 */
    public List<PatrolOptionVO> userOptions(String keyword) {
        return mapper.selectUserOptions(CheckUtils.trim(keyword));
    }

    // ==================== 私有工具 ====================

    /** 新增/编辑共用表单（校验并归一后的值）。 */
    private static final class PlanForm {
        private String title;
        private String planType;
        private String content;
        private LocalDate startDate;
        private LocalDate endDate;
        private String inspectorId;
        private List<String> siteIds;
    }

    /**
     * 新增/编辑共用校验：名称/类型/内容/起止时间/巡检人员/巡查范围均必填；结束不得早于开始。
     * <p>不合法抛 400。
     */
    private PlanForm validatePlanForm(PatrolScheduleSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        PlanForm form = new PlanForm();
        form.title = CheckUtils.trim(req.getTitle());
        if (form.title == null) {
            throw new IllegalArgumentException("计划名称不能为空");
        }
        if (form.title.length() > 200) {
            throw new IllegalArgumentException("计划名称超长（≤200 字）");
        }
        form.planType = PLAN_TYPE_CODES.get(CheckUtils.trim(req.getPlanType()));
        if (form.planType == null) {
            throw new IllegalArgumentException("计划类型无效（应为 年度计划/月度计划）");
        }
        form.content = CheckUtils.trim(req.getContent());
        if (form.content == null) {
            throw new IllegalArgumentException("巡查内容不能为空");
        }
        if (form.content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("巡查内容超长（≤1000 字）");
        }
        form.startDate = parseDate(req.getStartTime());
        if (form.startDate == null) {
            throw new IllegalArgumentException("计划开始时间格式无效（应为 yyyy-MM-dd）");
        }
        form.endDate = parseDate(req.getEndTime());
        if (form.endDate == null) {
            throw new IllegalArgumentException("计划结束时间格式无效（应为 yyyy-MM-dd）");
        }
        if (form.endDate.isBefore(form.startDate)) {
            throw new IllegalArgumentException("计划结束时间早于开始时间");
        }
        form.inspectorId = requireId(req.getInspectorId(), "巡检人员不能为空");
        form.siteIds = normalizeIds(req.getSiteIds(), "巡查范围不能为空");
        return form;
    }

    /** 批量状态更新：ids 去空去重；无命中行时提示计划不存在。 */
    private int updateStatus(List<String> ids, String statusCode) {
        List<String> uniqueIds = normalizeIds(ids, "请选择要操作的计划");
        int updated = mapper.updateStatus(uniqueIds, statusCode, OperatorUtils.currentOperatorId(fallbackOperator),
                LocalDateTime.now());
        if (updated == 0) {
            throw new IllegalArgumentException("计划不存在、已被删除或仍为草稿（请先发布）");
        }
        log.info("[巡检计划] 状态更新：目标={}, 计划数={}, 实际更新={}", statusCode, uniqueIds.size(), updated);
        return updated;
    }

    /** 列表行编码翻译（计划类型 / 状态）；展示态按巡检记录批量推导（一次 IN 查询，避免逐行）。 */
    private void fillLabels(List<PatrolScheduleVO> rows) {
        List<String> publishedIds = new ArrayList<>();
        for (PatrolScheduleVO row : rows) {
            if ("#2#".equals(row.getStatusCode())) {
                publishedIds.add(row.getId());
            }
        }
        Set<String> startedIds = publishedIds.isEmpty() ? Collections.emptySet()
                : new HashSet<>(mapper.selectStartedScheduleIds(publishedIds));
        for (PatrolScheduleVO row : rows) {
            row.setPlanTypeLabel(DictUtils.label(PLAN_TYPE_LABELS, row.getPlanType()));
            applyDisplayStatus(row, startedIds.contains(row.getId()));
        }
    }

    /** 展示状态推导 + 中文翻译（列表行与详情共用）。 */
    private void applyDisplayStatus(PatrolScheduleVO vo, boolean started) {
        vo.setStatusCode(deriveDisplayStatus(vo.getStatusCode(), started));
        vo.setStatusLabel(DictUtils.label(STATUS_LABELS, vo.getStatusCode()));
    }

    /**
     * 展示状态推导：草稿/已完成/已取消为人工状态，原样返回；
     * 已发布 #2# 按是否已开始巡检（该计划下存在巡检记录）——有记录=进行中 #2#，无记录=未开始 #5#。
     */
    private String deriveDisplayStatus(String storedStatus, boolean started) {
        if ("#2#".equals(storedStatus)) {
            return started ? "#2#" : "#5#";
        }
        return storedStatus;
    }

    /**
     * 状态筛选归一为存储编码：支持 草稿/已发布（进行中兼容等同）/已完成/已取消 及编码；
     * 「未开始」为按巡检记录推导的展示态，不落库，直接 400 提示，避免静默返回空列表。
     */
    private String resolveStatusFilter(String status) {
        String value = CheckUtils.trim(status);
        if (value == null) {
            return null;
        }
        if ("已发布".equals(value)) {
            return "#2#";
        }
        if ("未开始".equals(value)) {
            throw new IllegalArgumentException("状态「未开始」为按巡检记录推导的展示态，不支持作为筛选条件");
        }
        return DictUtils.resolveCode(STATUS_LABELS, value);
    }

    /** 计划类型参数归一：annual / monthly；其他值返回 null（不过滤）。 */
    private String normalizePlanType(String planType) {
        String value = CheckUtils.trim(planType);
        if (value == null) {
            return null;
        }
        if ("annual".equalsIgnoreCase(value) || "年度计划".equals(value) || "#1#".equals(value)
                || "#wavn#".equals(value)) {
            return "annual";
        }
        if ("monthly".equalsIgnoreCase(value) || "月度计划".equals(value) || "#zjgg#".equals(value)) {
            return "monthly";
        }
        return null;
    }

    /** 自动生成计划编号：XCJH + 13 位毫秒时间戳（碰撞时毫秒递进）。 */
    private String generateCode() {
        long timestamp = System.currentTimeMillis();
        String candidate = CODE_PREFIX + timestamp;
        while (mapper.countByCode(candidate) > 0) {
            timestamp++;
            candidate = CODE_PREFIX + timestamp;
        }
        return candidate;
    }

    /** id 列表去空去重（保序）；为空抛 400。 */
    private List<String> normalizeIds(List<String> ids, String message) {
        Set<String> unique = new LinkedHashSet<>();
        if (ids != null) {
            for (String id : ids) {
                String t = CheckUtils.trim(id);
                if (t != null) {
                    unique.add(t);
                }
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return new ArrayList<>(unique);
    }

    /** 必填 id：去空白，空抛 400。 */
    private String requireId(String value, String message) {
        String id = CheckUtils.trim(value);
        if (id == null) {
            throw new IllegalArgumentException(message);
        }
        return id;
    }

    /**
     * 日期解析：兼容 yyyy-M-d / yyyy/M/d / 带时间串取日期段（口径同导入服务）。
     */
    private LocalDate parseDate(String raw) {
        String value = CheckUtils.trim(raw);
        if (value == null) {
            return null;
        }
        String datePart = value.length() > 10 ? value.substring(0, 10) : value;
        String[] patterns = {"yyyy-M-d", "yyyy/M/d"};
        for (String pattern : patterns) {
            try {
                return LocalDate.parse(datePart, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        try {
            return LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 巡检时间解析：兼容 yyyy-MM-dd HH:mm[:ss] / ISO yyyy-MM-ddTHH:mm[:ss]。
     */
    private LocalDateTime parseDateTime(String raw) {
        String value = CheckUtils.trim(raw);
        if (value == null) {
            return null;
        }
        String normalized = value.replace('T', ' ');
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd"};
        for (String pattern : patterns) {
            try {
                if ("yyyy-MM-dd".equals(pattern)) {
                    return LocalDate.parse(normalized, DateTimeFormatter.ofPattern(pattern)).atStartOfDay();
                }
                return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        return null;
    }
}
