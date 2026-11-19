package com.qgyun.hltgq.hltgqsite.sms.controller;

import com.qgyun.hltgq.hltgqsite.auth.RequireAdmin;
import com.qgyun.hltgq.hltgqsite.model.util.DownloadHeaderUtils;
import com.qgyun.hltgq.hltgqsite.sms.service.SmsReceiverService;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsReceiverSaveVO;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsReceiverVO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 短信接收人管理（仅系统管理员）：页面 message-rule-setting.html「短信管理」Tab 的后端接口。
 * <p>发送取人口径见 {@link SmsReceiverService#resolvePhones}：完全以接收人表为准，
 * 表未建/为空时该类短信不发送（不读取任何配置清单）。
 */
@RequireAdmin
@RestController
@RequestMapping("/sms/receiver")
public class SmsReceiverController {

    private final SmsReceiverService smsReceiverService;

    public SmsReceiverController(SmsReceiverService smsReceiverService) {
        this.smsReceiverService = smsReceiverService;
    }

    /** 页面元数据：类型字典、表状态（未建 / 空 / 已有数据） */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return smsReceiverService.meta();
    }

    /** 接收人分页：bizType 可选过滤（daily / alarm）；表未建时返回空列表 */
    @GetMapping("/page")
    public Map<String, Object> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String bizType) {
        return smsReceiverService.page(page, size, bizType);
    }

    /** 导出接收人 xlsx（bizType 可选：不传导出全部，传则仅该类型） */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String bizType) {
        String fileName = "短信接收人_" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, DownloadHeaderUtils.attachment(fileName))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(smsReceiverService.exportExcel(bizType));
    }

    /**
     * 导入接收人 xlsx。
     * <p>mode=replace（默认）以文件为准：文件中出现的每个类型，库中不在文件内的记录删除；
     * mode=merge 仅新增/更新不删除。任一行校验失败整体拒绝（400）。
     */
    @PostMapping("/import")
    public Map<String, Object> importFile(@RequestParam("file") MultipartFile file,
                                          @RequestParam(defaultValue = "replace") String mode) {
        return smsReceiverService.importExcel(file, mode);
    }

    /** 新增接收人（类型/姓名/号码必填，同类型同号码查重） */
    @PostMapping
    public SmsReceiverVO create(@RequestBody SmsReceiverSaveVO body) {
        return smsReceiverService.create(body);
    }

    /** 编辑接收人（类型锁定，可改姓名/号码） */
    @PutMapping("/{id}")
    public SmsReceiverVO update(@PathVariable String id, @RequestBody SmsReceiverSaveVO body) {
        return smsReceiverService.update(id, body);
    }

    /** 删除接收人 */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String id) {
        smsReceiverService.delete(id);
        return true;
    }
}
