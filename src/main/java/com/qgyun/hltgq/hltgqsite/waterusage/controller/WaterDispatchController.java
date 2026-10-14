package com.qgyun.hltgq.hltgqsite.waterusage.controller;

import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.service.WaterDispatchService;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.DemandOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchSaveVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterPlanVO;
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

import java.util.List;
import java.util.Map;

/**
 * 调度方案管理（灌区用水模块页面四之二）。
 * <p>状态机：待执行 →（开始执行）执行中 →（完成）已完成；待执行/执行中 →（取消）已取消。
 * 已计费（bill_id 非空）禁改、禁取消、禁删，须先删除关联账单；删除不限执行态（未计费即可）。
 * 编辑（全字段）仅待执行；调整（水费页「编辑水量」：计划放水量/时段/备注）未计费未取消即可。
 * <p>配额闭环：关联用水计划时受「该计划该户计划水量」约束，变动后回写计划 scheduled_qty。
 * 编号 DD + 4 位序号，冲突重试同用水户。
 */
@RestController
@RequestMapping("/water-dispatch")
public class WaterDispatchController {

    private static final Logger log = LoggerFactory.getLogger(WaterDispatchController.class);

    @Autowired
    private WaterDispatchService dispatchService;

    /** 表单元数据（执行状态字典 + 当天日期） */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return dispatchService.meta();
    }

    /** Tab 角标统计（全部/待执行/执行中/已完成/已取消） */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return dispatchService.stats();
    }

    /** 列表分页（关键字 / 状态 / 关联计划筛选；status 传前端英文码 pending/running/done/canceled） */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String planId) {
        return dispatchService.page(page, size, keyword, status, planId);
    }

    /** 详情 */
    @GetMapping("/detail")
    public WaterDispatchVO detail(@RequestParam String id) {
        return dispatchService.detail(id);
    }

    /** 「关联用水计划」下拉（仅已生效） */
    @GetMapping("/plans")
    public List<WaterPlanVO> plans(@RequestParam(required = false) String keyword) {
        return dispatchService.plans(keyword);
    }

    /** 「关联需水申请」下拉（仅已通过；可按用水户过滤） */
    @GetMapping("/demands")
    public List<DemandOptionVO> demands(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String userId) {
        return dispatchService.demands(keyword, userId);
    }

    /** 新增（待执行；编号冲突最多重试 3 次） */
    @PostMapping("/create")
    public WaterDispatchVO create(@RequestBody WaterDispatchSaveVO body) {
        log.info("收到调度方案新增请求：userId={}，gateId={}，planId={}，demandId={}",
                body == null ? null : body.getWaterUserId(),
                body == null ? null : body.getGateId(),
                body == null ? null : body.getPlanId(),
                body == null ? null : body.getDemandId());
        return WaterUsageSupport.retryOnNoConflict("uk_water_dispatch_no", "调度方案",
                () -> dispatchService.create(body), log);
    }

    /** 编辑（仅待执行且未计费：全字段） */
    @PutMapping("/update")
    public WaterDispatchVO update(@RequestParam String id, @RequestBody WaterDispatchSaveVO body) {
        log.info("收到调度方案编辑请求：id={}", id);
        return dispatchService.update(id, body);
    }

    /** 调整（水费页「编辑水量」：计划放水量/执行时段/备注；未计费未取消即可） */
    @PutMapping("/adjust")
    public WaterDispatchVO adjust(@RequestParam String id, @RequestBody WaterDispatchSaveVO body) {
        log.info("收到调度方案调整请求：id={}", id);
        return dispatchService.adjust(id, body);
    }

    /** 状态流转（action = start 开始执行 / done 完成 / cancel 取消） */
    @PostMapping("/status")
    public Map<String, Object> status(@RequestParam String id, @RequestParam String action) {
        log.info("收到调度方案状态流转请求：id={}，action={}", id, action);
        dispatchService.changeStatus(id, action);
        return WaterUsageSupport.ok();
    }

    /** 删除（未计费即可，不限执行态；记录挂接置空留痕） */
    @DeleteMapping("/delete")
    public Map<String, Object> delete(@RequestParam String id) {
        log.info("收到调度方案删除请求：id={}", id);
        dispatchService.delete(id);
        return WaterUsageSupport.ok();
    }
}
