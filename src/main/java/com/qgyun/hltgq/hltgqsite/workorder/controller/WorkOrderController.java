package com.qgyun.hltgq.hltgqsite.workorder.controller;

import com.qgyun.hltgq.hltgqsite.workorder.service.WorkOrderService;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderListVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 工单管理查询接口（/work-order）。
 * <p>筛选：所属站点（id/编号/名称）+ 处理时间（pyxcen 日期区间）+ 状态（中文/编码）；
 * 枚举字段返回中文并附编码原文（权威值）；实时查询无缓存；走现有登录会话鉴权。
 */
@RestController
@RequestMapping("/work-order")
public class WorkOrderController {

    @Autowired
    private WorkOrderService workOrderService;

    /**
     * 工单列表（按创建时间倒序，裸 List）。
     *
     * @param site      所属站点：站点 id / 编号 / 名称，可选
     * @param startDate 处理时间起 yyyy-MM-dd（含当日），可选
     * @param endDate   处理时间止 yyyy-MM-dd（含当日），可选
     * @param status    状态：待处理/处理中/已关闭/已取消/已逾期（或编码 #1#/#2#/#3#/#4#/#iizl#），可选
     */
    @GetMapping("/list")
    public List<WorkOrderListVO> list(
            @RequestParam(required = false) String site,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String status) {
        return workOrderService.list(site, startDate, endDate, status);
    }
}
