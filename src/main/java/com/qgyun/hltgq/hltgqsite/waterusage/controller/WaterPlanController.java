package com.qgyun.hltgq.hltgqsite.waterusage.controller;

import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.service.WaterPlanService;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.QuotaOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.UserOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterPlanSaveVO;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 用水计划管理（灌区用水模块页面三）。
 * <p>状态机：编制中 →（生效）已生效 →（归档）已归档；已生效 →（作废）已作废。
 * 生效前前端先调 conflict-check（只读预览）：blocked 非空终止；warnings 非空二次确认后 activate
 * （activate 事务内以同一校验函数最终裁决）。编号 YJ + 3 位序号，冲突重试同用水户。
 */
@RestController
@RequestMapping("/water-plan")
public class WaterPlanController {

    private static final Logger log = LoggerFactory.getLogger(WaterPlanController.class);

    @Autowired
    private WaterPlanService planService;

    /** 表单元数据（类型/状态/旬字典 + 当天日期） */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return planService.meta();
    }

    /**
     * 列表分页（关键字 / 类型 / 状态筛选）。
     *
     * @return total / page / size / pages / records（含用水户数 userCount）
     */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String planType,
                                    @RequestParam(required = false) String status) {
        return planService.page(page, size, keyword, planType, status);
    }

    /** 详情（主表 + 明细） */
    @GetMapping("/detail")
    public WaterPlanVO detail(@RequestParam String id) {
        return planService.detail(id);
    }

    /** 明细「用水户」下拉（仅正常状态） */
    @GetMapping("/users")
    public List<UserOptionVO> users(@RequestParam(required = false) String keyword) {
        return planService.users(keyword);
    }

    /** 明细「适用定额」下拉 */
    @GetMapping("/quotas")
    public List<QuotaOptionVO> quotas(@RequestParam(required = false) String keyword) {
        return planService.quotas(keyword);
    }

    /** 测算水量 = 灌溉面积 × 定额标准（明细行自动测算） */
    @GetMapping("/calc")
    public Map<String, Object> calc(@RequestParam BigDecimal area, @RequestParam String quotaId) {
        return planService.calc(area, quotaId);
    }

    /** 新增（编号冲突最多重试 3 次，每次新事务重新取号） */
    @PostMapping("/create")
    public WaterPlanVO create(@RequestBody WaterPlanSaveVO body) {
        log.info("收到用水计划新增请求：type={}，year={}，month={}，xun={}，items={}",
                body == null ? null : body.getPlanType(), body == null ? null : body.getYear(),
                body == null ? null : body.getMonth(), body == null ? null : body.getXun(),
                body == null || body.getItems() == null ? 0 : body.getItems().size());
        return WaterUsageSupport.retryOnNoConflict("uk_water_plan_no", "用水计划",
                () -> planService.create(body), log);
    }

    /** 编辑（编制中 = 全量替换；已生效 = 仅明细计划水量与超测算说明） */
    @PutMapping("/update")
    public WaterPlanVO update(@RequestParam String id, @RequestBody WaterPlanSaveVO body) {
        log.info("收到用水计划编辑请求：id={}，items={}", id,
                body == null || body.getItems() == null ? 0 : body.getItems().size());
        return planService.update(id, body);
    }

    /** 删除（仅编制中；主表 + 明细物理删除） */
    @DeleteMapping("/delete")
    public Map<String, Object> delete(@RequestParam String id) {
        log.info("收到用水计划删除请求：id={}", id);
        planService.delete(id);
        return WaterUsageSupport.ok();
    }

    /** 生效校验只读预览（blocked / warnings） */
    @GetMapping("/conflict-check")
    public Map<String, Object> conflictCheck(@RequestParam String id) {
        return planService.conflictCheck(id);
    }

    /** 生效（编制中 → 已生效；三层校验最终裁决） */
    @PostMapping("/activate")
    public Map<String, Object> activate(@RequestParam String id) {
        log.info("收到用水计划生效请求：id={}", id);
        planService.activate(id);
        return WaterUsageSupport.ok();
    }

    /** 归档（已生效 → 已归档） */
    @PostMapping("/archive")
    public Map<String, Object> archive(@RequestParam String id) {
        log.info("收到用水计划归档请求：id={}", id);
        planService.archive(id);
        return WaterUsageSupport.ok();
    }

    /** 作废（已生效 → 已作废；原因必填） */
    @PostMapping("/void")
    public Map<String, Object> voidPlan(@RequestParam String id, @RequestParam String reason) {
        log.info("收到用水计划作废请求：id={}", id);
        planService.voidPlan(id, reason);
        return WaterUsageSupport.ok();
    }
}
