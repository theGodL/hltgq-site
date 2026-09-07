package com.qgyun.hltgq.hltgqsite.decision.controller;

import com.qgyun.hltgq.hltgqsite.decision.service.DroughtDecisionService;
import com.qgyun.hltgq.hltgqsite.decision.vo.DroughtDecisionVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 智能抗旱决策接口（2026-09 新增，页面 drought-trend-analysis.html）。
 * <p>GET /drought-decision/sites 站点下拉；
 * GET /drought-decision/query 一次查询同时返回实测墒情 + 预测墒情（含降雨与干旱等级）。
 */
@RestController
@RequestMapping("/drought-decision")
public class DroughtDecisionController {

    private final DroughtDecisionService droughtDecisionService;

    public DroughtDecisionController(DroughtDecisionService droughtDecisionService) {
        this.droughtDecisionService = droughtDecisionService;
    }

    /** 抗旱站点下拉（复用墒情监测站点） */
    @GetMapping("/sites")
    public List<DroughtDecisionVO.Site> sites() {
        return droughtDecisionService.sites();
    }

    /**
     * 实测 + 预测一次返回。
     *
     * @param stcd      站点编号（必填）
     * @param startDate 起始日期 yyyy-MM-dd（可选，默认 14 天前）
     * @param endDate   截止日期 yyyy-MM-dd（可选，默认今天）
     */
    @GetMapping("/query")
    public DroughtDecisionVO query(
            @RequestParam String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return droughtDecisionService.query(stcd, startDate, endDate);
    }
}
