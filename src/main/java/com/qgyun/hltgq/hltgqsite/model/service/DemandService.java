package com.qgyun.hltgq.hltgqsite.model.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.entity.BaseWaterEntity;
import com.qgyun.hltgq.hltgqsite.entity.DemandAreaSummary;
import com.qgyun.hltgq.hltgqsite.entity.DemandBranchDetail;
import com.qgyun.hltgq.hltgqsite.entity.DemandRecord;
import com.qgyun.hltgq.hltgqsite.mapper.DemandAreaSummaryMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DemandBranchDetailMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DemandRecordMapper;
import com.qgyun.hltgq.hltgqsite.model.client.ModelClient;
import com.qgyun.hltgq.hltgqsite.model.task.ModelTaskExecutor;
import com.qgyun.hltgq.hltgqsite.model.util.AuditUtils;
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
import com.qgyun.hltgq.hltgqsite.model.util.TenDayMapUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.DemandSubmitRequest;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOf;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.round2;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOf;

/**
 * 需水预测服务。
 * <p>写参数(calculating) → 调 /demand(guarantee_rate/canal_eff/target_total，表格直传 demand_table) →
 * 支渠明细纵向展开 18 旬 + 片区/分灌区汇总(summary_type) + 行政区透视（模型响应无行政区汇总表，
 * 后端按支渠明细 county×旬 聚合）→
 * irrigated_area 按支渠去重求和、peak_tenday 分组 argmax 回写 → completed。
 */
@Service
public class DemandService {

    private static final Logger log = LoggerFactory.getLogger(DemandService.class);

    private final DemandRecordMapper recordMapper;
    private final DemandBranchDetailMapper branchDetailMapper;
    private final DemandAreaSummaryMapper areaSummaryMapper;
    private final ModelClient modelClient;
    private final ModelTaskExecutor taskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final String corpCode;
    private final String createdBy;

    public DemandService(DemandRecordMapper recordMapper,
                         DemandBranchDetailMapper branchDetailMapper,
                         DemandAreaSummaryMapper areaSummaryMapper,
                         ModelClient modelClient,
                         ModelTaskExecutor taskExecutor,
                         TransactionTemplate transactionTemplate,
                         ObjectMapper objectMapper,
                         @Value("${hltgq.corp-code}") String corpCode,
                         @Value("${hltgq.created-by}") String createdBy) {
        this.recordMapper = recordMapper;
        this.branchDetailMapper = branchDetailMapper;
        this.areaSummaryMapper = areaSummaryMapper;
        this.modelClient = modelClient;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.corpCode = corpCode;
        this.createdBy = createdBy;
    }

    /**
     * 提交需水预测（秒回 recordId），异步执行模型计算。
     */
    public String submit(DemandSubmitRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String guaranteeRate = normalizeGuaranteeRate(req.getGuaranteeRate());
        double canalEff = req.getCanalEff() == null ? 0.589 : req.getCanalEff();
        if (canalEff < 0 || canalEff > 1) {
            throw new IllegalArgumentException("渠系水利用系数必须在 0~1 之间");
        }
        Double targetTotal = req.getTargetTotal();
        if (targetTotal != null && targetTotal <= 0) {
            throw new IllegalArgumentException("目标总毛需水量必须大于 0");
        }

        // 组装模型请求体（tableRows 直传模型 demand_table；未传则模型沿用服务器默认需水预测表）
        Map<String, Object> modelBody = new LinkedHashMap<>();
        modelBody.put("guarantee_rate", guaranteeRate);
        modelBody.put("canal_eff", canalEff);
        modelBody.put("target_total", targetTotal);
        if (req.getTableRows() != null && !req.getTableRows().isEmpty()) {
            modelBody.put("demand_table", req.getTableRows());
        }
        Map<String, Object> archiveBody = new LinkedHashMap<>(modelBody);

        // 写主表（参数留存 + 请求归档，calculating）
        DemandRecord record = new DemandRecord();
        record.setSchemeName(buildSchemeName(req, guaranteeRate));
        record.setStatus(ModelRecordCommonService.STATUS_CALCULATING);
        record.setDelFlag(BoolTextUtils.FALSE);
        record.setGuaranteeRate(guaranteeRate);
        record.setCanalEff(canalEff);
        record.setTargetTotal(targetTotal);
        record.setCorpCode(corpCode);
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy(ModelRecordCommonService.operatorId(createdBy));
        record.setUpdatedAt(record.getCreatedAt());
        record.setUpdatedBy(createdBy);
        try {
            record.setRequestJson(objectMapper.writeValueAsString(archiveBody));
        } catch (Exception e) {
            throw new IllegalArgumentException("请求体归档失败: " + e.getMessage());
        }
        recordMapper.insert(record);

        String recordId = record.getId();
        taskExecutor.submit(() -> execute(recordId, modelBody));
        log.info("需水预测任务已提交：recordId={}, guaranteeRate={}", recordId, guaranteeRate);
        return recordId;
    }

    /** 保证率规范化：数字转字符串，空默认 "90"，校验取值 */
    private String normalizeGuaranteeRate(String value) {
        String rate = value == null || value.trim().isEmpty() ? "90" : value.trim();
        if (!"50".equals(rate) && !"75".equals(rate) && !"90".equals(rate) && !"多年平均".equals(rate)) {
            throw new IllegalArgumentException("保证率必须是 50 / 75 / 90 / 多年平均 之一");
        }
        return rate;
    }

    private String buildSchemeName(DemandSubmitRequest req, String guaranteeRate) {
        if (req.getSchemeName() != null && !req.getSchemeName().trim().isEmpty()) {
            return req.getSchemeName().trim();
        }
        return "需水预测_" + guaranteeRate;
    }

    /**
     * 异步任务：调模型 → 事务内写支渠明细/片区汇总/分灌区汇总+回写汇总 → 更新状态。
     * <p>事务边界：模型调用在事务外，明细写入 + 汇总回写包在事务内，失败整体回滚。
     */
    private void execute(String recordId, Map<String, Object> modelBody) {
        try {
            JsonNode response = modelClient.postJson(ModelClient.PATH_DEMAND, modelBody);

            // 模型响应支渠明细无「行政区」列（手册 4.4：仅 片区/分灌区/支渠/灌区面积/种植作物种类 及逐旬需水），
            // 而行政区是入参 demand_table 必填列 → 按支渠名从入参匹配，作为 county 主数据源；
            // 响应若含「行政区」键则优先直取（兼容模型后续升级）
            Map<String, String> countyByBranch = new HashMap<>();
            Object demandTable = modelBody.get("demand_table");
            if (demandTable instanceof List) {
                for (Object rowObj : (List<?>) demandTable) {
                    if (!(rowObj instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> row = (Map<?, ?>) rowObj;
                    Object branch = row.get("支渠");
                    Object countyValue = row.get("行政区");
                    if (branch != null && countyValue != null) {
                        countyByBranch.put(String.valueOf(branch), String.valueOf(countyValue));
                    }
                }
            }

            // 1. 回写 summary 接口直出字段
            JsonNode summary = response.path("summary");
            DemandRecord record = new DemandRecord();
            record.setId(recordId);
            record.setTotalDemand(doubleOf(summary, "总毛需水量_万方"));
            record.setBranchCount(doubleOf(summary, "支渠数量"));

            // 2. 事务内：支渠明细纵向展开 18 旬 + 片区/分灌区汇总 + 预计算字段回写
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    LocalDateTime now = LocalDateTime.now();

                    // 2.1 支渠明细纵向展开 18 旬（每支渠 1 条 → 18 条），同时聚行政区×旬透视
                    Map<String, Double> tendayTotal = new HashMap<>();
                    Map<String, Map<String, Double>> countyTenday = new HashMap<>();
                    boolean countyKeyChecked = false;
                    for (JsonNode item : response.path("支渠明细")) {
                        if (!countyKeyChecked) {
                            countyKeyChecked = true;
                            if (!item.has("行政区")) {
                                log.info("需水支渠明细无「行政区」列（手册 4.4 契约），county 改按入参 tableRows 匹配，命中{}条",
                                        countyByBranch.size());
                            }
                        }
                        String branchName = textOf(item, "支渠");
                        String district = textOf(item, "片区");
                        String subDistrict = textOf(item, "分灌区");
                        String county = textOf(item, "行政区");
                        if (county == null) {
                            county = countyByBranch.get(branchName);
                        }
                        String cropTypes = textOf(item, "种植作物种类");
                        Double area = doubleOf(item, "灌区面积");
                        for (String label : TenDayMapUtils.TEN_DAY_MAP.keySet()) {
                            DemandBranchDetail detail = new DemandBranchDetail();
                            detail.setRecordId(recordId);
                            detail.setDistrict(district);
                            detail.setSubDistrict(subDistrict);
                            detail.setCounty(county);
                            detail.setBranchName(branchName);
                            detail.setArea(area);
                            detail.setCropTypes(cropTypes);
                            detail.setTendayLabel(label);
                            detail.setDemandVolume(doubleOf(item, label));
                            Integer order = TenDayMapUtils.sortOrderOf(label);
                            detail.setSortOrder(order == null ? null : order.doubleValue());
                            fillBase(detail, now);
                            branchDetailMapper.insert(detail);
                            if (detail.getDemandVolume() != null) {
                                tendayTotal.merge(label, detail.getDemandVolume(), Double::sum);
                                if (county != null) {
                                    countyTenday.computeIfAbsent(county, k -> new HashMap<>())
                                            .merge(label, detail.getDemandVolume(), Double::sum);
                                }
                            }
                        }
                    }

                    // 2.2 片区汇总 / 分灌区汇总 / 行政区透视 → area_summary（summary_type 区分）
                    writeAreaSummary(recordId, response.path("片区汇总"), "片区", "片区", now);
                    writeAreaSummary(recordId, response.path("分灌区汇总"), "分灌区", "分灌区", now);
                    writeCountySummary(recordId, countyTenday, now);

                    // 2.3 回写预计算字段：irrigated_area 固定 105.83（全灌区面积，模型方 2026-10-28 定稿），peak_tenday（argmax）
                    record.setIrrigatedArea(105.83);
                    record.setPeakTenday(argmax(tendayTotal));
                    record.setStatus(ModelRecordCommonService.STATUS_COMPLETED);
                    record.setUpdatedAt(LocalDateTime.now());
                    recordMapper.updateById(record);
                }
            });
            log.info("需水预测完成：recordId={}", recordId);
        } catch (Exception e) {
            markFailed(recordId, e);
        }
    }

    /** 片区/分灌区汇总：每条区域对象按 18 旬纵向展开写入 area_summary */
    private void writeAreaSummary(String recordId, JsonNode array, String type, String nameField,
                                  LocalDateTime now) {
        for (JsonNode item : array) {
            String areaName = textOf(item, nameField);
            for (String label : TenDayMapUtils.TEN_DAY_MAP.keySet()) {
                DemandAreaSummary summary = new DemandAreaSummary();
                summary.setRecordId(recordId);
                summary.setSummaryType(type);
                summary.setAreaName(areaName);
                summary.setTendayLabel(label);
                summary.setDemandVolume(doubleOf(item, label));
                Integer order = TenDayMapUtils.sortOrderOf(label);
                summary.setSortOrder(order == null ? null : order.doubleValue());
                fillBase(summary, now);
                areaSummaryMapper.insert(summary);
            }
        }
    }

    /** 行政区汇总：模型响应无独立行政区汇总表，按支渠明细 county×旬 透视聚合写入 area_summary */
    private void writeCountySummary(String recordId, Map<String, Map<String, Double>> countyTenday,
                                    LocalDateTime now) {
        for (Map.Entry<String, Map<String, Double>> countyEntry : countyTenday.entrySet()) {
            for (String label : TenDayMapUtils.TEN_DAY_MAP.keySet()) {
                DemandAreaSummary summary = new DemandAreaSummary();
                summary.setRecordId(recordId);
                summary.setSummaryType("行政区");
                summary.setAreaName(countyEntry.getKey());
                summary.setTendayLabel(label);
                summary.setDemandVolume(countyEntry.getValue().get(label));
                Integer order = TenDayMapUtils.sortOrderOf(label);
                summary.setSortOrder(order == null ? null : order.doubleValue());
                fillBase(summary, now);
                areaSummaryMapper.insert(summary);
            }
        }
    }

    /** 按旬分组求和的 argmax：返回需水量合计最大的旬标签，无数据返回 null */
    private String argmax(Map<String, Double> tendayTotal) {
        String peak = null;
        double max = Double.MIN_VALUE;
        for (Map.Entry<String, Double> entry : tendayTotal.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                peak = entry.getKey();
            }
        }
        return peak;
    }

    /**
     * 生成需水预测逐旬明细 Excel（模型无 Excel 输出，后端按落库明细生成）：
     * Sheet「支渠明细」18 旬宽表（每支渠一行）+ Sheet「分区汇总」（片区/分灌区/行政区三类）。
     * 明细字段保留原始精度，行合计保留 2 位；无明细（failed 方案）仅输出表头。
     */
    public byte[] exportExcel(String recordId) {
        QueryWrapper<DemandBranchDetail> branchWrapper = new QueryWrapper<>();
        branchWrapper.eq("\"record_id\"", recordId).orderByAsc("\"sort_order\"").orderByAsc("\"branch_name\"");
        List<DemandBranchDetail> branchDetails = branchDetailMapper.selectList(branchWrapper);
        QueryWrapper<DemandAreaSummary> summaryWrapper = new QueryWrapper<>();
        summaryWrapper.eq("\"record_id\"", recordId).orderByAsc("\"sort_order\"").orderByAsc("\"area_name\"");
        List<DemandAreaSummary> areaSummaries = areaSummaryMapper.selectList(summaryWrapper);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<String> labels = new ArrayList<>(TenDayMapUtils.TEN_DAY_MAP.keySet());
            writeBranchSheet(workbook, branchDetails, labels);
            writeSummarySheet(workbook, areaSummaries, labels);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("需水明细 Excel 生成失败: " + e.getMessage());
        }
    }

    /** 支渠明细宽表：片区/分灌区/支渠/行政区/灌区面积(万亩)/种植作物种类 + 18 旬 + 合计(万方) */
    private void writeBranchSheet(XSSFWorkbook workbook, List<DemandBranchDetail> rows, List<String> labels) {
        Sheet sheet = workbook.createSheet("支渠明细");
        Row header = sheet.createRow(0);
        int col = 0;
        for (String h : new String[]{"片区", "分灌区", "支渠", "行政区", "灌区面积(万亩)", "种植作物种类"}) {
            header.createCell(col++).setCellValue(h);
        }
        for (String label : labels) {
            header.createCell(col++).setCellValue(label);
        }
        header.createCell(col).setCellValue("合计(万方)");
        // 按支渠分组（保持查询顺序），组内按旬填充
        Map<String, DemandBranchDetail> meta = new LinkedHashMap<>();
        Map<String, Map<String, Double>> volumes = new LinkedHashMap<>();
        for (DemandBranchDetail d : rows) {
            if (d.getBranchName() == null) {
                continue;
            }
            meta.putIfAbsent(d.getBranchName(), d);
            volumes.computeIfAbsent(d.getBranchName(), k -> new LinkedHashMap<>())
                    .put(d.getTendayLabel(), d.getDemandVolume());
        }
        int r = 1;
        for (Map.Entry<String, DemandBranchDetail> entry : meta.entrySet()) {
            DemandBranchDetail m = entry.getValue();
            Row row = sheet.createRow(r++);
            int c = 0;
            row.createCell(c++).setCellValue(m.getDistrict() == null ? "" : m.getDistrict());
            row.createCell(c++).setCellValue(m.getSubDistrict() == null ? "" : m.getSubDistrict());
            row.createCell(c++).setCellValue(entry.getKey());
            row.createCell(c++).setCellValue(m.getCounty() == null ? "" : m.getCounty());
            if (m.getArea() != null) {
                row.createCell(c).setCellValue(m.getArea());
            }
            c++;
            row.createCell(c++).setCellValue(m.getCropTypes() == null ? "" : m.getCropTypes());
            double total = 0;
            Map<String, Double> v = volumes.get(entry.getKey());
            for (String label : labels) {
                Double val = v.get(label);
                if (val != null) {
                    row.createCell(c).setCellValue(val);
                    total += val;
                }
                c++;
            }
            row.createCell(c).setCellValue(round2(total));
        }
    }

    /** 分区汇总：汇总类型/区域名称 + 18 旬 + 合计(万方)；类型顺序 片区→分灌区→行政区，同类按区域名 */
    private void writeSummarySheet(XSSFWorkbook workbook, List<DemandAreaSummary> rows, List<String> labels) {
        Sheet sheet = workbook.createSheet("分区汇总");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("汇总类型");
        header.createCell(1).setCellValue("区域名称");
        int col = 2;
        for (String label : labels) {
            header.createCell(col++).setCellValue(label);
        }
        header.createCell(col).setCellValue("合计(万方)");
        // 分组保序：类型 片区→分灌区→行政区 优先，其余按首现；同类按区域名排序
        List<String[]> pairs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, Map<String, Double>> values = new LinkedHashMap<>();
        for (DemandAreaSummary s : rows) {
            String type = s.getSummaryType() == null ? "" : s.getSummaryType();
            String name = s.getAreaName() == null ? "" : s.getAreaName();
            String key = type + "|" + name;
            if (seen.add(key)) {
                pairs.add(new String[]{type, name});
            }
            values.computeIfAbsent(key, k -> new LinkedHashMap<>())
                    .put(s.getTendayLabel(), s.getDemandVolume());
        }
        final List<String> typeOrder = Arrays.asList("片区", "分灌区", "行政区");
        pairs.sort((a, b) -> {
            int ia = typeOrder.indexOf(a[0]);
            int ib = typeOrder.indexOf(b[0]);
            if (ia < 0) {
                ia = typeOrder.size();
            }
            if (ib < 0) {
                ib = typeOrder.size();
            }
            if (ia != ib) {
                return Integer.compare(ia, ib);
            }
            return a[1].compareTo(b[1]);
        });
        int r = 1;
        for (String[] pair : pairs) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(pair[0]);
            row.createCell(1).setCellValue(pair[1]);
            Map<String, Double> v = values.get(pair[0] + "|" + pair[1]);
            int c = 2;
            double total = 0;
            for (String label : labels) {
                Double val = v.get(label);
                if (val != null) {
                    row.createCell(c).setCellValue(val);
                    total += val;
                }
                c++;
            }
            row.createCell(c).setCellValue(round2(total));
        }
    }

    /**
     * 支渠作物设置默认配置（GET /water-forecast/demand/crop-config）。
     * <p>数据源：classpath:data/tables/demand-default-table.json（模型方提供的默认需水预测表，
     * 82 行六列：片区/分灌区/支渠/行政区/灌区面积/种植作物种类），按文档 4.1 英文键返回。
     */
    public List<Map<String, Object>> cropConfig() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (java.io.InputStream in = getClass().getClassLoader()
                .getResourceAsStream("data/tables/demand-default-table.json")) {
            if (in == null) {
                throw new IllegalStateException("内置默认需水预测表缺失：data/tables/demand-default-table.json");
            }
            JsonNode root = objectMapper.readTree(in);
            if (root != null && root.isArray()) {
                for (JsonNode item : root) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("district", textOf(item, "片区"));
                    row.put("subDistrict", textOf(item, "分灌区"));
                    row.put("branchName", textOf(item, "支渠"));
                    row.put("county", textOf(item, "行政区"));
                    // 灌区面积保留三位小数，多余直接丢弃（截断非四舍五入，模型方 2026-10-28 要求）
                    row.put("area", truncate3(doubleOf(item, "灌区面积")));
                    row.put("cropType", textOf(item, "种植作物种类"));
                    rows.add(row);
                }
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("内置默认需水预测表解析失败：" + e.getMessage(), e);
        }
    }

    /** 截断到三位小数（多余直接丢弃，非四舍五入）；null 返回 null */
    private Double truncate3(Double value) {
        if (value == null) {
            return null;
        }
        return Math.floor(value * 1000.0) / 1000.0;
    }

    /** 审计五连写（委托 AuditUtils） */
    private void fillBase(BaseWaterEntity entity, LocalDateTime now) {
        AuditUtils.fillCreate(entity, now, createdBy, corpCode);
    }

    private void markFailed(String recordId, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String errorMsg = msg.length() > 500 ? msg.substring(0, 500) : msg;
        DemandRecord record = new DemandRecord();
        record.setId(recordId);
        record.setStatus(ModelRecordCommonService.STATUS_FAILED);
        record.setErrorMsg(errorMsg);
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.error("需水预测失败：recordId={}, error={}", recordId, errorMsg);
    }
}
