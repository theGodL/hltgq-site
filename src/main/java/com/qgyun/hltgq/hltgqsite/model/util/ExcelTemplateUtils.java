package com.qgyun.hltgq.hltgqsite.model.util;

import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;

/**
 * Excel 导入模板生成工具（Apache POI，XSSF，仅 .xlsx）：枚举下拉、输入提示、列宽（唯一实现点）。
 * <p>下拉为数据区整列数据验证（显式列表约束）：单选列 strict=true 时输入非选项值弹错误框拦截；
 * 多选列 strict=false 仅显示提示不拦截（值可用顿号拼接多项）。
 * <p>注：XSSF 的 {@code setSuppressDropDownArrow} 命名与 Excel 实际行为相反（底层 showDropDown
 * XML 属性语义被反转）：调用 {@code true} 才显示下拉箭头，已按 POI 5.2.5 源码与生成文件复核。
 */
public final class ExcelTemplateUtils {

    /** 列宽上下限（字符数），与前端导出（monitor-export.js fitCols）同口径 */
    private static final int MIN_COL_WIDTH = 10;
    private static final int MAX_COL_WIDTH = 30;

    /** POI 列宽单位：1 字符宽 = 256 */
    private static final int WIDTH_UNIT = 256;

    private ExcelTemplateUtils() {
    }

    /** 按表头文本设置列宽（中文按 2 字符宽估算，与前端导出同口径） */
    public static void fitColumnWidths(Sheet sheet, String[] headers) {
        for (int i = 0; i < headers.length; i++) {
            int len = 0;
            for (int j = 0; j < headers[i].length(); j++) {
                len += headers[i].charAt(j) > 255 ? 2 : 1;
            }
            int width = Math.min(MAX_COL_WIDTH, Math.max(MIN_COL_WIDTH, len + 4));
            sheet.setColumnWidth(i, width * WIDTH_UNIT);
        }
    }

    /**
     * 枚举列下拉 + 输入提示（数据区整列）。
     *
     * @param sheet      工作表
     * @param col        列序号（0 基）
     * @param options    下拉选项（中文名）
     * @param strict     true=输入非选项值报错（单选列）；false=仅提示不拦截（多选列）
     * @param promptTitle 输入提示标题
     * @param promptText  输入提示内容
     * @param firstRow   数据区首行（0 基，表头为第 0 行）
     * @param lastRow    数据区末行（0 基，含）
     */
    public static void addDropdown(Sheet sheet, int col, String[] options, boolean strict,
                                   String promptTitle, String promptText, int firstRow, int lastRow) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(options);
        DataValidation validation = helper.createValidation(constraint,
                new CellRangeAddressList(firstRow, lastRow, col, col));
        // XSSF：true 才显示下拉箭头（showDropDown XML 属性语义反转，POI 5.2.5 实测）
        validation.setSuppressDropDownArrow(true);
        validation.setShowPromptBox(true);
        validation.createPromptBox(promptTitle, promptText);
        if (strict) {
            validation.setShowErrorBox(true);
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.createErrorBox("输入无效", "请从下拉列表中选择：" + String.join(" / ", options));
        } else {
            validation.setShowErrorBox(false);
        }
        sheet.addValidationData(validation);
    }

    /**
     * 列输入提示（无下拉）：选中单元格时悬浮显示填写说明，仅提示不拦截。
     * <p>实现为「恒真」自定义约束的数据验证（单元格只挂提示框，不产生任何输入限制）。
     */
    public static void addPrompt(Sheet sheet, int col, String promptTitle, String promptText,
                                 int firstRow, int lastRow) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createCustomConstraint("TRUE");
        DataValidation validation = helper.createValidation(constraint,
                new CellRangeAddressList(firstRow, lastRow, col, col));
        validation.setShowPromptBox(true);
        validation.createPromptBox(promptTitle, promptText);
        validation.setShowErrorBox(false);
        sheet.addValidationData(validation);
    }
}
