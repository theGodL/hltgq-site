package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.decision.service.DecisionStaticTableService;
import com.qgyun.hltgq.hltgqsite.decision.service.WaterBranchService;
import com.qgyun.hltgq.hltgqsite.decision.vo.WaterBranchVO;
import com.qgyun.hltgq.hltgqsite.entity.DecisionAdminMonth;
import com.qgyun.hltgq.hltgqsite.entity.DecisionAdminYear;
import com.qgyun.hltgq.hltgqsite.entity.DecisionBranchDetail;
import com.qgyun.hltgq.hltgqsite.entity.DecisionExtraTable;
import com.qgyun.hltgq.hltgqsite.entity.DecisionRecord;
import com.qgyun.hltgq.hltgqsite.entity.DecisionScaleFactor;
import com.qgyun.hltgq.hltgqsite.entity.DecisionSupplyAlloc;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionAdminMonthMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionAdminYearMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionBranchDetailMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionExtraTableMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionScaleFactorMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionSupplyAllocMapper;
import com.qgyun.hltgq.hltgqsite.model.client.ModelClient;
import com.qgyun.hltgq.hltgqsite.model.service.DecisionService;
import com.qgyun.hltgq.hltgqsite.model.service.ModelRecordCommonService;
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DownloadHeaderUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.DecisionSubmitRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配水调度接口（/water-decision）。
 * <p>2026-10-26 定稿：入参仅水资源配置方案一个下拉（必传），canal_eff 后端链式追溯，
 * 调 /decision(tens=null 全 18 旬, save_excel=true)；
 * /area-table、/flow-limit-table 为静态表只读接口（面积分配.xlsx、支渠限制流量.xlsx）。
 */
@RestController
@RequestMapping("/water-decision")
public class DecisionController {

    @Autowired
    private DecisionService decisionService;

    @Autowired
    private DecisionStaticTableService staticTableService;

    @Autowired
    private ModelRecordCommonService commonService;

    @Autowired
    private ModelClient modelClient;

    @Autowired
    private DecisionRecordMapper recordMapper;

    @Autowired
    private DecisionScaleFactorMapper scaleFactorMapper;

    @Autowired
    private DecisionBranchDetailMapper branchDetailMapper;

    @Autowired
    private DecisionAdminMonthMapper adminMonthMapper;

    @Autowired
    private DecisionAdminYearMapper adminYearMapper;

    @Autowired
    private DecisionSupplyAllocMapper supplyAllocMapper;

    @Autowired
    private DecisionExtraTableMapper extraTableMapper;

    @Autowired
    private WaterBranchService waterBranchService;

    /** 提交配水调度（秒回 recordId），后台异步调模型计算 */
    @PostMapping
    public Map<String, Object> submit(@RequestBody DecisionSubmitRequest req) {
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", decisionService.submit(req));
        return result;
    }

    /**
     * 下载灌溉需水逐旬明细 Excel：优先取计算完成时存档的方案快照（按 recordId 隔离，防串档），
     * 快照缺失（历史方案/快照存档失败）时降级实时代理模型下载接口。
     * 已删除方案拒绝下载（快照已清理，降级代理会拿到其他方案文件）。
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam String recordId) {
        DecisionRecord record = commonService.require(recordId, recordMapper);
        if (BoolTextUtils.TRUE.equals(record.getDelFlag())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "方案已删除");
        }
        byte[] bytes = decisionService.readExcelSnapshot(recordId);
        if (bytes == null) {
            bytes = modelClient.downloadBytes("/decision/download");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        DownloadHeaderUtils.attachment("灌溉需水逐旬明细_" + recordId + ".xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    /** 轮询调度方案执行状态：{id, status, errorMsg} */
    @GetMapping("/status/{id}")
    public Map<String, Object> status(@PathVariable String id) {
        return commonService.status(id, recordMapper);
    }

    /** 面积分配静态表（片区面积 + 行政区面积） */
    @GetMapping("/area-table")
    public Map<String, Object> areaTable() {
        return staticTableService.areaTable();
    }

    /** 支渠限制流量静态表（支渠约束流量 + 片区约束流量，空限制流量→null） */
    @GetMapping("/flow-limit-table")
    public Map<String, Object> flowLimitTable() {
        return staticTableService.flowLimitTable();
    }

    /** 调度方案详情：主表 + 缩放系数 + 支渠明细（含缺水量三列）+ V3 三张汇总表 + 4 张扩展表 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        DecisionRecord record = commonService.require(id, recordMapper);
        QueryWrapper<DecisionScaleFactor> factorWrapper = new QueryWrapper<>();
        factorWrapper.eq("\"record_id\"", id).orderByAsc("\"sort_order\"");
        List<DecisionScaleFactor> scaleFactors = scaleFactorMapper.selectList(factorWrapper);
        QueryWrapper<DecisionBranchDetail> branchWrapper = new QueryWrapper<>();
        branchWrapper.eq("\"record_id\"", id).orderByAsc("\"sort_order\"").orderByAsc("\"branch_name\"");
        List<DecisionBranchDetail> branchDetails = branchDetailMapper.selectList(branchWrapper);
        QueryWrapper<DecisionAdminMonth> adminMonthWrapper = new QueryWrapper<>();
        adminMonthWrapper.eq("\"record_id\"", id)
                .orderByAsc("\"year\"").orderByAsc("\"month\"").orderByAsc("\"district\"");
        List<DecisionAdminMonth> adminMonthSummary = adminMonthMapper.selectList(adminMonthWrapper);
        QueryWrapper<DecisionAdminYear> adminYearWrapper = new QueryWrapper<>();
        adminYearWrapper.eq("\"record_id\"", id).orderByAsc("\"year\"").orderByAsc("\"district\"");
        List<DecisionAdminYear> adminYearSummary = adminYearMapper.selectList(adminYearWrapper);
        QueryWrapper<DecisionSupplyAlloc> allocWrapper = new QueryWrapper<>();
        allocWrapper.eq("\"record_id\"", id).last("LIMIT 1");
        DecisionSupplyAlloc supplyAlloc = supplyAllocMapper.selectOne(allocWrapper);
        QueryWrapper<DecisionExtraTable> extraWrapper = new QueryWrapper<>();
        extraWrapper.eq("\"record_id\"", id).orderByAsc("\"table_key\"").orderByAsc("\"sort_order\"");
        List<DecisionExtraTable> extraTables = extraTableMapper.selectList(extraWrapper);
        // rowJson 落库为 JSON 字符串，此处解析为对象返回（前端免 parse；解析失败降级原字符串）
        List<Map<String, Object>> extraTableViews = new ArrayList<>();
        for (DecisionExtraTable row : extraTables) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tableKey", row.getTableKey());
            item.put("rowKey", row.getRowKey());
            item.put("sortOrder", row.getSortOrder());
            Object rowData = row.getRowJson();
            if (rowData instanceof String) {
                try {
                    rowData = new ObjectMapper().readTree((String) rowData);
                } catch (Exception e) {
                    // 解析失败保留原始字符串
                }
            }
            item.put("rowJson", rowData);
            extraTableViews.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("scaleFactors", scaleFactors);
        result.put("branchDetails", branchDetails);
        result.put("adminMonthSummary", adminMonthSummary);
        result.put("adminYearSummary", adminYearSummary);
        result.put("supplyAlloc", supplyAlloc);
        result.put("extraTables", extraTableViews);
        return result;
    }

    /** 调度历史方案列表 */
    @GetMapping("/list")
    public List<DecisionRecord> list() {
        return commonService.list(recordMapper);
    }

    /**
     * 拓扑按旬支渠数据：日期映射到对应旬（不传 = 方案首旬）。
     * 拓扑每支渠单值展示（无旬维度），重名支渠靠 key 与拓扑节点精确命中。
     *
     * @param id        方案 ID
     * @param startDate 起始日期 yyyy-MM-dd，可选；不传 = 方案首旬
     */
    @GetMapping("/{id}/branches")
    public WaterBranchVO branches(@PathVariable String id,
                                  @RequestParam(required = false) String startDate) {
        commonService.require(id, recordMapper);
        return waterBranchService.branches(id, startDate);
    }

    /** 调度方案重命名：body {"name": "新名称"} */
    @PutMapping("/{id}/name")
    public Map<String, Object> rename(@PathVariable String id, @RequestBody Map<String, String> body) {
        commonService.rename(id, body == null ? null : body.get("name"), recordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    /** 调度方案逻辑删除（del_flag=#1#），同步清理方案 Excel 快照 */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        commonService.softDelete(id, recordMapper);
        decisionService.deleteExcelSnapshot(id);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }
}
