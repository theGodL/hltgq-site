package com.qgyun.hltgq.hltgqsite.patrol.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.patrol.service.PatrolRecordService;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolOptionVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordDetailVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordPhotoItemVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordRowVO;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordSaveRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
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
 * 巡检记录管理接口（/patrol-record/*，patrol-record-list.html + patrol-record-add.html）。
 *
 * <p>列表页：分页（巡检时间区间 + 站点）、详情（查看弹窗）、新增/编辑（抽屉内嵌表单页）、
 * 删除/提交（仅草稿）、新增问题（复用 /patrol-issue 页面）。
 * <p>状态口径：新增落草稿 #1#；列表「提交」置已提交 #2#，提交前要求站点/设备/结果/照片齐全；
 * 已提交记录不可编辑/删除。
 * <p>现场照片：自建表 t_auto_hltgq_water_patrol_record_photo（最多 3 张、单张 ≤5MB），
 * 不写平台 site_image 关系字段。
 */
@RestController
@RequestMapping("/patrol-record")
public class PatrolRecordController {

    @Autowired
    private PatrolRecordService recordService;

    /**
     * 记录分页（按巡检时间倒序）。
     *
     * @param startTime 开始日期 yyyy-MM-dd，可选（含当天）
     * @param endTime   结束日期 yyyy-MM-dd，可选（含当天）
     * @param siteId    巡检站点 id，可选
     */
    @GetMapping("/page")
    public Page<PatrolRecordRowVO> page(@RequestParam(required = false) String startTime,
                                        @RequestParam(required = false) String endTime,
                                        @RequestParam(required = false) String siteId,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size) {
        return recordService.page(startTime, endTime, siteId, page, size);
    }

    /** 记录详情（查看弹窗 / 编辑回填）：含现场照片与关联问题；不存在返回 400。 */
    @GetMapping("/detail")
    public PatrolRecordDetailVO detail(@RequestParam String id) {
        return recordService.detail(id);
    }

    /**
     * 所属计划候选（仅已发布 #2# 进行中计划）；
     * includeId 用于编辑时保留当前计划（原计划可能已完成/取消）。
     */
    @GetMapping("/plans")
    public List<PatrolOptionVO> plans(@RequestParam(required = false) String includeId) {
        return recordService.planOptions(includeId);
    }

    /** 新增记录（草稿）：站点须在计划巡查范围、对象须属于站点、照片 1~3 张；返回记录 id。
     *  幂等：该计划下有空白占位草稿时优先填充它（返回其 id），不会产生「草稿 + 正式」两条。 */
    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody PatrolRecordSaveRequest request) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", recordService.create(request));
        out.put("success", true);
        return out;
    }

    /** 编辑记录（仅草稿）：照片以入参列表为准，返回记录 id。 */
    @PostMapping("/update")
    public Map<String, Object> update(@RequestBody PatrolRecordSaveRequest request) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", recordService.update(request));
        out.put("success", true);
        return out;
    }

    /** 删除记录（仅草稿）：记录下已上报问题时拒绝。 */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestBody IdRequest request) {
        recordService.remove(request == null ? null : request.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    /** 提交记录（草稿 → 已提交）：缺站点/对象/结果/照片的脏草稿拒绝提交。 */
    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody IdRequest request) {
        recordService.submit(request == null ? null : request.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    /**
     * 上传现场照片（multipart，字段名 file）：仅图片、单张 ≤5MB、最多 3 张；
     * 返回照片 id 与预览地址，保存记录时把 id 放入 photoIds。
     */
    @PostMapping("/photo")
    public PatrolRecordPhotoItemVO uploadPhoto(@RequestParam("file") MultipartFile file) {
        return recordService.uploadPhoto(file);
    }

    /** 读取现场照片（浏览器 img 直连；登录态校验由拦截器统一处理）。 */
    @GetMapping("/photo/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable String id) {
        PatrolRecordService.PhotoContent data = recordService.readPhoto(id);
        return ResponseEntity.ok()
                .contentType(mediaTypeOf(data.getExt()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(data.getContent());
    }

    /** 移除未保存的照片（误传清理；已保存到记录的不允许移除）。 */
    @PostMapping("/photo/remove")
    public Map<String, Object> removePhoto(@RequestBody IdRequest request) {
        recordService.removePhoto(request == null ? null : request.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    /** 扩展名 → 图片 Content-Type（未知按二进制流，避免浏览器猜测）。 */
    private static MediaType mediaTypeOf(String ext) {
        if (ext == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        switch (ext.toLowerCase()) {
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG;
            case "png":
                return MediaType.IMAGE_PNG;
            case "gif":
                return MediaType.IMAGE_GIF;
            case "bmp":
                return MediaType.parseMediaType("image/bmp");
            case "webp":
                return MediaType.parseMediaType("image/webp");
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /** 单条操作请求体（{ "id": "..." }）。 */
    @Data
    public static class IdRequest {
        private String id;
    }
}
