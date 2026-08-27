package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qgyun.hltgq.hltgqsite.entity.AllocateRecord;
import com.qgyun.hltgq.hltgqsite.entity.AllocateTenday;
import com.qgyun.hltgq.hltgqsite.mapper.AllocateRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.AllocateTendayMapper;
import com.qgyun.hltgq.hltgqsite.model.service.AllocateService;
import com.qgyun.hltgq.hltgqsite.model.service.ModelRecordCommonService;
import com.qgyun.hltgq.hltgqsite.model.util.ExcelParseUtils;
import com.qgyun.hltgq.hltgqsite.model.util.TenDayMapUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.AllocateSubmitRequest;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 水资源配置接口（/water-allocation）。
 * <p>auto 模式：三个下拉（中长期/需水/损失方案）+ 水位参数；
 * manual 模式：上传配水基础数据收集表（POI 解析 rows）。
 */
@RestController
@RequestMapping("/water-allocation")
public class AllocateController {

    /** 手动输入必填列契约（/allocate mode=manual rows） */
    private static final String[] MANUAL_REQUIRED_COLUMNS = {
            "日期", "BP预测来水量（万方）", "库面蒸发数据（万方）", "灌溉需水", "城镇需水",
            "农村生活需水量", "河道生态需水总量", "塘坝可供水量（万方）", "水厂供水", "花凉亭水库灌溉供水"
    };

    @Autowired
    private AllocateService allocateService;

    @Autowired
    private ModelRecordCommonService commonService;

    @Autowired
    private AllocateRecordMapper recordMapper;

    @Autowired
    private AllocateTendayMapper tendayMapper;

    /** 提交水资源配置（秒回 recordId），后台异步调模型计算 */
    @PostMapping
    public Map<String, Object> submit(@RequestBody AllocateSubmitRequest req) {
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", allocateService.submit(req));
        return result;
    }

    /** 上传配水基础数据收集表（manual 模式）：POI 解析 rows 后异步提交 */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(required = false) Double startLevel,
                                      @RequestParam(required = false) Double floodLimitLevel,
                                      @RequestParam(required = false) Double maxLevel,
                                      @RequestParam(required = false) Boolean useManualSpill,
                                      @RequestParam(required = false) String schemeName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        AllocateSubmitRequest req = new AllocateSubmitRequest();
        req.setMode("manual");
        req.setStartLevel(startLevel);
        req.setFloodLimitLevel(floodLimitLevel);
        req.setMaxLevel(maxLevel);
        req.setUseManualSpill(useManualSpill);
        req.setSchemeName(schemeName);
        req.setRows(parseManualRows(file));
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", allocateService.submit(req));
        return result;
    }

    /** 下载配水基础数据收集表模板（自制：36 旬日期列预填 + 10 列数据空表） */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] bytes = buildTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=配水基础数据收集表.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    /** POI 生成模板：表头 + 36 行（日期列预填 1月上旬~12月下旬） */
    private byte[] buildTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("配水基础数据");
            Row header = sheet.createRow(0);
            for (int i = 0; i < MANUAL_REQUIRED_COLUMNS.length; i++) {
                header.createCell(i).setCellValue(MANUAL_REQUIRED_COLUMNS[i]);
            }
            header.createCell(MANUAL_REQUIRED_COLUMNS.length).setCellValue("弃水");
            int rowIdx = 1;
            for (String label : TenDayMapUtils.FULL_TEN_DAY_MAP.keySet()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(label);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("模板生成失败: " + e.getMessage());
        }
    }

    /** POI 解析上传 Excel → rows（数值列转 Double，日期列保留旬标签字符串） */
    private List<Map<String, Object>> parseManualRows(MultipartFile file) {
        List<Map<String, String>> rows;
        try {
            rows = ExcelParseUtils.parseRows(file.getInputStream());
        } catch (Exception e) {
            throw new IllegalArgumentException("配水基础数据表解析失败（仅支持 .xlsx 格式）: " + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("配水基础数据表无数据行");
        }
        Map<String, String> first = rows.get(0);
        List<String> missing = new ArrayList<>();
        for (String column : MANUAL_REQUIRED_COLUMNS) {
            if (!first.containsKey(column)) {
                missing.add(column);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("配水基础数据表缺少以下列: " + String.join(", ", missing));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, String> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (!"日期".equals(key) && value != null && !value.isEmpty()) {
                    try {
                        item.put(key, Double.parseDouble(value));
                        continue;
                    } catch (NumberFormatException ignored) {
                        // 非数值保留原始字符串
                    }
                }
                item.put(key, value);
            }
            result.add(item);
        }
        return result;
    }

    /** 轮询配置方案执行状态：{id, status, errorMsg} */
    @GetMapping("/status/{id}")
    public Map<String, Object> status(@PathVariable String id) {
        return commonService.status(id, recordMapper);
    }

    /** 配置方案详情：主表 + 旬尺度明细 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        AllocateRecord record = commonService.require(id, recordMapper);
        QueryWrapper<AllocateTenday> wrapper = new QueryWrapper<>();
        wrapper.eq("\"record_id\"", id).orderByAsc("\"sort_order\"");
        List<AllocateTenday> tendays = tendayMapper.selectList(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("tendays", tendays);
        return result;
    }

    /** 配置历史方案列表 */
    @GetMapping("/list")
    public List<AllocateRecord> list() {
        return commonService.list(recordMapper);
    }

    /** 配置方案重命名：body {"name": "新名称"} */
    @PutMapping("/{id}/name")
    public Map<String, Object> rename(@PathVariable String id, @RequestBody Map<String, String> body) {
        commonService.rename(id, body == null ? null : body.get("name"), recordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    /** 配置方案逻辑删除（del_flag=#1#） */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        commonService.softDelete(id, recordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }
}
