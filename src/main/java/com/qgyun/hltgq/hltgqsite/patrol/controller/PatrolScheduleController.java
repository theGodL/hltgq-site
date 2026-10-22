package com.qgyun.hltgq.hltgqsite.patrol.controller;

import com.qgyun.hltgq.hltgqsite.model.util.DownloadHeaderUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ExcelTemplateUtils;
import com.qgyun.hltgq.hltgqsite.patrol.service.PatrolScheduleImportService;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolImportResult;
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
 * 巡查计划导入接口（/patrol-schedule）。
 * <p>两个入口：① GET /import-template 下载导入模板（POI 动态生成：枚举下拉 + 输入提示 + 列宽）；
 * ② POST /import 上传 xlsx 解析入库（逐行结果）。
 * 契约与逐行规则详见 src/main/resources/导入-导出对接接口.md。
 */
@RestController
@RequestMapping("/patrol-schedule")
public class PatrolScheduleController {

    @Autowired
    private PatrolScheduleImportService importService;

    /** 下载巡查计划导入模板（响应文件名：巡查计划导入模板.xlsx） */
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        DownloadHeaderUtils.attachment("巡查计划导入模板.xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(buildTemplate());
    }

    /**
     * POI 动态生成模板：表头（与 {@link PatrolScheduleImportService#TEMPLATE_HEADERS} 同源）
     * + 枚举下拉（计划类型/状态单选严格校验）+ 匹配规则输入提示 + 列宽。
     * <p>巡查范围提示按站点名称（兼容编号）匹配；创建人/巡检人员提示按人员登录名匹配，
     * 巡检人员为单选（多个用顿号分隔时取第一个匹配项）。
     */
    private byte[] buildTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("巡查计划");
            String[] headers = PatrolScheduleImportService.TEMPLATE_HEADERS;
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int first = 1;
            int last = PatrolScheduleImportService.MAX_ROWS;
            ExcelTemplateUtils.addPrompt(sheet, 0, "计划编号",
                    "留空自动生成编号；填写须唯一", first, last);
            ExcelTemplateUtils.addDropdown(sheet, 2, PatrolScheduleImportService.PLAN_TYPE_OPTIONS, true,
                    "计划类型", "下拉选择：年度计划 / 月度计划", first, last);
            ExcelTemplateUtils.addPrompt(sheet, 3, "巡查范围",
                    "填站点名称或站点编号；多个用顿号分隔（如：汪屋闸、1080）", first, last);
            ExcelTemplateUtils.addPrompt(sheet, 4, "巡查内容", "必填，不超过 1000 字", first, last);
            ExcelTemplateUtils.addPrompt(sheet, 5, "计划开始时间", "格式 yyyy-MM-dd", first, last);
            ExcelTemplateUtils.addPrompt(sheet, 6, "计划结束时间", "格式 yyyy-MM-dd；不得早于开始时间", first, last);
            ExcelTemplateUtils.addPrompt(sheet, 7, "创建人",
                    "填人员登录名；留空则默认当前操作人", first, last);
            ExcelTemplateUtils.addPrompt(sheet, 8, "巡检人员",
                    "填人员登录名（单选）；多个用顿号分隔时取第一个", first, last);
            ExcelTemplateUtils.addDropdown(sheet, 9, PatrolScheduleImportService.STATUS_OPTIONS, true,
                    "状态", "可留空，默认草稿；下拉选择：草稿 / 已发布 / 已完成 / 已取消（已发布后展示态按是否开始巡检推导）", first, last);
            ExcelTemplateUtils.fitColumnWidths(sheet, headers);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("模板生成失败: " + e.getMessage());
        }
    }

    /**
     * 上传导入巡查计划：multipart file（模板格式 .xlsx）。
     * <p>整体性错误（空文件/非 xlsx/缺列/超 300 行/无数据行）返回 400；
     * 行级问题在响应体中逐行给出，单行失败不影响其他行。
     */
    @PostMapping("/import")
    public PatrolImportResult importFile(@RequestParam("file") MultipartFile file) {
        return importService.importFile(file);
    }
}
