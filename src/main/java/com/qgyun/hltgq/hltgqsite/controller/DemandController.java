package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qgyun.hltgq.hltgqsite.entity.DemandAreaSummary;
import com.qgyun.hltgq.hltgqsite.entity.DemandBranchDetail;
import com.qgyun.hltgq.hltgqsite.entity.DemandRecord;
import com.qgyun.hltgq.hltgqsite.mapper.DemandAreaSummaryMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DemandBranchDetailMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DemandRecordMapper;
import com.qgyun.hltgq.hltgqsite.model.service.DemandService;
import com.qgyun.hltgq.hltgqsite.model.service.ModelRecordCommonService;
import com.qgyun.hltgq.hltgqsite.model.util.ExcelParseUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.DemandSubmitRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 需水预测接口（/water-forecast/demand）。
 * <p>两种输入方式：① 模板上传解析（POST /upload）② 拓扑图表格 JSON 输入（POST 本体）。
 * 模型接口表格入参改造完成前，表格数据仅随 request_json 归档，按保证率/渠系系数调模型。
 */
@RestController
@RequestMapping("/water-forecast/demand")
public class DemandController {

    /** 需水基础数据收集表模板（classpath:static/templates 下） */
    private static final String TEMPLATE_LOCATION = "classpath:static/templates/WaterDemandForecast_Template.xlsx";

    @Autowired
    private DemandService demandService;

    @Autowired
    private ModelRecordCommonService commonService;

    @Autowired
    private DemandRecordMapper recordMapper;

    @Autowired
    private DemandBranchDetailMapper branchDetailMapper;

    @Autowired
    private DemandAreaSummaryMapper areaSummaryMapper;

    @Autowired
    private ResourceLoader resourceLoader;

    /** 下载需水基础数据收集表模板 */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() throws Exception {
        Resource resource = resourceLoader.getResource(TEMPLATE_LOCATION);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes;
        try (java.io.InputStream in = resource.getInputStream()) {
            bytes = StreamUtils.copyToByteArray(in);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=WaterDemandForecast_Template.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    /**
     * 拓扑图表格 JSON 输入：body 含 guaranteeRate/canalEff/targetTotal/tableRows。
     * 秒回 recordId，后台异步调模型。
     */
    @PostMapping
    public Map<String, Object> submit(@RequestBody DemandSubmitRequest req) {
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", demandService.submit(req));
        return result;
    }

    /**
     * 模板上传解析：multipart file（WaterDemandForecast_Template.xlsx 格式）
     * + guaranteeRate/canalEff/targetTotal/schemeName 表单参数。
     * POI 解析 rows 归档后异步调模型，秒回 recordId。
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(required = false) String guaranteeRate,
                                      @RequestParam(required = false) Double canalEff,
                                      @RequestParam(required = false) Double targetTotal,
                                      @RequestParam(required = false) String schemeName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        DemandSubmitRequest req = new DemandSubmitRequest();
        req.setGuaranteeRate(guaranteeRate);
        req.setCanalEff(canalEff);
        req.setTargetTotal(targetTotal);
        req.setSchemeName(schemeName);
        req.setTableRows(parseTableRows(file));
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", demandService.submit(req));
        return result;
    }

    /** POI 解析上传 Excel → 表格行归档（灌区面积列转数值，其余保留字符串） */
    private List<Map<String, Object>> parseTableRows(MultipartFile file) {
        List<Map<String, String>> rows;
        try {
            rows = ExcelParseUtils.parseRows(file.getInputStream());
        } catch (Exception e) {
            throw new IllegalArgumentException("模板解析失败（仅支持 .xlsx 格式）: " + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("模板无数据行");
        }
        Map<String, String> first = rows.get(0);
        if (!first.containsKey("片区") || !first.containsKey("支渠")) {
            throw new IllegalArgumentException("模板表头缺少必需列：片区 / 支渠");
        }
        List<Map<String, Object>> tableRows = new ArrayList<>();
        for (Map<String, String> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if ("灌区面积".equals(key) && value != null && !value.isEmpty()) {
                    try {
                        item.put(key, Double.parseDouble(value));
                        continue;
                    } catch (NumberFormatException ignored) {
                        // 非数值保留原始字符串
                    }
                }
                item.put(key, value);
            }
            tableRows.add(item);
        }
        return tableRows;
    }

    /** 轮询需水方案执行状态：{id, status, errorMsg} */
    @GetMapping("/status/{id}")
    public Map<String, Object> status(@PathVariable String id) {
        return commonService.status(id, recordMapper);
    }

    /** 需水方案详情：主表 + 支渠明细 + 片区/分灌区汇总 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        DemandRecord record = commonService.require(id, recordMapper);
        QueryWrapper<DemandBranchDetail> branchWrapper = new QueryWrapper<>();
        branchWrapper.eq("\"record_id\"", id).orderByAsc("\"sort_order\"").orderByAsc("\"branch_name\"");
        List<DemandBranchDetail> branchDetails = branchDetailMapper.selectList(branchWrapper);
        QueryWrapper<DemandAreaSummary> summaryWrapper = new QueryWrapper<>();
        summaryWrapper.eq("\"record_id\"", id).orderByAsc("\"sort_order\"").orderByAsc("\"area_name\"");
        List<DemandAreaSummary> areaSummaries = areaSummaryMapper.selectList(summaryWrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("branchDetails", branchDetails);
        result.put("areaSummaries", areaSummaries);
        return result;
    }

    /** 需水历史方案列表 */
    @GetMapping("/list")
    public List<DemandRecord> list() {
        return commonService.list(recordMapper);
    }

    /** 需水方案重命名：body {"name": "新名称"} */
    @PutMapping("/{id}/name")
    public Map<String, Object> rename(@PathVariable String id, @RequestBody Map<String, String> body) {
        commonService.rename(id, body == null ? null : body.get("name"), recordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    /** 需水方案逻辑删除（del_flag=#1#） */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        commonService.softDelete(id, recordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }
}
