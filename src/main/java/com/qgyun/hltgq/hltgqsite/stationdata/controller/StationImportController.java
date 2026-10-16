package com.qgyun.hltgq.hltgqsite.stationdata.controller;

import com.qgyun.hltgq.hltgqsite.model.util.DownloadHeaderUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ExcelTemplateUtils;
import com.qgyun.hltgq.hltgqsite.stationdata.service.StationImportService;
import com.qgyun.hltgq.hltgqsite.stationdata.vo.StationImportResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

/**
 * 站点信息导入接口（/station-data）。
 * <p>两个入口：① GET /import-template 下载导入模板（POI 动态生成：枚举下拉 + 输入提示 + 列宽）；
 * ② POST /import 上传 xlsx 解析入库（逐行结果）。
 * 契约与逐行规则详见 src/main/resources/导入-导出对接接口.md「站点信息导入」章节。
 */
@RestController
@RequestMapping("/station-data")
public class StationImportController {

    @Autowired
    private StationImportService importService;

    /** 下载站点信息导入模板（响应文件名：站点信息导入模板.xlsx） */
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        DownloadHeaderUtils.attachment("站点信息导入模板.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(buildTemplate());
    }

    /**
     * POI 动态生成模板：表头（与 {@link StationImportService#TEMPLATE_HEADERS} 同源）+ 枚举下拉 + 输入提示 + 列宽。
     * <p>监测类型 / 监测方法为多选列（下拉仅提示不拦截）；站点状态 / 是否接通市电为单选列（严格校验）。
     */
    private byte[] buildTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("站点信息");
            String[] headers = StationImportService.TEMPLATE_HEADERS;
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int first = 1;
            int last = StationImportService.MAX_ROWS;
            ExcelTemplateUtils.addDropdown(sheet, 5, StationImportService.TYPE_OPTIONS, false,
                    "监测类型", "多选：多个用顿号分隔（如：水位站、视频站）；下拉可选单项", first, last);
            ExcelTemplateUtils.addDropdown(sheet, 6, StationImportService.STATUS_OPTIONS, true,
                    "站点状态", "下拉选择：在线 / 离线", first, last);
            ExcelTemplateUtils.addDropdown(sheet, 7, StationImportService.METHOD_OPTIONS, false,
                    "监测方法", "多选：多个用顿号分隔（如：雷达波量水、一体化闸门）；下拉可选单项", first, last);
            ExcelTemplateUtils.addDropdown(sheet, 10, StationImportService.POWER_OPTIONS, true,
                    "是否接通市电", "下拉选择：是 / 否", first, last);
            ExcelTemplateUtils.fitColumnWidths(sheet, headers);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("模板生成失败: " + e.getMessage());
        }
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
