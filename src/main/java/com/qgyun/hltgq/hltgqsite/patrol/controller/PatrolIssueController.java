package com.qgyun.hltgq.hltgqsite.patrol.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.model.util.ApiResultUtils;
import com.qgyun.hltgq.hltgqsite.model.util.MediaTypeUtils;
import com.qgyun.hltgq.hltgqsite.patrol.service.PatrolIssueService;
import com.qgyun.hltgq.hltgqsite.patrol.support.PatrolPhotoSupport;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueAdminDetailVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueAdminRequest;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueCreateRequest;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueCreateVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssuePhotoItemVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssuePhotoVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueRecordContextVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolIssueRowVO;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderCreateVO;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 巡检问题接口（/patrol-issue/*，PC 巡检记录抽屉、H5 与问题记录管理页共用）。
 *
 * <p>页面：pc 端 patrol-plan-list.html 巡检记录行「新增问题」→ 右侧抽屉内嵌 patrol-issue-add.html；
 * 问题记录管理页 patrol-issue-list.html（分页/详情/作废/关闭/直接处理/填写结果/转工单）；
 * H5 端不依赖页面，直接调用本组接口（上传照片 → 提交问题）。
 * <p>鉴权：与其它接口一致走登录拦截器；发现人缺省取当前登录人，审计列取当前操作人。
 * <p>契约详见 src/main/resources/H5接口.md「新增巡检问题」章节。
 */
@RestController
@RequestMapping("/patrol-issue")
public class PatrolIssueController {

    @Autowired
    private PatrolIssueService issueService;

    /** 新增问题：返回问题 id 与自动生成的编号。 */
    @PostMapping("/create")
    public PatrolIssueCreateVO create(@RequestBody PatrolIssueCreateRequest request) {
        return issueService.create(request);
    }

    /**
     * 问题分页（按发现时间倒序）。
     *
     * @param title  问题标题模糊，可选
     * @param siteId 所属站点 id，可选
     * @param status 状态：待处理/处理中/已转工单/已关闭/已作废（或编码），可选
     */
    @GetMapping("/page")
    public Page<PatrolIssueRowVO> page(@RequestParam(required = false) String title,
                                       @RequestParam(required = false) String siteId,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "10") long size) {
        return issueService.page(title, siteId, status, page, size);
    }

    /** 问题详情（查看弹窗）：含关联工单；现场照片走 /photos。 */
    @GetMapping("/detail")
    public PatrolIssueAdminDetailVO detail(@RequestParam String id) {
        return issueService.detail(id);
    }

    /** 作废问题（仅待处理）：置已作废。 */
    @PostMapping("/void")
    public Map<String, Object> voidIssue(@RequestBody PatrolIssueAdminRequest request) {
        issueService.voidIssue(request == null ? null : request.getId());
        return ApiResultUtils.ok();
    }

    /** 关闭问题（仅处理中）：置已关闭。 */
    @PostMapping("/close")
    public Map<String, Object> closeIssue(@RequestBody PatrolIssueAdminRequest request) {
        issueService.closeIssue(request == null ? null : request.getId());
        return ApiResultUtils.ok();
    }

    /** 直接处理（仅待处理）：状态转为处理中，处理方式置直接处理。 */
    @PostMapping("/handle")
    public Map<String, Object> directHandle(@RequestBody PatrolIssueAdminRequest request) {
        issueService.directHandle(request == null ? null : request.getId());
        return ApiResultUtils.ok();
    }

    /** 填写处理结果（仅处理中）：只写处理结果，不改状态，可重复保存。 */
    @PostMapping("/result")
    public Map<String, Object> saveResult(@RequestBody PatrolIssueAdminRequest request) {
        issueService.saveResult(request);
        return ApiResultUtils.ok();
    }

    /**
     * 转工单（仅待处理）：新建待指派工单并把问题置为已转工单；
     * 返回新工单 id/编号/标题。
     */
    @PostMapping("/transfer")
    public Map<String, Object> transfer(@RequestBody PatrolIssueAdminRequest request) {
        WorkOrderCreateVO order = issueService.transfer(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("workOrderId", order.getId());
        out.put("workOrderCode", order.getCode());
        out.put("workOrderTitle", order.getTitle());
        return out;
    }

    /**
     * 巡检记录上下文（新增问题页回填）：站点/设备/时间以记录为准；
     * 记录已带站点时 siteLocked=true，记录缺站点属数据异常（不允许上报）。
     */
    @GetMapping("/record-context")
    public PatrolIssueRecordContextVO recordContext(@RequestParam("recordId") String recordId) {
        return issueService.recordContext(recordId);
    }

    /**
     * 上传现场照片（multipart，字段名 file）：仅图片、单张 ≤10MB、最多 9 张；
     * 返回照片 id 与预览地址，提交问题时把 id 放入 photoIds。
     */
    @PostMapping("/photo")
    public PatrolIssuePhotoVO uploadPhoto(@RequestParam("file") MultipartFile file) {
        return issueService.uploadPhoto(file);
    }

    /** 读取现场照片（浏览器 img 直连；登录态校验由拦截器统一处理）。 */
    @GetMapping("/photo/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable String id) {
        PatrolPhotoSupport.PhotoContent data = issueService.readPhoto(id);
        return ResponseEntity.ok()
                .contentType(MediaTypeUtils.of(data.getExt()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(data.getContent());
    }

    /**
     * 按问题 id 查现场照片列表（H5 / PC 回显）：返回顺序与提交时一致，
     * url 为站点内相对路径，前端拼 API 前缀后直接用于 img。
     */
    @GetMapping("/photos")
    public List<PatrolIssuePhotoItemVO> photos(@RequestParam("issueId") String issueId) {
        return issueService.listPhotos(issueId);
    }

    /** 移除未提交的照片（误传清理；已提交到问题记录的不允许移除）。 */
    @PostMapping("/photo/remove")
    public Map<String, Object> removePhoto(@RequestBody PhotoRemoveRequest request) {
        issueService.removePhoto(request == null ? null : request.getId());
        return ApiResultUtils.ok();
    }

    /** 移除照片请求体（{ "id": "..." }）。 */
    @Data
    public static class PhotoRemoveRequest {
        private String id;
    }
}
