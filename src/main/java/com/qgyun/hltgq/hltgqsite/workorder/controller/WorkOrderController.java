package com.qgyun.hltgq.hltgqsite.workorder.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.workorder.service.WorkOrderService;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderAssignRequest;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderCreateVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderDetailVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderListVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderOrgVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderResultRequest;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderRowVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderSaveRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单管理接口（/work-order/*，work-order-list.html + work-order-export.html）。
 *
 * <p>列表页：分页（所属站点 + 处理时间区间 + 状态）、详情（查看弹窗）、
 * 编辑（抽屉内嵌 work-order-add.html，仅待指派）、删除/指派/取消（仅待指派）、
 * 填写处理结果/提交验收（仅处理中）；既有 /list（裸 List）继续服务导出页。
 * <p>状态口径：#1# 待指派、#2# 处理中、#3# 已关闭、#4# 已取消、#iizl# 已逾期；
 * 状态流转均为条件更新，状态已变化返回 400 提示刷新。
 */
@RestController
@RequestMapping("/work-order")
public class WorkOrderController {

    @Autowired
    private WorkOrderService workOrderService;

    /**
     * 工单列表（裸 List，导出页使用，按创建时间倒序）。
     *
     * @param site      所属站点：站点 id / 编号 / 名称，可选
     * @param startDate 处理时间起 yyyy-MM-dd（含当日），可选
     * @param endDate   处理时间止 yyyy-MM-dd（含当日），可选
     * @param status    状态：待指派/处理中/已关闭/已取消/已逾期（或编码），可选
     */
    @GetMapping("/list")
    public List<WorkOrderListVO> list(
            @RequestParam(required = false) String site,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String status) {
        return workOrderService.list(site, startDate, endDate, status);
    }

    /**
     * 工单分页（按创建时间倒序）。
     *
     * @param site      所属站点：站点 id / 编号 / 名称，可选
     * @param startDate 处理时间起 yyyy-MM-dd（含当天），可选
     * @param endDate   处理时间止 yyyy-MM-dd（含当天），可选
     * @param status    状态：待指派/处理中/已关闭/已取消/已逾期（或编码），可选
     */
    @GetMapping("/page")
    public Page<WorkOrderRowVO> page(
            @RequestParam(required = false) String site,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return workOrderService.page(site, startDate, endDate, status, page, size);
    }

    /** 工单详情（查看弹窗 / 编辑回填）；不存在返回 400。 */
    @GetMapping("/detail")
    public WorkOrderDetailVO detail(@RequestParam String id) {
        return workOrderService.detail(id);
    }

    /** 负责部门候选（编辑/指派弹窗下拉）。 */
    @GetMapping("/orgs")
    public List<WorkOrderOrgVO> orgs() {
        return workOrderService.orgs();
    }

    /** 新增工单：状态默认待指派，编号自动生成（GD + 日期时间 + 流水）。 */
    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody WorkOrderSaveRequest request) {
        WorkOrderCreateVO order = workOrderService.create(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("id", order.getId());
        out.put("code", order.getCode());
        return out;
    }

    /** 编辑工单（仅待指派）：站点/设备/部门/人员/要求完成时间全量覆盖。 */
    @PostMapping("/update")
    public Map<String, Object> update(@RequestBody WorkOrderSaveRequest request) {
        workOrderService.update(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    /** 删除工单（仅待指派）：关联问题回退为待处理。 */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestBody IdRequest request) {
        workOrderService.remove(request == null ? null : request.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    /** 指派工单（仅待指派）：处理人员必填，负责部门/要求完成时间可选。 */
    @PostMapping("/assign")
    public Map<String, Object> assign(@RequestBody WorkOrderAssignRequest request) {
        workOrderService.assign(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    /** 取消工单（仅待指派）：关联问题回退为待处理。 */
    @PostMapping("/cancel")
    public Map<String, Object> cancel(@RequestBody IdRequest request) {
        workOrderService.cancel(request == null ? null : request.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    /** 填写处理结果（仅处理中）：写处理结果与处理时间，可重复保存。 */
    @PostMapping("/result")
    public Map<String, Object> result(@RequestBody WorkOrderResultRequest request) {
        workOrderService.saveResult(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    /** 提交验收（仅处理中且已填写处理结果）：状态置为已关闭。 */
    @PostMapping("/submit-accept")
    public Map<String, Object> submitAccept(@RequestBody IdRequest request) {
        workOrderService.submitAccept(request == null ? null : request.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    /** 单条操作请求体（{ "id": "..." }）。 */
    @Data
    public static class IdRequest {
        private String id;
    }
}
