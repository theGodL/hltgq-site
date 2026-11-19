package com.qgyun.hltgq.hltgqsite.sms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ExcelParseUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ExcelTemplateUtils;
import com.qgyun.hltgq.hltgqsite.sms.client.AliyunSmsClient;
import com.qgyun.hltgq.hltgqsite.sms.entity.SmsReceiver;
import com.qgyun.hltgq.hltgqsite.sms.mapper.SmsReceiverMapper;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsReceiverSaveVO;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsReceiverVO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 短信接收人管理（页面 message-rule-setting.html「短信管理」Tab）与发送取人服务。
 *
 * <p><b>发送取人口径（宁可少发不误发）</b>：发送<b>完全以接收人表为准</b>，不再读取任何配置清单：
 * <ul>
 *   <li>表不存在（未执行 sms-receiver-ddl.sql）或查询失败 → 本次不发送，并记 error 日志；</li>
 *   <li>表内某类型 0 人 → 该类型不发送（页面删除即停发，不会被旧配置静默复活）。</li>
 * </ul>
 *
 * <p><b>列表口径</b>：表未建/为空时 page 返回空列表（页面提示新增），不再展示配置虚拟行。
 */
@Service
public class SmsReceiverService {

    private static final Logger log = LoggerFactory.getLogger(SmsReceiverService.class);

    /** 短信类型：每日 8 点报 */
    public static final String BIZ_DAILY = "daily";

    /** 短信类型：报警短信（雨量告警 / 水位涨幅共用同一清单） */
    public static final String BIZ_ALARM = "alarm";

    /** 短信类型字典（编码 → 名称，顺序即页面展示顺序） */
    private static final String[][] BIZ_TYPE_LABELS = {
            {BIZ_DAILY, "每日8点报"}, {BIZ_ALARM, "报警短信"}
    };

    /** 手机号格式：1 开头 11 位数字 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");

    private static final int NAME_MAX_LENGTH = 32;

    private static final int PAGE_SIZE_DEFAULT = 20;

    private static final int PAGE_SIZE_MAX = 200;

    private final SmsReceiverMapper receiverMapper;

    /** 企业编码兜底（会话缺失时使用，列可空但保持平台惯例） */
    @Value("${hltgq.corp-code}")
    private String corpCode;

    /** 操作人兜底（会话缺失时使用） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public SmsReceiverService(SmsReceiverMapper receiverMapper) {
        this.receiverMapper = receiverMapper;
    }

    // ==================== 发送取人（发送链路唯一入口） ====================

    /**
     * 解析某类型的发送手机号清单（唯一来源：接收人表）。
     * <p>表不存在/查询失败或该类型 0 人 → 返回空清单（不发送），<b>不抛异常</b>，
     * 不影响 08:01 日汇总与告警任务的其余逻辑。
     */
    public List<String> resolvePhones(String bizType) {
        String type = requireBizType(bizType);
        List<String> phones;
        try {
            phones = receiverMapper.selectPhonesByType(type);
        } catch (Exception e) {
            log.error("[短信接收人] 接收人表不可用（请确认已执行 sms-receiver-ddl.sql），本次不发送 bizType={} err={}",
                    type, e.toString());
            return Collections.emptyList();
        }
        if (phones.isEmpty()) {
            log.warn("[短信接收人] 接收人表中 {} 类型无接收人，本次不发送（如需接收请在短信管理页添加）", type);
            return Collections.emptyList();
        }
        log.info("[短信接收人] 发送取人 bizType={} 人数={} 号码={}", type, phones.size(), masked(phones));
        return phones;
    }

    // ==================== 页面：元数据 / 分页 ====================

    /** 元数据：类型字典、表状态（未建 / 空 / 已有数据） */
    public Map<String, Object> meta() {
        long tableCount = countAllSafe();
        boolean ddlReady = tableCount >= 0;
        long total = ddlReady ? tableCount : 0;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bizTypes", bizTypeOptions());
        out.put("ddlReady", ddlReady);
        out.put("tableCount", total);
        out.put("mode", total > 0 ? "table" : "empty");
        return out;
    }

    /**
     * 接收人分页：bizType 可选过滤；表未建时返回空列表（ddlReady=false，页面提示执行 DDL）。
     */
    public Map<String, Object> page(int page, int size, String bizType) {
        String type = optionalBizType(bizType);
        int safePage = Math.max(page, 1);
        int safeSize = normalizeSize(size);
        long tableCount = countAllSafe();
        boolean ddlReady = tableCount >= 0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ddlReady", ddlReady);
        out.put("mode", tableCount > 0 ? "table" : "empty");
        if (!ddlReady) {
            out.put("total", 0L);
            out.put("page", (long) safePage);
            out.put("size", (long) safeSize);
            out.put("pages", 0L);
            out.put("records", Collections.emptyList());
            return out;
        }

        IPage<SmsReceiverVO> result = receiverMapper.selectReceiverPage(
                new Page<>(safePage, safeSize), type);
        for (SmsReceiverVO vo : result.getRecords()) {
            vo.setBizTypeLabel(bizTypeName(vo.getBizType()));
            vo.setSource("table");
        }
        out.put("total", result.getTotal());
        out.put("page", result.getCurrent());
        out.put("size", result.getSize());
        out.put("pages", result.getPages());
        out.put("records", result.getRecords());
        return out;
    }

    // ==================== 导入 / 导出（Excel） ====================

    /** 导出/导入表头（顺序即列序：类型、姓名、手机号） */
    public static final String[] EXCEL_HEADERS = {"短信类型", "姓名", "手机号码"};

    /** 导入行数上限（接收人量级远小于此） */
    private static final int IMPORT_MAX_ROWS = 1000;

    /** 导入类型字典：中文名/代码（忽略大小写）→ 落库代码 */
    private static final Map<String, String> IMPORT_TYPE_CODES = new LinkedHashMap<>();

    static {
        IMPORT_TYPE_CODES.put("每日8点报", BIZ_DAILY);
        IMPORT_TYPE_CODES.put(BIZ_DAILY, BIZ_DAILY);
        IMPORT_TYPE_CODES.put("报警短信", BIZ_ALARM);
        IMPORT_TYPE_CODES.put(BIZ_ALARM, BIZ_ALARM);
    }

    /**
     * 导出接收人 xlsx（当前筛选下的全部数据，不分页）：列 = 类型中文名 / 姓名 / 手机号码（号码写为文本）。
     */
    public byte[] exportExcel(String bizType) {
        String type = optionalBizType(bizType);
        List<SmsReceiver> rows;
        try {
            QueryWrapper<SmsReceiver> query = new QueryWrapper<>();
            if (type != null) {
                query.eq("\"biz_type\"", type);
            }
            rows = receiverMapper.selectList(query);
        } catch (Exception e) {
            throw new IllegalArgumentException("接收人表不存在，请先执行 sms-receiver-ddl.sql");
        }
        // 类型按字典序（每日8点报在前），同类型按号码
        rows.sort((a, b) -> {
            int t = Integer.compare(typeOrder(a.getBizType()), typeOrder(b.getBizType()));
            return t != 0 ? t : String.valueOf(a.getPhone()).compareTo(String.valueOf(b.getPhone()));
        });
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("短信接收人");
            CellStyle headStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headStyle.setFont(bold);
            Row head = sheet.createRow(0);
            for (int i = 0; i < EXCEL_HEADERS.length; i++) {
                Cell cell = head.createCell(i);
                cell.setCellValue(EXCEL_HEADERS[i]);
                cell.setCellStyle(headStyle);
            }
            int index = 1;
            for (SmsReceiver row : rows) {
                Row line = sheet.createRow(index++);
                line.createCell(0).setCellValue(bizTypeName(row.getBizType()));
                line.createCell(1).setCellValue(row.getName() == null ? "" : row.getName());
                line.createCell(2).setCellValue(row.getPhone() == null ? "" : row.getPhone());
            }
            ExcelTemplateUtils.fitColumnWidths(sheet, EXCEL_HEADERS);
            sheet.setColumnWidth(1, Math.max(sheet.getColumnWidth(1), 16 * 256));
            sheet.setColumnWidth(2, Math.max(sheet.getColumnWidth(2), 16 * 256));
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("导出失败：" + e.getMessage());
        }
    }

    /**
     * 导入接收人 xlsx（第一行必须是表头，且含「短信类型」「姓名」「手机号码」三列；姓名单元格可留空）。
     * <p><b>安全红线</b>：识别不到表头或缺列（如误删表头/删掉姓名列、首行就是数据）→ 整单拒绝，
     * 绝不按列序猜测，避免首行数据被当表头丢弃后误删号码，或姓名列缺失时整批姓名被清空。
     * <p><b>整体校验</b>：任一行不合法则整体拒绝（400），返回前 5 条错误便于修正后重导。
     * <p><b>写入口径</b>：mode=replace（默认）以文件为准——<b>文件中出现的每个类型</b>，
     * 库中不在文件内的接收人删除，文件未出现的类型不受影响；mode=merge 仅新增/更新，不删除。
     * <p>姓名允许为空（兼容存量未补录数据）；填写时 ≤32 字。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importExcel(MultipartFile file, String mode) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的 xlsx 文件");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("仅支持 .xlsx 格式文件（可直接使用导出的文件编辑后导入）");
        }
        boolean replace = !"merge".equalsIgnoreCase(trim(mode));

        ExcelParseUtils.SheetData sheet;
        try {
            sheet = ExcelParseUtils.parseSheet(file.getInputStream());
        } catch (Exception e) {
            throw new IllegalArgumentException("文件解析失败，请确认是有效的 xlsx 文件：" + e.getMessage());
        }
        List<ExcelParseUtils.RowEntry> rows = sheet.getRows();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("文件中没有数据行（第一行为表头，数据从第二行开始）");
        }
        if (rows.size() > IMPORT_MAX_ROWS) {
            throw new IllegalArgumentException("单次最多导入 " + IMPORT_MAX_ROWS + " 行，当前文件 " + rows.size() + " 行");
        }

        // 定位列：必须按表头关键字识别（不按列序猜）；识别不到表头直接拒绝，防止首行数据被当表头丢弃后误删
        List<String> headers = sheet.getHeaders();
        int typeCol = findColumn(headers, "短信类型", "类型", "biz_type", "bizType");
        int nameCol = findColumn(headers, "姓名", "接收人", "名字", "name");
        int phoneCol = findColumn(headers, "手机号码", "手机号", "手机", "号码", "电话", "phone", "mobile");
        if (typeCol < 0 || nameCol < 0 || phoneCol < 0) {
            throw new IllegalArgumentException("未识别到表头：第一行需包含「短信类型」「姓名」「手机号码」三列"
                    + "（请直接使用导出的文件填写，不要删除或修改表头）");
        }

        List<String> errors = new ArrayList<>();
        List<SmsReceiver> parsed = new ArrayList<>();
        Set<String> fileKeys = new HashSet<>();
        for (ExcelParseUtils.RowEntry entry : rows) {
            Map<String, String> data = entry.getData();
            String rawType = trim(cellValue(data, headers, typeCol));
            String name = trim(cellValue(data, headers, nameCol));
            String phone = trim(cellValue(data, headers, phoneCol));
            String typeCode = rawType == null ? null : IMPORT_TYPE_CODES.get(rawType.toLowerCase(Locale.ROOT));
            String prefix = "第 " + entry.getRowNum() + " 行：";
            if (typeCode == null) {
                errors.add(prefix + "短信类型须为「每日8点报」或「报警短信」");
                continue;
            }
            if (name != null && name.length() > NAME_MAX_LENGTH) {
                errors.add(prefix + "姓名最长 " + NAME_MAX_LENGTH + " 字");
                continue;
            }
            if (phone == null) {
                errors.add(prefix + "手机号码不能为空");
                continue;
            }
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                errors.add(prefix + "手机号码格式不正确（须为 1 开头的 11 位数字）");
                continue;
            }
            if (!fileKeys.add(typeCode + "|" + phone)) {
                errors.add(prefix + "文件内同类型同号码重复");
                continue;
            }
            SmsReceiver row = new SmsReceiver();
            row.setBizType(typeCode);
            row.setName(name);
            row.setPhone(phone);
            parsed.add(row);
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(buildImportError(errors));
        }

        List<SmsReceiver> existing;
        try {
            existing = receiverMapper.selectList(null);
        } catch (Exception e) {
            throw new IllegalArgumentException("接收人表不存在，请先执行 sms-receiver-ddl.sql");
        }
        Map<String, SmsReceiver> existMap = new LinkedHashMap<>();
        for (SmsReceiver row : existing) {
            existMap.put(row.getBizType() + "|" + row.getPhone(), row);
        }

        String operator = currentOperatorId();
        LocalDateTime now = LocalDateTime.now();
        Set<String> fileTypes = new HashSet<>();
        Set<String> keptIds = new HashSet<>();
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        int noName = 0;
        for (SmsReceiver row : parsed) {
            fileTypes.add(row.getBizType());
            if (row.getName() == null) {
                noName++;
            }
            SmsReceiver old = existMap.get(row.getBizType() + "|" + row.getPhone());
            if (old == null) {
                applyCreateAudit(row);
                receiverMapper.insert(row);
                keptIds.add(row.getId());
                inserted++;
                continue;
            }
            keptIds.add(old.getId());
            if (sameName(old.getName(), row.getName())) {
                unchanged++;
                continue;
            }
            UpdateWrapper<SmsReceiver> wrapper = new UpdateWrapper<>();
            wrapper.eq("\"id\"", old.getId());
            wrapper.set("\"name\"", row.getName());
            wrapper.set("\"updated_at\"", now);
            wrapper.set("\"updated_by\"", operator);
            receiverMapper.update(null, wrapper);
            updated++;
        }

        int deleted = 0;
        if (replace) {
            for (SmsReceiver old : existing) {
                if (fileTypes.contains(old.getBizType()) && !keptIds.contains(old.getId())) {
                    receiverMapper.deleteById(old.getId());
                    deleted++;
                }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", replace ? "replace" : "merge");
        out.put("total", parsed.size());
        out.put("inserted", inserted);
        out.put("updated", updated);
        out.put("deleted", deleted);
        out.put("unchanged", unchanged);
        out.put("noName", noName);
        log.info("[短信管理] 导入接收人：mode={} 行数={} 新增={} 更新={} 删除={} 未变={} 姓名待补={} 操作人={}",
                replace ? "replace" : "merge", parsed.size(), inserted, updated, deleted, unchanged, noName, operator);
        return out;
    }

    // ==================== 新增 / 编辑 / 删除 ====================

    /** 新增接收人：类型/姓名/号码必填，同类型同号码查重 */
    public SmsReceiverVO create(SmsReceiverSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String bizType = requireBizType(req.getBizType());
        String name = requireName(req.getName());
        String phone = requirePhone(req.getPhone());
        if (receiverMapper.countSamePhone(bizType, phone, null) > 0) {
            throw new IllegalArgumentException("该类型下手机号 " + phone + " 已存在，请勿重复添加");
        }
        SmsReceiver entity = new SmsReceiver();
        entity.setBizType(bizType);
        entity.setName(name);
        entity.setPhone(phone);
        applyCreateAudit(entity);
        receiverMapper.insert(entity);
        log.info("[短信管理] 新增接收人：id={} 类型={} 姓名={} 号码={} 操作人={}",
                entity.getId(), bizTypeName(bizType), name, AliyunSmsClient.maskPhone(phone), entity.getUpdatedBy());
        return detail(entity.getId());
    }

    /** 编辑接收人：类型锁定，可改姓名/号码，查重排除自身 */
    public SmsReceiverVO update(String id, SmsReceiverSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        SmsReceiver row = requireRow(id);
        String reqType = trim(req.getBizType());
        if (reqType != null && !reqType.equals(row.getBizType())) {
            throw new IllegalArgumentException("短信类型不可修改，如需更换请删除后重新新增");
        }
        String name = requireName(req.getName());
        String phone = requirePhone(req.getPhone());
        if (receiverMapper.countSamePhone(row.getBizType(), phone, row.getId()) > 0) {
            throw new IllegalArgumentException("该类型下手机号 " + phone + " 已存在");
        }
        String operator = currentOperatorId();
        UpdateWrapper<SmsReceiver> wrapper = new UpdateWrapper<>();
        wrapper.eq("\"id\"", row.getId());
        wrapper.set("\"name\"", name);
        wrapper.set("\"phone\"", phone);
        wrapper.set("\"updated_at\"", LocalDateTime.now());
        wrapper.set("\"updated_by\"", operator);
        receiverMapper.update(null, wrapper);
        log.info("[短信管理] 编辑接收人：id={} 类型={} 姓名={} 号码={} 操作人={}",
                row.getId(), bizTypeName(row.getBizType()), name, AliyunSmsClient.maskPhone(phone), operator);
        return detail(row.getId());
    }

    /** 删除接收人（物理删除；已发短信记录不受影响） */
    public void delete(String id) {
        SmsReceiver row = requireRow(id);
        receiverMapper.deleteById(row.getId());
        log.info("[短信管理] 删除接收人：id={} 类型={} 姓名={} 号码={} 操作人={}",
                row.getId(), bizTypeName(row.getBizType()), row.getName(),
                AliyunSmsClient.maskPhone(row.getPhone()), currentOperatorId());
    }

    /** 单行详情（新增/编辑回显） */
    public SmsReceiverVO detail(String id) {
        SmsReceiver row = requireRow(id);
        SmsReceiverVO vo = new SmsReceiverVO();
        vo.setId(row.getId());
        vo.setBizType(row.getBizType());
        vo.setBizTypeLabel(bizTypeName(row.getBizType()));
        vo.setName(row.getName());
        vo.setPhone(row.getPhone());
        vo.setSource("table");
        vo.setUpdatedAt(row.getUpdatedAt());
        return vo;
    }

    // ==================== 校验与工具 ====================

    private SmsReceiver requireRow(String id) {
        String key = trim(id);
        if (key == null) {
            throw new IllegalArgumentException("接收人 id 不能为空");
        }
        SmsReceiver row = receiverMapper.selectById(key);
        if (row == null) {
            throw new IllegalArgumentException("接收人不存在或已删除");
        }
        return row;
    }

    private static String requireBizType(String raw) {
        String code = trim(raw);
        for (String[] pair : BIZ_TYPE_LABELS) {
            if (pair[0].equals(code)) {
                return code;
            }
        }
        throw new IllegalArgumentException("短信类型仅支持 daily（每日8点报）/ alarm（报警短信）");
    }

    private static String optionalBizType(String raw) {
        String code = trim(raw);
        return code == null ? null : requireBizType(code);
    }

    private static String requireName(String raw) {
        String name = trim(raw);
        if (name == null) {
            throw new IllegalArgumentException("请输入姓名");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("姓名最长 " + NAME_MAX_LENGTH + " 字");
        }
        return name;
    }

    private static String requirePhone(String raw) {
        String phone = trim(raw);
        if (phone == null) {
            throw new IllegalArgumentException("请输入手机号码");
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("手机号码格式不正确（须为 1 开头的 11 位数字）");
        }
        return phone;
    }

    private static String bizTypeName(String code) {
        for (String[] pair : BIZ_TYPE_LABELS) {
            if (pair[0].equals(code)) {
                return pair[1];
            }
        }
        return code;
    }

    private List<Map<String, Object>> bizTypeOptions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String[] pair : BIZ_TYPE_LABELS) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", pair[0]);
            item.put("label", pair[1]);
            out.add(item);
        }
        return out;
    }

    /** 类型导出排序：daily=0 / alarm=1 / 其他=2 */
    private static int typeOrder(String bizType) {
        if (BIZ_DAILY.equals(bizType)) {
            return 0;
        }
        if (BIZ_ALARM.equals(bizType)) {
            return 1;
        }
        return 2;
    }

    /** 按表头关键字定位列（忽略大小写）；未识别到返回 -1（不按列序兜底） */
    private static int findColumn(List<String> headers, String... keys) {
        for (int i = 0; i < headers.size(); i++) {
            String header = trim(headers.get(i));
            if (header == null) {
                continue;
            }
            for (String key : keys) {
                if (header.equalsIgnoreCase(key)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 取某列单元格字符串（列不存在时返回空串） */
    private static String cellValue(Map<String, String> data, List<String> headers, int index) {
        if (index < 0 || index >= headers.size()) {
            return "";
        }
        String value = data.get(headers.get(index));
        return value == null ? "" : value;
    }

    /** 姓名比较（空串与 null 等价） */
    private static boolean sameName(String left, String right) {
        String a = trim(left);
        String b = trim(right);
        return a == null ? b == null : a.equals(b);
    }

    /** 拼装导入校验错误提示（总数 + 前 5 条） */
    private static String buildImportError(List<String> errors) {
        StringBuilder sb = new StringBuilder("导入校验未通过，共 ").append(errors.size()).append(" 处问题：");
        int limit = Math.min(errors.size(), 5);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append("；");
            }
            sb.append(errors.get(i));
        }
        if (errors.size() > limit) {
            sb.append("；……");
        }
        return sb.toString();
    }

    /** 表内总数：-1 = 表不存在/查询异常（页面据此提示执行 DDL；发送时不发） */
    private long countAllSafe() {
        try {
            return receiverMapper.countAll();
        } catch (Exception e) {
            log.debug("[短信接收人] 接收人表不可用：{}", e.toString());
            return -1L;
        }
    }

    /** 号码清单打码（日志不落明文） */
    private static String masked(List<String> phones) {
        List<String> out = new ArrayList<>(phones.size());
        for (String phone : phones) {
            out.add(AliyunSmsClient.maskPhone(phone));
        }
        return String.join(",", out);
    }

    private void applyCreateAudit(SmsReceiver entity) {
        UserContext user = UserContextHolder.currentUser();
        String operator = user != null && trim(user.getUserId()) != null
                ? user.getUserId() : fallbackOperator;
        String corp = user != null && trim(user.getCorpCode()) != null
                ? user.getCorpCode() : corpCode;
        LocalDateTime now = LocalDateTime.now();
        entity.setCorpCode(corp);
        entity.setCreatedAt(now);
        entity.setCreatedBy(operator);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operator);
    }

    private String currentOperatorId() {
        UserContext user = UserContextHolder.currentUser();
        if (user != null && trim(user.getUserId()) != null) {
            return user.getUserId();
        }
        return fallbackOperator;
    }

    private static int normalizeSize(int size) {
        if (size < 1) {
            return PAGE_SIZE_DEFAULT;
        }
        return Math.min(size, PAGE_SIZE_MAX);
    }

    private static String trim(String value) {
        return CheckUtils.trim(value);
    }
}
