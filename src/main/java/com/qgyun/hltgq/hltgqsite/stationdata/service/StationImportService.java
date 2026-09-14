package com.qgyun.hltgq.hltgqsite.stationdata.service;

import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.entity.StationArchive;
import com.qgyun.hltgq.hltgqsite.model.util.ExcelParseUtils;
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
 *   <li>渠系类别 / 管理单位：按名称精确匹配（渠系管理表 gfaegg / 档案表 zzkaec），
 *       未匹配或重名判行失败；留空不关联</li>
 *   <li>监测类型 / 监测方法：多选（顿号分隔，兼容 , | ；），每项按中文名称映射为编码
 *       （#N#|#N# 落库）；未知名称判行失败；留空不写</li>
 *   <li>经度 / 纬度：须成对填写，数字且经度 -180~180、纬度 -90~90</li>
 *   <li>站点状态（在线/离线）、是否接通市电（是/否）：中文或编码均可；其他值判行失败</li>
 *   <li>联系电话：11 位手机号（1 开头）</li>
 * </ul>
 */
@Service
public class StationImportService {

    private static final Logger log = LoggerFactory.getLogger(StationImportService.class);

    /** 模板列名（与 StationImport_Template.xlsx 表头一致） */
    private static final String COL_CANAL = "渠系类别";
    private static final String COL_NAME = "站点名称";
    private static final String COL_CODE = "站点编号";
    private static final String COL_LOCATION = "站点位置";
    private static final String COL_TYPE = "监测类型";
    private static final String COL_LON = "经度";
    private static final String COL_LAT = "纬度";
    private static final String COL_STATUS = "站点状态";
    private static final String COL_UNIT = "管理单位";
    private static final String COL_POWER = "是否接通市电";
    private static final String COL_METHOD = "监测方法";
    private static final String COL_COMM = "传输方式";
    private static final String COL_OWNER = "负责人";
    private static final String COL_PHONE = "联系电话";
    private static final String COL_INTRO = "介绍";
    private static final String COL_MODEL = "模型类型";
    private static final String COL_NOTE = "操作说明";

    /** 必需表头列（缺失直接 400） */
    private static final String[] REQUIRED_COLUMNS = {COL_NAME, COL_CODE};

    /** 数据行上限（与模板预留区域一致） */
    private static final int MAX_ROWS = 300;

    /** 监测类型：填写值（中文或编码）→ 落库编码（epjutj，9 档） */
    private static final Map<String, String> TYPE_CODES = new LinkedHashMap<>();

    /** 监测方法：填写值（中文或编码）→ 落库编码（nxtggq，8 档） */
    private static final Map<String, String> METHOD_CODES = new LinkedHashMap<>();

    /** 站点状态：在线/离线 → 编码（zebpsu） */
    private static final Map<String, String> STATUS_CODES = new LinkedHashMap<>();

    /** 是否接通市电：是/否 → 编码（waljdn） */
    private static final Map<String, String> POWER_CODES = new LinkedHashMap<>();

    static {
        TYPE_CODES.put("水位站", "#1#");
        TYPE_CODES.put("雨量站", "#2#");
        TYPE_CODES.put("流量站", "#3#");
        TYPE_CODES.put("闸站", "#4#");
        TYPE_CODES.put("视频站", "#5#");
        TYPE_CODES.put("模型", "#6#");
        TYPE_CODES.put("墒情", "#7#");
        TYPE_CODES.put("水质", "#8#");
        TYPE_CODES.put("气象", "#9#");
        for (int i = 1; i <= 9; i++) {
            TYPE_CODES.put("#" + i + "#", "#" + i + "#");
        }

        METHOD_CODES.put("水工建筑物法", "#1#");
        METHOD_CODES.put("全自动测流车", "#2#");
        METHOD_CODES.put("单垂线流速分布法", "#3#");
        METHOD_CODES.put("雷达波量水", "#4#");
        METHOD_CODES.put("雷达测流矩阵", "#5#");
        METHOD_CODES.put("ADCP 测流", "#6#");
        METHOD_CODES.put("一体化闸门", "#7#");
        METHOD_CODES.put("土壤墒情", "#8#");
        for (int i = 1; i <= 8; i++) {
            METHOD_CODES.put("#" + i + "#", "#" + i + "#");
        }

        STATUS_CODES.put("在线", "#1#");
        STATUS_CODES.put("离线", "#2#");
        STATUS_CODES.put("#1#", "#1#");
        STATUS_CODES.put("#2#", "#2#");

        POWER_CODES.put("是", "#1#");
        POWER_CODES.put("否", "#2#");
        POWER_CODES.put("#1#", "#1#");
        POWER_CODES.put("#2#", "#2#");
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
                rowResult.setMessage(rootMessage(e));
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

        // 3. 渠系类别（按渠系名称精确匹配渠系管理表；留空不关联）
        String canalRaw = value(data, COL_CANAL);
        String canalId = canalRaw.isEmpty() ? null
                : matchSingleId(canalRaw, "渠系", mapper.selectCanalIds(canalRaw));

        // 4. 管理单位（按名称精确匹配档案表自关联记录；留空不关联）
        String unitRaw = value(data, COL_UNIT);
        String unitId = unitRaw.isEmpty() ? null
                : matchSingleId(unitRaw, "管理单位", mapper.selectUnitIds(unitRaw));

        // 5. 监测类型 / 监测方法（多选，名称 → 编码拼接）
        String typeCodes = parseMultiCodes(value(data, COL_TYPE), TYPE_CODES, "监测类型");
        String methodCodes = parseMultiCodes(value(data, COL_METHOD), METHOD_CODES, "监测方法");

        // 6. 坐标定位（经度/纬度须成对填写）
        BigDecimal[] coords = parseCoords(value(data, COL_LON), value(data, COL_LAT));

        // 7. 站点状态 / 是否接通市电（中文或编码；留空不写）
        String statusRaw = value(data, COL_STATUS);
        String status = statusRaw.isEmpty() ? null
                : requireCode(statusRaw, STATUS_CODES, "站点状态无效（应为 在线/离线）：");
        String powerRaw = value(data, COL_POWER);
        String power = powerRaw.isEmpty() ? null
                : requireCode(powerRaw, POWER_CODES, "是否接通市电无效（应为 是/否）：");

        // 8. 联系电话（11 位手机号）
        String phone = value(data, COL_PHONE);
        if (!phone.isEmpty() && !phone.matches("1\\d{10}")) {
            throw new IllegalArgumentException("联系电话格式无效（应为 11 位手机号）：" + phone);
        }

        // 9. 组装入库（系统字段后端生成；null 字段不写入）
        StationArchive entity = new StationArchive();
        entity.setName(name);
        entity.setCode(code);
        entity.setLocation(emptyToNull(value(data, COL_LOCATION)));
        entity.setTypeCodes(typeCodes);
        entity.setLon(coords[0]);
        entity.setLat(coords[1]);
        entity.setStatus(status);
        entity.setUnitId(unitId);
        entity.setCanalId(canalId);
        entity.setMainsPower(power);
        entity.setMethodCodes(methodCodes);
        entity.setComm(emptyToNull(value(data, COL_COMM)));
        entity.setOwner(emptyToNull(value(data, COL_OWNER)));
        entity.setPhone(emptyToNull(phone));
        entity.setIntro(emptyToNull(value(data, COL_INTRO)));
        entity.setModelType(emptyToNull(value(data, COL_MODEL)));
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
            throw new IllegalArgumentException("入库失败：" + rootMessage(e));
        }
        usedCodes.add(code);
        return entity;
    }

    // ==================== 私有工具 ====================

    /** 单选名称匹配：0 行未匹配 / 多行重名均判行失败；1 行返回 id */
    private String matchSingleId(String raw, String label, List<String> ids) {
        if (ids.isEmpty()) {
            throw new IllegalArgumentException(label + "「" + raw + "」未匹配");
        }
        if (ids.size() > 1) {
            throw new IllegalArgumentException(label + "「" + raw + "」存在多个匹配");
        }
        return ids.get(0);
    }

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

    /** 空串转 null（数据库写入 null，不写空串） */
    private String emptyToNull(String v) {
        return v == null || v.isEmpty() ? null : v;
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
            log.warn("[站点导入] 当前请求无用户上下文，created_by/updated_by 置空");
            return null;
        }
        return user.getUserId();
    }
}
