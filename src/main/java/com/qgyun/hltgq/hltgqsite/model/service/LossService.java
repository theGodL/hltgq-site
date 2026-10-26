package com.qgyun.hltgq.hltgqsite.model.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.entity.LossDetail;
import com.qgyun.hltgq.hltgqsite.entity.LossRecord;
import com.qgyun.hltgq.hltgqsite.mapper.LossDetailMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LossRecordMapper;
import com.qgyun.hltgq.hltgqsite.model.client.ModelClient;
import com.qgyun.hltgq.hltgqsite.model.task.ModelTaskExecutor;
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
import com.qgyun.hltgq.hltgqsite.model.util.TenDayDateUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.LossSubmitRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOfAny;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.parseDateTime;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.round2;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOfAny;

/**
 * 水量损失预测服务（中长期，/loss mode=long）。
 * <p>2026-10-26 会议定稿：参数来源方案链路（sourceRecordId）删除，不再从历史方案复现参数；
 * 入参 年渗漏量（annualLeakageWanM3，默认 156）+ 情景（scenario，丰/平/枯/推荐值，可选），
 * 调 /loss(mode=long, annual_leakage_wan_m3, scenario)。
 * <p>响应 V3 口径：summary 总蒸发量/总渗漏损失分离（total_loss=两者之和），
 * 明细 data[] 蒸发/渗漏分列（evaporation_volume/leakage_volume），predict_volume 恒 null。
 * <p>事务边界：模型调用（/loss）在事务外，明细写入 + 汇总回写包在事务内，失败整体回滚。
 */
@Service
public class LossService {

    private static final Logger log = LoggerFactory.getLogger(LossService.class);

    /** 年渗漏量默认值(万方)：模型契约 annual_leakage_wan_m3 默认 156 */
    private static final double DEFAULT_ANNUAL_LEAKAGE = 156.0;

    private final LossRecordMapper recordMapper;
    private final LossDetailMapper detailMapper;
    private final ModelClient modelClient;
    private final ModelTaskExecutor taskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final String corpCode;
    private final String createdBy;

    public LossService(LossRecordMapper recordMapper,
                       LossDetailMapper detailMapper,
                       ModelClient modelClient,
                       ModelTaskExecutor taskExecutor,
                       TransactionTemplate transactionTemplate,
                       ObjectMapper objectMapper,
                       @Value("${hltgq.corp-code}") String corpCode,
                       @Value("${hltgq.created-by}") String createdBy) {
        this.recordMapper = recordMapper;
        this.detailMapper = detailMapper;
        this.modelClient = modelClient;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.corpCode = corpCode;
        this.createdBy = createdBy;
    }

    /**
     * 提交水量损失预测（秒回 recordId），异步执行模型计算。
     * <p>仅支持 mode=long（短期损失已按会议口径剔除）。
     */
    public String submit(LossSubmitRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String mode = req.getMode() == null || req.getMode().trim().isEmpty()
                ? "long" : req.getMode().trim();
        if (!"long".equals(mode)) {
            throw new IllegalArgumentException("水量损失预测仅支持中长期模式（short 已下线）");
        }
        String scenario = normalizeScenario(req.getScenario());
        double annualLeakage = req.getAnnualLeakageWanM3() == null
                ? DEFAULT_ANNUAL_LEAKAGE : req.getAnnualLeakageWanM3();
        if (annualLeakage < 0) {
            throw new IllegalArgumentException("年渗漏量不能为负数，当前值：" + annualLeakage);
        }

        // 组装模型请求体（V3 契约：mode / annual_leakage_wan_m3 / scenario）
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        body.put("annual_leakage_wan_m3", annualLeakage);
        if (scenario != null) {
            body.put("scenario", scenario);
        }

        // 写主表（参数留存 + 请求归档，calculating）
        LossRecord record = new LossRecord();
        record.setSchemeName(buildSchemeName(req));
        record.setStatus(ModelRecordCommonService.STATUS_CALCULATING);
        record.setDelFlag(BoolTextUtils.FALSE);
        record.setMode(mode);
        record.setScenario(scenario);
        record.setAnnualLeakageWanM3(annualLeakage);
        record.setCorpCode(corpCode);
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy(ModelRecordCommonService.operatorId(createdBy));
        record.setUpdatedAt(record.getCreatedAt());
        record.setUpdatedBy(createdBy);
        try {
            record.setRequestJson(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalArgumentException("请求体归档失败: " + e.getMessage());
        }
        recordMapper.insert(record);

        String recordId = record.getId();
        taskExecutor.submit(() -> execute(recordId, body));
        log.info("水量损失预测任务已提交：recordId={}, mode=long, scenario={}, annualLeakage={}",
                recordId, scenario, annualLeakage);
        return recordId;
    }

    /** 情景白名单：丰/平/枯/推荐值；null/空 → null（模型默认），其他值拒绝 */
    private String normalizeScenario(String scenario) {
        if (scenario == null || scenario.trim().isEmpty()) {
            return null;
        }
        String value = scenario.trim();
        if (!"丰".equals(value) && !"平".equals(value) && !"枯".equals(value) && !"推荐值".equals(value)) {
            throw new IllegalArgumentException("情景必须是 丰 / 平 / 枯 / 推荐值 之一，当前值：" + value);
        }
        return value;
    }

    private String buildSchemeName(LossSubmitRequest req) {
        if (req.getSchemeName() != null && !req.getSchemeName().trim().isEmpty()) {
            return req.getSchemeName().trim();
        }
        return "损失预测_自动";
    }

    /**
     * 异步任务：调模型 → 事务内写明细（蒸发/渗漏分列）+ 回写 summary → 更新状态。
     * <p>事务边界：模型调用在事务外，明细写入 + 汇总回写包在事务内，失败整体回滚。
     */
    private void execute(String recordId, Map<String, Object> body) {
        try {
            JsonNode response = modelClient.postJson(ModelClient.PATH_LOSS, body);

            // 1. summary V3 口径：蒸发/渗漏分离
            JsonNode summary = response.path("summary");
            LossRecord record = new LossRecord();
            record.setId(recordId);
            Double totalEvaporation = doubleOfAny(summary, "总蒸发量_万方", "总蒸发损失_万方");
            Double totalLeakage = doubleOfAny(summary, "总渗漏损失_万方", "总渗漏量_万方");
            record.setTotalEvaporation(totalEvaporation);
            record.setTotalLeakage(totalLeakage);
            record.setTotalLoss(round2(sumOrNull(totalEvaporation, totalLeakage)));
            record.setTotalInflow(doubleOfAny(summary, "总预测来水量_万方", "总入库水量_万方"));

            // 2. 事务内：写明细（data[] 蒸发/渗漏分列）+ 回写主表 completed
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    LocalDateTime now = LocalDateTime.now();
                    int year = LocalDate.now().getYear();
                    for (JsonNode item : response.path("data")) {
                        LossDetail detail = new LossDetail();
                        detail.setRecordId(recordId);
                        // 日期或旬标签+年份 → 旬首日期
                        detail.setDataDate(resolveDataDate(textOfAny(item, "日期", "Date"), year));
                        Double evaporation = doubleOfAny(item, "蒸发损失_万方", "蒸发量_万方");
                        Double leakage = doubleOfAny(item, "渗漏损失_万方", "渗漏量_万方");
                        detail.setEvaporationVolume(evaporation);
                        detail.setLeakageVolume(leakage);
                        detail.setLossVolume(round2(sumOrNull(evaporation, leakage)));
                        detail.setCorpCode(corpCode);
                        detail.setCreatedAt(now);
                        detail.setCreatedBy(createdBy);
                        detail.setUpdatedAt(now);
                        detail.setUpdatedBy(createdBy);
                        detailMapper.insert(detail);
                    }
                    record.setStatus(ModelRecordCommonService.STATUS_COMPLETED);
                    record.setUpdatedAt(LocalDateTime.now());
                    recordMapper.updateById(record);
                }
            });
            log.info("水量损失预测完成：recordId={}, mode=long", recordId);
        } catch (Exception e) {
            markFailed(recordId, e);
        }
    }

    /** 两数相加；任一为 null 返回另一个，均 null 返回 null */
    private static Double sumOrNull(Double a, Double b) {
        if (a == null && b == null) {
            return null;
        }
        return (a == null ? 0 : a) + (b == null ? 0 : b);
    }

    /** 具体日期直接解析；日期或旬标签 + 年份 → 旬首日期 */
    private LocalDateTime resolveDataDate(String dateText, Integer year) {
        if (dateText == null) {
            return null;
        }
        LocalDateTime dateTime = parseDateTime(dateText);
        if (dateTime != null) {
            return dateTime;
        }
        if (year != null) {
            LocalDate firstDate = TenDayDateUtils.toFirstDate(dateText, year);
            return firstDate == null ? null : firstDate.atStartOfDay();
        }
        return null;
    }

    private void markFailed(String recordId, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String errorMsg = msg.length() > 500 ? msg.substring(0, 500) : msg;
        LossRecord record = new LossRecord();
        record.setId(recordId);
        record.setStatus(ModelRecordCommonService.STATUS_FAILED);
        record.setErrorMsg(errorMsg);
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.error("水量损失预测失败：recordId={}, error={}", recordId, errorMsg);
    }
}
