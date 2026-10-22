package com.qgyun.hltgq.hltgqsite.patrol.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.patrol.service.PatrolPlanService;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolOptionVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordSaveVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolScheduleDetailVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolScheduleSaveVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolScheduleVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolSiteVO;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 巡检计划管理页接口（patrol-plan-list.html / patrol-plan-add.html）。
 *
 * <p>列表页：分页列表（全状态 + 创建人/计划类型筛选）、详情（查看弹窗）、
 * 计划下巡检记录分页（查看巡检记录弹窗）、添加巡检记录（弹窗）、
 * 发布草稿、编辑草稿、完成/取消/批量删除（顶部与行操作）。
 * <p>状态口径：新增/导入落草稿；发布后按<b>巡检记录</b>推导展示态——尚无巡检记录为未开始、
 * 已有巡检记录为进行中；完成/取消为人工终态。
 * <p>新增页：create + 站点/用户下拉；添加记录弹窗另需计划巡查范围站点与站点设备下拉。
 * <p>导入模板与导入仍由 {@link PatrolScheduleController} 提供（/patrol-schedule/import-template、/import）。
 */
@RestController
@RequestMapping("/patrol-schedule")
public class PatrolPlanController {

    @Autowired
    private PatrolPlanService planService;

    /**
     * 计划分页（全状态，按计划开始时间倒序）。
     *
     * @param name      计划名称模糊，可选
     * @param creatorId 创建人用户 id，可选
     * @param planType  计划类型：annual / monthly（也接受 年度计划/月度计划 或编码），可选
     * @param status    存储状态：草稿/已发布（进行中同义）/已完成/已取消 或编码，可选；
     *                  未开始 为按巡检记录推导的展示态，不支持筛选（传未开始返回 400）
     */
    @GetMapping("/page")
    public Page<PatrolScheduleVO> page(@RequestParam(required = false) String name,
                                       @RequestParam(required = false) String creatorId,
                                       @RequestParam(required = false) String planType,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "10") long size) {
        return planService.page(name, creatorId, planType, status, page, size);
    }

    /** 计划详情（查看弹窗）；不存在返回 400。 */
    @GetMapping("/detail")
    public PatrolScheduleDetailVO detail(@RequestParam String id) {
        return planService.detail(id);
    }

    /** 计划下的巡检记录分页（查看巡检记录弹窗，按巡检时间倒序）。 */
    @GetMapping("/records")
    public Page<PatrolRecordVO> records(@RequestParam String scheduleId,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size) {
        return planService.records(scheduleId, page, size);
    }

    /** 计划巡查范围站点（添加巡检记录弹窗的站点下拉）。 */
    @GetMapping("/plan-sites")
    public List<PatrolSiteVO> planSites(@RequestParam String scheduleId) {
        return planService.detail(scheduleId).getSites();
    }

    /** 发布计划（批量）：仅草稿可发布，已过结束日期的计划拒绝（需先编辑）；发布后展示态按是否开始巡检流转。 */
    @PostMapping("/publish")
    public Map<String, Object> publish(@RequestBody IdsRequest request) {
        int updated = planService.publish(request == null ? null : request.getIds());
        return result(updated);
    }

    /** 完成计划（批量）：状态置已完成。 */
    @PostMapping("/complete")
    public Map<String, Object> complete(@RequestBody IdsRequest request) {
        int updated = planService.complete(request == null ? null : request.getIds());
        return result(updated);
    }

    /** 取消计划（批量）：状态置已取消。 */
    @PostMapping("/cancel")
    public Map<String, Object> cancel(@RequestBody IdsRequest request) {
        int updated = planService.cancel(request == null ? null : request.getIds());
        return result(updated);
    }

    /** 批量删除计划；计划下存在巡检记录时拒绝（400）。 */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestBody IdsRequest request) {
        int deleted = planService.remove(request == null ? null : request.getIds());
        return result(deleted);
    }

    /** 新增计划（新增页面提交）：返回新计划 id 与自动生成的编号。 */
    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody PatrolScheduleSaveVO request) {
        String id = planService.create(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("success", true);
        return out;
    }

    /** 编辑计划（仅草稿）：返回计划 id；非草稿或不存在返回 400。 */
    @PostMapping("/update")
    public Map<String, Object> update(@RequestBody PatrolScheduleSaveVO request) {
        String id = planService.update(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("success", true);
        return out;
    }

    /**
     * 添加巡检记录（弹窗提交）；站点须在计划巡查范围、设备须属于该站点。
     * <p>现场照片先调 /patrol-record/photo 上传（最多 3 张）；状态为已提交时至少 1 张，草稿可空。
     */
    @PostMapping("/record")
    public Map<String, Object> addRecord(@RequestBody PatrolRecordSaveVO request) {
        planService.addRecord(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    /** 创建人 / 巡检人员候选（姓名或登录名模糊）。 */
    @GetMapping("/users")
    public List<PatrolOptionVO> users(@RequestParam(required = false) String keyword) {
        return planService.userOptions(keyword);
    }

    /** 站点候选（新增计划巡查范围，名称/编号模糊）。 */
    @GetMapping("/sites")
    public List<PatrolOptionVO> sites(@RequestParam(required = false) String keyword) {
        return planService.siteOptions(keyword);
    }

    /** 设备候选（添加巡检记录：按所选站点）。 */
    @GetMapping("/devices")
    public List<PatrolOptionVO> devices(@RequestParam String siteId) {
        return planService.deviceOptions(siteId);
    }

    /** 统一成功响应：{ success: true, count: 影响行数 } */
    private Map<String, Object> result(int count) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("count", count);
        return out;
    }

    /** 批量操作请求体（{ "ids": ["..."] }）。 */
    @Data
    public static class IdsRequest {
        private List<String> ids;
    }
}
