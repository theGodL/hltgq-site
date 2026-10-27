package com.qgyun.hltgq.hltgqsite.emergencysupport.controller;

import com.qgyun.hltgq.hltgqsite.emergencysupport.service.EmergencySupportService;
import com.qgyun.hltgq.hltgqsite.emergencysupport.vo.EmergencyAttachmentVO;
import com.qgyun.hltgq.hltgqsite.emergencysupport.vo.EmergencyMaterialOptionVO;
import com.qgyun.hltgq.hltgqsite.emergencysupport.vo.EmergencySupportSaveVO;
import com.qgyun.hltgq.hltgqsite.emergencysupport.vo.EmergencySupportVO;
import com.qgyun.hltgq.hltgqsite.model.util.ApiResultUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DownloadHeaderUtils;
import com.qgyun.hltgq.hltgqsite.model.util.MediaTypeUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.FileDownloadVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 应急保障（自研页面：列表 / 新增 / 查看 / 编辑 / 删除 / 物资装备联动 / 附件）。
 * <p>权限：与库存管理同口径，所有登录用户可用（拦截器统一鉴权）；
 * 编号由服务端生成且只读，编辑不修改编号。
 */
@RestController
@RequestMapping("/emergency-support")
public class EmergencySupportController {

    private static final Logger log = LoggerFactory.getLogger(EmergencySupportController.class);

    @Autowired
    private EmergencySupportService supportService;

    /** 表单元数据：保障类别 / 当前状态下拉、物资装备联动类别编码、当天日期。 */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return supportService.meta();
    }

    /**
     * 列表分页。
     *
     * @param page     页码（默认 1）
     * @param size     每页条数（默认 20，上限 100）
     * @param category 保障类别（中文或编码，可选）
     * @param status   当前状态（中文或编码，可选）
     * @param keyword  编号/名称关键字（可选）
     * @return total / page / size / pages / records
     */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String keyword) {
        return supportService.page(page, size, category, status, keyword);
    }

    /**
     * 详情（含附件清单）。
     *
     * @param id 应急保障记录 ID（必填）
     */
    @GetMapping("/detail")
    public EmergencySupportVO detail(@RequestParam String id) {
        return supportService.detail(id);
    }

    /**
     * 新增（编号服务端生成：YJBZ-yyyyMMdd-当日序号）。
     *
     * @return 保存后的详情
     */
    @PostMapping("/save")
    public EmergencySupportVO save(@RequestBody EmergencySupportSaveVO body) {
        log.info("收到应急保障新增请求：category={}，name={}，attachments={}",
                body == null ? null : body.getCategory(),
                body == null ? null : body.getName(),
                body == null || body.getAttachmentIds() == null ? 0 : body.getAttachmentIds().size());
        return supportService.save(body);
    }

    /**
     * 编辑（编号不变；附件按保留集合重绑）。
     *
     * @return 编辑后的详情
     */
    @PostMapping("/update")
    public EmergencySupportVO update(@RequestBody EmergencySupportSaveVO body) {
        log.info("收到应急保障编辑请求：id={}，name={}，attachments={}",
                body == null ? null : body.getId(),
                body == null ? null : body.getName(),
                body == null || body.getAttachmentIds() == null ? 0 : body.getAttachmentIds().size());
        return supportService.update(body);
    }

    /** 删除（主表物理删除 + 附件清理）。 */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestParam String id) {
        log.info("收到应急保障删除请求：id={}", id);
        supportService.delete(id);
        return ApiResultUtils.ok();
    }

    /**
     * 物资装备联动候选：应急物资台账行（含当前库存数量，选择后前端自动带出）。
     *
     * @param keyword 物资编号/名称关键字（可选）
     */
    @GetMapping("/materials")
    public List<EmergencyMaterialOptionVO> materials(@RequestParam(required = false) String keyword) {
        return supportService.materialOptions(keyword);
    }

    /**
     * 附件上传（multipart，单文件 ≤20MB；白名单：图片/PDF/Word/Excel）。
     *
     * @return {id, fileName, fileSize, fileExt}
     */
    @PostMapping("/attachment/upload")
    public EmergencyAttachmentVO uploadAttachment(@RequestParam("file") MultipartFile file) {
        log.info("收到应急保障附件上传请求：{}（{} 字节）",
                file == null ? null : file.getOriginalFilename(),
                file == null ? 0 : file.getSize());
        return supportService.uploadAttachment(file);
    }

    /**
     * 附件下载（Content-Disposition 带原始文件名，前端 fetch + blob 使用）。
     *
     * @param id 附件 ID（必填）
     */
    @GetMapping("/attachment/download")
    public ResponseEntity<byte[]> downloadAttachment(@RequestParam String id) {
        FileDownloadVO file = supportService.downloadAttachment(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, DownloadHeaderUtils.attachment(file.getFileName()))
                .contentType(MediaTypeUtils.of(file.getFileExt()))
                .body(file.getContent());
    }

    /** 附件移除（新增取消时清理未关联附件；物理删文件 + 删行）。 */
    @PostMapping("/attachment/remove")
    public Map<String, Object> removeAttachment(@RequestParam String id) {
        supportService.removeAttachment(id);
        return ApiResultUtils.ok();
    }
}
