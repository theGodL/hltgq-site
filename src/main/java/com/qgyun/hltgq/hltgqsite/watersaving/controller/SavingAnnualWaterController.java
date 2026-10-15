package com.qgyun.hltgq.hltgqsite.watersaving.controller;

import com.qgyun.hltgq.hltgqsite.auth.RequireAdmin;
import com.qgyun.hltgq.hltgqsite.watersaving.SavingSupport;
import com.qgyun.hltgq.hltgqsite.watersaving.service.SavingAnnualWaterService;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingAnnualSaveVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingAnnualVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingPrevPlanVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 年度水量设置（节水管理：年度水量记录维护）。
 * <p>潜力 = 上一年实际 − 上一年计划（万m³，可为负）；潜力年度全表唯一；
 * 计划自动带出取依据年度年度计划（排除已作废）可手工覆盖。被方案引用的记录禁止删除、潜力年度禁止修改。
 * <p>权限：查询所有登录用户可用；新增/编辑/删除仅系统管理员（@RequireAdmin，拦截器校验）。
 */
@RestController
@RequestMapping("/water-saving/annual-water")
public class SavingAnnualWaterController {

    private static final Logger log = LoggerFactory.getLogger(SavingAnnualWaterController.class);

    @Autowired
    private SavingAnnualWaterService annualWaterService;

    /** 全量列表（潜力年度倒序；含服务端计算的潜力与来源文案） */
    @GetMapping("/list")
    public List<SavingAnnualVO> list() {
        return annualWaterService.list();
    }

    /**
     * 上一年度计划带出（弹窗选定潜力年度后调用）。
     *
     * @param year 潜力年度（返回其依据年度 year−1 的年度计划；未找到 found=false）
     */
    @GetMapping("/prev-plan")
    public SavingPrevPlanVO prevPlan(@RequestParam Integer year) {
        return annualWaterService.prevYearPlan(year);
    }

    /** 详情（单条） */
    @GetMapping("/detail")
    public SavingAnnualVO detail(@RequestParam String id) {
        return annualWaterService.detail(id);
    }

    /** 保存（有 id 更新 / 无 id 新增；水量变化联动作废引用方案核验） */
    @RequireAdmin
    @PostMapping("/save")
    public SavingAnnualVO save(@RequestBody SavingAnnualSaveVO body) {
        log.info("收到年度水量保存请求：id={}，potentialYear={}",
                body == null ? null : body.getId(),
                body == null ? null : body.getPotentialYear());
        return annualWaterService.save(body);
    }

    /** 删除（被方案引用禁止） */
    @RequireAdmin
    @DeleteMapping("/delete")
    public Map<String, Object> delete(@RequestParam String id) {
        log.info("收到年度水量删除请求：id={}", id);
        annualWaterService.delete(id);
        return SavingSupport.ok();
    }
}
