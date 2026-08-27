package com.qgyun.hltgq.hltgqsite.model.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.entity.LongPredictMonthly;
import com.qgyun.hltgq.hltgqsite.entity.LongPredictRecord;
import com.qgyun.hltgq.hltgqsite.entity.LongPredictTenday;
import com.qgyun.hltgq.hltgqsite.mapper.LongPredictMonthlyMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LongPredictRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.LongPredictTendayMapper;
import com.qgyun.hltgq.hltgqsite.model.client.ModelClient;
import com.qgyun.hltgq.hltgqsite.model.task.ModelTaskExecutor;
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
import com.qgyun.hltgq.hltgqsite.model.util.TenDayDateUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.LongPredictRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOf;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOfAny;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.parseDate;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOf;

/**
 * 中长期来水预测服务。
 * <p>写参数(calculating) → 调 /predict → monthly 表(year_month→stat_date 月初)
 * → tenday 表(tenday_label+predict_date 旬首) → annual/max/min 汇总回写 → completed。
 */
@Service
public class LongPredictService {

    private static final Logger log = LoggerFactory.getLogger(LongPredictService.class);

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final LongPredictRecordMapper recordMapper;
    private final LongPredictTendayMapper tendayMapper;
    private final LongPredictMonthlyMapper monthlyMapper;
    private final ModelClient modelClient;
    private final ModelTaskExecutor taskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final String corpCode;
    private final String createdBy;

    public LongPredictService(LongPredictRecordMapper recordMapper,
                              LongPredictTendayMapper tendayMapper,
                              LongPredictMonthlyMapper monthlyMapper,
                              ModelClient modelClient,
                              ModelTaskExecutor taskExecutor,
                              TransactionTemplate transactionTemplate,
                              ObjectMapper objectMapper,
                              @Value("${hltgq.corp-code}") String corpCode,
                              @Value("${hltgq.created-by}") String createdBy) {
        this.recordMapper = recordMapper;
        this.tendayMapper = tendayMapper;
        this.monthlyMapper = monthlyMapper;
        this.modelClient = modelClient;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.corpCode = corpCode;
        this.createdBy = createdBy;
    }

    /**
     * 提交中长期来水预测（秒回 recordId），异步执行模型计算。
     */
    public String submit(LongPredictRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String scenario = req.getScenario() == null || req.getScenario().trim().isEmpty()
                ? null : req.getScenario().trim();
        if (scenario != null && !"丰".equals(scenario) && !"平".equals(scenario) && !"枯".equals(scenario)) {
            throw new IllegalArgumentException("来水情景必须是 丰 / 平 / 枯 之一");
        }
        boolean useHistorical = Boolean.TRUE.equals(req.getUseHistorical());
        boolean retrain = Boolean.TRUE.equals(req.getRetrain());

        // 组装模型请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", scenario);
        body.put("use_historical", useHistorical);
        if (useHistorical) {
            body.put("historical_year", req.getHistoricalYear() == null ? 2020 : req.getHistoricalYear());
        }
        body.put("retrain", retrain);
        if (retrain) {
            body.put("epochs", req.getEpochs() == null ? 20000 : req.getEpochs());
            body.put("hidden_neurons", req.getHiddenNeurons() == null ? 200 : req.getHiddenNeurons());
            body.put("learning_rate", req.getLearningRate() == null ? 0.005 : req.getLearningRate());
        }

        // 写主表（参数留存 + 请求归档，calculating）
        LongPredictRecord record = new LongPredictRecord();
        record.setSchemeName(buildSchemeName(req, scenario));
        record.setStatus("calculating");
        record.setDelFlag(BoolTextUtils.FALSE);
        record.setScenario(scenario);
        record.setUseHistorical(BoolTextUtils.boolToText(useHistorical));
        if (useHistorical) {
            record.setHistoricalYear((double) (req.getHistoricalYear() == null ? 2020 : req.getHistoricalYear()));
        }
        record.setRetrain(BoolTextUtils.boolToText(retrain));
        if (retrain) {
            record.setEpochs((double) (req.getEpochs() == null ? 20000 : req.getEpochs()));
            record.setHiddenNeurons((double) (req.getHiddenNeurons() == null ? 200 : req.getHiddenNeurons()));
            record.setLearningRate(req.getLearningRate() == null ? 0.005 : req.getLearningRate());
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
        taskExecutor.submit(() -> execute(recordId, body));
        log.info("中长期来水预测任务已提交：recordId={}, scenario={}", recordId, scenario);
        return recordId;
    }

    private String buildSchemeName(LongPredictRequest req, String scenario) {
        if (req.getSchemeName() != null && !req.getSchemeName().trim().isEmpty()) {
            return req.getSchemeName().trim();
        }
        return "中长期预报_" + (scenario == null ? "原始" : scenario);
    }

    /**
     * 异步任务：调模型 → 事务内写 monthly/tenday 明细+回写汇总 → 更新状态。
     * <p>事务边界：模型调用在事务外，明细写入 + 汇总回写包在事务内，失败整体回滚。
     */
    private void execute(String recordId, Map<String, Object> body) {
        try {
            JsonNode response = modelClient.postJson(ModelClient.PATH_PREDICT, body);

            // 1. 回写 scenario / val_metrics（大小写兼容；NSE 模型不返回，其余 5 指标全采集）
            LongPredictRecord record = new LongPredictRecord();
            record.setId(recordId);
            // 模型不回显 scenario 时不覆盖用户提交值
            String respScenario = textOf(response, "scenario");
            if (respScenario != null && !respScenario.trim().isEmpty()) {
                record.setScenario(respScenario);
            }
            JsonNode metrics = response.path("val_metrics");
            if (!metrics.isMissingNode() && !metrics.isNull()) {
                record.setNse(doubleOfAny(metrics, "NSE", "nse"));
                record.setRmse(doubleOfAny(metrics, "RMSE", "rmse"));
                record.setMae(doubleOfAny(metrics, "MAE", "mae"));
                record.setMse(doubleOfAny(metrics, "MSE", "mse"));
                record.setR2(doubleOfAny(metrics, "R2", "r2"));
                record.setSmape(doubleOfAny(metrics, "SMAPE", "smape"));
            }

            // 2. 事务内：写 monthly（year_month → stat_date 月初）+ tenday（日期 → 旬标签 + 旬首日期）+ 回写汇总
            final int inferredYear = inferYear(response);
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    LocalDateTime now = LocalDateTime.now();
                    double annualVolume = 0;
                    for (JsonNode item : response.path("monthly")) {
                        LongPredictMonthly monthly = new LongPredictMonthly();
                        monthly.setRecordId(recordId);
                        String yearMonth = textOf(item, "年月");
                        monthly.setYearMonth(yearMonth);
                        monthly.setActualTotal(doubleOf(item, "真实总量_万方"));
                        monthly.setPredictTotal(doubleOf(item, "预测总量_万方"));
                        LocalDate statDate = parseYearMonth(yearMonth);
                        monthly.setStatDate(statDate == null ? null : statDate.atStartOfDay());
                        monthly.setCorpCode(corpCode);
                        monthly.setCreatedAt(now);
                        monthly.setCreatedBy(createdBy);
                        monthly.setUpdatedAt(now);
                        monthly.setUpdatedBy(createdBy);
                        monthlyMapper.insert(monthly);
                        if (monthly.getPredictTotal() != null) {
                            annualVolume += monthly.getPredictTotal();
                        }
                    }

                    double maxVolume = Double.MIN_VALUE;
                    double minVolume = Double.MAX_VALUE;
                    boolean hasTenday = false;
                    for (JsonNode item : response.path("data")) {
                        LongPredictTenday tenday = new LongPredictTenday();
                        tenday.setRecordId(recordId);
                        String dateText = textOf(item, "日期");
                        LocalDate date = parseDate(dateText);
                        LocalDate firstDate;
                        if (date != null) {
                            // 日期格式：按日期所在旬反推旬首
                            firstDate = TenDayDateUtils.toFirstDate(TenDayDateUtils.toLabel(date), date.getYear());
                            tenday.setTendayLabel(TenDayDateUtils.toLabel(firstDate));
                        } else {
                            // 旬标签格式："5月上旬"
                            tenday.setTendayLabel(dateText);
                            firstDate = TenDayDateUtils.toFirstDate(dateText, inferredYear);
                        }
                        tenday.setPredictDate(firstDate == null ? null : firstDate.atStartOfDay());
                        tenday.setPredictVolume(doubleOf(item, "预测来水量_万方"));
                        tenday.setActualVolume(doubleOf(item, "真实来水量_万方"));
                        tenday.setCorpCode(corpCode);
                        tenday.setCreatedAt(now);
                        tenday.setCreatedBy(createdBy);
                        tenday.setUpdatedAt(now);
                        tenday.setUpdatedBy(createdBy);
                        tendayMapper.insert(tenday);
                        if (tenday.getPredictVolume() != null) {
                            hasTenday = true;
                            maxVolume = Math.max(maxVolume, tenday.getPredictVolume());
                            minVolume = Math.min(minVolume, tenday.getPredictVolume());
                        }
                    }

                    // 3. 回写汇总 + completed
                    record.setAnnualPredictVolume(round2(annualVolume));
                    if (hasTenday) {
                        record.setMaxTendayVolume(round2(maxVolume));
                        record.setMinTendayVolume(round2(minVolume));
                    }
                    record.setStatus("completed");
                    record.setUpdatedAt(LocalDateTime.now());
                    recordMapper.updateById(record);
                }
            });
            log.info("中长期来水预测完成：recordId={}", recordId);
        } catch (Exception e) {
            markFailed(recordId, e);
        }
    }

    /** 从 monthly[].年月 推断年份（用于旬标签转日期），取不到用当前年份 */
    private int inferYear(JsonNode response) {
        for (JsonNode item : response.path("monthly")) {
            String yearMonth = textOf(item, "年月");
            LocalDate date = parseYearMonth(yearMonth);
            if (date != null) {
                return date.getYear();
            }
        }
        return LocalDate.now().getYear();
    }

    private static LocalDate parseYearMonth(String yearMonth) {
        if (yearMonth == null || yearMonth.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(yearMonth.trim().substring(0, 7) + "-01");
        } catch (Exception e) {
            try {
                return LocalDate.parse(yearMonth.trim() + "-01", DateTimeFormatter.ofPattern("yyyy-M-d"));
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private void markFailed(String recordId, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String errorMsg = msg.length() > 500 ? msg.substring(0, 500) : msg;
        LongPredictRecord record = new LongPredictRecord();
        record.setId(recordId);
        record.setStatus("failed");
        record.setErrorMsg(errorMsg);
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.error("中长期来水预测失败：recordId={}, error={}", recordId, errorMsg);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
