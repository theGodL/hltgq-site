package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qgyun.hltgq.hltgqsite.entity.MoistureDetail;
import com.qgyun.hltgq.hltgqsite.entity.MoistureRecord;
import com.qgyun.hltgq.hltgqsite.mapper.MoistureDetailMapper;
import com.qgyun.hltgq.hltgqsite.mapper.MoistureRecordMapper;
import com.qgyun.hltgq.hltgqsite.model.service.ModelRecordCommonService;
import com.qgyun.hltgq.hltgqsite.model.service.MoisturePredictService;
import com.qgyun.hltgq.hltgqsite.model.vo.MoistureSubmitRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 墒情预测接口（/water-forecast/moisture），模型 /moisture（2026-09 新增）。
 * <p>通用接口同构：POST 提交(秒回 recordId) → GET status/{id} 轮询 → GET {id} 详情
 * → GET list 历史方案 → PUT {id}/name 重命名 → DELETE {id} 逻辑删除(del_flag=#1#)。
 */
@RestController
@RequestMapping("/water-forecast/moisture")
public class MoisturePredictController {

    @Autowired
    private MoisturePredictService moisturePredictService;

    @Autowired
    private ModelRecordCommonService commonService;

    @Autowired
    private MoistureRecordMapper recordMapper;

    @Autowired
    private MoistureDetailMapper detailMapper;

    /** 提交墒情预测（秒回 recordId），后台异步调模型计算 */
    @PostMapping
    public Map<String, Object> submit(@RequestBody(required = false) MoistureSubmitRequest req) {
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", moisturePredictService.submit(req));
        return result;
    }

    /** 轮询墒情方案执行状态：{id, status, errorMsg} */
    @GetMapping("/status/{id}")
    public Map<String, Object> status(@PathVariable String id) {
        return commonService.status(id, recordMapper);
    }

    /** 墒情方案详情：主表 + 逐小时明细 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        MoistureRecord record = commonService.require(id, recordMapper);
        QueryWrapper<MoistureDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("\"record_id\"", id).orderByAsc("\"site\"").orderByAsc("\"tm\"");
        List<MoistureDetail> details = detailMapper.selectList(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("details", details);
        return result;
    }

    /** 墒情历史方案列表 */
    @GetMapping("/list")
    public List<MoistureRecord> list() {
        return commonService.list(recordMapper);
    }

    /** 墒情方案重命名：body {"name": "新名称"} */
    @PutMapping("/{id}/name")
    public Map<String, Object> rename(@PathVariable String id, @RequestBody Map<String, String> body) {
        commonService.rename(id, body == null ? null : body.get("name"), recordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    /** 墒情方案逻辑删除（del_flag=#1#） */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        commonService.softDelete(id, recordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }
}
