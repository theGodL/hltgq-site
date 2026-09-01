package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.mapper.OperationDecisionMapper;
import com.qgyun.hltgq.hltgqsite.vo.OperationDecisionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行管理决策接口：日期区间查询 + 总览计数 + 状态分布（问题/工单）。
 */
@RestController
@RequestMapping("/operation")
public class OperationDecisionController {

    /** 问题状态编码 → 中文名（顺序即柱状图坐标顺序） */
    private static final String[][] ISSUE_STATUS = {
            {"#1#", "待处理"},
            {"#2#", "处理中"},
            {"#3#", "已转工单"},
            {"#4#", "已关闭"},
            {"#5#", "已作废"},
    };

    /** 工单状态编码 → 中文名（demo 页面文案为"待指派"，对应表 status=#1# 待处理） */
    private static final String[][] ORDER_STATUS = {
            {"#1#", "待指派"},
            {"#2#", "处理中"},
            {"#3#", "已关闭"},
            {"#4#", "已取消"},
    };

    @Autowired
    private OperationDecisionMapper operationDecisionMapper;

    /**
     * 运行管理决策总览：巡查总次数 / 问题上报数 / 工单数 + 问题状态分布 + 工单状态分布。
     * <p>统计口径：巡查总次数仅统计已提交巡检记录（排除草稿），按巡检时间 time 过滤；
     * 问题按发现时间 time 过滤；工单按平台公共字段 created_at 过滤。
     * <p>状态分布固定项返回：无记录的状态补 0，保证前端柱状图坐标稳定。
     *
     * @param startDate 统计起始日期（含），格式 yyyy-MM-dd，可选（不传 = 不限）
     * @param endDate   统计截止日期（含），格式 yyyy-MM-dd，可选（不传 = 不限）
     */
    @GetMapping("/decision-summary")
    public OperationDecisionVO decisionSummary(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endTime = endDate == null ? null : endDate.atTime(LocalTime.MAX);

        OperationDecisionVO vo = new OperationDecisionVO();
        OperationDecisionVO.Summary summary = new OperationDecisionVO.Summary();
        summary.setPatrolCount(operationDecisionMapper.countPatrol(startTime, endTime));
        summary.setIssueCount(operationDecisionMapper.countIssue(startTime, endTime));
        summary.setOrderCount(operationDecisionMapper.countOrder(startTime, endTime));
        vo.setSummary(summary);
        vo.setIssueStatus(buildStatus(ISSUE_STATUS, operationDecisionMapper.groupIssueStatus(startTime, endTime)));
        vo.setOrderStatus(buildStatus(ORDER_STATUS, operationDecisionMapper.groupOrderStatus(startTime, endTime)));
        return vo;
    }

    /**
     * 按固定状态定义补齐分布：编码映射中文名，DB 无记录的状态补 0。
     */
    private List<OperationDecisionVO.StatusItem> buildStatus(String[][] defs,
                                                             List<OperationDecisionVO.StatusItem> rows) {
        Map<String, Long> countMap = new HashMap<>();
        for (OperationDecisionVO.StatusItem row : rows) {
            if (row.getName() != null) {
                countMap.put(row.getName(), row.getValue());
            }
        }
        List<OperationDecisionVO.StatusItem> result = new ArrayList<>();
        for (String[] def : defs) {
            OperationDecisionVO.StatusItem item = new OperationDecisionVO.StatusItem();
            item.setName(def[1]);
            item.setValue(countMap.getOrDefault(def[0], 0L));
            result.add(item);
        }
        return result;
    }
}
