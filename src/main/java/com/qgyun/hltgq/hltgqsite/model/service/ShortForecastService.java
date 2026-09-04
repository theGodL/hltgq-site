package com.qgyun.hltgq.hltgqsite.model.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.decision.mapper.FloodDroughtMapper;
import com.qgyun.hltgq.hltgqsite.entity.ShortForecastDaily;
import com.qgyun.hltgq.hltgqsite.entity.ShortForecastRecord;
import com.qgyun.hltgq.hltgqsite.mapper.ShortForecastDailyMapper;
import com.qgyun.hltgq.hltgqsite.mapper.ShortForecastRecordMapper;
import com.qgyun.hltgq.hltgqsite.model.client.ModelClient;
import com.qgyun.hltgq.hltgqsite.model.task.ModelTaskExecutor;
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOf;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.parseHourDateTime;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.round2;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOf;

/**
 * 短期来水预测服务（小时尺度，模型 /forecast V3）。
 * <p>标准写入流程：写参数(calculating) → 调 /forecast → 逐小时明细落库（16 天≈385 条）
 * → 回写主表汇总 → completed；异常 failed+error_msg。
 * <p>参数口径（会议定稿）：
 * <ul>
 *   <li>起调水位：自动取花凉亭坝上最新水位（不再由用户输入）；</li>
 *   <li>三开关（发电/泄洪隧洞/溢洪道）：默认均 false；</li>
 *   <li>逐小时降雨：未传时自动拉取气象 16 天逐小时数据，缺失小时填 0，起止与窗口对齐。</li>
 * </ul>
 */
@Service
public class ShortForecastService {

    private static final Logger log = LoggerFactory.getLogger(ShortForecastService.class);

    /** 模型窗口时间格式（YYYY-MM-DD H:M，与 /forecast 契约兼容） */
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 预报窗口小时数上限：气象预报 16 天 = 384 小时 */
    private static final int MAX_RANGE_HOURS = 384;

    /** 小时流量(m3/s) → 小时水量(万方) 换算系数：3600 / 10000 */
    private static final double RATE_TO_VOLUME = 0.36;

    private final ShortForecastRecordMapper recordMapper;
    private final ShortForecastDailyMapper dailyMapper;
    private final ModelClient modelClient;
    private final ModelTaskExecutor taskExecutor;
    private final StorageCurveService storageCurveService;
    private final ModelWeatherRainService weatherRainService;
    private final FloodDroughtMapper floodDroughtMapper;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final String corpCode;
    private final String createdBy;
    private final String obsLevelStcd;

    public ShortForecastService(ShortForecastRecordMapper recordMapper,
                                ShortForecastDailyMapper dailyMapper,
                                ModelClient modelClient,
                                ModelTaskExecutor taskExecutor,
                                StorageCurveService storageCurveService,
                                ModelWeatherRainService weatherRainService,
                                FloodDroughtMapper floodDroughtMapper,
                                TransactionTemplate transactionTemplate,
                                ObjectMapper objectMapper,
                                @Value("${hltgq.corp-code}") String corpCode,
                                @Value("${hltgq.created-by}") String createdBy,
                                @Value("${flood-drought.obs-level-stcd:3206400007}") String obsLevelStcd) {
        this.recordMapper = recordMapper;
        this.dailyMapper = dailyMapper;
        this.modelClient = modelClient;
        this.taskExecutor = taskExecutor;
        this.storageCurveService = storageCurveService;
        this.weatherRainService = weatherRainService;
        this.floodDroughtMapper = floodDroughtMapper;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.corpCode = corpCode;
        this.createdBy = createdBy;
        this.obsLevelStcd = trimToNull(obsLevelStcd);
    }

    /**
     * 提交短期来水预测（秒回 recordId），异步执行模型计算。
     */
    public String submit(ShortForecastRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        // start/end 必填 + 格式校验（YYYY-MM-DD HH:mm）
        LocalDateTime start = parseHour(req.getStart());
        if (start == null) {
            throw new IllegalArgumentException("开始时间不能为空（格式 YYYY-MM-DD HH:mm）");
        }
        LocalDateTime end = parseHour(req.getEnd());
        if (end == null) {
            throw new IllegalArgumentException("结束时间不能为空（格式 YYYY-MM-DD HH:mm）");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
        int steps = (int) ChronoUnit.HOURS.between(start, end) + 1;
        // 窗口含端点逐小时；上限 384 小时（气象 16 天），下限 2 步
        if (steps < 2 || steps - 1 > MAX_RANGE_HOURS) {
            throw new IllegalArgumentException("预报窗口必须在 1~" + MAX_RANGE_HOURS + " 小时之间");
        }
        boolean enablePower = Boolean.TRUE.equals(req.getEnablePower());
        boolean enableTunnel = Boolean.TRUE.equals(req.getEnableTunnel());
        boolean enableSpillway = Boolean.TRUE.equals(req.getEnableSpillway());
        if (req.getRainfall() != null && req.getRainfall().size() != steps) {
            throw new IllegalArgumentException("逐小时降雨数组长度必须等于预报窗口逐小时步数（" + steps + "）");
        }

        // 组装模型请求体（/forecast V3：start/end/start_level/三开关/rainfall）
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("start", start.format(HOUR_FMT));
        body.put("end", end.format(HOUR_FMT));
        Double startLevel = loadLatestLevel();
        if (startLevel != null) {
            body.put("start_level", startLevel);
        }
        body.put("enable_power", enablePower);
        body.put("enable_tunnel", enableTunnel);
        body.put("enable_spillway", enableSpillway);
        final List<Double> rainfall = req.getRainfall() == null
                ? weatherRainService.loadHourlyRain(start, steps) : req.getRainfall();
        body.put("rainfall", rainfall);

        // 写主表（参数留存 + 请求归档，calculating）
        ShortForecastRecord record = new ShortForecastRecord();
        record.setSchemeName(buildSchemeName(req, start, steps));
        record.setStatus(ModelRecordCommonService.STATUS_CALCULATING);
        record.setDelFlag(BoolTextUtils.FALSE);
        record.setStartDate(start);
        record.setEndDate(end);
        record.setDays((double) steps);
        record.setStartLevel(startLevel);
        record.setEnablePower(BoolTextUtils.boolToText(enablePower));
        record.setEnableTunnel(BoolTextUtils.boolToText(enableTunnel));
        record.setEnableSpillway(BoolTextUtils.boolToText(enableSpillway));
        record.setCorpCode(corpCode);
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy(createdBy);
        record.setUpdatedAt(record.getCreatedAt());
        record.setUpdatedBy(createdBy);
        try {
            record.setRequestJson(objectMapper.writeValueAsString(body));
            record.setRainfallJson(objectMapper.writeValueAsString(rainfall));
        } catch (Exception e) {
            throw new IllegalArgumentException("请求体归档失败: " + e.getMessage());
        }
        recordMapper.insert(record);

        // 异步执行模型计算
        String recordId = record.getId();
        taskExecutor.submit(() -> execute(recordId, body, start, rainfall));
        log.info("短期来水预测任务已提交：recordId={}, 窗口 {} ~ {}, steps={}", recordId,
                start.format(HOUR_FMT), end.format(HOUR_FMT), steps);
        return recordId;
    }

    private String buildSchemeName(ShortForecastRequest req, LocalDateTime start, int steps) {
        if (req.getSchemeName() != null && !req.getSchemeName().trim().isEmpty()) {
            return req.getSchemeName().trim();
        }
        return "短期预报_" + start.format(HOUR_FMT) + "_" + steps + "小时";
    }

    /**
     * 异步任务：调模型 → 构建逐小时明细 → 事务内写明细+回写汇总 → 更新状态。
     * <p>事务边界：模型调用（/forecast）在事务外，避免长事务占用连接；
     * 明细写入 + 汇总回写包在事务内，中途失败整体回滚，不留孤儿明细。
     */
    private void execute(String recordId, Map<String, Object> body,
                         LocalDateTime start, List<Double> rainfall) {
        try {
            // 1. 调用 /forecast
            JsonNode response = modelClient.postJson(ModelClient.PATH_FORECAST, body);

            // 2. 解析 meta/summary/data（逐小时演算表）
            JsonNode summary = response.path("summary");
            JsonNode meta = response.path("meta");
            JsonNode data = response.path("data");
            Map<LocalDateTime, Double> rainByHour = new HashMap<>();
            for (int i = 0; i < rainfall.size(); i++) {
                rainByHour.put(start.plusHours(i), rainfall.get(i));
            }

            // 3. 构建逐小时明细（含预计算字段，纯内存，事务外）
            List<ShortForecastDaily> dailies = new ArrayList<>();
            double peakInflowRate = 0;
            double totalOutflowVolume = 0;
            boolean hasPeak = false;
            LocalDateTime now = LocalDateTime.now();
            Double lastWaterLevel = null;
            for (JsonNode item : data) {
                ShortForecastDaily daily = new ShortForecastDaily();
                daily.setRecordId(recordId);
                daily.setForecastDate(parseHourDateTime(textOf(item, "时间")));
                // 降雨：入参逐小时序列按时间对齐（data 不携带降雨列）
                Double rain = daily.getForecastDate() == null ? null : rainByHour.get(daily.getForecastDate());
                daily.setRainfall(rain);
                // 流量：data 直出；水量=流量×0.36（小时量换算）
                Double inflowRate = doubleOf(item, "入库流量_m3s");
                Double outflowRate = doubleOf(item, "合计下泄流量_m3s");
                daily.setInflowRate(inflowRate);
                daily.setOutflowRate(outflowRate);
                daily.setInflowVolume(inflowRate == null ? null : round2(inflowRate * RATE_TO_VOLUME));
                daily.setOutflowVolume(outflowRate == null ? null : round2(outflowRate * RATE_TO_VOLUME));
                if (outflowRate != null) {
                    totalOutflowVolume += outflowRate * RATE_TO_VOLUME;
                }
                // 水位/库容：data 直出，库容缺失降级查库容曲线表
                Double waterLevel = doubleOf(item, "水位_m");
                daily.setWaterLevel(waterLevel);
                if (waterLevel != null) {
                    lastWaterLevel = waterLevel;
                }
                Double storage = doubleOf(item, "库容_万方");
                daily.setStorage(storage != null ? storage
                        : storageCurveService.getStorageByLevel(daily.getWaterLevel()));
                daily.setCorpCode(corpCode);
                daily.setCreatedAt(now);
                daily.setCreatedBy(createdBy);
                daily.setUpdatedAt(now);
                daily.setUpdatedBy(createdBy);
                dailies.add(daily);
                if (inflowRate != null) {
                    if (!hasPeak || inflowRate > peakInflowRate) {
                        peakInflowRate = inflowRate;
                        hasPeak = true;
                    }
                }
            }

            // 4. 事务内：写明细 + 回写主表汇总（任一失败整体回滚，不留孤儿明细）
            final double peakValue = peakInflowRate;
            final boolean hasPeakValue = hasPeak;
            final double outflowTotal = totalOutflowVolume;
            final Double finalWaterLevel = lastWaterLevel;
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    for (ShortForecastDaily daily : dailies) {
                        dailyMapper.insert(daily);
                    }
                    ShortForecastRecord record = new ShortForecastRecord();
                    record.setId(recordId);
                    record.setTotalRainfall(doubleOf(meta, "total_rainfall_mm"));
                    record.setTotalInflow(doubleOf(summary, "总来水量_万方"));
                    record.setTotalOutflow(round2(outflowTotal));
                    record.setFinalWaterLevel(finalWaterLevel);
                    record.setMaxWaterLevel(doubleOf(summary, "最高水位_m"));
                    // 峰值入库：summary 直出优先，缺失回退明细 MAX
                    Double peakFromSummary = doubleOf(summary, "入库洪峰流量_m3s");
                    record.setPeakInflowRate(peakFromSummary != null
                            ? round2(peakFromSummary) : (hasPeakValue ? round2(peakValue) : null));
                    record.setStatus(ModelRecordCommonService.STATUS_COMPLETED);
                    record.setUpdatedAt(LocalDateTime.now());
                    recordMapper.updateById(record);
                }
            });
            log.info("短期来水预测完成：recordId={}, 逐小时明细{}条", recordId, dailies.size());
        } catch (Exception e) {
            markFailed(recordId, e);
        }
    }

    /**
     * 起调水位：自动取花凉亭坝上最新一条水位（站点取配置 flood-drought.obs-level-stcd）。
     * 无数据返回 null（不传则模型默认 80.0）。
     */
    private Double loadLatestLevel() {
        if (obsLevelStcd == null) {
            log.warn("短期预报：坝上水位站未配置，start_level 不传（模型默认 80.0）");
            return null;
        }
        Double value = floodDroughtMapper.selectLatestLevel(obsLevelStcd);
        if (value == null) {
            log.warn("短期预报：坝上水位站 {} 无最新水位数据，start_level 不传（模型默认 80.0）", obsLevelStcd);
            return null;
        }
        return Math.round(value * 1000.0) / 1000.0;
    }

    /** 解析 YYYY-MM-DD HH:mm；非法格式返回 null。 */
    private static LocalDateTime parseHour(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), HOUR_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private void markFailed(String recordId, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String errorMsg = msg.length() > 500 ? msg.substring(0, 500) : msg;
        ShortForecastRecord record = new ShortForecastRecord();
        record.setId(recordId);
        record.setStatus(ModelRecordCommonService.STATUS_FAILED);
        record.setErrorMsg(errorMsg);
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.error("短期来水预测失败：recordId={}, error={}", recordId, errorMsg);
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
