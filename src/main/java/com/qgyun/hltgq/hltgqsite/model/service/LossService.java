package com.qgyun.hltgq.hltgqsite.model.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.entity.LossDetail;
import com.qgyun.hltgq.hltgqsite.entity.LossRecord;
import com.qgyun.hltgq.hltgqsite.entity.LongPredictRecord;
import com.qgyun.hltgq.hltgqsite.entity.ShortForecastRecord;
import com.qgyun.hltgq.hltgqsite.mapper.LossDetailMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LossRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LongPredictRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.ShortForecastRecordMapper;
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

import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOf;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOfAny;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.parseDate;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.parseDateTime;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOf;

/**
 * 水量损失预测服务。
 * <p>参数从所选历史方案 request_json 提取复现 → 调 /loss(mode=short/long) →
 * 主表 summary 兼容映射 + 明细（损失字段名兼容 损失水量_万方/蒸发损失_万方）→ completed。
 */
@Service
public class LossService {

    private static final Logger log = LoggerFactory.getLogger(LossService.class);

    private final LossRecordMapper recordMapper;
    private final LossDetailMapper detailMapper;
    private final ShortForecastRecordMapper shortRecordMapper;
    private final LongPredictRecordMapper longRecordMapper;
    private final ModelClient modelClient;
    private final ModelTaskExecutor taskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final String corpCode;
    private final String createdBy;

    public LossService(LossRecordMapper recordMapper,
                       LossDetailMapper detailMapper,
                       ShortForecastRecordMapper shortRecordMapper,
                       LongPredictRecordMapper longRecordMapper,
                       ModelClient modelClient,
                       ModelTaskExecutor taskExecutor,
                       TransactionTemplate transactionTemplate,
                       ObjectMapper objectMapper,
                       @Value("${hltgq.corp-code}") String corpCode,
                       @Value("${hltgq.created-by}") String createdBy) {
        this.recordMapper = recordMapper;
        this.detailMapper = detailMapper;
        this.shortRecordMapper = shortRecordMapper;
        this.longRecordMapper = longRecordMapper;
        this.modelClient = modelClient;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.corpCode = corpCode;
        this.createdBy = createdBy;
    }

    /** 短期模式白名单（与 /loss mode=short 契约一致，过滤 /forecast 的 discharge_mode 等多余参数） */
    private static final String[] SHORT_PARAM_KEYS = {
            "start_date", "days", "rainfall", "use_typical", "flood_idx",
            "adjust_rainfall", "initial_water_level", "target_total"
    };

    /** 中长期模式白名单（与 /loss mode=long 契约一致） */
    private static final String[] LONG_PARAM_KEYS = {
            "scenario", "use_historical", "historical_year", "retrain"
    };

    /**
     * 提交水量损失预测（秒回 recordId），异步执行模型计算。
     */
    public String submit(LossSubmitRequest req) {
        if (req == null || req.getMode() == null || req.getMode().trim().isEmpty()) {
            throw new IllegalArgumentException("模式不能为空（short / long）");
        }
        String mode = req.getMode().trim();
        if (!"short".equals(mode) && !"long".equals(mode)) {
            throw new IllegalArgumentException("模式必须是 short / long 之一");
        }
        if (req.getSourceRecordId() == null || req.getSourceRecordId().trim().isEmpty()) {
            throw new IllegalArgumentException("参数来源方案ID不能为空");
        }

        // 从所选历史方案 request_json 提取参数复现（白名单过滤）
        SourceScheme source = loadSource(mode, req.getSourceRecordId().trim());
        Map<String, Object> params = parseParams(source.requestJson);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        for (String key : "short".equals(mode) ? SHORT_PARAM_KEYS : LONG_PARAM_KEYS) {
            Object value = params.get(key);
            if (value != null) {
                body.put(key, value);
            }
        }

        // 写主表（参数留存 + 请求归档，calculating）
        LossRecord record = new LossRecord();
        record.setSchemeName(buildSchemeName(req, mode, source.schemeName));
        record.setStatus("calculating");
        record.setDelFlag(BoolTextUtils.FALSE);
        record.setMode(mode);
        if ("short".equals(mode)) {
            Object startDate = body.get("start_date");
            record.setStartDate(parseDateTime(startDate == null ? null : String.valueOf(startDate)));
            record.setDays(asDouble(body.get("days")));
            record.setRainfallJson(toJson(body.get("rainfall")));
        } else {
            record.setScenario(asText(body.get("scenario")));
            record.setUseHistorical(BoolTextUtils.boolToText(Boolean.TRUE.equals(body.get("use_historical"))));
            Double historicalYear = asDouble(body.get("historical_year"));
            record.setHistoricalYear(historicalYear == null ? null : historicalYear);
            record.setRetrain(BoolTextUtils.boolToText(Boolean.TRUE.equals(body.get("retrain"))));
        }
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
        Integer year = inferYear(mode, body);
        taskExecutor.submit(() -> execute(recordId, mode, body, year));
        log.info("水量损失预测任务已提交：recordId={}, mode={}, source={}",
                recordId, mode, req.getSourceRecordId());
        return recordId;
    }

    /** 短期模式取当前年份，长期模式优先 historical_year 否则当前年份（用于旬标签转日期） */
    private Integer inferYear(String mode, Map<String, Object> body) {
        if ("long".equals(mode)) {
            Double historicalYear = asDouble(body.get("historical_year"));
            if (historicalYear != null) {
                return historicalYear.intValue();
            }
        }
        return LocalDate.now().getYear();
    }

    private SourceScheme loadSource(String mode, String sourceRecordId) {
        if ("short".equals(mode)) {
            QueryWrapper<ShortForecastRecord> wrapper = new QueryWrapper<>();
            wrapper.eq("\"id\"", sourceRecordId).eq("\"del_flag\"", BoolTextUtils.FALSE);
            ShortForecastRecord record = shortRecordMapper.selectOne(wrapper);
            if (record == null) {
                throw new IllegalArgumentException("短期来水方案不存在或已删除：" + sourceRecordId);
            }
            return new SourceScheme(record.getRequestJson(), record.getSchemeName());
        }
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

    private String buildSchemeName(LossSubmitRequest req, String mode, String sourceName) {
        if (req.getSchemeName() != null && !req.getSchemeName().trim().isEmpty()) {
            return req.getSchemeName().trim();
        }
        return "损失预测_" + ("short".equals(mode) ? "短期" : "中长期")
                + (sourceName == null || sourceName.trim().isEmpty() ? "" : "_" + sourceName.trim());
    }

    /**
     * 异步任务：调模型 → 事务内写明细+回写 summary → 更新状态。
     * <p>事务边界：模型调用在事务外，明细写入 + 汇总回写包在事务内，失败整体回滚。
     */
    private void execute(String recordId, String mode, Map<String, Object> body, Integer year) {
        try {
            JsonNode response = modelClient.postJson(ModelClient.PATH_LOSS, body);

            // 1. summary 兼容映射（短期/长期字段名不同）
            JsonNode summary = response.path("summary");
            LossRecord record = new LossRecord();
            record.setId(recordId);
            record.setTotalLoss(doubleOfAny(summary, "总蒸发损失_万方", "总蒸发量_万方"));
            record.setTotalInflow(doubleOfAny(summary, "总入库水量_万方", "总预测来水量_万方"));

            // 2. 事务内：写明细（损失字段名兼容映射）+ 回写主表 completed
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    LocalDateTime now = LocalDateTime.now();
                    for (JsonNode item : response.path("data")) {
                        LossDetail detail = new LossDetail();
                        detail.setRecordId(recordId);
                        detail.setDataDate(resolveDataDate(mode, textOf(item, "日期"), year));
                        detail.setPredictVolume(doubleOf(item, "预测水量_万方"));
                        detail.setLossVolume(doubleOfAny(item, "损失水量_万方", "蒸发损失_万方"));
                        detail.setCorpCode(corpCode);
                        detail.setCreatedAt(now);
                        detail.setCreatedBy(createdBy);
                        detail.setUpdatedAt(now);
                        detail.setUpdatedBy(createdBy);
                        detailMapper.insert(detail);
                    }
                    record.setStatus("completed");
                    record.setUpdatedAt(LocalDateTime.now());
                    recordMapper.updateById(record);
                }
            });
            log.info("水量损失预测完成：recordId={}, mode={}", recordId, mode);
        } catch (Exception e) {
            markFailed(recordId, e);
        }
    }

    /** 短期=具体日期；长期=日期或旬标签+年份 → 旬首日期 */
    private LocalDateTime resolveDataDate(String mode, String dateText, Integer year) {
        if (dateText == null) {
            return null;
        }
        LocalDateTime dateTime = parseDateTime(dateText);
        if (dateTime != null) {
            return dateTime;
        }
        if ("long".equals(mode) && year != null) {
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
        record.setStatus("failed");
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

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("参数归档失败: {}", e.getMessage());
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
