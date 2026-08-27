package com.qgyun.hltgq.hltgqsite.model.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.entity.ShortForecastDaily;
import com.qgyun.hltgq.hltgqsite.entity.ShortForecastRecord;
import com.qgyun.hltgq.hltgqsite.mapper.ShortForecastDailyMapper;
import com.qgyun.hltgq.hltgqsite.mapper.ShortForecastRecordMapper;
import com.qgyun.hltgq.hltgqsite.model.client.ModelCallException;
import com.qgyun.hltgq.hltgqsite.model.client.ModelClient;
import com.qgyun.hltgq.hltgqsite.model.task.ModelTaskExecutor;
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
import com.qgyun.hltgq.hltgqsite.model.util.FlowRateUtils;
import com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils;
import com.qgyun.hltgq.hltgqsite.model.util.StorageCurveService;
import com.qgyun.hltgq.hltgqsite.model.vo.ShortForecastRequest;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOf;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOfAny;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.parseDate;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.parseDateTime;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.round2;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOf;

/**
 * 短期来水预测服务。
 * <p>标准写入流程：写参数(calculating) → 调 /forecast → 写逐日明细(含预计算字段)
 * → 同步调 /loss(mode=short) 补蒸发 → 回写主表汇总 → completed；异常 failed+error_msg。
 */
@Service
public class ShortForecastService {

    private static final Logger log = LoggerFactory.getLogger(ShortForecastService.class);

    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ShortForecastRecordMapper recordMapper;
    private final ShortForecastDailyMapper dailyMapper;
    private final ModelClient modelClient;
    private final ModelTaskExecutor taskExecutor;
    private final StorageCurveService storageCurveService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final String corpCode;
    private final String createdBy;

    public ShortForecastService(ShortForecastRecordMapper recordMapper,
                                ShortForecastDailyMapper dailyMapper,
                                ModelClient modelClient,
                                ModelTaskExecutor taskExecutor,
                                StorageCurveService storageCurveService,
                                TransactionTemplate transactionTemplate,
                                ObjectMapper objectMapper,
                                @Value("${hltgq.corp-code}") String corpCode,
                                @Value("${hltgq.created-by}") String createdBy) {
        this.recordMapper = recordMapper;
        this.dailyMapper = dailyMapper;
        this.modelClient = modelClient;
        this.taskExecutor = taskExecutor;
        this.storageCurveService = storageCurveService;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.corpCode = corpCode;
        this.createdBy = createdBy;
    }

    /**
     * 提交短期来水预测（秒回 recordId），异步执行模型计算。
     */
    public String submit(ShortForecastRequest req) {
        if (req == null || req.getStartDate() == null || req.getStartDate().trim().isEmpty()) {
            throw new IllegalArgumentException("起始日期不能为空（格式 YYYY-MM-DD）");
        }
        String startDate = req.getStartDate().trim();
        try {
            LocalDate.parse(startDate, DATE_ONLY);
        } catch (Exception e) {
            throw new IllegalArgumentException("起始日期格式错误，应为 YYYY-MM-DD");
        }
        int days = req.getDays() == null ? 30 : req.getDays();
        if (days < 1 || days > 30) {
            throw new IllegalArgumentException("预报天数必须在 1~30 之间");
        }
        boolean useTypical = Boolean.TRUE.equals(req.getUseTypical());
        boolean adjustRainfall = Boolean.TRUE.equals(req.getAdjustRainfall());
        String dischargeMode = req.getDischargeMode() == null ? "max" : req.getDischargeMode().trim();
        if (!"max".equals(dischargeMode) && !"none".equals(dischargeMode) && !"custom".equals(dischargeMode)) {
            throw new IllegalArgumentException("下泄模式必须是 max / none / custom 之一");
        }
        if (!useTypical && (req.getRainfall() == null || req.getRainfall().size() != days)) {
            throw new IllegalArgumentException("未使用典型洪水时，降雨量数组必填且长度等于预报天数");
        }
        if ("custom".equals(dischargeMode)
                && (req.getCustomDischarge() == null || req.getCustomDischarge().size() != days)) {
            throw new IllegalArgumentException("自定义下泄模式下，自定义下泄数组必填且长度等于预报天数");
        }
        if (adjustRainfall && (req.getTargetTotal() == null || req.getTargetTotal() <= 0)) {
            throw new IllegalArgumentException("调整降雨时目标总降雨量必须大于 0");
        }

        // 组装模型请求体（中文字段原样传递）
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("start_date", startDate);
        body.put("days", days);
        body.put("use_typical", useTypical);
        if (useTypical) {
            body.put("flood_idx", req.getFloodIdx() == null ? 0 : req.getFloodIdx());
        }
        if (!useTypical) {
            body.put("rainfall", req.getRainfall());
        }
        body.put("adjust_rainfall", adjustRainfall);
        if (adjustRainfall) {
            body.put("target_total", req.getTargetTotal());
        }
        body.put("initial_water_level", req.getInitialWaterLevel());
        body.put("discharge_mode", dischargeMode);
        if ("custom".equals(dischargeMode)) {
            body.put("custom_discharge", req.getCustomDischarge());
        }

        // 写主表（参数留存 + 请求归档，calculating）
        ShortForecastRecord record = new ShortForecastRecord();
        record.setSchemeName(buildSchemeName(req, startDate, days));
        record.setStatus("calculating");
        record.setDelFlag(BoolTextUtils.FALSE);
        record.setStartDate(parseDateTime(startDate));
        record.setDays((double) days);
        record.setUseTypical(BoolTextUtils.boolToText(useTypical));
        if (useTypical) {
            record.setFloodIdx((double) (req.getFloodIdx() == null ? 0 : req.getFloodIdx()));
        }
        record.setAdjustRainfall(BoolTextUtils.boolToText(adjustRainfall));
        record.setTargetTotal(req.getTargetTotal());
        record.setInitialWaterLevel(req.getInitialWaterLevel());
        record.setDischargeMode(dischargeMode);
        record.setCorpCode(corpCode);
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy(createdBy);
        record.setUpdatedAt(record.getCreatedAt());
        record.setUpdatedBy(createdBy);
        try {
            record.setRequestJson(objectMapper.writeValueAsString(body));
            record.setRainfallJson(req.getRainfall() == null ? null : objectMapper.writeValueAsString(req.getRainfall()));
            record.setCustomDischargeJson(req.getCustomDischarge() == null ? null
                    : objectMapper.writeValueAsString(req.getCustomDischarge()));
        } catch (Exception e) {
            throw new IllegalArgumentException("请求体归档失败: " + e.getMessage());
        }
        recordMapper.insert(record);

        // 异步执行模型计算
        String recordId = record.getId();
        taskExecutor.submit(() -> execute(recordId, body));
        log.info("短期来水预测任务已提交：recordId={}, startDate={}, days={}", recordId, startDate, days);
        return recordId;
    }

    private String buildSchemeName(ShortForecastRequest req, String startDate, int days) {
        if (req.getSchemeName() != null && !req.getSchemeName().trim().isEmpty()) {
            return req.getSchemeName().trim();
        }
        return "短期预报_" + startDate + "_" + days + "天";
    }

    /**
     * 异步任务：调模型 → 构建明细 → 事务内写明细+回写汇总 → 更新状态。
     * <p>事务边界：模型调用（/forecast、/loss）在事务外，避免长事务占用连接；
     * 明细写入 + 汇总回写包在事务内，中途失败整体回滚，不留孤儿明细。
     */
    private void execute(String recordId, Map<String, Object> body) {
        try {
            // 1. 调用 /forecast
            JsonNode response = modelClient.postJson(ModelClient.PATH_FORECAST, body);

            // 2. 解析 summary 与 data
            JsonNode summary = response.path("summary");
            JsonNode data = response.path("data");

            // 3. 构建逐日明细（含预计算字段，纯内存，事务外）
            List<ShortForecastDaily> dailies = new ArrayList<>();
            double peakInflowRate = 0;
            boolean hasPeak = false;
            LocalDateTime now = LocalDateTime.now();
            for (JsonNode item : data) {
                ShortForecastDaily daily = new ShortForecastDaily();
                daily.setRecordId(recordId);
                daily.setForecastDate(parseDateTime(textOf(item, "日期")));
                daily.setRainfall(doubleOf(item, "降雨量_mm"));
                daily.setInflowVolume(doubleOf(item, "入库水量_万方"));
                daily.setOutflowVolume(doubleOf(item, "出库水量_万方"));
                daily.setWaterLevel(doubleOf(item, "水位_m"));
                daily.setInflowRate(FlowRateUtils.volumeToRate(daily.getInflowVolume()));
                daily.setOutflowRate(FlowRateUtils.volumeToRate(daily.getOutflowVolume()));
                // 库容：模型直出「库容_万方」优先，缺失降级查库容曲线表
                Double storage = doubleOf(item, "库容_万方");
                daily.setStorage(storage != null ? storage
                        : storageCurveService.getStorageByLevel(daily.getWaterLevel()));
                daily.setCorpCode(corpCode);
                daily.setCreatedAt(now);
                daily.setCreatedBy(createdBy);
                daily.setUpdatedAt(now);
                daily.setUpdatedBy(createdBy);
                dailies.add(daily);
                if (daily.getInflowRate() != null) {
                    if (!hasPeak || daily.getInflowRate() > peakInflowRate) {
                        peakInflowRate = daily.getInflowRate();
                        hasPeak = true;
                    }
                }
            }
            // 4. 同步调 /loss(mode=short) 补蒸发（失败仅留空，不阻塞）
            Map<LocalDate, Double> evaporationMap = fetchShortEvaporation(body);
            for (ShortForecastDaily daily : dailies) {
                if (daily.getForecastDate() != null) {
                    daily.setEvaporation(evaporationMap.get(daily.getForecastDate().toLocalDate()));
                }
            }

            // 5. 事务内：写明细 + 回写主表汇总（任一失败整体回滚，不留孤儿明细）
            final double peakValue = peakInflowRate;
            final boolean hasPeakValue = hasPeak;
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    for (ShortForecastDaily daily : dailies) {
                        dailyMapper.insert(daily);
                    }
                    ShortForecastRecord record = new ShortForecastRecord();
                    record.setId(recordId);
                    record.setTotalRainfall(doubleOf(summary, "总降雨量_mm"));
                    record.setTotalInflow(doubleOf(summary, "总入库水量_万方"));
                    record.setTotalOutflow(doubleOf(summary, "总出库水量_万方"));
                    record.setFinalWaterLevel(doubleOf(summary, "期末水位_m"));
                    record.setMaxWaterLevel(doubleOf(summary, "最高水位_m"));
                    record.setPeakInflowRate(hasPeakValue ? round2(peakValue) : null);
                    record.setStatus("completed");
                    record.setUpdatedAt(LocalDateTime.now());
                    recordMapper.updateById(record);
                }
            });
            log.info("短期来水预测完成：recordId={}, 明细{}条", recordId, dailies.size());
        } catch (Exception e) {
            markFailed(recordId, e);
        }
    }

    /**
     * 同步调用 /loss(mode=short) 提取逐日蒸发量。
     * 入参复用 /forecast 的短期参数；返回 日期 → 蒸发量 映射。
     */
    private Map<LocalDate, Double> fetchShortEvaporation(Map<String, Object> forecastBody) {
        Map<LocalDate, Double> result = new HashMap<>();
        try {
            Map<String, Object> lossBody = new LinkedHashMap<>();
            lossBody.put("mode", "short");
            lossBody.put("start_date", forecastBody.get("start_date"));
            lossBody.put("days", forecastBody.get("days"));
            if (forecastBody.containsKey("rainfall")) {
                lossBody.put("rainfall", forecastBody.get("rainfall"));
            }
            lossBody.put("use_typical", forecastBody.getOrDefault("use_typical", Boolean.FALSE));
            if (forecastBody.containsKey("flood_idx")) {
                lossBody.put("flood_idx", forecastBody.get("flood_idx"));
            }
            if (forecastBody.containsKey("initial_water_level")) {
                lossBody.put("initial_water_level", forecastBody.get("initial_water_level"));
            }
            JsonNode response = modelClient.postJson(ModelClient.PATH_LOSS, lossBody);
            JsonNode data = response.path("data");
            for (JsonNode item : data) {
                LocalDate date = parseDate(textOf(item, "日期"));
                Double loss = doubleOfAny(item, "蒸发损失_万方", "损失水量_万方");
                if (date != null && loss != null) {
                    result.put(date, loss);
                }
            }
        } catch (ModelCallException e) {
            log.warn("短期蒸发量(/loss)调用失败，蒸发量留空：code={}, msg={}", e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("短期蒸发量(/loss)解析失败，蒸发量留空：{}", e.getMessage());
        }
        return result;
    }

    private void markFailed(String recordId, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String errorMsg = msg.length() > 500 ? msg.substring(0, 500) : msg;
        ShortForecastRecord record = new ShortForecastRecord();
        record.setId(recordId);
        record.setStatus("failed");
        record.setErrorMsg(errorMsg);
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.error("短期来水预测失败：recordId={}, error={}", recordId, errorMsg);
    }
}
