package com.qgyun.hltgq.hltgqsite.decision.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度静态表只读服务（/water-decision/area-table、/flow-limit-table）。
 * <p>数据源：classpath:data/tables 下两份静态表（模型优化会议提供）：
 * area-allocation.xlsx（片区面积 sheet：片区×面积；行政区面积 sheet：行政区×自流/提灌/总计），
 * branch-flow-limit.xlsx（支渠约束流量 sheet：片区/分灌区/支渠/行政区/限制流量，空限制流量返回 null；
 * 片区约束流量 sheet：片区×限制流量）。
 * <p>按表头定位 sheet（不依赖 sheet 顺序），表头列缺省时抛异常（静态表为部署资源，缺失即配置错误）。
 */
@Service
public class DecisionStaticTableService {

    /** 面积分配表（片区面积 + 行政区面积） */
    private static final String AREA_ALLOCATION = "classpath:data/tables/area-allocation.xlsx";

    /** 支渠限制流量表（支渠约束流量 + 片区约束流量） */
    private static final String FLOW_LIMIT = "classpath:data/tables/branch-flow-limit.xlsx";

    private final ResourceLoader resourceLoader;

    public DecisionStaticTableService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 面积分配：districts=片区面积（片区×面积），counties=行政区面积（行政区×自流/提灌/总计）。
     * 含「全灌区」合计行，前端自行取舍。
     */
    public Map<String, Object> areaTable() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> districts = new ArrayList<>();
        for (Map<String, String> row : readSheet(AREA_ALLOCATION, new String[]{"片区", "面积"}, null)) {
            if (blank(row.get("片区"))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("district", row.get("片区"));
            item.put("area", parseDouble(row.get("面积")));
            districts.add(item);
        }
        List<Map<String, Object>> counties = new ArrayList<>();
        for (Map<String, String> row : readSheet(AREA_ALLOCATION, new String[]{"自流", "提灌"}, null)) {
            if (blank(row.get("片区"))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("county", row.get("片区"));
            item.put("selfFlow", parseDouble(row.get("自流")));
            item.put("lift", parseDouble(row.get("提灌")));
            item.put("total", parseDouble(row.get("总计")));
            counties.add(item);
        }
        result.put("districts", districts);
        result.put("counties", counties);
        return result;
    }

    /**
     * 支渠限制流量：branches=支渠约束流量（片区/分灌区/支渠/行政区/限制流量，空限制流量→null），
     * districts=片区约束流量（片区×限制流量）。
     */
    public Map<String, Object> flowLimitTable() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> branches = new ArrayList<>();
        for (Map<String, String> row : readSheet(FLOW_LIMIT,
                new String[]{"片区", "分灌区", "支渠", "行政区", "限制流量"}, null)) {
            if (blank(row.get("支渠"))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("district", row.get("片区"));
            item.put("subDistrict", row.get("分灌区"));
            item.put("branch", row.get("支渠"));
            item.put("county", row.get("行政区"));
            item.put("flowLimit", parseDouble(row.get("限制流量")));
            branches.add(item);
        }
        List<Map<String, Object>> districts = new ArrayList<>();
        for (Map<String, String> row : readSheet(FLOW_LIMIT,
                new String[]{"片区", "限制流量"}, new String[]{"分灌区"})) {
            if (blank(row.get("片区"))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("district", row.get("片区"));
            item.put("flowLimit", parseDouble(row.get("限制流量")));
            districts.add(item);
        }
        result.put("branches", branches);
        result.put("districts", districts);
        return result;
    }

    /** 按表头定位 sheet（必需列全含、排除列不含）→ 数据行（表头→显示值，跳过全空行） */
    private List<Map<String, String>> readSheet(String location, String[] requiredHeaders,
                                                String[] forbiddenHeaders) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("调度静态表缺失：" + location);
        }
        try (InputStream in = resource.getInputStream();
             Workbook workbook = new XSSFWorkbook(in)) {
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : workbook) {
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    continue;
                }
                List<String> headers = new ArrayList<>();
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    headers.add(formatter.formatCellValue(headerRow.getCell(i)).trim());
                }
                if (!headers.containsAll(Arrays.asList(requiredHeaders))) {
                    continue;
                }
                if (forbiddenHeaders != null) {
                    boolean hit = false;
                    for (String forbidden : forbiddenHeaders) {
                        if (headers.contains(forbidden)) {
                            hit = true;
                            break;
                        }
                    }
                    if (hit) {
                        continue;
                    }
                }
                List<Map<String, String>> rows = new ArrayList<>();
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) {
                        continue;
                    }
                    Map<String, String> data = new LinkedHashMap<>();
                    boolean allBlank = true;
                    for (int i = 0; i < headers.size(); i++) {
                        String value = formatter.formatCellValue(row.getCell(i)).trim();
                        data.put(headers.get(i), value);
                        if (!value.isEmpty()) {
                            allBlank = false;
                        }
                    }
                    if (allBlank) {
                        continue;
                    }
                    rows.add(data);
                }
                return rows;
            }
        } catch (Exception e) {
            throw new IllegalStateException("调度静态表解析失败：" + location + "，原因：" + e.getMessage(), e);
        }
        throw new IllegalStateException("调度静态表缺少必需列 " + Arrays.toString(requiredHeaders) + "：" + location);
    }

    /** 显示值转数值；空/非数值返回 null */
    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
