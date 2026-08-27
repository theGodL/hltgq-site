package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qgyun.hltgq.hltgqsite.entity.LossDetail;
import com.qgyun.hltgq.hltgqsite.entity.LossRecord;
import com.qgyun.hltgq.hltgqsite.mapper.LossDetailMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LossRecordMapper;
import com.qgyun.hltgq.hltgqsite.model.service.LossService;
import com.qgyun.hltgq.hltgqsite.model.service.ModelRecordCommonService;
import com.qgyun.hltgq.hltgqsite.model.vo.LossSubmitRequest;
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
 * 水量损失预测接口（/water-forecast/loss）。
 * <p>入参：mode(short/long) + 参数来源方案ID，参数从所选历史方案 request_json 提取复现。
 */
@RestController
@RequestMapping("/water-forecast/loss")
public class LossController {

    @Autowired
    private LossService lossService;

    @Autowired
    private ModelRecordCommonService commonService;

    @Autowired
    private LossRecordMapper recordMapper;

    @Autowired
    private LossDetailMapper detailMapper;

    /** 提交水量损失预测（秒回 recordId），后台异步调模型计算 */
    @PostMapping
    public Map<String, Object> submit(@RequestBody LossSubmitRequest req) {
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", lossService.submit(req));
        return result;
    }

    /** 轮询损失方案执行状态：{id, status, errorMsg} */
    @GetMapping("/status/{id}")
    public Map<String, Object> status(@PathVariable String id) {
        return commonService.status(id, recordMapper);
    }

    /** 损失方案详情：主表 + 明细 */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        LossRecord record = commonService.require(id, recordMapper);
        QueryWrapper<LossDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("\"record_id\"", id).orderByAsc("\"data_date\"");
        List<LossDetail> details = detailMapper.selectList(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("details", details);
        return result;
    }

    /** 损失历史方案列表 */
    @GetMapping("/list")
    public List<LossRecord> list() {
        return commonService.list(recordMapper);
    }

    /** 损失方案重命名：body {"name": "新名称"} */
    @PutMapping("/{id}/name")
    public Map<String, Object> rename(@PathVariable String id, @RequestBody Map<String, String> body) {
        commonService.rename(id, body == null ? null : body.get("name"), recordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    /** 损失方案逻辑删除（del_flag=#1#） */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        commonService.softDelete(id, recordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }
}
