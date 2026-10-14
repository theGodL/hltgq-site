package com.qgyun.hltgq.hltgqsite.waterusage.controller;

import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.service.WaterDemandService;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.DemandOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.UserOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDemandSaveVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDemandVO;
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
 * 需水上报管理（灌区用水模块页面四之一）。
 * <p>状态机：待审核 →（审核）已通过/已驳回；待审核/已驳回可编辑（保存后回到待审核并清空审核痕迹）。
 * 删除保护：被调度方案引用时禁止删除。编号 XS + 4 位序号，冲突重试同用水户。
 */
@RestController
@RequestMapping("/water-demand")
public class WaterDemandController {

    private static final Logger log = LoggerFactory.getLogger(WaterDemandController.class);

    @Autowired
    private WaterDemandService demandService;

    /** 表单元数据（审核状态字典 + 用途建议 + 当天日期） */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return demandService.meta();
    }

    /** Tab 角标统计（全部/待审核/已通过/已驳回） */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return demandService.stats();
    }

    /** 列表分页（关键字 / 审核状态筛选；status 传前端英文码 wait/pass/reject） */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String status) {
        return demandService.page(page, size, keyword, status);
    }

    /** 详情 */
    @GetMapping("/detail")
    public WaterDemandVO detail(@RequestParam String id) {
        return demandService.detail(id);
    }

    /** 「用水户」下拉（表单） */
    @GetMapping("/users")
    public List<UserOptionVO> users(@RequestParam(required = false) String keyword) {
        return demandService.users(keyword);
    }

    /** 「关联需水申请」下拉（仅已通过；调度方案编制用） */
    @GetMapping("/demands")
    public List<DemandOptionVO> demands(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String userId) {
        return demandService.demands(keyword, userId);
    }

    /** 新增（编号冲突最多重试 3 次，每次新事务重新取号） */
    @PostMapping("/create")
    public WaterDemandVO create(@RequestBody WaterDemandSaveVO body) {
        log.info("收到需水上报新增请求：userId={}，gateId={}，amount={}",
                body == null ? null : body.getWaterUserId(),
                body == null ? null : body.getGateId(),
                body == null ? null : body.getAmount());
        return WaterUsageSupport.retryOnNoConflict("uk_water_demand_no", "需水上报",
                () -> demandService.create(body), log);
    }

    /** 编辑（仅待审核/已驳回；保存后回到待审核并清空审核痕迹） */
    @PutMapping("/update")
    public WaterDemandVO update(@RequestParam String id, @RequestBody WaterDemandSaveVO body) {
        log.info("收到需水上报编辑请求：id={}", id);
        return demandService.update(id, body);
    }

    /** 审核（action = pass 通过 / reject 驳回；驳回意见必填） */
    @PostMapping("/audit")
    public WaterDemandVO audit(@RequestParam String id,
                               @RequestParam String action,
                               @RequestParam(required = false) String opinion) {
        log.info("收到需水上报审核请求：id={}，action={}", id, action);
        return demandService.audit(id, action, opinion);
    }

    /** 删除（被调度方案引用时禁止） */
    @DeleteMapping("/delete")
    public Map<String, Object> delete(@RequestParam String id) {
        log.info("收到需水上报删除请求：id={}", id);
        demandService.delete(id);
        return WaterUsageSupport.ok();
    }
}
