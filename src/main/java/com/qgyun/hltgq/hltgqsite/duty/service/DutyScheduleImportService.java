package com.qgyun.hltgq.hltgqsite.duty.service;

import com.qgyun.hltgq.hltgqsite.duty.DutySupport;
import com.qgyun.hltgq.hltgqsite.duty.mapper.DutyScheduleMapper;
import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleDetailVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleImportResult;
import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleImportRowResult;
import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleSaveRequest;
import com.qgyun.hltgq.hltgqsite.model.util.ExcelParseUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 值班排班 Excel 导入服务（POST /duty/schedule/import，模板见 GET /duty/schedule/import-template）。
 *
 * <p>处理流程：文件校验（非空/.xlsx/≤300 行/表头）→ 单位与人员姓名索引 → 逐行解析。
 * 逐行独立：单行失败不影响其他行；解析通过的行组装 {@link ScheduleSaveRequest} 后复用
 * {@link DutyScheduleService#save} 入库（同源校验：必填/存在性/时间先后/人员时间冲突），
 * 每行独立事务，失败自动回滚该行。
 *
 * <p>逐行规则：
 * <ul>
 *   <li>值班日期：必填，yyyy-MM-dd（兼容 yyyy/M/d、yyyy.M.d、yyyy年M月d日、yyyyMMdd）</li>
 *   <li>班次：必填，白班/中班/夜班（或 #1#/#jhyu#/#zsfh#）</li>
 *   <li>开始时间 / 结束时间：HH:mm，留空按班次默认时段（08:00-16:00 / 16:00-24:00 / 00:00-08:00）；
 *       结束不晚于开始按次日处理（跨天班），24:00 按次日 00:00</li>
 *   <li>所在单位 / 带班领导：必填，填名称（与系统一致，重名须改填 id）</li>
 *   <li>值班人员：可选，多个姓名用顿号/逗号分隔（重名须改填 id）</li>
 * </ul>
 */
@Service
public class DutyScheduleImportService {

    private static final Logger log = LoggerFactory.getLogger(DutyScheduleImportService.class);

    /** 模板列名（与模板生成 / 解析共用，防止两处漂移） */
    private static final String COL_DATE = "值班日期";
    private static final String COL_SHIFT = "班次";
    private static final String COL_START = "开始时间";
    private static final String COL_END = "结束时间";
    private static final String COL_ORG = "所在单位";
    private static final String COL_LEADER = "带班领导";
    private static final String COL_MEMBERS = "值班人员";

    /** 模板表头（顺序即模板列序） */
    public static final String[] TEMPLATE_HEADERS = {
            COL_DATE, COL_SHIFT, COL_START, COL_END, COL_ORG, COL_LEADER, COL_MEMBERS};

    /** 班次选项（模板下拉与解析共用） */
    public static final String[] SHIFT_OPTIONS = {"白班", "中班", "夜班"};

    /** 数据行上限（与模板预留区域一致；控制器生成模板时引用） */
    public static final int MAX_ROWS = 300;

    /** 必需表头列（缺失直接 400） */
    private static final String[] REQUIRED_COLUMNS = {COL_DATE, COL_SHIFT, COL_ORG, COL_LEADER};

    /** 班次默认时段（开始, 结束；结束 24:00 按次日 00:00 处理） */
    private static final Map<String, String[]> SHIFT_TIMES = new LinkedHashMap<>();

    /** 请求时间文本格式（与 ScheduleSaveRequest 契约一致，分钟级 16 位） */
    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 人员多值分隔符：顿号为主，兼容逗号（中英文）/ 竖线 / 分号（中英文） */
    private static final String MEMBER_SEPARATOR = "[、,，|；;]+";

    static {
        SHIFT_TIMES.put("#1#", new String[]{"08:00", "16:00"});
        SHIFT_TIMES.put("#jhyu#", new String[]{"16:00", "24:00"});
        SHIFT_TIMES.put("#zsfh#", new String[]{"00:00", "08:00"});
    }

    @Autowired
    private DutyScheduleMapper mapper;

    @Autowired
    private DutyScheduleService scheduleService;

    /**
     * 导入值班排班 xlsx：整体性错误抛 IllegalArgumentException（→400），
     * 行级问题在返回结果中逐行给出，不中断其他行。
     */
    public ScheduleImportResult importFile(MultipartFile file) {
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

        Directory orgs = buildDirectory(mapper.selectOptionsOrgs());
        Directory users = buildDirectory(mapper.selectOptionsUsers());
        ScheduleImportResult result = new ScheduleImportResult();
        for (ExcelParseUtils.RowEntry entry : sheet.getRows()) {
            ScheduleImportRowResult rowResult = new ScheduleImportRowResult(entry.getRowNum());
            try {
                ScheduleSaveRequest request = buildRequest(entry.getData(), orgs, users);
                ScheduleDetailVO saved = scheduleService.save(request);
                rowResult.setOk(true);
                rowResult.setId(saved.getId());
                rowResult.setDutyDate(saved.getDutyDate());
                rowResult.setShift(saved.getShift());
                result.setSuccess(result.getSuccess() + 1);
            } catch (Exception e) {
                rowResult.setOk(false);
                rowResult.setMessage(ExceptionUtils.rootMessage(e, 200));
                result.setFailed(result.getFailed() + 1);
                log.error("[值班导入] 第 {} 行失败：{}", entry.getRowNum(), e.getMessage());
            }
            result.getRows().add(rowResult);
        }
        result.setTotal(sheet.getRows().size());
        log.info("[值班导入] 完成：total={}, success={}, failed={}",
                result.getTotal(), result.getSuccess(), result.getFailed());
        return result;
    }

    // ==================== 逐行处理 ====================

    /** 单行解析 → 保存请求（校验由 {@link DutyScheduleService#save} 统一兜底） */
    private ScheduleSaveRequest buildRequest(Map<String, String> data, Directory orgs, Directory users) {
        LocalDate day = parseDutyDate(value(data, COL_DATE));
        String shiftRaw = value(data, COL_SHIFT);
        if (shiftRaw.isEmpty()) {
            throw new IllegalArgumentException("班次为空");
        }
        String shiftCode = DutySupport.shiftCode(shiftRaw);
        if (shiftCode == null) {
            throw new IllegalArgumentException("班次无效（应为 白班/中班/夜班）：" + shiftRaw);
        }
        String[] times = buildTimes(day, shiftCode, value(data, COL_START), value(data, COL_END));
        String leaderRaw = value(data, COL_LEADER);
        if (leaderRaw.isEmpty()) {
            throw new IllegalArgumentException("带班领导为空");
        }

        ScheduleSaveRequest request = new ScheduleSaveRequest();
        request.setDutyDate(day.toString());
        request.setShiftCode(shiftCode);
        request.setTimeStart(times[0]);
        request.setTimeEnd(times[1]);
        request.setOrgId(orgs.resolve(value(data, COL_ORG), "所在单位"));
        request.setLeaderId(users.resolve(leaderRaw, "带班领导"));
        request.setMemberIds(resolveMembers(value(data, COL_MEMBERS), users));
        return request;
    }

    /**
     * 值班日期解析：yyyy-MM-dd 为主，兼容 / . 分隔、年月日中文、yyyyMMdd 与带时分后缀的显示值。
     * <p>歧义格式（如 10/21/26）不做猜测，直接报格式错误引导改用 yyyy-MM-dd。
     */
    private LocalDate parseDutyDate(String raw) {
        String s = DutySupport.trim(raw);
        if (s == null) {
            throw new IllegalArgumentException("值班日期为空");
        }
        String text = s.replace("年", "-").replace("月", "-").replace("日", "").trim();
        int space = text.indexOf(' ');
        if (space > 0) {
            text = text.substring(0, space);
        }
        text = text.replace('/', '-').replace('.', '-');
        String[] parts = text.split("-");
        if (parts.length == 3) {
            try {
                return LocalDate.of(Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
            } catch (Exception ignore) {
                // 落到下方统一报错
            }
        }
        if (text.matches("\\d{8}")) {
            try {
                return LocalDate.of(Integer.parseInt(text.substring(0, 4)),
                        Integer.parseInt(text.substring(4, 6)), Integer.parseInt(text.substring(6, 8)));
            } catch (Exception ignore) {
                // 落到下方统一报错
            }
        }
        throw new IllegalArgumentException("值班日期格式应为 yyyy-MM-dd：" + s);
    }

    /** 开始/结束时间 → yyyy-MM-dd HH:mm（留空取班次默认；结束不晚于开始按次日） */
    private String[] buildTimes(LocalDate day, String shiftCode, String startRaw, String endRaw) {
        String[] def = SHIFT_TIMES.get(shiftCode);
        int[] startHm = startRaw.isEmpty() ? parseHm(def[0], COL_START) : parseHm(startRaw, COL_START);
        int[] endHm = endRaw.isEmpty() ? parseHm(def[1], COL_END) : parseHm(endRaw, COL_END);
        if (startHm[0] >= 24) {
            throw new IllegalArgumentException("开始时间格式应为 HH:mm：" + startRaw);
        }
        LocalDateTime start = day.atTime(startHm[0], startHm[1]);
        LocalDateTime end;
        if (endHm[0] == 24 && endHm[1] == 0) {
            end = day.plusDays(1).atStartOfDay();
        } else {
            end = day.atTime(endHm[0], endHm[1]);
            if (!end.isAfter(start)) {
                end = end.plusDays(1);
            }
        }
        return new String[]{MINUTE_FMT.format(start), MINUTE_FMT.format(end)};
    }

    /** HH:mm 解析（允许 H:mm 与带秒的显示值；24:00 保留为 24 点由调用方折算次日） */
    private int[] parseHm(String raw, String label) {
        String s = DutySupport.trim(raw);
        if (s == null) {
            throw new IllegalArgumentException(label + "为空");
        }
        String text = s;
        int space = text.lastIndexOf(' ');
        if (space > 0) {
            text = text.substring(space + 1);
        }
        String[] parts = text.split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException(label + "格式应为 HH:mm：" + s);
        }
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour < 0 || hour > 24 || minute < 0 || minute > 59) {
                throw new NumberFormatException();
            }
            return new int[]{hour, minute};
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "格式应为 HH:mm：" + s);
        }
    }

    /** 值班人员姓名 → 用户 id（顿号/逗号分隔；留空返回空数组） */
    private List<String> resolveMembers(String raw, Directory users) {
        List<String> ids = new ArrayList<>();
        if (raw != null && !raw.trim().isEmpty()) {
            for (String part : raw.split(MEMBER_SEPARATOR)) {
                String name = part.trim();
                if (name.isEmpty()) {
                    continue;
                }
                ids.add(users.resolve(name, "值班人员"));
            }
        }
        return DutySupport.distinctIds(ids);
    }

    // ==================== 私有工具 ====================

    /** 取单元格值（缺列/空值返回空串，统一去首尾空白） */
    private String value(Map<String, String> data, String column) {
        String v = data.get(column);
        return v == null ? "" : v.trim();
    }

    /** 名称/id 索引（单位、人员共用）：按名称查 id，也允许直接填 id；重名单独记下由 resolve 报错 */
    private Directory buildDirectory(List<Map<String, Object>> rows) {
        Directory dir = new Directory();
        for (Map<String, Object> row : rows) {
            String id = DutySupport.trim(asText(row.get("id")));
            String name = DutySupport.trim(asText(row.get("name")));
            if (id == null) {
                continue;
            }
            dir.ids.add(id);
            if (name == null) {
                continue;
            }
            if (dir.nameToId.containsKey(name)) {
                dir.ambiguousNames.add(name);
            } else {
                dir.nameToId.put(name, id);
            }
        }
        return dir;
    }

    private String asText(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /** 名称/id 目录：唯一名称 → id；重名报错提示改填 id */
    private static final class Directory {

        private final Map<String, String> nameToId = new HashMap<>();
        private final Set<String> ambiguousNames = new HashSet<>();
        private final Set<String> ids = new HashSet<>();

        private String resolve(String raw, String label) {
            String key = DutySupport.trim(raw);
            if (key == null) {
                throw new IllegalArgumentException(label + "为空");
            }
            if (ids.contains(key)) {
                return key;
            }
            if (ambiguousNames.contains(key)) {
                throw new IllegalArgumentException(label + "名称重复，请改填 id：" + key);
            }
            String id = nameToId.get(key);
            if (id == null) {
                throw new IllegalArgumentException(label + "不存在：" + key);
            }
            return id;
        }
    }
}
