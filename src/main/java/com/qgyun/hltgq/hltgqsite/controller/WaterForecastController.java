package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qgyun.hltgq.hltgqsite.entity.LongPredictMonthly;
import com.qgyun.hltgq.hltgqsite.entity.LongPredictRecord;
import com.qgyun.hltgq.hltgqsite.entity.LongPredictTenday;
import com.qgyun.hltgq.hltgqsite.entity.ShortForecastDaily;
import com.qgyun.hltgq.hltgqsite.entity.ShortForecastRecord;
import com.qgyun.hltgq.hltgqsite.mapper.LongPredictMonthlyMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LongPredictRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LongPredictTendayMapper;
import com.qgyun.hltgq.hltgqsite.mapper.ShortForecastDailyMapper;
import com.qgyun.hltgq.hltgqsite.mapper.ShortForecastRecordMapper;
import com.qgyun.hltgq.hltgqsite.model.service.LongPredictService;
import com.qgyun.hltgq.hltgqsite.model.service.ModelRecordCommonService;
import com.qgyun.hltgq.hltgqsite.model.service.ShortForecastService;
import com.qgyun.hltgq.hltgqsite.model.vo.LongPredictRequest;
import com.qgyun.hltgq.hltgqsite.model.vo.ShortForecastRequest;
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
 * 水量预测接口（短期来水 / 中长期来水两个子模块）。
 * <p>通用接口同构：POST 提交(秒回 recordId) → GET status/{id} 轮询 → GET {id} 详情
 * → GET list 历史方案 → PUT {id}/name 重命名 → DELETE {id} 逻辑删除(del_flag=#1#)。
 */
@RestController
@RequestMapping("/water-forecast")
public class WaterForecastController {

    @Autowired
    private ShortForecastService shortForecastService;

    @Autowired
    private LongPredictService longPredictService;

    @Autowired
    private ModelRecordCommonService commonService;

    @Autowired
    private ShortForecastRecordMapper shortRecordMapper;

    @Autowired
    private ShortForecastDailyMapper shortDailyMapper;

    @Autowired
    private LongPredictRecordMapper longRecordMapper;

    @Autowired
    private LongPredictTendayMapper longTendayMapper;

    @Autowired
    private LongPredictMonthlyMapper longMonthlyMapper;

    // ==================== 短期来水预测 ====================

    /**
     * 提交短期来水预测（秒回 recordId），后台异步调模型计算。
     */
    @PostMapping("/short")
    public Map<String, Object> submitShort(@RequestBody ShortForecastRequest req) {
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", shortForecastService.submit(req));
        return result;
    }

    /** 轮询短期方案执行状态：{id, status, errorMsg} */
    @GetMapping("/short/status/{id}")
    public Map<String, Object> shortStatus(@PathVariable String id) {
        return commonService.status(id, shortRecordMapper);
    }

    /** 短期方案详情：主表 + 逐日明细 */
    @GetMapping("/short/{id}")
    public Map<String, Object> shortDetail(@PathVariable String id) {
        ShortForecastRecord record = commonService.require(id, shortRecordMapper);
        QueryWrapper<ShortForecastDaily> wrapper = new QueryWrapper<>();
        wrapper.eq("\"record_id\"", id).orderByAsc("\"forecast_date\"");
        List<ShortForecastDaily> dailies = shortDailyMapper.selectList(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("dailies", dailies);
        return result;
    }

    /** 短期历史方案列表 */
    @GetMapping("/short/list")
    public List<ShortForecastRecord> shortList() {
        return commonService.list(shortRecordMapper);
    }

    /** 短期方案重命名：body {"name": "新名称"} */
    @PutMapping("/short/{id}/name")
    public Map<String, Object> shortRename(@PathVariable String id, @RequestBody Map<String, String> body) {
        commonService.rename(id, body == null ? null : body.get("name"), shortRecordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    /** 短期方案逻辑删除（del_flag=#1#） */
    @DeleteMapping("/short/{id}")
    public Map<String, Object> shortDelete(@PathVariable String id) {
        commonService.softDelete(id, shortRecordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    // ==================== 中长期来水预测 ====================

    /**
     * 提交中长期来水预测（秒回 recordId），后台异步调模型计算。
     */
    @PostMapping("/long")
    public Map<String, Object> submitLong(@RequestBody LongPredictRequest req) {
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", longPredictService.submit(req));
        return result;
    }

    /** 轮询中长期方案执行状态：{id, status, errorMsg} */
    @GetMapping("/long/status/{id}")
    public Map<String, Object> longStatus(@PathVariable String id) {
        return commonService.status(id, longRecordMapper);
    }

    /** 中长期方案详情：主表 + 旬尺度明细 + 月尺度明细 */
    @GetMapping("/long/{id}")
    public Map<String, Object> longDetail(@PathVariable String id) {
        LongPredictRecord record = commonService.require(id, longRecordMapper);
        QueryWrapper<LongPredictTenday> tendayWrapper = new QueryWrapper<>();
        tendayWrapper.eq("\"record_id\"", id).orderByAsc("\"predict_date\"");
        List<LongPredictTenday> tendays = longTendayMapper.selectList(tendayWrapper);
        QueryWrapper<LongPredictMonthly> monthlyWrapper = new QueryWrapper<>();
        monthlyWrapper.eq("\"record_id\"", id).orderByAsc("\"stat_date\"");
        List<LongPredictMonthly> monthlies = longMonthlyMapper.selectList(monthlyWrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("tendays", tendays);
        result.put("monthlies", monthlies);
        return result;
    }

    /** 中长期历史方案列表 */
    @GetMapping("/long/list")
    public List<LongPredictRecord> longList() {
        return commonService.list(longRecordMapper);
    }

    /** 中长期方案重命名：body {"name": "新名称"} */
    @PutMapping("/long/{id}/name")
    public Map<String, Object> longRename(@PathVariable String id, @RequestBody Map<String, String> body) {
        commonService.rename(id, body == null ? null : body.get("name"), longRecordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    /** 中长期方案逻辑删除（del_flag=#1#） */
    @DeleteMapping("/long/{id}")
    public Map<String, Object> longDelete(@PathVariable String id) {
        commonService.softDelete(id, longRecordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }
}
