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
 * <p>第一行为表头，后续行为数据；{@link #parseRows} 返回 List&lt;Map&lt;表头, 单元格字符串&gt;&gt;，
 * {@link #parseSheet} 额外携带 Excel 实际行号（供导入逐行结果按真实行号返回）。
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
        for (RowEntry entry : parseSheet(in).getRows()) {
            rows.add(entry.getData());
        }
        return rows;
    }

    /**
     * 解析 xlsx 输入流为工作表数据（表头 + 带 Excel 行号的数据行条目）。
     * <p>与 {@link #parseRows} 同口径：跳过空行、表头去空格、数据行按表头列对齐缺列补空串；
     * 条目附带 Excel 实际行号（1 基，表头为第 1 行），空行不计入。
     */
    public static SheetData parseSheet(InputStream in) throws Exception {
        List<RowEntry> entries = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return new SheetData(new ArrayList<String>(), entries);
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
                entries.add(new RowEntry(row.getRowNum() + 1, data));
            }
            if (headers == null) {
                headers = new ArrayList<>();
            }
            return new SheetData(headers, entries);
        }
    }

    /** 工作表数据：表头列表 + 数据行条目（数据行按 Excel 行号升序） */
    public static final class SheetData {

        private final List<String> headers;
        private final List<RowEntry> rows;

        public SheetData(List<String> headers, List<RowEntry> rows) {
            this.headers = headers;
            this.rows = rows;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public List<RowEntry> getRows() {
            return rows;
        }
    }

    /** 数据行条目：Excel 实际行号（1 基，表头为第 1 行）+ 行数据（表头 → 单元格字符串） */
    public static final class RowEntry {

        private final int rowNum;
        private final Map<String, String> data;

        public RowEntry(int rowNum, Map<String, String> data) {
            this.rowNum = rowNum;
            this.data = data;
        }

        public int getRowNum() {
            return rowNum;
        }

        public Map<String, String> getData() {
            return data;
        }
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
