package com.qgyun.hltgq.hltgqsite.watersaving.controller;

import com.qgyun.hltgq.hltgqsite.auth.RequireAdmin;
import com.qgyun.hltgq.hltgqsite.watersaving.SavingSupport;
import com.qgyun.hltgq.hltgqsite.watersaving.service.SavingSchemeService;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingSchemeSaveVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingSchemeVO;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.SavingVerifyVO;
import com.qgyun.hltgq.hltgqsite.model.vo.FileDownloadVO;
import com.qgyun.hltgq.hltgqsite.model.util.DownloadHeaderUtils;
import com.qgyun.hltgq.hltgqsite.model.util.MediaTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 节水方案和措施（节水管理页面一：列表 / 新增 / 编辑 / 核验 / 刷新对比依据 / 附件 / 导出 / 生成文档）。
 * <p>达标链与核验作废（方案A）均为服务端派生计算，前端只展示。
 * <p>权限：查询所有登录用户可用；写操作（新增/编辑/核验/刷新依据/删除/附件）仅系统管理员
 * （@RequireAdmin，拦截器校验，非管理员 403）。
 */
@RestController
@RequestMapping("/water-saving/schemes")
public class SavingSchemeController {

    private static final Logger log = LoggerFactory.getLogger(SavingSchemeController.class);

    @Autowired
    private SavingSchemeService schemeService;

    /**
     * 列表分页（筛选：年度 / 名称 / 负责人 / 状态）。
     *
     * @return total / page / size / pages / records（含指标结果行、依据摘要、达标链）
     */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) Integer year,
                                    @RequestParam(required = false) String name,
                                    @RequestParam(required = false) String owner,
                                    @RequestParam(required = false) String status) {
        return schemeService.page(page, size, year, name, owner, status);
    }

    /** 详情（含指标计算结果行、依据摘要、达标链、待复核原因） */
    @GetMapping("/detail")
    public SavingSchemeVO detail(@RequestParam String id) {
        return schemeService.detail(id);
    }

    /** 新增（名称年度内唯一；目标 0 < 目标 ≤ 潜力；指标 ≥1 且启用） */
    @RequireAdmin
    @PostMapping("/create")
    public SavingSchemeVO create(@RequestBody SavingSchemeSaveVO body) {
        log.info("收到节水方案新增请求：name={}，evidenceId={}",
                body == null ? null : body.getName(),
                body == null ? null : body.getEvidenceId());
        return schemeService.create(body);
    }

    /** 编辑（换依据 → baseline 更新 + 曾核验作废；已核验方案状态提交值忽略） */
    @RequireAdmin
    @PutMapping("/update")
    public SavingSchemeVO update(@RequestParam String id, @RequestBody SavingSchemeSaveVO body) {
        log.info("收到节水方案编辑请求：id={}，name={}", id, body == null ? null : body.getName());
        return schemeService.update(id, body);
    }

    /** 核验（写入实施后水量等核验数据；对比条件确认 → 已完成，未确认 → 待复核） */
    @RequireAdmin
    @PostMapping("/verify")
    public SavingSchemeVO verify(@RequestBody SavingVerifyVO body) {
        log.info("收到节水方案核验请求：id={}", body == null ? null : body.getId());
        return schemeService.verify(body);
    }

    /** 刷新对比依据（baseline 拉齐为当前记录实际值；曾核验同时作废） */
    @RequireAdmin
    @PostMapping("/refresh-basis")
    public SavingSchemeVO refreshBasis(@RequestParam String id) {
        log.info("收到刷新对比依据请求：id={}", id);
        return schemeService.refreshBasis(id);
    }

    /** 删除（附件文件提交后清理） */
    @RequireAdmin
    @DeleteMapping("/delete")
    public Map<String, Object> delete(@RequestParam String id) {
        log.info("收到节水方案删除请求：id={}", id);
        schemeService.delete(id);
        return SavingSupport.ok();
    }

    /** 生成措施文档（服务端拼装文本，前端下载 .txt） */
    @GetMapping("/document")
    public Map<String, Object> document(@RequestParam String id) {
        return schemeService.document(id);
    }

    /**
     * 附件上传（上传即替换；单文件 ≤2MB；白名单：图片/PDF/Word/Excel）。
     *
     * @return 方案详情（含附件名）
     */
    @RequireAdmin
    @PostMapping("/attachment/upload")
    public SavingSchemeVO uploadAttachment(@RequestParam String id, @RequestParam("file") MultipartFile file) {
        log.info("收到节水方案附件上传请求：id={}，file={}（{} 字节）", id,
                file == null ? null : file.getOriginalFilename(),
                file == null ? 0 : file.getSize());
        return schemeService.uploadAttachment(id, file);
    }

    /** 附件下载（Content-Disposition 带原始文件名，前端 fetch + blob 使用） */
    @GetMapping("/attachment/download")
    public ResponseEntity<byte[]> downloadAttachment(@RequestParam String id) {
        FileDownloadVO file = schemeService.downloadAttachment(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, DownloadHeaderUtils.attachment(file.getFileName()))
                .contentType(MediaTypeUtils.of(file.getFileExt()))
                .body(file.getContent());
    }

    /** 附件移除（清空列 + 物理删文件） */
    @RequireAdmin
    @PostMapping("/attachment/remove")
    public SavingSchemeVO removeAttachment(@RequestParam String id) {
        log.info("收到节水方案附件移除请求：id={}", id);
        return schemeService.removeAttachment(id);
    }

    /** 导出 CSV（方案 × 指标展开；includeDemo=false 默认排除虚拟演示数据） */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "false") boolean includeDemo) {
        log.info("收到节水方案导出请求：includeDemo={}", includeDemo);
        FileDownloadVO file = schemeService.exportCsv(includeDemo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, DownloadHeaderUtils.attachment(file.getFileName()))
                .contentType(MediaTypeUtils.of(file.getFileExt()))
                .body(file.getContent());
    }
}
