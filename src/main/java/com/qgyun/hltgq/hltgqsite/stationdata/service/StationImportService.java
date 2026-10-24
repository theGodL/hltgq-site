package com.qgyun.hltgq.hltgqsite.stationdata.service;

import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.entity.StationArchive;
import com.qgyun.hltgq.hltgqsite.model.util.ExcelParseUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ExceptionUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ValueConvertUtils;
import com.qgyun.hltgq.hltgqsite.stationdata.mapper.StationImportMapper;
import com.qgyun.hltgq.hltgqsite.stationdata.vo.StationImportResult;
import com.qgyun.hltgq.hltgqsite.stationdata.vo.StationImportRowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 站点信息 Excel 导入服务（POST /station-data/import，模板见 GET /station-data/import-template）。
 *
 * <p>处理流程：文件校验（非空/.xlsx/≤300 行）→ 表头校验（缺必需列直接 400）→ 逐行解析入库。
 * 逐行独立：单行失败不影响其他行；校验通过的行单条 INSERT 落库（单语句天然原子，无需显式事务）。
 *
 * <p>逐行规则（契约定稿见 src/main/resources/导入-导出对接接口.md）：
 * <ul>
 *   <li>站点名称 / 站点编号：必填；编号批内与库中双重防重（重复行失败）</li>
 *   <li>监测类型 / 监测方法：多选（顿号分隔，兼容 , | ；），每项按中文名称映射为编码
 *       （#N#|#N# 落库）；未知名称判行失败；留空不写</li>
 *   <li>经度 / 纬度：须成对填写，数字且经度 -180~180、纬度 -90~90</li>
 *   <li>站点状态（在线/离线）、是否接通市电（是/否）：中文或编码均可；其他值判行失败</li>
 *   <li>联系电话：11 位手机号（1 开头）</li>
 *   <li>其余文本列（站点位置 / 负责人 / 传输方式 / 介绍 / 操作规程）：原样写入，留空不写</li>
 * </ul>
 */
@Service
public class StationImportService {

    private static final Logger log = LoggerFactory.getLogger(StationImportService.class);

    /** 模板列名（与动态生成模板 / 导出列一致，顺序即模板列序） */
    private static final String COL_NAME = "站点名称";
    private static final String COL_CODE = "站点编号";
    private static final String COL_LOCATION = "站点位置";
    private static final String COL_LON = "经度";
    private static final String COL_LAT = "纬度";
    private static final String COL_TYPE = "监测类型";
    private static final String COL_STATUS = "站点状态";
    private static final String COL_METHOD = "监测方法";
    private static final String COL_OWNER = "负责人";
    private static final String COL_PHONE = "联系电话";
    private static final String COL_POWER = "是否接通市电";
    private static final String COL_COMM = "传输方式";
    private static final String COL_INTRO = "介绍";
    private static final String COL_NOTE = "操作规程";

    /** 模板表头（顺序即模板列序；模板块与解析列名共用，防止两处漂移） */
    public static final String[] TEMPLATE_HEADERS = {
            COL_NAME, COL_CODE, COL_LOCATION, COL_LON, COL_LAT, COL_TYPE, COL_STATUS, COL_METHOD,
            COL_OWNER, COL_PHONE, COL_POWER, COL_COMM, COL_INTRO, COL_NOTE};

    /** 监测类型选项（模板下拉与解析映射共用） */
    public static final String[] TYPE_OPTIONS = {
            "水位站", "雨量站", "流量站", "闸站", "视频站", "模型", "墒情", "水质", "气象"};

    /** 监测方法选项（模板下拉与解析映射共用） */
    public static final String[] METHOD_OPTIONS = {
            "水工建筑物法", "全自动测流车", "单垂线流速分布法", "雷达波量水",
            "雷达测流矩阵", "ADCP 测流", "一体化闸门", "土壤墒情"};

    /** 站点状态选项（模板下拉与解析映射共用） */
    public static final String[] STATUS_OPTIONS = {"在线", "离线"};

    /** 是否接通市电选项（模板下拉与解析映射共用） */
    public static final String[] POWER_OPTIONS = {"是", "否"};

    /** 必需表头列（缺失直接 400） */
    private static final String[] REQUIRED_COLUMNS = {COL_NAME, COL_CODE};

    /** 数据行上限（与模板预留区域一致；控制器生成模板时引用） */
    public static final int MAX_ROWS = 300;

    /** 监测类型：填写值（中文或编码）→ 落库编码（epjutj，9 档） */
    private static final Map<String, String> TYPE_CODES = new LinkedHashMap<>();

    /** 监测方法：填写值（中文或编码）→ 落库编码（nxtggq，8 档） */
    private static final Map<String, String> METHOD_CODES = new LinkedHashMap<>();

    /** 站点状态：在线/离线 → 编码（zebpsu） */
    private static final Map<String, String> STATUS_CODES = new LinkedHashMap<>();

    /** 是否接通市电：是/否 → 编码（waljdn） */
    private static final Map<String, String> POWER_CODES = new LinkedHashMap<>();

    static {
        for (int i = 0; i < TYPE_OPTIONS.length; i++) {
            TYPE_CODES.put(TYPE_OPTIONS[i], "#" + (i + 1) + "#");
        }
        for (int i = 1; i <= TYPE_OPTIONS.length; i++) {
            TYPE_CODES.put("#" + i + "#", "#" + i + "#");
        }

        for (int i = 0; i < METHOD_OPTIONS.length; i++) {
            METHOD_CODES.put(METHOD_OPTIONS[i], "#" + (i + 1) + "#");
        }
        for (int i = 1; i <= METHOD_OPTIONS.length; i++) {
            METHOD_CODES.put("#" + i + "#", "#" + i + "#");
        }

        for (int i = 0; i < STATUS_OPTIONS.length; i++) {
            STATUS_CODES.put(STATUS_OPTIONS[i], "#" + (i + 1) + "#");
            STATUS_CODES.put("#" + (i + 1) + "#", "#" + (i + 1) + "#");
        }

        for (int i = 0; i < POWER_OPTIONS.length; i++) {
            POWER_CODES.put(POWER_OPTIONS[i], "#" + (i + 1) + "#");
            POWER_CODES.put("#" + (i + 1) + "#", "#" + (i + 1) + "#");
        }
    }

    /** 多值分隔符：顿号为主，兼容逗号/竖线/全角分号 */
    private static final String MULTI_VALUE_SEPARATOR = "[、,|；]+";

    @Autowired
    private StationImportMapper mapper;

    @Value("${hltgq.corp-code}")
    private String corpCode;

    /**
     * 导入站点信息 xlsx：整体性错误抛 IllegalArgumentException（→400），
     * 行级问题在返回结果中逐行给出，不中断其他行。
     */
    public StationImportResult importFile(MultipartFile file) {
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
        StationImportResult result = new StationImportResult();
        for (ExcelParseUtils.RowEntry entry : sheet.getRows()) {
            StationImportRowResult rowResult = new StationImportRowResult(entry.getRowNum());
            try {
                StationArchive entity = processRow(entry.getData(), usedCodes, operatorId);
                rowResult.setOk(true);
                rowResult.setId(entity.getId());
                rowResult.setCode(entity.getCode());
                result.setSuccess(result.getSuccess() + 1);
            } catch (Exception e) {
                rowResult.setOk(false);
                rowResult.setMessage(ExceptionUtils.rootMessage(e, 200));
                result.setFailed(result.getFailed() + 1);
                log.error("[站点导入] 第 {} 行失败：{}", entry.getRowNum(), e.getMessage());
            }
            result.getRows().add(rowResult);
        }
        result.setTotal(sheet.getRows().size());
        log.info("[站点导入] 完成：total={}, success={}, failed={}, 操作人={}",
                result.getTotal(), result.getSuccess(), result.getFailed(), operatorId);
        return result;
    }

    // ==================== 逐行处理 ====================

    /** 单行解析校验并入库（单条 INSERT）；返回实体（含生成的 id），失败抛异常 */
    private StationArchive processRow(Map<String, String> data, Set<String> usedCodes, String operatorId) {
        // 1. 站点名称（必填）
        String name = value(data, COL_NAME);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("站点名称为空");
        }

        // 2. 站点编号（必填，批内 + 库中双重防重）
        String code = value(data, COL_CODE);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("站点编号为空");
        }
        if (usedCodes.contains(code)) {
            throw new IllegalArgumentException("站点编号在本批次内重复：" + code);
        }
        if (mapper.countByCode(code) > 0) {
            throw new IllegalArgumentException("站点编号已存在：" + code);
        }

        // 3. 监测类型 / 监测方法（多选，名称 → 编码拼接）
        String typeCodes = parseMultiCodes(value(data, COL_TYPE), TYPE_CODES, "监测类型");
        String methodCodes = parseMultiCodes(value(data, COL_METHOD), METHOD_CODES, "监测方法");

        // 4. 经度 / 纬度（须成对填写）
        BigDecimal[] coords = parseCoords(value(data, COL_LON), value(data, COL_LAT));

        // 5. 站点状态 / 是否接通市电（中文或编码；留空不写）
        String statusRaw = value(data, COL_STATUS);
        String status = statusRaw.isEmpty() ? null
                : requireCode(statusRaw, STATUS_CODES, "站点状态无效（应为 在线/离线）：");
        String powerRaw = value(data, COL_POWER);
        String power = powerRaw.isEmpty() ? null
                : requireCode(powerRaw, POWER_CODES, "是否接通市电无效（应为 是/否）：");

        // 6. 联系电话（11 位手机号）
        String phone = value(data, COL_PHONE);
        if (!phone.isEmpty() && !phone.matches("1\\d{10}")) {
            throw new IllegalArgumentException("联系电话格式无效（应为 11 位手机号）：" + phone);
        }

        // 7. 组装入库（系统字段后端生成；null 字段不写入）
        StationArchive entity = new StationArchive();
        entity.setName(name);
        entity.setCode(code);
        entity.setLocation(emptyToNull(value(data, COL_LOCATION)));
        entity.setTypeCodes(typeCodes);
        entity.setLon(coords[0]);
        entity.setLat(coords[1]);
        entity.setStatus(status);
        entity.setMainsPower(power);
        entity.setMethodCodes(methodCodes);
        entity.setComm(emptyToNull(value(data, COL_COMM)));
        entity.setOwner(emptyToNull(value(data, COL_OWNER)));
        entity.setPhone(emptyToNull(phone));
        entity.setIntro(emptyToNull(value(data, COL_INTRO)));
        entity.setOperationNote(emptyToNull(value(data, COL_NOTE)));
        LocalDateTime now = LocalDateTime.now();
        entity.setCorpCode(corpCode);
        entity.setCreatedAt(now);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
        try {
            mapper.insert(entity);
        } catch (Exception e) {
            throw new IllegalArgumentException("入库失败：" + ExceptionUtils.rootMessage(e, 200));
        }
        usedCodes.add(code);
        return entity;
    }

    // ==================== 私有工具 ====================

    /** 多选：按顿号/逗号/竖线/全角分号拆分 → 逐项映射编码（未知值抛异常）→ 去重保序拼接（| 分隔）；全空返回 null */
    private String parseMultiCodes(String raw, Map<String, String> codes, String label) {
        if (raw.isEmpty()) {
            return null;
        }
        Set<String> out = new LinkedHashSet<>();
        for (String item : splitMulti(raw)) {
            String code = codes.get(item);
            if (code == null) {
                throw new IllegalArgumentException(label + "无效：" + item);
            }
            out.add(code);
        }
        if (out.isEmpty()) {
            return null;
        }
        return String.join("|", out);
    }

    /** 单选枚举：中文或编码 → 落库编码；未知值抛异常 */
    private String requireCode(String raw, Map<String, String> codes, String errorPrefix) {
        String code = codes.get(raw);
        if (code == null) {
            throw new IllegalArgumentException(errorPrefix + raw);
        }
        return code;
    }

    /** 坐标解析：均空返回 [null, null]；仅填一个或非法/超范围抛异常 */
    private BigDecimal[] parseCoords(String rawLon, String rawLat) {
        if (rawLon.isEmpty() && rawLat.isEmpty()) {
            return new BigDecimal[]{null, null};
        }
        if (rawLon.isEmpty() || rawLat.isEmpty()) {
            throw new IllegalArgumentException("经度与纬度需同时填写");
        }
        return new BigDecimal[]{
                parseCoord(rawLon, "经度", new BigDecimal("-180"), new BigDecimal("180")),
                parseCoord(rawLat, "纬度", new BigDecimal("-90"), new BigDecimal("90"))};
    }

    /** 单个坐标解析：数字校验 + 范围校验 */
    private BigDecimal parseCoord(String raw, String label, BigDecimal min, BigDecimal max) {
        BigDecimal v;
        try {
            v = new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "格式无效（应为数字）：" + raw);
        }
        if (v.compareTo(min) < 0 || v.compareTo(max) > 0) {
            throw new IllegalArgumentException(label + "超出范围（" + min.toPlainString()
                    + "~" + max.toPlainString() + "）：" + raw);
        }
        return v;
    }

    /** 多值拆分：按顿号/逗号/竖线/全角分号拆分，去首尾空白，剔除空项（委托 ValueConvertUtils） */
    private List<String> splitMulti(String raw) {
        return ValueConvertUtils.split(raw, MULTI_VALUE_SEPARATOR);
    }

    /** 取单元格值（缺列/空值返回空串，统一去首尾空白） */
    private String value(Map<String, String> data, String column) {
        String v = data.get(column);
        return v == null ? "" : v.trim();
    }

    /** 空串转 null（数据库写入 null，不写空串） */
    private String emptyToNull(String v) {
        return v == null || v.isEmpty() ? null : v;
    }

    /** 当前登录人 userId（鉴权已由拦截器保证；异常场景容错为 null，审计字段留空） */
    private String currentUserId() {
        UserContext user = UserContextHolder.currentUser();
        if (user == null || user.getUserId() == null) {
            log.warn("[站点导入] 当前请求无用户上下文，created_by/updated_by 置空");
            return null;
        }
        return user.getUserId();
    }
}
