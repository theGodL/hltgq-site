package com.qgyun.hltgq.hltgqsite.wateruse.controller;

import com.qgyun.hltgq.hltgqsite.wateruse.service.WaterUseSummaryService;
import com.qgyun.hltgq.hltgqsite.wateruse.vo.WaterUseReportRowVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 用水总结接口（/water-use-summary）。
 * <p>页面：static/water-use-summary.html（月/灌季/年维度 → 用水量/灌溉水利用系数/应收水费）。
 * <p>页面「天」维度对应本接口 dimension=month：数据为业主按月手录，前端需将日期区间控件
 * 改为月份区间（详见《用水总结接口.md》）。
 * <p>页面双维度组 A/B 由前端两次调用本接口实现，口径完全一致。
 */
@RestController
@RequestMapping("/water-use-summary")
public class WaterUseSummaryController {

    @Autowired
    private WaterUseSummaryService waterUseSummaryService;

    /**
     * 用水总结报表：按 dimension 归桶（month=月 / season=灌季 / year=年）。
     *
     * @param dimension 归桶粒度：month / season / year
     * @param startTime 查询起点（yyyy-MM-dd）
     * @param endTime   查询终点（yyyy-MM-dd）
     */
    @GetMapping("/report")
    public List<WaterUseReportRowVO> report(
            @RequestParam String dimension,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endTime) {
        return waterUseSummaryService.report(dimension, startTime, endTime);
    }
}
