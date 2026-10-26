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
import com.qgyun.hltgq.hltgqsite.model.util.DownloadHeaderUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.DemandSubmitRequest;
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 需水预测接口（/water-forecast/demand）。
 * <p>2026-10-26 定稿：模板下载/上传已下线，仅保留拓扑图表格 JSON 输入（POST 本体 tableRows 直传模型 demand_table）。
 * 详情返回主表 + 支渠明细 + 片区/分灌区/行政区三类 area_summary。
 */
@RestController
@RequestMapping("/water-forecast/demand")
public class DemandController {

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

    /** 轮询需水方案执行状态：{id, status, errorMsg} */
    @GetMapping("/status/{id}")
    public Map<String, Object> status(@PathVariable String id) {
        return commonService.status(id, recordMapper);
    }

    /**
     * 下载需水预测逐旬明细 Excel：后端按落库明细生成（Sheet1 支渠明细宽表 + Sheet2 分区汇总），
     * 与详情接口同口径。
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam String recordId) {
        commonService.require(recordId, recordMapper);
        byte[] bytes = demandService.exportExcel(recordId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        DownloadHeaderUtils.attachment("需水预测逐旬明细_" + recordId + ".xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    /** 需水方案详情：主表 + 支渠明细 + 片区/分灌区/行政区汇总 */
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
