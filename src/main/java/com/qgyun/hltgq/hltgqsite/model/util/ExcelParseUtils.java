package com.qgyun.hltgq.hltgqsite.model.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 解析工具（Apache POI），仅支持 .xlsx。
 * <p>第一行为表头，后续行为数据；返回 List&lt;Map&lt;表头, 单元格字符串&gt;&gt;。
 * 单元格统一按显示值读取（DataFormatter），数值不带尾随零。
 */
public final class ExcelParseUtils {

    private ExcelParseUtils() {
    }

    /**
     * 解析 xlsx 输入流为行数据列表。
     * 跳过空行；表头去空格；数据行按表头列对齐，缺列补空串。
     */
    public static List<Map<String, String>> parseRows(InputStream in) throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return rows;
            }
            DataFormatter formatter = new DataFormatter();
            List<String> headers = null;
            for (Row row : sheet) {
                if (headers == null) {
                    headers = readHeaders(row, formatter);
                    continue;
                }
                if (isBlankRow(row)) {
                    continue;
                }
                Map<String, String> data = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String value = "";
                    Cell cell = row.getCell(i);
                    if (cell != null) {
                        value = formatter.formatCellValue(cell).trim();
                    }
                    data.put(headers.get(i), value);
                }
                rows.add(data);
            }
        }
        return rows;
    }

    private static List<String> readHeaders(Row row, DataFormatter formatter) {
        List<String> headers = new ArrayList<>();
        if (row != null) {
            for (Cell cell : row) {
                String header = formatter.formatCellValue(cell).trim();
                headers.add(header.isEmpty() ? "列" + (headers.size() + 1) : header);
            }
        }
        return headers;
    }

    private static boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !cell.toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
