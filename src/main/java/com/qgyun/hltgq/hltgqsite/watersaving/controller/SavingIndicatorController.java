package com.qgyun.hltgq.hltgqsite.watersaving.controller;

import com.qgyun.hltgq.hltgqsite.auth.RequireAdmin;
import com.qgyun.hltgq.hltgqsite.watersaving.SavingSupport;
import com.qgyun.hltgq.hltgqsite.watersaving.service.SavingIndicatorService;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingIndicatorSaveVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingIndicatorVO;
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
 * 节水指标体系（节水管理页面二：指标维护 / 启停 / 删除）。
 * <p>计算方式固定三种（节水量 / 节水率 / 目标完成率，方法驱动服务端计算）；名称全局唯一（含停用）；
 * 已关联方案的指标计算方式锁定、不可删除（可停用）；目标值变更、启停变更联动方案留痕。
 * <p>权限：查询所有登录用户可用；写操作仅系统管理员（@RequireAdmin，拦截器校验）。
 */
@RestController
@RequestMapping("/water-saving/indicators")
public class SavingIndicatorController {

    private static final Logger log = LoggerFactory.getLogger(SavingIndicatorController.class);

    @Autowired
    private SavingIndicatorService indicatorService;

    /** 全量列表（含关联方案数；keyword 名称模糊） */
    @GetMapping("/list")
    public List<SavingIndicatorVO> list(@RequestParam(required = false) String keyword) {
        return indicatorService.list(keyword);
    }

    /** 详情（编辑回显） */
    @GetMapping("/detail")
    public SavingIndicatorVO detail(@RequestParam String id) {
        return indicatorService.detail(id);
    }

    /** 新增（名称全局唯一含停用；计算方式三选一；目标值选填） */
    @RequireAdmin
    @PostMapping("/create")
    public SavingIndicatorVO create(@RequestBody SavingIndicatorSaveVO body) {
        log.info("收到节水指标新增请求：name={}，method={}",
                body == null ? null : body.getName(),
                body == null ? null : body.getMethod());
        return indicatorService.create(body);
    }

    /** 编辑（已关联方案的指标计算方式锁定；目标值变更联动留痕） */
    @RequireAdmin
    @PutMapping("/update")
    public SavingIndicatorVO update(@RequestParam String id, @RequestBody SavingIndicatorSaveVO body) {
        log.info("收到节水指标编辑请求：id={}，name={}", id, body == null ? null : body.getName());
        return indicatorService.update(id, body);
    }

    /** 启停切换（停用后新方案不可勾选，既有关联保留） */
    @RequireAdmin
    @PostMapping("/toggle")
    public SavingIndicatorVO toggle(@RequestParam String id) {
        log.info("收到节水指标启停请求：id={}", id);
        return indicatorService.toggle(id);
    }

    /** 删除（被方案关联禁止；建议改为停用） */
    @RequireAdmin
    @DeleteMapping("/delete")
    public Map<String, Object> delete(@RequestParam String id) {
        log.info("收到节水指标删除请求：id={}", id);
        indicatorService.delete(id);
        return SavingSupport.ok();
    }
}
