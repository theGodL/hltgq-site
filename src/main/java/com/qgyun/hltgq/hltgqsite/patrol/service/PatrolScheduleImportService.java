package com.qgyun.hltgq.hltgqsite.patrol.service;

import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.entity.PatrolSchedule;
import com.qgyun.hltgq.hltgqsite.model.util.ExcelParseUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.patrol.mapper.PatrolScheduleMapper;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolImportResult;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolImportRowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 巡查计划 Excel 导入服务（POST /patrol-schedule/import，模板见 GET /patrol-schedule/import-template）。
 *
 * <p>处理流程：文件校验（非空/.xlsx/≤300 行）→ 表头校验（缺必需列直接 400）→ 逐行解析入库。
 * 逐行独立：单行失败不影响其他行；校验通过的行在**单行事务**内写主表 + 巡查范围/巡检人员关系中间表。
 *
 * <p>逐行规则（契约定稿见 src/main/resources/导入-导出对接接口.md）：
 * <ul>
 *   <li>计划编号：留空自动生成 XCJH + 13 位毫秒时间戳（批内与库中防重）；填写须唯一</li>
 *   <li>计划类型：年度计划/#1#、月度计划/#zjgg#；状态忽略填写值统一落 #1# 草稿</li>
 *   <li>起止时间：兼容 yyyy-M-d、yyyy/M/d、带时间串取日期段；按当日零点落库；结束不得早于开始</li>
 *   <li>创建人：留空 = 当前登录人；填写按姓名精确匹配（未匹配/重名判行失败）</li>
 *   <li>巡查范围/巡检人员：逐项按站点名称或编号 / 用户姓名精确匹配；
 *       未匹配、重名的项忽略并在行结果提示；全部项均未匹配判行失败</li>
 * </ul>
 *
 * <p>关系中间表（巡查范围/巡检人员）为直写平台关系表，表名/field_id 按平台命名约定推导，
 * 属联调核对项（见 PatrolScheduleMapper 注释与服务日志 [巡查导入] 关系表写入）。
 */
@Service
public class PatrolScheduleImportService {

    private static final Logger log = LoggerFactory.getLogger(PatrolScheduleImportService.class);

    /** 模板列名（与 PatrolScheduleImport_Template.xlsx 表头一致） */
    private static final String COL_CODE = "计划编号";
    private static final String COL_TITLE = "计划名称";
    private static final String COL_TYPE = "计划类型";
    private static final String COL_SCOPE = "巡查范围";
    private static final String COL_CONTENT = "巡查内容";
    private static final String COL_START = "计划开始时间";
    private static final String COL_END = "计划结束时间";
    private static final String COL_CREATOR = "创建人";
    private static final String COL_USERS = "巡检人员";

    /** 必需表头列（缺失直接 400，按模板列序输出提示） */
    private static final String[] REQUIRED_COLUMNS = {
            COL_TITLE, COL_TYPE, COL_SCOPE, COL_CONTENT, COL_START, COL_END, COL_USERS};

    /** 数据行上限（与模板预留区域一致） */
    private static final int MAX_ROWS = 300;

    /** 巡查内容长度上限（平台字段定义 ≤1000） */
    private static final int CONTENT_MAX_LENGTH = 1000;

    /** 计划编号前缀：自动生成 = XCJH + 13 位毫秒时间戳（与平台表单默认值同形态） */
    private static final String CODE_PREFIX = "XCJH";

    /** 导入统一落草稿 */
    private static final String DRAFT_STATUS = "#1#";

    /** 计划类型：填写值（中文或编码）→ 落库编码 */
    private static final Map<String, String> PLAN_TYPE_CODES = new LinkedHashMap<>();

    static {
        PLAN_TYPE_CODES.put("年度计划", "#1#");
        PLAN_TYPE_CODES.put("月度计划", "#zjgg#");
        PLAN_TYPE_CODES.put("#1#", "#1#");
        PLAN_TYPE_CODES.put("#zjgg#", "#zjgg#");
    }

    /** 关系表 field_id：巡查范围多选字段 key（联调核对项，见 PatrolScheduleMapper） */
    private static final String FIELD_SCOPE_SITE = "inspection_scope_site";

    /** 关系表 field_id：巡检人员多选字段 key（联调核对项，见 PatrolScheduleMapper） */
    private static final String FIELD_USER = "user";

    /** 多值分隔符：顿号为主，兼容逗号/竖线/全角分号 */
    private static final String MULTI_VALUE_SEPARATOR = "[、,|；]+";

    @Autowired
    private PatrolScheduleMapper mapper;

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${hltgq.corp-code}")
    private String corpCode;

    /**
     * 导入巡查计划 xlsx：整体性错误抛 IllegalArgumentException（→400），
     * 行级问题在返回结果中逐行给出，不中断其他行。
     */
    public PatrolImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        ExcelParseUtils.SheetData sheet;
        try {
            sheet = ExcelParseUtils.parseSheet(file.getInputStream());
        } catch (Exception e) {
            throw new IllegalArgumentException("导入文件解析失败（仅支持 .xlsx 格式）");
        }
        if (sheet.getRows().size() > MAX_ROWS) {
            throw new IllegalArgumentException("导入文件数据行超出上限（300 行）");
        }
        if (sheet.getRows().isEmpty()) {
            throw new IllegalArgumentException("导入文件无数据行");
        }
        List<String> missing = new ArrayList<>();
        for (String column : REQUIRED_COLUMNS) {
            if (!sheet.getHeaders().contains(column)) {
                missing.add(column);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("导入文件缺少以下列: " + String.join(", ", missing));
        }

        String operatorId = currentUserId();
        Set<String> usedCodes = new HashSet<>();
        PatrolImportResult result = new PatrolImportResult();
        for (ExcelParseUtils.RowEntry entry : sheet.getRows()) {
            PatrolImportRowResult rowResult = new PatrolImportRowResult(entry.getRowNum());
            List<String> notes = new ArrayList<>();
            try {
                String id = processRow(entry.getData(), usedCodes, operatorId, notes, rowResult);
                rowResult.setOk(true);
                rowResult.setId(id);
                if (!notes.isEmpty()) {
                    rowResult.setMessage(String.join("；", notes));
                }
                result.setSuccess(result.getSuccess() + 1);
            } catch (Exception e) {
                rowResult.setOk(false);
                rowResult.setId(null);
                rowResult.setCode(null);
                rowResult.setMessage(rootMessage(e));
                result.setFailed(result.getFailed() + 1);
                log.error("[巡查导入] 第 {} 行失败：{}", entry.getRowNum(), e.getMessage());
            }
            result.getRows().add(rowResult);
        }
        result.setTotal(sheet.getRows().size());
        log.info("[巡查导入] 完成：total={}, success={}, failed={}, 操作人={}",
                result.getTotal(), result.getSuccess(), result.getFailed(), operatorId);
        return result;
    }

    // ==================== 逐行处理 ====================

    /** 单行解析校验并入库（主表 + 关系中间表，单行事务）；返回记录 id，失败抛异常 */
    private String processRow(Map<String, String> data, Set<String> usedCodes, String operatorId,
                              List<String> notes, PatrolImportRowResult rowResult) {
        // 1. 计划编号：留空自动生成，填写查重（批内 + 库中）
        String code = value(data, COL_CODE);
        if (code.isEmpty()) {
            code = generateCode(usedCodes);
        } else {
            if (usedCodes.contains(code)) {
                throw new IllegalArgumentException("计划编号在本批次内重复：" + code);
            }
            if (mapper.countByCode(code) > 0) {
                throw new IllegalArgumentException("计划编号已存在：" + code);
            }
        }

        // 2. 计划名称
        String title = value(data, COL_TITLE);
        if (title.isEmpty()) {
            throw new IllegalArgumentException("计划名称为空");
        }

        // 3. 计划类型
        String planType = PLAN_TYPE_CODES.get(value(data, COL_TYPE));
        if (planType == null) {
            throw new IllegalArgumentException("计划类型无效（应为 年度计划/月度计划）");
        }

        // 4. 巡查内容
        String content = value(data, COL_CONTENT);
        if (content.isEmpty()) {
            throw new IllegalArgumentException("巡查内容为空");
        }
        if (content.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("巡查内容超长（≤1000 字）");
        }

        // 5. 起止时间（兼容 yyyy-M-d / yyyy/M/d / 带时间串取日期段）
        LocalDate startDate = parseDate(value(data, COL_START));
        if (startDate == null) {
            throw new IllegalArgumentException("计划开始时间格式无效（应为 yyyy-MM-dd）");
        }
        LocalDate endDate = parseDate(value(data, COL_END));
        if (endDate == null) {
            throw new IllegalArgumentException("计划结束时间格式无效（应为 yyyy-MM-dd）");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("计划结束时间早于开始时间");
        }

        // 6. 创建人：留空 = 当前登录人；填写按姓名精确匹配（未匹配/重名判行失败）
        String creatorRaw = value(data, COL_CREATOR);
        String creatorId;
        if (creatorRaw.isEmpty()) {
            creatorId = operatorId;
        } else {
            List<Map<String, Object>> matches = mapper.selectUserMatches(creatorRaw);
            if (matches.isEmpty()) {
                throw new IllegalArgumentException("创建人「" + creatorRaw + "」未匹配到用户");
            }
            if (matches.size() > 1) {
                throw new IllegalArgumentException("创建人「" + creatorRaw + "」存在多个匹配用户");
            }
            creatorId = (String) matches.get(0).get("id");
        }

        // 7. 巡查范围：逐项按站点名称/编号匹配，部分未匹配忽略并提示，全未匹配判行失败
        String scopeRaw = value(data, COL_SCOPE);
        if (scopeRaw.isEmpty()) {
            throw new IllegalArgumentException("巡查范围为空");
        }
        Set<String> scopeSiteIds = new LinkedHashSet<>();
        for (String item : splitMulti(scopeRaw)) {
            List<Map<String, Object>> matches = mapper.selectSiteMatches(item);
            if (matches.size() == 1) {
                scopeSiteIds.add((String) matches.get(0).get("id"));
            } else if (matches.isEmpty()) {
                notes.add("站点「" + item + "」未匹配，已忽略");
            } else {
                notes.add("站点「" + item + "」存在多个匹配，已忽略");
            }
        }
        if (scopeSiteIds.isEmpty()) {
            throw new IllegalArgumentException("巡查范围未匹配到任何站点");
        }

        // 8. 巡检人员：逐项按姓名匹配，部分未匹配忽略并提示，全未匹配判行失败
        String usersRaw = value(data, COL_USERS);
        if (usersRaw.isEmpty()) {
            throw new IllegalArgumentException("巡检人员为空");
        }
        Set<String> inspectorIds = new LinkedHashSet<>();
        for (String item : splitMulti(usersRaw)) {
            List<Map<String, Object>> matches = mapper.selectUserMatches(item);
            if (matches.size() == 1) {
                inspectorIds.add((String) matches.get(0).get("id"));
            } else if (matches.isEmpty()) {
                notes.add("巡检人员「" + item + "」未匹配，已忽略");
            } else {
                notes.add("巡检人员「" + item + "」存在多个匹配，已忽略");
            }
        }
        if (inspectorIds.isEmpty()) {
            throw new IllegalArgumentException("巡检人员未匹配到任何用户");
        }

        // 9. 入库：主表 + 关系中间表（单行事务，关系写入失败整行回滚并给出原因）
        LocalDateTime now = LocalDateTime.now();
        PatrolSchedule entity = new PatrolSchedule();
        entity.setCode(code);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setStartTime(startDate.atStartOfDay());
        entity.setEndTime(endDate.atStartOfDay());
        entity.setPlanType(planType);
        entity.setStatus(DRAFT_STATUS);
        entity.setCorpCode(corpCode);
        entity.setCreatedAt(now);
        entity.setCreatedBy(creatorId);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(creatorId);
        try {
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    mapper.insert(entity);
                    for (String siteId : scopeSiteIds) {
                        mapper.insertSiteRelation(shortIdGenerator.nextUUID(null), siteId,
                                entity.getId(), FIELD_SCOPE_SITE, corpCode, now, creatorId);
                    }
                    for (String userId : inspectorIds) {
                        mapper.insertUserRelation(shortIdGenerator.nextUUID(null), userId,
                                entity.getId(), FIELD_USER, corpCode, now, creatorId);
                    }
                    log.info("[巡查导入] 关系表写入：计划id={}, 编号={}, 巡查范围={}条(field_id={}), 巡检人员={}条(field_id={})",
                            entity.getId(), entity.getCode(), scopeSiteIds.size(), FIELD_SCOPE_SITE,
                            inspectorIds.size(), FIELD_USER);
                }
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("入库失败：" + rootMessage(e));
        }
        usedCodes.add(code);
        rowResult.setCode(code);
        return entity.getId();
    }

    // ==================== 私有工具 ====================

    /** 自动生成计划编号：XCJH + 13 位毫秒时间戳；与批内已用、库中已有编号防重（碰撞时毫秒递进） */
    private String generateCode(Set<String> usedCodes) {
        long timestamp = System.currentTimeMillis();
        String candidate = CODE_PREFIX + timestamp;
        while (usedCodes.contains(candidate) || mapper.countByCode(candidate) > 0) {
            timestamp++;
            candidate = CODE_PREFIX + timestamp;
        }
        return candidate;
    }

    /**
     * 日期解析：支持 yyyy-M-d、yyyy/M/d、yyyy.M.d、yyyy年M月d日，
     * 带时间串（空格或 T 分隔）取日期段；解析失败返回 null。
     */
    private LocalDate parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim()
                .replace('/', '-').replace('.', '-')
                .replace('年', '-').replace('月', '-').replace("日", "");
        int cut = text.indexOf(' ');
        if (cut < 0) {
            cut = text.indexOf('T');
        }
        if (cut > 0) {
            text = text.substring(0, cut);
        }
        String[] parts = text.split("-");
        if (parts.length != 3) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (Exception e) {
            return null;
        }
    }

    /** 多值拆分：按顿号/逗号/竖线/全角分号拆分，去首尾空白，剔除空项 */
    private List<String> splitMulti(String raw) {
        List<String> items = new ArrayList<>();
        for (String part : raw.split(MULTI_VALUE_SEPARATOR)) {
            String item = part.trim();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    /** 取单元格值（缺列/空值返回空串，统一去首尾空白） */
    private String value(Map<String, String> data, String column) {
        String v = data.get(column);
        return v == null ? "" : v.trim();
    }

    /** 取最深层原因消息（供逐行失败原因展示），截断 200 字 */
    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    /** 当前登录人 userId（鉴权已由拦截器保证；异常场景容错为 null，审计字段留空） */
    private String currentUserId() {
        UserContext user = UserContextHolder.currentUser();
        if (user == null || user.getUserId() == null) {
            log.warn("[巡查导入] 当前请求无用户上下文，created_by/updated_by 置空");
            return null;
        }
        return user.getUserId();
    }
}
