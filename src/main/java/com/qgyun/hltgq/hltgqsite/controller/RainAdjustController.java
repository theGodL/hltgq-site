package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qgyun.hltgq.hltgqsite.auth.RequireAdmin;
import com.qgyun.hltgq.hltgqsite.service.RainAdjustService;
import com.qgyun.hltgq.hltgqsite.vo.RainAdjustSaveVO;
import com.qgyun.hltgq.hltgqsite.vo.RainAdjustVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 雨量补偿配置维护（监测数据删除方案 §5.1；仅系统管理员）。
 * <p>维护入口＝站点+设备联动（选站后联动该站设备下拉）；保存后 ≤5 分钟（mq 缓存 TTL）
 * 自动生效，无需重启 mq。操作先例：设备侧复位/再次调整前，先在此停用或更新补偿值，
 * 避免过渡窗口错补（方案 §6.8/§6.9）。
 */
@RequireAdmin
@RestController
@RequestMapping("/rain-adjust")
public class RainAdjustController {

    @Autowired
    private RainAdjustService rainAdjustService;

    /** 列表分页（站点名/设备名翻译；siteId/keyword 可选过滤） */
    @GetMapping("/page")
    public Map<String, Object> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String keyword) {
        IPage<RainAdjustVO> result = rainAdjustService.page(page, size, siteId, keyword);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", result.getTotal());
        out.put("page", result.getCurrent());
        out.put("size", result.getSize());
        out.put("pages", result.getPages());
        out.put("records", result.getRecords());
        return out;
    }

    /** 站点候选（新增表单下拉） */
    @GetMapping("/sites")
    public List<RainAdjustVO.SiteOption> sites() {
        return rainAdjustService.sites();
    }

    /** 某站设备候选（按站点联动下拉；含监测类型中文名翻译） */
    @GetMapping("/devices")
    public List<RainAdjustVO.DeviceOption> devices(@RequestParam String siteId) {
        return rainAdjustService.devices(siteId);
    }

    /** 新增配置（site/device/offsetValue 必填；device 全局唯一；stcd 由档案自动填充） */
    @PostMapping
    public RainAdjustVO create(@RequestBody RainAdjustSaveVO body) {
        return rainAdjustService.create(body);
    }

    /** 编辑配置（仅 offsetValue/enabled/remark 生效；site/device 锁定不可改） */
    @PutMapping("/{id}")
    public RainAdjustVO update(@PathVariable String id, @RequestBody RainAdjustSaveVO body) {
        return rainAdjustService.update(id, body);
    }

    /** 删除配置（停用优先；删除出厂默认站配置将触发 mq 预置自检 WARN，前端确认已提示） */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String id) {
        rainAdjustService.delete(id);
        return true;
    }
}
