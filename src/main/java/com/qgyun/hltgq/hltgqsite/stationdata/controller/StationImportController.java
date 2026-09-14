package com.qgyun.hltgq.hltgqsite.stationdata.controller;

import com.qgyun.hltgq.hltgqsite.model.util.DownloadHeaderUtils;
import com.qgyun.hltgq.hltgqsite.stationdata.service.StationImportService;
import com.qgyun.hltgq.hltgqsite.stationdata.vo.StationImportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 站点信息导入接口（/station-data）。
 * <p>两个入口：① GET /import-template 下载导入模板；② POST /import 上传 xlsx 解析入库（逐行结果）。
 * 契约与逐行规则详见 src/main/resources/导入-导出对接接口.md「站点信息导入」章节。
 */
@RestController
@RequestMapping("/station-data")
public class StationImportController {

    /** 站点信息导入模板（classpath:static/templates 下） */
    private static final String TEMPLATE_LOCATION = "classpath:static/templates/StationImport_Template.xlsx";

    @Autowired
    private StationImportService importService;

    @Autowired
    private ResourceLoader resourceLoader;

    /** 下载站点信息导入模板（响应文件名：站点信息导入模板.xlsx） */
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate() throws Exception {
        Resource resource = resourceLoader.getResource(TEMPLATE_LOCATION);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes;
        try (InputStream in = resource.getInputStream()) {
            bytes = StreamUtils.copyToByteArray(in);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        DownloadHeaderUtils.attachment("站点信息导入模板.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    /**
     * 上传导入站点信息：multipart file（模板格式 .xlsx）。
     * <p>整体性错误（空文件/非 xlsx/缺列/超 300 行/无数据行）返回 400；
     * 行级问题在响应体中逐行给出，单行失败不影响其他行。
     */
    @PostMapping("/import")
    public StationImportResult importFile(@RequestParam("file") MultipartFile file) {
        return importService.importFile(file);
    }
}
