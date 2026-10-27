package com.qgyun.hltgq.hltgqsite.workorder.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.model.util.ApiResultUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DownloadHeaderUtils;
import com.qgyun.hltgq.hltgqsite.model.util.MediaTypeUtils;
import com.qgyun.hltgq.hltgqsite.workorder.service.WorkOrderService;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderAssignRequest;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderAttachmentDownloadVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderAttachmentVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderCreateVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderDetailVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderListVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderOrgVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderResultRequest;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderRowVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderSaveRequest;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
 * <p>处理结果附件：上传即落盘并插行（order_id 为空），保存处理结果时按附件 id 集合绑定；
 * 未归属附件可即时移除，工单删除连带清理行与文件。
 * <p>状态口径：#1# 待指派、#2# 处理中、#3# 已关闭、#4# 已取消、#iizl# 已逾期；
 * 状态流转均为条件更新，状态已变化返回 400 提示刷新。
 */
@RestController
@RequestMapping("/work-order")
public class WorkOrderController {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderController.class);

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
        return ApiResultUtils.ok();
    }

    /** 删除工单（仅待指派）：关联问题回退为待处理。 */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestBody IdRequest request) {
        workOrderService.remove(request == null ? null : request.getId());
        return ApiResultUtils.ok();
    }

    /** 指派工单（仅待指派）：处理人员必填，负责部门/要求完成时间可选。 */
    @PostMapping("/assign")
    public Map<String, Object> assign(@RequestBody WorkOrderAssignRequest request) {
        workOrderService.assign(request);
        return ApiResultUtils.ok();
    }

    /** 取消工单（仅待指派）：关联问题回退为待处理。 */
    @PostMapping("/cancel")
    public Map<String, Object> cancel(@RequestBody IdRequest request) {
        workOrderService.cancel(request == null ? null : request.getId());
        return ApiResultUtils.ok();
    }

    /** 填写处理结果（仅处理中）：写处理结果与处理时间，可重复保存。 */
    @PostMapping("/result")
    public Map<String, Object> result(@RequestBody WorkOrderResultRequest request) {
        workOrderService.saveResult(request);
        return ApiResultUtils.ok();
    }

    /** 提交验收（仅处理中且已填写处理结果）：状态置为已关闭。 */
    @PostMapping("/submit-accept")
    public Map<String, Object> submitAccept(@RequestBody IdRequest request) {
        workOrderService.submitAccept(request == null ? null : request.getId());
        return ApiResultUtils.ok();
    }

    /** 处理结果附件上传（multipart 单文件；白名单：图片/PDF/Word/Excel；返回 {id, fileName, fileSize, fileExt}） */
    @PostMapping("/attachment/upload")
    public WorkOrderAttachmentVO uploadAttachment(@RequestParam("file") MultipartFile file) {
        log.info("收到工单附件上传请求：{}（{} 字节）",
                file == null ? null : file.getOriginalFilename(),
                file == null ? 0 : file.getSize());
        return workOrderService.uploadAttachment(file);
    }

    /** 处理结果附件下载（Content-Disposition 带原始文件名，前端 fetch + blob 使用） */
    @GetMapping("/attachment/download")
    public ResponseEntity<byte[]> downloadAttachment(@RequestParam String id) {
        WorkOrderAttachmentDownloadVO file = workOrderService.downloadAttachment(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, DownloadHeaderUtils.attachment(file.getFileName()))
                .contentType(MediaTypeUtils.of(file.getFileExt()))
                .body(file.getContent());
    }

    /** 处理结果附件移除（上传后取消 / 弹窗内移除新上传：仅未归属附件可删） */
    @PostMapping("/attachment/remove")
    public Map<String, Object> removeAttachment(@RequestParam String id) {
        log.info("收到工单附件移除请求：id={}", id);
        workOrderService.removeAttachment(id);
        return ApiResultUtils.ok();
    }

    /** 单条操作请求体（{ "id": "..." }）。 */
    @Data
    public static class IdRequest {
        private String id;
    }
}
