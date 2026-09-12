package com.qgyun.hltgq.hltgqsite.watersaving.controller;

import com.qgyun.hltgq.hltgqsite.watersaving.service.WaterSavingIndicatorService;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.WaterSavingIndicatorVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 节水体系查询接口（/water-saving-indicator）。
 * <p>枚举字段返回中文（状态）并附编码原文（权威值）；年度统一 YYYY 输出；
 * 明细 ijjwkn 不在此接口返回；实时查询无缓存；走现有登录会话鉴权。
 */
@RestController
@RequestMapping("/water-saving-indicator")
public class WaterSavingIndicatorController {

    @Autowired
    private WaterSavingIndicatorService waterSavingIndicatorService;

    /**
     * 节水体系列表（按年度倒序，裸 List）。
     *
     * @param name   指标名称模糊，可选
     * @param year   年度（YYYY），可选
     * @param status 状态：待填报/已填报/审核中/已退回/已归档（或编码），可选
     */
    @GetMapping("/list")
    public List<WaterSavingIndicatorVO> list(@RequestParam(required = false) String name,
                                             @RequestParam(required = false) String year,
                                             @RequestParam(required = false) String status) {
        return waterSavingIndicatorService.list(name, year, status);
    }
}
