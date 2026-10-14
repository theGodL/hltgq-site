package com.qgyun.hltgq.hltgqsite.waterusage.controller;

import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.service.WaterQuotaService;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterQuotaSaveVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterQuotaVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用水定额标准库（灌区用水模块页面二）。
 * <p>定额编辑不回溯既有计划（计划明细为快照）；删除保护两级规则同用水户。
 */
@RestController
@RequestMapping("/water-quota")
public class WaterQuotaController {

    private static final Logger log = LoggerFactory.getLogger(WaterQuotaController.class);

    @Autowired
    private WaterQuotaService quotaService;

    /**
     * 列表分页（关键字：作物种类 / 灌溉方式 / 备注）。
     *
     * @return total / page / size / pages / records（含被引用计划数 refCount）
     */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String keyword) {
        return quotaService.page(page, size, keyword);
    }

    /** 详情（编辑回显） */
    @GetMapping("/detail")
    public WaterQuotaVO detail(@RequestParam String id) {
        return quotaService.detail(id);
    }

    /** 新增 */
    @PostMapping("/create")
    public WaterQuotaVO create(@RequestBody WaterQuotaSaveVO body) {
        log.info("收到定额新增请求：cropType={}，method={}，value={}",
                body == null ? null : body.getCropType(),
                body == null ? null : body.getIrrigationMethod(),
                body == null ? null : body.getQuotaValue());
        return quotaService.create(body);
    }

    /** 编辑（仅影响新建计划测算，不回溯已生效/已归档计划核定水量） */
    @PutMapping("/update")
    public WaterQuotaVO update(@RequestParam String id, @RequestBody WaterQuotaSaveVO body) {
        log.info("收到定额编辑请求：id={}，cropType={}", id, body == null ? null : body.getCropType());
        return quotaService.update(id, body);
    }

    /** 删除前置检查（blocked 非空禁止删除；pending 非空需二次确认） */
    @GetMapping("/delete-check")
    public Map<String, Object> deleteCheck(@RequestParam String id) {
        return quotaService.deleteCheck(id);
    }

    /** 删除（force=true 时连带清理编制中计划明细并重算总水量） */
    @DeleteMapping("/delete")
    public Map<String, Object> delete(@RequestParam String id,
                                      @RequestParam(defaultValue = "false") boolean force) {
        log.info("收到定额删除请求：id={}，force={}", id, force);
        quotaService.delete(id, force);
        return WaterUsageSupport.ok();
    }
}
