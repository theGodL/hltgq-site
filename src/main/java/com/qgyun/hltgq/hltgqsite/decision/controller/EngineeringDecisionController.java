package com.qgyun.hltgq.hltgqsite.decision.controller;

import com.qgyun.hltgq.hltgqsite.decision.service.EngineeringDecisionService;
import com.qgyun.hltgq.hltgqsite.decision.vo.EngineeringSummaryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工程管理决策接口（/engineering）。
 */
@RestController
@RequestMapping("/engineering")
public class EngineeringDecisionController {

    @Autowired
    private EngineeringDecisionService engineeringDecisionService;

    /**
     * 工程管理决策总览：站点风险等级堆叠柱 + 工单类型饼图 + 问题状态饼图。
     * 全量统计无日期过滤，一次请求渲染整页。
     */
    @GetMapping("/decision-summary")
    public EngineeringSummaryVO decisionSummary() {
        return engineeringDecisionService.summary();
    }
}
