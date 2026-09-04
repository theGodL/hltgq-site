package com.qgyun.hltgq.hltgqsite.model.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.entity.LossDetail;
import com.qgyun.hltgq.hltgqsite.entity.LossRecord;
import com.qgyun.hltgq.hltgqsite.entity.LongPredictRecord;
import com.qgyun.hltgq.hltgqsite.mapper.LossDetailMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LossRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LongPredictRecordMapper;
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
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOfAny;

/**
 * 水量损失预测服务（中长期，/loss mode=long）。
 * <p>2026-09 会议定稿：短期损失（mode=short）已整体剔除，仅保留中长期来水对应的旬蒸发损失。
 * <p>参数从所选中长期方案 request_json 提取复现 → 调 /loss(mode=long) →
 * 主表 summary 兼容映射 + 明细（Date/Evaporation_万方/Predicted_W）→ completed。
 */
@Service
public class LossService {

    private static final Logger log = LoggerFactory.getLogger(LossService.class);

    private final LossRecordMapper recordMapper;
    private final LossDetailMapper detailMapper;
    private final LongPredictRecordMapper longRecordMapper;
    private final ModelClient modelClient;
    private final ModelTaskExecutor taskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final String corpCode;
    private final String createdBy;

    public LossService(LossRecordMapper recordMapper,
                       LossDetailMapper detailMapper,
                       LongPredictRecordMapper longRecordMapper,
                       ModelClient modelClient,
                       ModelTaskExecutor taskExecutor,
                       TransactionTemplate transactionTemplate,
                       ObjectMapper objectMapper,
                       @Value("${hltgq.corp-code}") String corpCode,
                       @Value("${hltgq.created-by}") String createdBy) {
        this.recordMapper = recordMapper;
        this.detailMapper = detailMapper;
        this.longRecordMapper = longRecordMapper;
        this.modelClient = modelClient;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.corpCode = corpCode;
        this.createdBy = createdBy;
    }

    /** 中长期模式白名单（与 /loss mode=long 契约一致） */
    private static final String[] LONG_PARAM_KEYS = {
            "scenario", "use_historical", "historical_year", "retrain"
    };

    /**
     * 提交水量损失预测（秒回 recordId），异步执行模型计算。
     * <p>仅支持 mode=long（短期损失已按会议口径剔除）。
     */
    public String submit(LossSubmitRequest req) {
        if (req == null || req.getMode() == null || req.getMode().trim().isEmpty()) {
            throw new IllegalArgumentException("模式不能为空（long）");
        }
        String mode = req.getMode().trim();
        if (!"long".equals(mode)) {
            throw new IllegalArgumentException("水量损失预测仅支持中长期模式（short 已下线）");
        }
        if (req.getSourceRecordId() == null || req.getSourceRecordId().trim().isEmpty()) {
            throw new IllegalArgumentException("参数来源方案ID不能为空");
        }

        // 从所选中长期方案 request_json 提取参数复现（白名单过滤）
        SourceScheme source = loadSource(req.getSourceRecordId().trim());
        Map<String, Object> params = parseParams(source.requestJson);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        for (String key : LONG_PARAM_KEYS) {
            Object value = params.get(key);
            if (value != null) {
                body.put(key, value);
            }
        }

        // 写主表（参数留存 + 请求归档，calculating）
        LossRecord record = new LossRecord();
        record.setSchemeName(buildSchemeName(req, source.schemeName));
        record.setStatus(ModelRecordCommonService.STATUS_CALCULATING);
        record.setDelFlag(BoolTextUtils.FALSE);
        record.setMode(mode);
        record.setScenario(asText(body.get("scenario")));
        record.setUseHistorical(BoolTextUtils.boolToText(Boolean.TRUE.equals(body.get("use_historical"))));
        Double historicalYear = asDouble(body.get("historical_year"));
        record.setHistoricalYear(historicalYear);
        record.setRetrain(BoolTextUtils.boolToText(Boolean.TRUE.equals(body.get("retrain"))));
        record.setCorpCode(corpCode);
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy(createdBy);
        record.setUpdatedAt(record.getCreatedAt());
        record.setUpdatedBy(createdBy);
        try {
            record.setRequestJson(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalArgumentException("请求体归档失败: " + e.getMessage());
        }
        recordMapper.insert(record);

        String recordId = record.getId();
        Integer year = historicalYear == null ? LocalDate.now().getYear() : historicalYear.intValue();
        taskExecutor.submit(() -> execute(recordId, body, year));
        log.info("水量损失预测任务已提交：recordId={}, mode=long, source={}",
                recordId, req.getSourceRecordId());
        return recordId;
    }

    private SourceScheme loadSource(String sourceRecordId) {
        QueryWrapper<LongPredictRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("\"id\"", sourceRecordId).eq("\"del_flag\"", BoolTextUtils.FALSE);
        LongPredictRecord record = longRecordMapper.selectOne(wrapper);
        if (record == null) {
            throw new IllegalArgumentException("中长期来水方案不存在或已删除：" + sourceRecordId);
        }
        return new SourceScheme(record.getRequestJson(), record.getSchemeName());
    }

    private Map<String, Object> parseParams(String requestJson) {
        if (requestJson == null || requestJson.trim().isEmpty()) {
            throw new IllegalArgumentException("所选方案缺少请求归档(request_json)，无法复现参数");
        }
        try {
            JsonNode node = objectMapper.readTree(requestJson);
            Map<String, Object> params = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> params.put(entry.getKey(), toPlain(entry.getValue())));
            return params;
        } catch (Exception e) {
            throw new IllegalArgumentException("所选方案请求归档解析失败: " + e.getMessage());
        }
    }

    /** JsonNode → 普通 Java 对象（Map/List/标量/null） */
    private Object toPlain(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            java.util.List<Object> list = new java.util.ArrayList<>();
            node.forEach(child -> list.add(toPlain(child)));
            return list;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> map.put(entry.getKey(), toPlain(entry.getValue())));
        return map;
    }

    private String buildSchemeName(LossSubmitRequest req, String sourceName) {
        if (req.getSchemeName() != null && !req.getSchemeName().trim().isEmpty()) {
            return req.getSchemeName().trim();
        }
        return "损失预测_中长期"
                + (sourceName == null || sourceName.trim().isEmpty() ? "" : "_" + sourceName.trim());
    }

    /**
     * 异步任务：调模型 → 事务内写明细+回写 summary → 更新状态。
     * <p>事务边界：模型调用在事务外，明细写入 + 汇总回写包在事务内，失败整体回滚。
     */
    private void execute(String recordId, Map<String, Object> body, Integer year) {
        try {
            JsonNode response = modelClient.postJson(ModelClient.PATH_LOSS, body);

            // 1. summary 兼容映射（中长期字段名）
            JsonNode summary = response.path("summary");
            LossRecord record = new LossRecord();
            record.setId(recordId);
            record.setTotalLoss(doubleOfAny(summary, "总蒸发量_万方", "总蒸发损失_万方"));
            record.setTotalInflow(doubleOfAny(summary, "总预测来水量_万方", "总入库水量_万方"));

            // 2. 事务内：写明细（Date/Evaporation_万方/Predicted_W）+ 回写主表 completed
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    LocalDateTime now = LocalDateTime.now();
                    for (JsonNode item : response.path("data")) {
                        LossDetail detail = new LossDetail();
                        detail.setRecordId(recordId);
                        // 日期或旬标签+年份 → 旬首日期
                        detail.setDataDate(resolveDataDate(textOfAny(item, "日期", "Date"), year));
                        detail.setPredictVolume(doubleOfAny(item, "Predicted_W", "预测水量_万方"));
                        detail.setLossVolume(doubleOfAny(item, "Evaporation_万方", "损失水量_万方", "蒸发损失_万方"));
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

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 参数来源方案（request_json + 方案名） */
    private static class SourceScheme {
        final String requestJson;
        final String schemeName;

        SourceScheme(String requestJson, String schemeName) {
            this.requestJson = requestJson;
            this.schemeName = schemeName;
        }
    }
}
