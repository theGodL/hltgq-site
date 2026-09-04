package com.qgyun.hltgq.hltgqsite.model.service;

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
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
import com.qgyun.hltgq.hltgqsite.model.util.TenDayMapUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.DemandSubmitRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOf;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.round2;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOf;

/**
 * 需水预测服务。
 * <p>写参数(calculating) → 调 /demand(guarantee_rate/canal_eff/target_total，表格直传 demand_table) →
 * 支渠明细纵向展开 18 旬 + 片区/分灌区汇总(summary_type) →
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
        record.setCreatedBy(createdBy);
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

                    // 2.1 支渠明细纵向展开 18 旬（每支渠 1 条 → 18 条）
                    Map<String, Double> tendayTotal = new HashMap<>();
                    Map<String, Double> branchArea = new HashMap<>();
                    for (JsonNode item : response.path("支渠明细")) {
                        String branchName = textOf(item, "支渠");
                        String district = textOf(item, "片区");
                        String subDistrict = textOf(item, "分灌区");
                        String cropTypes = textOf(item, "种植作物种类");
                        Double area = doubleOf(item, "灌区面积");
                        if (branchName != null && area != null) {
                            branchArea.putIfAbsent(branchName, area);
                        }
                        for (String label : TenDayMapUtils.TEN_DAY_MAP.keySet()) {
                            DemandBranchDetail detail = new DemandBranchDetail();
                            detail.setRecordId(recordId);
                            detail.setDistrict(district);
                            detail.setSubDistrict(subDistrict);
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
                            }
                        }
                    }

                    // 2.2 片区汇总 / 分灌区汇总 → area_summary（summary_type 区分）
                    writeAreaSummary(recordId, response.path("片区汇总"), "片区", "片区", now);
                    writeAreaSummary(recordId, response.path("分灌区汇总"), "分灌区", "分灌区", now);

                    // 2.3 回写预计算字段：irrigated_area（支渠去重求和）、peak_tenday（argmax）
                    double irrigatedArea = 0;
                    for (Double area : branchArea.values()) {
                        irrigatedArea += area;
                    }
                    record.setIrrigatedArea(round2(irrigatedArea));
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

    private void fillBase(BaseWaterEntity entity, LocalDateTime now) {
        entity.setCorpCode(corpCode);
        entity.setCreatedAt(now);
        entity.setCreatedBy(createdBy);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(createdBy);
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
