package com.qgyun.hltgq.hltgqsite.decision.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.decision.mapper.FloodDroughtMapper;
import com.qgyun.hltgq.hltgqsite.decision.vo.HydroChartVO;
import com.qgyun.hltgq.hltgqsite.decision.vo.HydroSubmitRequest;
import com.qgyun.hltgq.hltgqsite.decision.vo.HydroTaskVO;
import com.qgyun.hltgq.hltgqsite.decision.vo.ObsDailyVO;
import com.qgyun.hltgq.hltgqsite.decision.vo.ObsPointVO;
import com.qgyun.hltgq.hltgqsite.model.client.ModelClient;
import com.qgyun.hltgq.hltgqsite.model.service.ModelRecordCommonService;
import com.qgyun.hltgq.hltgqsite.model.task.ModelTaskExecutor;
import com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.weather.service.WeatherService;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherListItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * 防洪抗旱决策水文分析服务。
 * <p>异步三连：POST 秒回 recordId → 轮询 status → completed 拉图数据。
 * 中间态存 Redis（TTL 24h，不落库）：任务无主表留存需求，过期自动清理。
 * <p>编排：实测区间聚合（雨量水文日口径 + 水位/流量 8 时整点值）
 * → 天气逐小时降雨（Open-Meteo，预测上限 16 天）
 * → 模型 /forecast 逐小时窗口（start/end 含端点，rainfall 为等长逐小时序列）逐日水位/流量。
 * <p>null 语义：实测缺数不补 0；水位/流量分界点（split-1 下标）两序列双写保证折线连续。
 */
@Service
public class FloodDroughtService {

    private static final Logger log = LoggerFactory.getLogger(FloodDroughtService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 模型逐小时模拟结果「时间」字段格式（如 2026-09-04 00:00:00） */
    private static final DateTimeFormatter SIM_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String KEY_PREFIX = "flood-drought:hydro:";

    /** 同区间去重键前缀（提交幂等：同区间 24h 内复用同一任务，防连点重复排队） */
    private static final String DEDUP_PREFIX = "flood-drought:hydro:dedup:";

    /** 天气预报预测上限（Open-Meteo forecast_days，超过截断） */
    private static final int MAX_FORECAST_DAYS = 16;

    /** 站点类型编码：水位 / 雨量 / 流量 */
    private static final String TYPE_WATER_LEVEL = "#1#";
    private static final String TYPE_RAIN = "#2#";
    private static final String TYPE_FLOW = "#3#";

    private final FloodDroughtMapper mapper;
    private final ModelClient modelClient;
    private final ModelTaskExecutor taskExecutor;
    private final WeatherService weatherService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ShortIdGenerator idGenerator;

    private final String obsRainStcd;
    private final String obsLevelStcd;
    private final String obsFlowStcd;
    private final String weatherLon;
    private final String weatherLat;
    private final int maxRangeDays;
    private final long cacheTtlHours;

    public FloodDroughtService(FloodDroughtMapper mapper,
                               ModelClient modelClient,
                               ModelTaskExecutor taskExecutor,
                               WeatherService weatherService,
                               StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               ShortIdGenerator idGenerator,
                               @Value("${flood-drought.obs-rain-stcd:}") String obsRainStcd,
                               @Value("${flood-drought.obs-level-stcd:}") String obsLevelStcd,
                               @Value("${flood-drought.obs-flow-stcd:}") String obsFlowStcd,
                               @Value("${flood-drought.weather-lon:}") String weatherLon,
                               @Value("${flood-drought.weather-lat:}") String weatherLat,
                               @Value("${flood-drought.max-range-days:90}") int maxRangeDays,
                               @Value("${flood-drought.cache-ttl-hours:24}") long cacheTtlHours) {
        this.mapper = mapper;
        this.modelClient = modelClient;
        this.taskExecutor = taskExecutor;
        this.weatherService = weatherService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.obsRainStcd = trimToNull(obsRainStcd);
        this.obsLevelStcd = trimToNull(obsLevelStcd);
        this.obsFlowStcd = trimToNull(obsFlowStcd);
        this.weatherLon = trimToNull(weatherLon);
        this.weatherLat = trimToNull(weatherLat);
        this.maxRangeDays = maxRangeDays;
        this.cacheTtlHours = cacheTtlHours;
    }

    // ==================== 提交 / 轮询 / 详情 ====================

    /**
     * 提交防洪抗旱水文分析（秒回任务 ID），后台异步取数 + 调模型。
     * Redis 不可达时抛 502：任务状态存储是唯一跟踪途径，不可静默丢失。
     * <p>同区间幂等：24h 内同 (startDate, endDate) 复用同一任务（completed 直接复用结果、
     * calculating 复用排队中任务），防连点重复创建；failed 后清除去重键允许重试。
     * 键占位用 SET NX 原子操作，并发同区间提交仅首个请求创建任务。
     */
    public String submit(HydroSubmitRequest req) {
        LocalDate[] range = parseRange(req);
        LocalDate start = range[0];
        LocalDate end = range[1];
        String dedupKey = DEDUP_PREFIX + start + "_" + end;
        // 去重命中：completed/calculating 复用；failed 残留（Redis 异常导致清除失败）或
        // 任务已过期（键残留指向不存在任务）→ 清键后重新创建，避免 24h 内无法重试
        String existingId = getDedupId(dedupKey);
        if (existingId != null) {
            HydroTaskVO existing = readTask(existingId);
            if (existing != null && !ModelRecordCommonService.STATUS_FAILED.equals(existing.getStatus())) {
                log.info("防洪抗旱水文任务同区间复用：id={}, 区间 {} ~ {}", existingId, start, end);
                return existingId;
            }
            deleteDedupId(dedupKey);
        }
        String id = idGenerator.nextUUID(null);
        // 原子占位（SET NX）：拿到键者为主提交者；未拿到说明并发请求已创建，复用其任务
        if (saveDedupIdIfAbsent(dedupKey, id)) {
            HydroTaskVO task = new HydroTaskVO();
            task.setId(id);
            task.setStatus(ModelRecordCommonService.STATUS_CALCULATING);
            saveTask(task);
            taskExecutor.submit(() -> execute(id, start, end, dedupKey));
            log.info("防洪抗旱水文任务已提交：id={}, 区间 {} ~ {}", id, start, end);
            return id;
        }
        String winner = getDedupId(dedupKey);
        if (winner != null) {
            log.info("防洪抗旱水文任务同区间并发复用：id={}, 区间 {} ~ {}", winner, start, end);
            return winner;
        }
        // 极端竞态（键在占位与读取间过期）：本请求继续创建执行，去重降级为可用性优先
        HydroTaskVO task = new HydroTaskVO();
        task.setId(id);
        task.setStatus(ModelRecordCommonService.STATUS_CALCULATING);
        saveTask(task);
        taskExecutor.submit(() -> execute(id, start, end, dedupKey));
        log.info("防洪抗旱水文任务已提交（去重键过期降级）：id={}, 区间 {} ~ {}", id, start, end);
        return id;
    }

    /** 任务中间态查询；无记录 404。 */
    public HydroTaskVO require(String id) {
        HydroTaskVO task = readTask(id);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在或已过期");
        }
        return task;
    }

    /** 图数据查询：仅 completed 后可用；未完成 409、无记录 404。 */
    public HydroChartVO detail(String id) {
        HydroTaskVO task = require(id);
        if (!ModelRecordCommonService.STATUS_COMPLETED.equals(task.getStatus()) || task.getChart() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    ModelRecordCommonService.STATUS_FAILED.equals(task.getStatus()) ? "任务执行失败" : "任务尚未完成");
        }
        return task.getChart();
    }

    // ==================== 异步编排 ====================

    private void execute(String id, LocalDate start, LocalDate end, String dedupKey) {
        try {
            HydroChartVO chart = buildChart(start, end);
            HydroTaskVO task = new HydroTaskVO();
            task.setId(id);
            task.setStatus(ModelRecordCommonService.STATUS_COMPLETED);
            task.setChart(chart);
            saveTaskQuietly(task);
            log.info("防洪抗旱水文任务完成：id={}, 实测 {} 天，预测至 {}", id, chart.getSplit(),
                    chart.getForecastEndDate());
        } catch (Exception e) {
            HydroTaskVO task = new HydroTaskVO();
            task.setId(id);
            task.setStatus(ModelRecordCommonService.STATUS_FAILED);
            task.setErrorMsg(truncate(e.getMessage()));
            saveTaskQuietly(task);
            // 失败清除去重键：同区间允许重试（模型故障恢复后重新提交可重算）
            deleteDedupId(dedupKey);
            log.error("防洪抗旱水文任务失败：id={}, error={}", id, task.getErrorMsg(), e);
        }
    }

    /**
     * 组装图数据：实测段聚合 + 天气预测降雨 + 模型预测水位/流量。
     */
    private HydroChartVO buildChart(LocalDate start, LocalDate end) {
        LocalDate today = LocalDate.now();
        int n = (int) ChronoUnit.DAYS.between(start, end) + 1;
        List<String> dates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            dates.add(start.plusDays(i).format(DATE_FMT));
        }
        // 实测段 = 区间内 ≤ 今天的日期
        LocalDate obsEnd = end.isAfter(today) ? today : end;
        int split = obsEnd.isBefore(start) ? 0 : (int) ChronoUnit.DAYS.between(start, obsEnd) + 1;

        List<Double> rainObs = new ArrayList<>(Collections.nCopies(n, null));
        List<Double> levelObs = new ArrayList<>(Collections.nCopies(n, null));
        List<Double> flowObs = new ArrayList<>(Collections.nCopies(n, null));
        List<Double> rainPred = new ArrayList<>(Collections.nCopies(n, null));
        List<Double> levelPred = new ArrayList<>(Collections.nCopies(n, null));
        List<Double> flowPred = new ArrayList<>(Collections.nCopies(n, null));

        // 站点解析：配置优先，未配置按站点类型自动选站（无站则该序列全 null）
        String rainStcd = resolveStcd(obsRainStcd, TYPE_RAIN);
        String levelStcd = resolveStcd(obsLevelStcd, TYPE_WATER_LEVEL);
        String flowStcd = resolveStcd(obsFlowStcd, TYPE_FLOW);

        // 实测雨量（水文日口径）
        if (split > 0 && rainStcd != null) {
            fillRainObs(rainObs, rainStcd, start, obsEnd, dates);
        }
        // 实测水位/流量（每日 8 时整点值）
        if (split > 0) {
            if (levelStcd != null) {
                fillPointObs(levelObs, mapper.selectLevelPoints(levelStcd,
                        start.atStartOfDay(), obsEnd.atTime(LocalTime.MAX)), dates);
            }
            if (flowStcd != null) {
                fillPointObs(flowObs, mapper.selectFlowPoints(flowStcd,
                        start.atStartOfDay(), obsEnd.atTime(LocalTime.MAX)), dates);
            }
        }

        // 预测段：天气 16 天上限 + 模型 /forecast（逐小时窗口）
        String forecastEndDate = null;
        if (split < n) {
            int predStart = Math.max(0, split - 1);
            int predDays = Math.min(MAX_FORECAST_DAYS, n - predStart);
            // 一次拉取逐小时天气：图表逐日聚合与模型逐小时窗口共用（同源）
            Map<LocalDateTime, Double> hourlyRain = loadHourlyRain(rainStcd);
            Double[] weatherRain = aggregateDailyRain(hourlyRain, dates, predStart);
            // 图表 rainPred：预测段逐日填（截断段留 null），分界点不双写降雨
            for (int i = split; i < n && i < split + MAX_FORECAST_DAYS; i++) {
                rainPred.set(i, weatherRain[i]);
            }
            // 模型 /forecast 新契约（2026-09 手册）：start/end 逐小时窗口（含端点），rainfall 为等长逐小时序列
            // （缺失小时填 0）；start_level 自动取坝上最新水位（无则不传，模型默认 80.0）；
            // 三类下泄开关（发电/泄洪隧洞/溢洪道）默认均 false（会议 2 口径）
            LocalDateTime modelStart = LocalDate.parse(dates.get(predStart), DATE_FMT).atStartOfDay();
            LocalDateTime modelEnd = LocalDate.parse(dates.get(predStart + predDays - 1), DATE_FMT).atStartOfDay();
            int steps = (int) ChronoUnit.HOURS.between(modelStart, modelEnd) + 1;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("start", modelStart.format(HOUR_FMT));
            body.put("end", modelEnd.format(HOUR_FMT));
            Double startLevel = loadLatestLevel(levelStcd);
            if (startLevel != null) {
                body.put("start_level", startLevel);
            }
            body.put("enable_power", false);
            body.put("enable_tunnel", false);
            body.put("enable_spillway", false);
            List<Double> rainfall = new ArrayList<>(steps);
            for (int i = 0; i < steps; i++) {
                Double v = hourlyRain.get(modelStart.plusHours(i));
                rainfall.add(v == null ? 0.0 : round1(v));
            }
            body.put("rainfall", rainfall);
            JsonNode response = modelClient.postJson(ModelClient.PATH_FORECAST, body);
            // 新契约响应：meta/summary/data（逐小时演算表，含「时间」）/curve（水位-泄量曲线表，无「时间」）。
            // 线上曾实测逐小时数组键名随模型版本变化（curve 条数 < meta.hours）→ 不依赖键名，
            // 遍历根级所有数组字段，凡含合法「时间」的逐小时条目一律收集
            List<String> rootFields = new ArrayList<>();
            Map<LocalDateTime, JsonNode> byHour = new TreeMap<>();
            int curveCount = 0;
            Iterator<Map.Entry<String, JsonNode>> fields = response.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                rootFields.add(field.getKey());
                JsonNode value = field.getValue();
                if (value == null || !value.isArray()) {
                    continue;
                }
                for (JsonNode item : value) {
                    if (item == null || !item.isObject()) {
                        continue;
                    }
                    if ("curve".equals(field.getKey())) {
                        curveCount++;
                    }
                    String t = JsonFieldUtils.textOf(item, "时间");
                    if (t == null || t.isEmpty()) {
                        continue;
                    }
                    try {
                        byHour.put(LocalDateTime.parse(t, SIM_FMT), item);
                    } catch (Exception ignored) {
                        // 时间格式异常的行跳过
                    }
                }
            }
            log.info("防洪抗旱模型 /forecast 响应根字段={}，curve 条数={}，逐小时模拟 {} 条，窗口 {}~{}",
                    rootFields, curveCount, byHour.size(), modelStart.format(HOUR_FMT), modelEnd.format(HOUR_FMT));
            // 逐小时 → 逐日：每日 8 时整点（07:00~08:00 最后一条），无则当日最后一条（与实测段口径一致）
            for (int i = predStart; i < predStart + predDays; i++) {
                JsonNode item = pickAtEightOfSim(byHour, LocalDate.parse(dates.get(i), DATE_FMT));
                if (item == null) {
                    continue;
                }
                Double level = JsonFieldUtils.doubleOf(item, "水位_m");
                Double flow = JsonFieldUtils.doubleOf(item, "入库流量_m3s");
                levelPred.set(i, level == null ? null : round3(level));
                flowPred.set(i, flow == null ? null : round3(flow));
            }
            forecastEndDate = dates.get(predStart + predDays - 1);
        }

        HydroChartVO chart = new HydroChartVO();
        chart.setDates(dates);
        chart.setSplit(split);
        chart.setRainObs(rainObs);
        chart.setRainPred(rainPred);
        chart.setLevelObs(levelObs);
        chart.setLevelPred(levelPred);
        chart.setFlowObs(flowObs);
        chart.setFlowPred(flowPred);
        chart.setForecastEndDate(forecastEndDate);
        return chart;
    }

    /** 模型 /forecast 起调水位：取坝上最新一条水位；无数据返回 null（不传则模型默认 80.0）。 */
    private Double loadLatestLevel(String levelStcd) {
        if (levelStcd == null) {
            return null;
        }
        Double value = mapper.selectLatestLevel(levelStcd);
        if (value == null) {
            log.warn("防洪抗旱：水位站 {} 无最新水位数据，start_level 不传（模型默认 80.0）", levelStcd);
            return null;
        }
        return round3(value);
    }

    // ==================== 实测段聚合 ====================

    /**
     * 雨量水文日聚合：标签 D = (D-1 08:00, D 08:00] 区间内 DYP 正向增量之和（SQL 聚合）。
     */
    private void fillRainObs(List<Double> rainObs, String stcd,
                             LocalDate start, LocalDate obsEnd, List<String> dates) {
        LocalDateTime winStart = start.minusDays(1).atTime(8, 0);
        LocalDateTime winEnd = obsEnd.plusDays(1).atTime(8, 0);
        Map<LocalDate, Double> daily = new HashMap<>();
        for (ObsDailyVO row : mapper.selectRainDaily(stcd, winStart, winEnd)) {
            if (row.getD() != null && row.getValue() != null) {
                daily.put(row.getD().toLocalDate(), row.getValue());
            }
        }
        for (int i = 0; i < dates.size(); i++) {
            Double v = daily.get(LocalDate.parse(dates.get(i), DATE_FMT));
            rainObs.set(i, v == null ? null : round1(v));
        }
    }

    /**
     * 每日 8 时整点值：当日 (07:00, 08:00] 最后一条；无则回退当日最后一条；整日无数据 null。
     */
    private void fillPointObs(List<Double> obs, List<ObsPointVO> points, List<String> dates) {
        Map<LocalDate, List<ObsPointVO>> byDay = new HashMap<>();
        for (ObsPointVO p : points) {
            if (p.getTm() == null || p.getValue() == null) {
                continue;
            }
            byDay.computeIfAbsent(p.getTm().toLocalDate(), k -> new ArrayList<>()).add(p);
        }
        for (int i = 0; i < dates.size(); i++) {
            List<ObsPointVO> dayPoints = byDay.get(LocalDate.parse(dates.get(i), DATE_FMT));
            if (dayPoints == null || dayPoints.isEmpty()) {
                continue;
            }
            obs.set(i, round3(pickAtEight(dayPoints).getValue()));
        }
    }

    /** 按 tm 升序输入：窗口 (07:00, 08:00] 内最后一条；无则当日最后一条。 */
    private ObsPointVO pickAtEight(List<ObsPointVO> dayPoints) {
        ObsPointVO fallback = null;
        ObsPointVO atEight = null;
        for (ObsPointVO p : dayPoints) {
            fallback = p;
            LocalTime t = p.getTm().toLocalTime();
            if (t.isAfter(LocalTime.of(7, 0)) && !t.isAfter(LocalTime.of(8, 0))) {
                atEight = p;
            }
        }
        return atEight != null ? atEight : fallback;
    }

    /** 逐小时模拟序列取某日 8 时整点条目：(07:00, 08:00] 最后一条；无则当日最后一条；整日无数据 null。 */
    private JsonNode pickAtEightOfSim(Map<LocalDateTime, JsonNode> byHour, LocalDate day) {
        JsonNode atEight = null;
        JsonNode fallback = null;
        for (Map.Entry<LocalDateTime, JsonNode> e : byHour.entrySet()) {
            LocalDateTime t = e.getKey();
            if (!t.toLocalDate().equals(day)) {
                continue;
            }
            fallback = e.getValue();
            LocalTime time = t.toLocalTime();
            if (time.isAfter(LocalTime.of(7, 0)) && !time.isAfter(LocalTime.of(8, 0))) {
                atEight = e.getValue();
            }
        }
        return atEight != null ? atEight : fallback;
    }

    // ==================== 天气预测降雨 ====================

    /**
     * 逐小时天气降雨映射（date+hour 拼 LocalDateTime → 降雨 mm）。
     * 天气不可用（坐标缺失/上游失败/限流）时返回空 Map（模型侧统一填 0）。
     */
    private Map<LocalDateTime, Double> loadHourlyRain(String rainStcd) {
        Map<LocalDateTime, Double> byHour = new HashMap<>();
        Double[] coord = resolveWeatherCoord(rainStcd);
        if (coord == null) {
            log.warn("防洪抗旱：天气坐标未配置且雨量站无经纬度，预测降雨缺失");
            return byHour;
        }
        try {
            List<WeatherListItemVO> hours = weatherService.hourlyWeather(coord[0], coord[1], null, null, null);
            for (WeatherListItemVO h : hours) {
                if (h.getDate() == null || h.getHour() == null || h.getRainfall() == null) {
                    continue;
                }
                try {
                    byHour.put(LocalDateTime.parse(h.getDate() + " " + h.getHour(), HOUR_FMT), h.getRainfall());
                } catch (Exception ignored) {
                    // 日期/小时格式异常的行跳过
                }
            }
        } catch (Exception e) {
            log.warn("防洪抗旱：天气预测降雨获取失败，模型按无雨预测：{}", e.getMessage());
        }
        return byHour;
    }

    /**
     * 逐小时降雨按日聚合（图表 rainPred 口径），与模型逐小时窗口同源。
     *
     * @param predStart 预测起点下标（= split-1 或 0），仅此之后的日期需要天气数据
     * @return 长度 = dates 的数组；[predStart, predStart+16) 有值或 null（缺失），其余 null
     */
    private Double[] aggregateDailyRain(Map<LocalDateTime, Double> hourlyRain, List<String> dates, int predStart) {
        int n = dates.size();
        Double[] result = new Double[n];
        Map<LocalDate, Double> daily = new HashMap<>();
        for (Map.Entry<LocalDateTime, Double> e : hourlyRain.entrySet()) {
            daily.merge(e.getKey().toLocalDate(), e.getValue(), Double::sum);
        }
        int maxIdx = Math.min(n, predStart + MAX_FORECAST_DAYS);
        for (int i = Math.max(0, predStart); i < maxIdx; i++) {
            Double v = daily.get(LocalDate.parse(dates.get(i), DATE_FMT));
            result[i] = v == null ? null : round1(v);
        }
        return result;
    }

    /** 天气坐标：配置优先，未配置回退雨量站经纬度；均无则 null。 */
    private Double[] resolveWeatherCoord(String rainStcd) {
        Double lon = parseDouble(weatherLon);
        Double lat = parseDouble(weatherLat);
        if (lon != null && lat != null) {
            return new Double[]{lon, lat};
        }
        if (rainStcd != null) {
            FloodDroughtMapper.SiteCoord coord = mapper.selectStationCoord(rainStcd);
            if (coord != null && coord.getLon() != null && coord.getLat() != null) {
                return new Double[]{coord.getLon(), coord.getLat()};
            }
        }
        return null;
    }

    // ==================== 站点解析与参数校验 ====================

    /** 配置优先；未配置按站点类型自动选站。 */
    private String resolveStcd(String configured, String typeCode) {
        if (configured != null) {
            return configured;
        }
        return mapper.selectStationByType(typeCode);
    }

    private LocalDate[] parseRange(HydroSubmitRequest req) {
        if (req == null || req.getStartDate() == null || req.getStartDate().trim().isEmpty()) {
            throw new IllegalArgumentException("起始日期不能为空（格式 YYYY-MM-DD）");
        }
        if (req.getEndDate() == null || req.getEndDate().trim().isEmpty()) {
            throw new IllegalArgumentException("截止日期不能为空（格式 YYYY-MM-DD）");
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(req.getStartDate().trim(), DATE_FMT);
            end = LocalDate.parse(req.getEndDate().trim(), DATE_FMT);
        } catch (Exception e) {
            throw new IllegalArgumentException("日期格式错误，应为 YYYY-MM-DD");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("截止日期不得早于起始日期");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > maxRangeDays) {
            throw new IllegalArgumentException("统计区间不得超过 " + maxRangeDays + " 天");
        }
        return new LocalDate[]{start, end};
    }

    // ==================== Redis 中间态 ====================

    private String keyOf(String id) {
        return KEY_PREFIX + id;
    }

    /** 读取任务中间态；Redis 不可达/损坏时降级视为不存在（上层 404）。 */
    private HydroTaskVO readTask(String id) {
        try {
            String json = redisTemplate.opsForValue().get(keyOf(id));
            return json == null ? null : objectMapper.readValue(json, HydroTaskVO.class);
        } catch (Exception e) {
            log.warn("防洪抗旱任务状态读取失败 id={}: {}", id, e.getMessage());
            return null;
        }
    }

    /** 写入任务中间态（TTL 24h）；Redis 不可达抛异常，提交方直接感知失败。 */
    private void saveTask(HydroTaskVO task) {
        try {
            redisTemplate.opsForValue().set(keyOf(task.getId()),
                    objectMapper.writeValueAsString(task), cacheTtlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            throw new IllegalStateException("任务状态存储失败: " + e.getMessage());
        }
    }

    /** 异步执行中的状态回写：失败仅记日志，不中断已完成的编排。 */
    private void saveTaskQuietly(HydroTaskVO task) {
        try {
            saveTask(task);
        } catch (Exception e) {
            log.error("防洪抗旱任务状态回写失败 id={}: {}", task.getId(), e.getMessage());
        }
    }

    /** 查同区间去重键（返回已存在的任务 ID）；Redis 异常视为未命中（去重失效但主链路可用）。 */
    private String getDedupId(String dedupKey) {
        try {
            return redisTemplate.opsForValue().get(dedupKey);
        } catch (Exception e) {
            log.warn("防洪抗旱去重键读取失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 原子占位同区间去重键（SET NX + TTL，与任务 TTL 一致）：
     * 成功返回 true（本请求为主提交者）；键已存在返回 false（并发提交复用已有任务）。
     * Redis 不可达时降级返回 true（去重失效但主链路可用）。
     */
    private boolean saveDedupIdIfAbsent(String dedupKey, String id) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(dedupKey, id, cacheTtlHours, TimeUnit.HOURS);
            return acquired == null || acquired;
        } catch (Exception e) {
            log.warn("防洪抗旱去重键占位失败（降级允许提交）: {}", e.getMessage());
            return true;
        }
    }

    /** 清除同区间去重键（任务失败后允许重试）；失败仅记日志。 */
    private void deleteDedupId(String dedupKey) {
        try {
            redisTemplate.delete(dedupKey);
        } catch (Exception e) {
            log.warn("防洪抗旱去重键清除失败: {}", e.getMessage());
        }
    }

    // ==================== 工具 ====================

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String truncate(String msg) {
        String text = msg == null ? "未知错误" : msg;
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private static Double parseDouble(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
