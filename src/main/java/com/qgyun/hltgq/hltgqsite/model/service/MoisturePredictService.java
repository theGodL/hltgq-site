package com.qgyun.hltgq.hltgqsite.model.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.entity.MoistureDetail;
import com.qgyun.hltgq.hltgqsite.entity.MoistureRecord;
import com.qgyun.hltgq.hltgqsite.mapper.MoistureDetailMapper;
import com.qgyun.hltgq.hltgqsite.mapper.MoistureRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.SoilMoistureMapper;
import com.qgyun.hltgq.hltgqsite.model.client.ModelClient;
import com.qgyun.hltgq.hltgqsite.model.task.ModelTaskExecutor;
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.MoistureSubmitRequest;
import com.qgyun.hltgq.hltgqsite.vo.MoistureInitVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.doubleOf;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.parseHourDateTime;
import static com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils.textOf;

/**
 * 墒情预测服务（模型 /moisture）：四站点土壤含水量逐小时推演 + 干旱等级判定。
 * <p>标准写入流程：写参数(calculating) → 调 /moisture → 逐小时明细落库（16 天≈385 条/站）
 * → 回写主表 completed；异常 failed+error_msg。
 * <p>参数口径（会议定稿）：
 * <ul>
 *   <li>四站点：太湖毕岭(9000000132)/望江(9000000133)/宿松(9000000134)/怀宁麻塘湖(9000000135)；</li>
 *   <li>初始墒情：各站最新一条三层含水量均有效的数据（设备异常 -9991/不存在 -999 排除），时间截整到整点；</li>
 *   <li>降雨序列：最早初始时刻起逐小时（默认 16 天 = 385 点），自动拉气象，缺失填 0；</li>
 *   <li>field_capacity/soil_texture/decay_rates/g_denominator 一律不传（模型已率定）。</li>
 * </ul>
 */
@Service
public class MoisturePredictService {

    private static final Logger log = LoggerFactory.getLogger(MoisturePredictService.class);

    /** 四站点映射：stcd → 模型站名（模型 init_states/results 以站名为 key） */
    private static final Map<String, String> STATION_NAMES = new LinkedHashMap<>();

    /** moisture 主表 status 平台配单选字典，必须存 # 编码：#1#=计算中、#2#=已完成、#3#=失败 */
    private static final String STATUS_CALCULATING = "#1#";
    private static final String STATUS_COMPLETED = "#2#";
    private static final String STATUS_FAILED = "#3#";

    static {
        STATION_NAMES.put("9000000132", "太湖毕岭");
        STATION_NAMES.put("9000000133", "望江");
        STATION_NAMES.put("9000000134", "宿松");
        STATION_NAMES.put("9000000135", "怀宁麻塘湖");
    }

    /** 模型 /moisture 时间格式（YYYY/M/D H:M，单数字段不补零） */
    private static final DateTimeFormatter MODEL_TIME_FMT = DateTimeFormatter.ofPattern("yyyy/M/d H:mm");

    /** 默认预测天数（与气象逐小时预报 16 天上限一致） */
    private static final int DEFAULT_DAYS = 16;

    /** 预测天数上限 */
    private static final int MAX_DAYS = 16;

    private final MoistureRecordMapper recordMapper;
    private final MoistureDetailMapper detailMapper;
    private final SoilMoistureMapper soilMoistureMapper;
    private final ModelClient modelClient;
    private final ModelTaskExecutor taskExecutor;
    private final ModelWeatherRainService weatherRainService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final String corpCode;
    private final String createdBy;

    public MoisturePredictService(MoistureRecordMapper recordMapper,
                                  MoistureDetailMapper detailMapper,
                                  SoilMoistureMapper soilMoistureMapper,
                                  ModelClient modelClient,
                                  ModelTaskExecutor taskExecutor,
                                  ModelWeatherRainService weatherRainService,
                                  TransactionTemplate transactionTemplate,
                                  ObjectMapper objectMapper,
                                  @Value("${hltgq.corp-code}") String corpCode,
                                  @Value("${hltgq.created-by}") String createdBy) {
        this.recordMapper = recordMapper;
        this.detailMapper = detailMapper;
        this.soilMoistureMapper = soilMoistureMapper;
        this.modelClient = modelClient;
        this.taskExecutor = taskExecutor;
        this.weatherRainService = weatherRainService;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.corpCode = corpCode;
        this.createdBy = createdBy;
    }

    /**
     * 提交墒情预测（秒回 recordId），异步执行模型计算。
     */
    public String submit(MoistureSubmitRequest req) {
        int days = req == null || req.getDays() == null ? DEFAULT_DAYS : req.getDays();
        if (days < 1 || days > MAX_DAYS) {
            throw new IllegalArgumentException("预测天数必须在 1~" + MAX_DAYS + " 之间");
        }

        // 1. 四站初始墒情（各自最新一条三层均有效数据，缺失站点跳过）
        List<MoistureInitVO> inits = soilMoistureMapper.selectLatestValidPerStation(
                new ArrayList<>(STATION_NAMES.keySet()));
        LocalDateTime start = null;
        Map<String, Map<String, Object>> initStates = new LinkedHashMap<>();
        for (MoistureInitVO init : inits) {
            String name = STATION_NAMES.get(init.getStcd());
            if (name == null || init.getTm() == null) {
                continue;
            }
            // 初始时刻截整到整点（不向未来取整）
            LocalDateTime hour = init.getTm().withMinute(0).withSecond(0).withNano(0);
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("time", hour.format(MODEL_TIME_FMT));
            state.put("mten", round1(init.getMten()));
            state.put("mtwenty", round1(init.getMtwenty()));
            state.put("mthirty", round1(init.getMthirty()));
            initStates.put(name, state);
            if (start == null || hour.isBefore(start)) {
                start = hour;
            }
        }
        if (initStates.isEmpty()) {
            throw new IllegalArgumentException("四站点均无有效初始墒情数据（设备异常或数据缺失）");
        }

        // 2. 降雨序列：最早初始时刻起逐小时（含起点，16 天 = 385 点），缺失填 0
        final LocalDateTime startTime = start;
        int steps = days * 24 + 1;
        List<Double> rain = weatherRainService.loadHourlyRain(startTime, steps);
        List<Map<String, Object>> rainData = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", startTime.plusHours(i).format(MODEL_TIME_FMT));
            point.put("rain", rain.get(i));
            rainData.add(point);
        }

        // 3. 组装模型请求体（不传 field_capacity/soil_texture/decay_rates/g_denominator）
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("init_states", initStates);
        body.put("rain_data", rainData);
        body.put("save_csv", false);

        // 4. 写主表（参数留存 + 请求归档，calculating）
        MoistureRecord record = new MoistureRecord();
        record.setSchemeName(buildSchemeName(req, startTime, days));
        record.setStatus(STATUS_CALCULATING);
        record.setDelFlag(BoolTextUtils.FALSE);
        record.setStartTime(startTime);
        record.setEndTime(startTime.plusHours(steps - 1));
        record.setStationCount((double) initStates.size());
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

        // 5. 四站 stcd → 站点ID（平台档案主键，明细 site 列存站点ID）
        Map<String, String> siteIds = new LinkedHashMap<>();
        for (Map<String, String> row : soilMoistureMapper.selectStationIdsByStcds(
                new ArrayList<>(STATION_NAMES.keySet()))) {
            if (row.get("stcd") != null && row.get("id") != null) {
                siteIds.put(row.get("stcd"), row.get("id"));
            }
        }

        // 6. 异步执行模型计算
        final String recordId = record.getId();
        final Map<String, String> siteIdMap = siteIds;
        taskExecutor.submit(() -> execute(recordId, body, siteIdMap));
        log.info("墒情预测任务已提交：recordId={}, 站点{}个, 起点 {}, 步数={}", recordId,
                initStates.size(), startTime.format(MODEL_TIME_FMT), steps);
        return recordId;
    }

    private String buildSchemeName(MoistureSubmitRequest req, LocalDateTime start, int days) {
        if (req != null && req.getSchemeName() != null && !req.getSchemeName().trim().isEmpty()) {
            return req.getSchemeName().trim();
        }
        return "墒情预测_" + start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "_" + days + "天";
    }

    /**
     * 异步任务：调模型 → 构建逐小时明细 → 事务内写明细+回写汇总 → 更新状态。
     * <p>事务边界：模型调用（/moisture）在事务外，避免长事务占用连接；
     * 明细写入 + 主表回写包在事务内，中途失败整体回滚，不留孤儿明细。
     */
    private void execute(String recordId, Map<String, Object> body, Map<String, String> siteIds) {
        try {
            // 1. 调用 /moisture
            JsonNode response = modelClient.postJson(ModelClient.PATH_MOISTURE, body);

            // 2. 解析 results（站名 → 逐小时数组），构建明细（纯内存，事务外）
            JsonNode results = response.path("results");
            List<MoistureDetail> details = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            for (Map.Entry<String, String> station : STATION_NAMES.entrySet()) {
                String siteId = siteIds.get(station.getKey());
                if (siteId == null) {
                    // 档案表无该站 ID：不落明细行（site 列存站点ID，无 ID 无法关联站点档案）
                    log.warn("墒情明细跳过站点 {}：档案表无站点ID（stcd={}）", station.getValue(), station.getKey());
                    continue;
                }
                JsonNode stationRows = results.path(station.getValue());
                if (!stationRows.isArray()) {
                    continue;
                }
                for (JsonNode row : stationRows) {
                    MoistureDetail detail = new MoistureDetail();
                    detail.setRecordId(recordId);
                    // 站点ID（平台档案主键 UUID），与平台表单模型 site 关联字段口径一致
                    detail.setSite(siteId);
                    detail.setTm(parseHourDateTime(textOf(row, "时间")));
                    detail.setRainfall(doubleOf(row, "降雨_mm"));
                    detail.setMten(doubleOf(row, "10cm_%"));
                    detail.setMtwenty(doubleOf(row, "20cm_%"));
                    detail.setMthirty(doubleOf(row, "30cm_%"));
                    detail.setGValue(doubleOf(row, "G值(RSM)"));
                    // 干旱等级归一化为平台编码（模型返回中文或编码均可）
                    detail.setDroughtLevel(normalizeDroughtLevel(textOf(row, "干旱等级")));
                    detail.setCorpCode(corpCode);
                    detail.setCreatedAt(now);
                    detail.setCreatedBy(createdBy);
                    detail.setUpdatedAt(now);
                    detail.setUpdatedBy(createdBy);
                    details.add(detail);
                }
            }

            // 3. 事务内：写明细 + 回写主表 completed（任一失败整体回滚）
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    for (MoistureDetail detail : details) {
                        detailMapper.insert(detail);
                    }
                    MoistureRecord record = new MoistureRecord();
                    record.setId(recordId);
                    record.setStatus(STATUS_COMPLETED);
                    record.setUpdatedAt(LocalDateTime.now());
                    recordMapper.updateById(record);
                }
            });
            log.info("墒情预测完成：recordId={}, 逐小时明细{}条", recordId, details.size());
        } catch (Exception e) {
            markFailed(recordId, e);
        }
    }

    private void markFailed(String recordId, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String errorMsg = msg.length() > 500 ? msg.substring(0, 500) : msg;
        MoistureRecord record = new MoistureRecord();
        record.setId(recordId);
        record.setStatus(STATUS_FAILED);
        record.setErrorMsg(errorMsg);
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.error("墒情预测失败：recordId={}, error={}", recordId, errorMsg);
    }

    /** BigDecimal → double 保留 1 位小数；null 返回 null。 */
    private static Double round1(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return Math.round(value.doubleValue() * 10.0) / 10.0;
    }

    /**
     * 干旱等级归一化为平台编码（#1#无旱 / #2#轻旱 / #3#中旱 / #4#重旱 / #5#特旱）：
     * 模型返回中文或编码均可正确落库；未知值原样透传；null 返回 null。
     */
    private static String normalizeDroughtLevel(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String value = text.trim();
        switch (value) {
            case "无旱":
            case "#1#":
                return "#1#";
            case "轻旱":
            case "#2#":
                return "#2#";
            case "中旱":
            case "#3#":
                return "#3#";
            case "重旱":
            case "#4#":
                return "#4#";
            case "特旱":
            case "#5#":
                return "#5#";
            default:
                return value;
        }
    }
}
