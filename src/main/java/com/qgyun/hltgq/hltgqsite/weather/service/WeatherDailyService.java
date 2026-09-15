package com.qgyun.hltgq.hltgqsite.weather.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.qgyun.hltgq.hltgqsite.weather.client.WeatherCallException;
import com.qgyun.hltgq.hltgqsite.weather.client.WeatherLongRangeClient;
import com.qgyun.hltgq.hltgqsite.weather.client.WeatherOpenMeteoClient;
import com.qgyun.hltgq.hltgqsite.weather.support.RateLimiter;
import com.qgyun.hltgq.hltgqsite.weather.support.StaleCacheSupport;
import com.qgyun.hltgq.hltgqsite.weather.support.WeatherConvertUtils;
import com.qgyun.hltgq.hltgqsite.weather.support.WeatherExecutors;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherDailyCacheVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherDailyListVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherDailyVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * N1 未来 40 天日级预报服务（方案 §3）。
 * <p><b>两段</b>：A 段 1~16 天（Forecast API，`accuracy=exact`，概率取上游预计算值）；
 * B 段 17~40 天（Seasonal EC46 50 成员聚合，`accuracy=extended`，概率由成员降水比例推导）。
 * <p><b>按段缓存</b>（方案 §3.4 规则二，复审 A6）：key=`weather:daily:{lon},{lat}`（不含 days），
 * 每段各记 `pulledAt/expireAt`，<b>无顶层过期时钟</b>；写回只动本次所拉的段。
 * <p><b>读取流程</b>（段级判定）：各段齐备且 fresh → 合并去重升序截取；
 * 齐备但有 stale → 立即返回（新+旧混合）+ 仅对 stale 段后台异步刷新；
 * 有缺失段（从未拉过、列表为空或超旧值上限 24h）→ 按 miss 同步补拉，失败 → 返回可用段 + `degradedSegments`；
 * 全不可得 → `source=default` + 空 list（不抛 5xx）。
 * <p><b>两条硬约束</b>：① 超旧值上限的段一律视为不可用——即便补拉失败也不得回退展示过期数据（方案 §7.1）；
 * ② `days ≤ {@code weather.daily.exact-days}` 的请求只含 A 段，B 段数据不在请求范围内。
 * <p><b>并行</b>：两段用 `weather-upstream` 命名池并行（严禁 commonPool，评审 A5）。
 */
@Service
public class WeatherDailyService {

    private static final Logger log = LoggerFactory.getLogger(WeatherDailyService.class);

    /** 段名（与方案 §3.4 缓存结构一致） */
    public static final String SEGMENT_EXACT = "exact";
    public static final String SEGMENT_EXTENDED = "extended";

    private static final String CACHE_KEY_PREFIX = "weather:daily:";
    private static final String SOURCE_OPENMETEO = "openmeteo";
    private static final String SOURCE_DEFAULT = "default";

    /** 合并后最大天数（40 天 = A 段 16 + B 段 24） */
    private static final int MAX_FORECAST_DAYS = 40;

    /** EC46 成员上限（实测 50，留余量以免上游加成员后漏读） */
    private static final int MAX_MEMBERS = 60;

    /** 段拉取的兜底等待上限（秒）：上游 connect 3s + read 10s 最坏约 13s，留余量 */
    private static final int SEGMENT_WAIT_SECONDS = 15;

    /** 中文星期（下标 = DayOfWeek.getValue() - 1） */
    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    /** 写回分段锁：避免两段并发写回时互相覆盖（16 把锁按 key 散列，避免锁表无限增长） */
    private final Object[] writeLocks = new Object[16];

    private final Random random = new Random();

    @Autowired
    private WeatherOpenMeteoClient forecastClient;

    @Autowired
    private WeatherLongRangeClient longRangeClient;

    @Autowired
    private StaleCacheSupport cache;

    @Autowired
    private WeatherExecutors executors;

    @Autowired
    private RateLimiter rateLimiter;

    /** 默认/最大返回天数 */
    @Value("${weather.daily.days:40}")
    private int dailyDays;

    /** A 段天数（Forecast API 硬上限 16），也是 B 段的起点 */
    @Value("${weather.daily.exact-days:16}")
    private int exactSegmentDays;

    /** 段新鲜期 TTL（分钟） */
    @Value("${weather.daily.cache-ttl-minutes:180}")
    private int segmentTtlMinutes;

    /** 段 TTL 抖动上限（分钟），各段独立随机 */
    @Value("${weather.daily.cache-jitter-minutes:30}")
    private int segmentJitterMinutes;

    /** 旧值上限（小时）：超过则按缺失处理并同步补拉 */
    @Value("${weather.daily.stale-max-hours:24}")
    private int staleMaxHours;

    /** B 段上游请求天数（EC46 实测 45 天有效） */
    @Value("${weather.seasonal.forecast-days:46}")
    private int seasonalForecastDays;

    /** 成员降水阈值（mm）：推导 B 段降水概率用 */
    @Value("${weather.rain-trend.min-rain-threshold:0.1}")
    private double minRainThreshold;

    /**
     * 补段单飞等待超时（秒）：等待方需等最慢段的上游返回（EC46 约 2~5 s），故独立于
     * 实时卡片口径（{@code weather.single-flight.timeout-seconds}），避免并发冷启动时等待方直接降级为空。
     */
    @Value("${weather.daily.single-flight-wait-seconds:8}")
    private int singleFlightWaitSeconds;

    /** 缺省站点名 */
    @Value("${weather.default-location:}")
    private String defaultLocation;

    {
        for (int i = 0; i < writeLocks.length; i++) {
            writeLocks[i] = new Object();
        }
    }

    /**
     * 日级预报（对外入口）。
     *
     * @param days 返回天数，null/越界按 1~{@code weather.daily.days} 归一（&gt;40 按 40 处理）
     */
    public WeatherDailyListVO daily(double lon, double lat, Integer days, String location) {
        rateLimiter.acquire();
        return loadDaily(lon, lat, normalizeDays(days), resolveLocation(location));
    }

    /**
     * 预热（P3a）：按默认天数走一次完整读取流程，段齐备且新鲜时<b>不访问上游</b>。
     * <p>刻意不做限流（由任务自身节流），失败仅记日志。
     */
    public void warmUp(double lon, double lat) {
        try {
            WeatherDailyListVO vo = loadDaily(lon, lat, normalizeDays(null), resolveLocation(null));
            log.info("日级预报预热完成: 坐标=({},{}) 天数={} 来源={} 缺失段={}",
                    lon, lat, vo.getDays(), vo.getSource(), vo.getDegradedSegments());
        } catch (Exception e) {
            log.warn("日级预报预热失败: 坐标=({},{}) {}", lon, lat, e.getMessage());
        }
    }

    // ==================== 读取主流程（段级判定） ====================

    private WeatherDailyListVO loadDaily(double lon, double lat, int days, String location) {
        String key = buildCacheKey(lon, lat);
        boolean needExtended = days > exactSegmentDays;
        long now = StaleCacheSupport.nowMs();

        WeatherDailyCacheVO cacheVo = cache.readRaw(key, WeatherDailyCacheVO.class);

        List<String> needed = needExtended
                ? Arrays.asList(SEGMENT_EXACT, SEGMENT_EXTENDED)
                : Collections.singletonList(SEGMENT_EXACT);
        List<String> missing = new ArrayList<>();
        List<String> staleSegments = new ArrayList<>();
        for (String segment : needed) {
            WeatherDailyCacheVO.Segment current = segment(cacheVo, segment);
            if (!usable(current, now)) {
                // 从未拉过 / 列表为空 / 旧值已超上限：一律按缺失处理并同步补拉（方案 §3.4、§7.1）
                missing.add(segment);
            } else if (!current.fresh(now)) {
                staleSegments.add(segment);
            }
        }

        if (!missing.isEmpty()) {
            WeatherDailyCacheVO snapshot = cacheVo;
            WeatherDailyCacheVO merged = cache.singleFlight(key, "日级预报补段",
                    () -> refill(key, snapshot, missing, lon, lat), snapshot, singleFlightWaitSeconds);
            if (merged != null) {
                cacheVo = merged;
            }
        }

        for (String segment : staleSegments) {
            // 仅对仍未刷新的 stale 段后台刷新；段级 key 保证同一段不重复提交，
            // 与补段路径之间的重复拉取由 pullSegments 的「拉取前复查」兜底（方案 §7.1 刷新去重）
            cache.refreshAsync(key + ":refresh:" + segment, "日级预报后台刷新",
                    () -> refreshSegment(key, segment, lon, lat));
        }

        // 只取可用段：超旧值上限的段一律视为空——补拉失败时不得把过期数据当有效值返回（方案 §7.1）
        List<WeatherDailyVO> exactList = usableList(cacheVo, SEGMENT_EXACT, now);
        // days ≤ exact-days 的请求只含 A 段：B 段数据（第 17 天起）不在请求范围内，不得混入
        List<WeatherDailyVO> extendedList = needExtended
                ? usableList(cacheVo, SEGMENT_EXTENDED, now)
                : Collections.emptyList();

        List<String> degraded = new ArrayList<>();
        if (exactList.isEmpty()) {
            degraded.add(SEGMENT_EXACT);
        }
        if (needExtended && extendedList.isEmpty()) {
            degraded.add(SEGMENT_EXTENDED);
        }

        List<WeatherDailyVO> list = merge(exactList, extendedList, days);
        WeatherDailyListVO vo = new WeatherDailyListVO();
        vo.setLocation(location);
        vo.setSource(list.isEmpty() ? SOURCE_DEFAULT : SOURCE_OPENMETEO);
        vo.setUpdatedAt(cacheVo == null || cacheVo.getUpdatedAt() == null
                ? LocalDateTime.now().toString() : cacheVo.getUpdatedAt());
        vo.setDays(list.size());
        vo.setExactDays(countExact(list));
        vo.setDegradedSegments(degraded);
        vo.setList(list);
        return vo;
    }

    /** 同步补拉缺失段并写回（单飞内层；无写回时重读 Redis，最后才退回调用前快照） */
    private WeatherDailyCacheVO refill(String key, WeatherDailyCacheVO base, List<String> missing, double lon, double lat) {
        Map<String, WeatherDailyCacheVO.Segment> pulled = pullSegments(key, missing, lon, lat);
        WeatherDailyCacheVO latest = null;
        for (Map.Entry<String, WeatherDailyCacheVO.Segment> entry : pulled.entrySet()) {
            latest = writeSegment(key, entry.getKey(), entry.getValue());
        }
        if (latest != null) {
            return latest;
        }
        // 本次未写回任何段：可能全部失败，也可能是并发流程已抢先刷新（拉取前复查跳过）——
        // 重读一次取最新状态，避免把「调用前的快照」当成现状（超限段由 usableList 过滤，不会因此返回过期数据）
        WeatherDailyCacheVO current = cache.readRaw(key, WeatherDailyCacheVO.class);
        if (current != null) {
            return current;
        }
        log.warn("日级预报补段未取得数据 key={} 缺失段={}（返回已有段）", key, missing);
        return base;
    }

    /** 后台刷新单段（stale 场景；写回只动该段） */
    private void refreshSegment(String key, String segment, double lon, double lat) {
        Map<String, WeatherDailyCacheVO.Segment> pulled =
                pullSegments(key, Collections.singletonList(segment), lon, lat);
        WeatherDailyCacheVO.Segment refreshed = pulled.get(segment);
        if (refreshed == null) {
            throw new WeatherCallException("段刷新失败: " + segment);
        }
        writeSegment(key, segment, refreshed);
        log.info("日级预报段刷新完成 key={} 段={} 天数={}", key, segment, refreshed.getList().size());
    }

    /**
     * 并行拉取指定段（命名线程池；单段失败不影响其他段）。
     * <p>拉取前复查：并发的补段与后台刷新可能已刷新同一段，复查命中即跳过，避免重复打上游（方案 §7.1 刷新去重）。
     */
    private Map<String, WeatherDailyCacheVO.Segment> pullSegments(String key, List<String> segments, double lon, double lat) {
        WeatherDailyCacheVO current = cache.readRaw(key, WeatherDailyCacheVO.class);
        long now = StaleCacheSupport.nowMs();
        List<String> pending = new ArrayList<>(segments.size());
        for (String segment : segments) {
            WeatherDailyCacheVO.Segment existing = segment(current, segment);
            if (existing != null && existing.getList() != null && !existing.getList().isEmpty()
                    && existing.fresh(now)) {
                log.info("日级预报段已由其他流程刷新，跳过本次拉取 key={} 段={}", key, segment);
                continue;
            }
            pending.add(segment);
        }
        if (pending.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<CompletableFuture<WeatherDailyCacheVO.Segment>> futures = new ArrayList<>(pending.size());
        for (String segment : pending) {
            futures.add(CompletableFuture.supplyAsync(() -> safePull(segment, lon, lat), executors.upstream()));
        }
        Map<String, WeatherDailyCacheVO.Segment> pulled = new LinkedHashMap<>();
        for (int i = 0; i < pending.size(); i++) {
            String segment = pending.get(i);
            try {
                WeatherDailyCacheVO.Segment result = futures.get(i).get(SEGMENT_WAIT_SECONDS, TimeUnit.SECONDS);
                if (result != null) {
                    pulled.put(segment, result);
                }
            } catch (Exception e) {
                log.warn("日级预报段拉取失败 段={}: {}", segment, e.getMessage());
            }
        }
        return pulled;
    }

    /** 拉取单段并组装为段对象；失败返回 null（由上层置 degradedSegments，不抛 5xx） */
    private WeatherDailyCacheVO.Segment safePull(String segment, double lon, double lat) {
        try {
            List<WeatherDailyVO> list = SEGMENT_EXACT.equals(segment)
                    ? parseExact(lon, lat) : aggregateExtended(lon, lat);
            if (list.isEmpty()) {
                log.warn("日级预报段解析结果为空 段={}", segment);
                return null;
            }
            long nowMs = StaleCacheSupport.nowMs();
            WeatherDailyCacheVO.Segment result = new WeatherDailyCacheVO.Segment();
            result.setPulledAt(LocalDateTime.now().toString());
            result.setPulledAtMs(nowMs);
            result.setExpireAt(nowMs + segmentTtlMillis());
            result.setList(list);
            return result;
        } catch (WeatherCallException e) {
            log.warn("日级预报段上游失败 段={}: {}", segment, e.getMessage());
            return null;
        } catch (RuntimeException e) {
            // 上游结构意外变化（解析异常）：该段降级，不影响另一段
            log.error("日级预报段解析异常 段=" + segment, e);
            return null;
        }
    }

    /** 写回单段：只更新该段，其余段从 Redis 现值保留（方案 §3.4：互不干扰） */
    private WeatherDailyCacheVO writeSegment(String key, String segment, WeatherDailyCacheVO.Segment value) {
        synchronized (lockFor(key)) {
            WeatherDailyCacheVO current = cache.readRaw(key, WeatherDailyCacheVO.class);
            if (current == null) {
                current = new WeatherDailyCacheVO();
            }
            current.setUpdatedAt(LocalDateTime.now().toString());
            current.getSegments().put(segment, value);
            cache.writeRaw(key, current, redisTtlSeconds(current));
            return current;
        }
    }

    // ==================== A 段（Forecast 1~16 天） ====================

    private List<WeatherDailyVO> parseExact(double lon, double lat) {
        JsonNode root = forecastClient.daily(lon, lat, exactSegmentDays);
        JsonNode daily = root == null ? null : root.get("daily");
        if (daily == null || daily.isNull()) {
            throw new WeatherCallException("响应缺少 daily 节点（A 段）");
        }
        JsonNode times = daily.get("time");
        JsonNode tempMaxes = daily.get("temperature_2m_max");
        JsonNode tempMins = daily.get("temperature_2m_min");
        JsonNode precips = daily.get("precipitation_sum");
        JsonNode probabilities = daily.get("precipitation_probability_max");
        JsonNode codes = daily.get("weather_code");
        JsonNode windSpeeds = daily.get("wind_speed_10m_max");
        JsonNode windDirs = daily.get("wind_direction_10m_dominant");
        JsonNode humidities = daily.get("relative_humidity_2m_mean");
        if (times == null || tempMaxes == null || tempMins == null || precips == null
                || probabilities == null || codes == null || windSpeeds == null
                || windDirs == null || humidities == null) {
            throw new WeatherCallException("daily 节点字段缺失（A 段）");
        }

        int size = times.size();
        List<WeatherDailyVO> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String date = textAt(times, i);
            if (date == null) {
                continue;
            }
            Integer code = intAt(codes, i);
            Double rainfall = doubleAt(precips, i);
            Double windSpeed = doubleAt(windSpeeds, i);
            Integer windDegree = intAt(windDirs, i);

            WeatherDailyVO vo = new WeatherDailyVO();
            vo.setDate(date);
            vo.setWeekday(weekdayOf(date));
            vo.setWeather(code == null ? null : WeatherConvertUtils.translateWeatherCode(code));
            vo.setWeatherIcon(code == null ? null : String.valueOf(code));
            vo.setTempMax(roundInt(doubleAt(tempMaxes, i)));
            vo.setTempMin(roundInt(doubleAt(tempMins, i)));
            vo.setRainfall(rainfall == null ? null : WeatherConvertUtils.truncateRainfall(rainfall));
            vo.setRainProbability(intAt(probabilities, i));
            vo.setHumidity(roundInt(doubleAt(humidities, i)));
            vo.setWindDirection(windDegree == null ? null : WeatherConvertUtils.translateWindDirection(windDegree));
            vo.setWindLevel(windSpeed == null ? null : WeatherConvertUtils.calculateWindLevel(windSpeed));
            vo.setWindSpeed(roundInt(windSpeed));
            vo.setAccuracy(SEGMENT_EXACT);
            list.add(vo);
        }
        log.info("日级 A 段(Forecast) 解析完成: 请求天数={} 解析天数={} 起始={}",
                exactSegmentDays, list.size(), list.isEmpty() ? "-" : list.get(0).getDate());
        return list;
    }

    // ==================== B 段（EC46 成员聚合） ====================

    /** B 段聚合用的成员变量（与 {@code WeatherLongRangeClient} 请求的 daily 变量一致） */
    private static final String[] EXTENDED_VARIABLES = {
            "temperature_2m_max", "temperature_2m_min", "precipitation_sum",
            "relative_humidity_2m_mean", "wind_speed_10m_max",
            "wind_direction_10m_dominant", "cloud_cover_mean"};

    /**
     * B 段：EC46 50 成员跨成员聚合（方案 §3.3）。
     * <p>null 成员不计入分母；有效成员数为 0 时该字段返回 null；仅保留第 17~40 天（B 段职责范围）。
     */
    private List<WeatherDailyVO> aggregateExtended(double lon, double lat) {
        JsonNode root = longRangeClient.seasonalDaily(lon, lat, seasonalForecastDays);
        JsonNode daily = root == null ? null : root.get("daily");
        if (daily == null || daily.isNull()) {
            throw new WeatherCallException("响应缺少 daily 节点（B 段）");
        }
        JsonNode times = daily.get("time");
        if (times == null || times.isNull() || times.size() == 0) {
            throw new WeatherCallException("daily.time 缺失（B 段）");
        }

        LocalDate from = LocalDate.now().plusDays(exactSegmentDays);
        LocalDate to = LocalDate.now().plusDays(MAX_FORECAST_DAYS - 1L);

        // 各变量的可用成员数只需探测一次（按日期复用）
        Map<String, Integer> memberCounts = new HashMap<>();
        for (String variable : EXTENDED_VARIABLES) {
            memberCounts.put(variable, memberCount(daily, variable));
        }
        int rainMembers = memberCounts.get("precipitation_sum");

        int size = times.size();
        List<WeatherDailyVO> list = new ArrayList<>();
        StringBuilder memberLog = new StringBuilder();
        for (int i = 0; i < size; i++) {
            String date = textAt(times, i);
            if (date == null) {
                continue;
            }
            LocalDate day;
            try {
                day = LocalDate.parse(date);
            } catch (Exception e) {
                continue;
            }
            if (day.isBefore(from) || day.isAfter(to)) {
                continue;
            }

            Double tempMax = memberMean(daily, "temperature_2m_max", i, memberCounts.get("temperature_2m_max"));
            Double tempMin = memberMean(daily, "temperature_2m_min", i, memberCounts.get("temperature_2m_min"));
            Double rainfall = memberMean(daily, "precipitation_sum", i, rainMembers);
            Double humidity = memberMean(daily, "relative_humidity_2m_mean", i, memberCounts.get("relative_humidity_2m_mean"));
            Double windSpeed = memberMean(daily, "wind_speed_10m_max", i, memberCounts.get("wind_speed_10m_max"));
            Double windDegree = memberMean(daily, "wind_direction_10m_dominant", i, memberCounts.get("wind_direction_10m_dominant"));
            Double cloudCover = memberMean(daily, "cloud_cover_mean", i, memberCounts.get("cloud_cover_mean"));
            Integer probability = memberRainProbability(daily, "precipitation_sum", i, rainMembers);
            int validMembers = validMemberCount(daily, "precipitation_sum", i, rainMembers);

            Double rainfallTruncated = rainfall == null ? null : WeatherConvertUtils.truncateRainfall(rainfall);
            int weatherCode = WeatherConvertUtils.deriveWeatherCode(probability, rainfallTruncated, cloudCover);

            WeatherDailyVO vo = new WeatherDailyVO();
            vo.setDate(date);
            vo.setWeekday(weekdayOf(date));
            vo.setWeather(WeatherConvertUtils.translateWeatherCode(weatherCode));
            vo.setWeatherIcon(String.valueOf(weatherCode));
            vo.setTempMax(roundInt(tempMax));
            vo.setTempMin(roundInt(tempMin));
            vo.setRainfall(rainfallTruncated);
            vo.setRainProbability(probability);
            vo.setHumidity(roundInt(humidity));
            vo.setWindDirection(windDegree == null
                    ? null : WeatherConvertUtils.translateWindDirection((int) Math.round(windDegree)));
            vo.setWindLevel(windSpeed == null ? null : WeatherConvertUtils.calculateWindLevel(windSpeed));
            vo.setWindSpeed(roundInt(windSpeed));
            vo.setAccuracy(SEGMENT_EXTENDED);
            list.add(vo);

            if (memberLog.length() > 0) {
                memberLog.append(',');
            }
            memberLog.append(date).append(':').append(validMembers).append('/').append(rainMembers);
        }
        log.info("日级 B 段(EC46) 成员聚合完成: 天数={} 起始={} 有效成员数(日期:有效/可用)={}",
                list.size(), list.isEmpty() ? "-" : list.get(0).getDate(), memberLog);
        return list;
    }

    /** 成员变量可用的成员个数（探测 `_member01`…；EC46 实测 50） */
    private int memberCount(JsonNode daily, String variable) {
        int count = 0;
        for (int k = 1; k <= MAX_MEMBERS; k++) {
            if (memberArray(daily, variable, k) == null) {
                break;
            }
            count = k;
        }
        return count;
    }

    /** 成员数组节点：兼容 `var_member01` 与 `var_member1` 两种命名 */
    private JsonNode memberArray(JsonNode daily, String variable, int index) {
        JsonNode padded = daily.get(variable + "_member" + (index < 10 ? "0" + index : String.valueOf(index)));
        return padded != null ? padded : daily.get(variable + "_member" + index);
    }

    /** 跨成员均值（null 成员不计入分母；有效成员数为 0 返回 null） */
    private Double memberMean(JsonNode daily, String variable, int dayIndex, int members) {
        double sum = 0D;
        int valid = 0;
        for (int k = 1; k <= members; k++) {
            Double value = doubleAt(memberArray(daily, variable, k), dayIndex);
            if (value != null) {
                sum += value;
                valid++;
            }
        }
        return valid == 0 ? null : sum / valid;
    }

    /** 成员降水比例 → 概率（%，四舍五入）；有效成员数为 0 返回 null */
    private Integer memberRainProbability(JsonNode daily, String variable, int dayIndex, int members) {
        int valid = 0;
        int rainy = 0;
        for (int k = 1; k <= members; k++) {
            Double value = doubleAt(memberArray(daily, variable, k), dayIndex);
            if (value == null) {
                continue;
            }
            valid++;
            if (value >= minRainThreshold) {
                rainy++;
            }
        }
        return valid == 0 ? null : (int) Math.round(rainy * 100.0D / valid);
    }

    /** 有效成员数（非 null），用于响应日志核对样本量（方案 §3.3 / §8.4） */
    private int validMemberCount(JsonNode daily, String variable, int dayIndex, int members) {
        int valid = 0;
        for (int k = 1; k <= members; k++) {
            if (doubleAt(memberArray(daily, variable, k), dayIndex) != null) {
                valid++;
            }
        }
        return valid;
    }

    // ==================== 合并 / 截取 ====================

    /**
     * 合并两段（方案 §3.4 规则三）：以 `date` 为主键对齐，`exact` 优先；
     * 只保留今天（含）之后；缺失日期不补行；按日期升序截取前 days 天。
     */
    private List<WeatherDailyVO> merge(List<WeatherDailyVO> exactList, List<WeatherDailyVO> extendedList, int days) {
        Map<String, WeatherDailyVO> byDate = new HashMap<>();
        for (WeatherDailyVO vo : extendedList) {
            if (vo.getDate() != null) {
                byDate.put(vo.getDate(), vo);
            }
        }
        for (WeatherDailyVO vo : exactList) {
            if (vo.getDate() != null) {
                byDate.put(vo.getDate(), vo);
            }
        }

        String today = LocalDate.now().toString();
        List<WeatherDailyVO> merged = new ArrayList<>();
        for (WeatherDailyVO vo : byDate.values()) {
            // yyyy-MM-dd 字典序即时间序
            if (vo.getDate().compareTo(today) >= 0) {
                merged.add(vo);
            }
        }
        merged.sort(Comparator.comparing(WeatherDailyVO::getDate));
        return merged.size() > days ? new ArrayList<>(merged.subList(0, days)) : merged;
    }

    private int countExact(List<WeatherDailyVO> list) {
        int count = 0;
        for (WeatherDailyVO vo : list) {
            if (SEGMENT_EXACT.equals(vo.getAccuracy())) {
                count++;
            }
        }
        return count;
    }

    // ==================== 工具方法 ====================

    private int normalizeDays(Integer days) {
        int max = Math.max(1, Math.min(MAX_FORECAST_DAYS, dailyDays));
        if (days == null) {
            return max;
        }
        return Math.max(1, Math.min(max, days));
    }

    private String resolveLocation(String location) {
        return (location == null || location.trim().isEmpty()) ? defaultLocation : location.trim();
    }

    private String buildCacheKey(double lon, double lat) {
        // 坐标归一 6 位小数（与现有 weather 缓存口径一致）；Locale.US 避免小数点被本地化
        return CACHE_KEY_PREFIX + String.format(Locale.US, "%.6f,%.6f", lon, lat);
    }

    /** 段 TTL（含独立抖动），避免多坐标、多段集体失效 */
    private long segmentTtlMillis() {
        long minutes = segmentTtlMinutes + random.nextInt(Math.max(0, segmentJitterMinutes) + 1);
        return minutes * 60_000L;
    }

    private long staleMaxMillis() {
        return staleMaxHours * 3600_000L;
    }

    /** key 级 TTL = max(各段 expireAt) + 旧值上限（方案 §3.4，避免某段先被淘汰） */
    private long redisTtlSeconds(WeatherDailyCacheVO cacheVo) {
        long maxExpireAt = StaleCacheSupport.nowMs();
        for (WeatherDailyCacheVO.Segment segment : cacheVo.getSegments().values()) {
            if (segment != null && segment.getExpireAt() > maxExpireAt) {
                maxExpireAt = segment.getExpireAt();
            }
        }
        long ttlSeconds = (maxExpireAt - StaleCacheSupport.nowMs()) / 1000L + staleMaxHours * 3600L;
        return Math.max(60L, ttlSeconds);
    }

    private Object lockFor(String key) {
        return writeLocks[Math.abs(key.hashCode() % writeLocks.length)];
    }

    private WeatherDailyCacheVO.Segment segment(WeatherDailyCacheVO cacheVo, String name) {
        return cacheVo == null || cacheVo.getSegments() == null ? null : cacheVo.getSegments().get(name);
    }

    /**
     * 取「可用段」列表：不存在 / 列表为空 / 旧值已超上限（{@code weather.daily.stale-max-hours}）
     * 一律返回空列表——超限旧值不得作为有效数据返回（方案 §7.1），由调用方置 {@code degradedSegments}。
     */
    private List<WeatherDailyVO> usableList(WeatherDailyCacheVO cacheVo, String name, long now) {
        WeatherDailyCacheVO.Segment segment = segment(cacheVo, name);
        return usable(segment, now) ? segment.getList() : Collections.emptyList();
    }

    /** 段是否可用：存在、列表非空，且「新鲜 或 未超旧值上限（stale 但可兜底）」 */
    private boolean usable(WeatherDailyCacheVO.Segment segment, long now) {
        if (segment == null || segment.getList() == null || segment.getList().isEmpty()) {
            return false;
        }
        return segment.fresh(now) || !beyondStaleLimit(segment, now);
    }

    /**
     * 段是否已超旧值上限：以 {@code pulledAtMs} 为准；该字段缺失（异常/历史数据）时退化为
     * 「过期时长超过上限」判定，宁可少用旧值（与实时卡片口径一致）。
     */
    private boolean beyondStaleLimit(WeatherDailyCacheVO.Segment segment, long now) {
        long staleMaxMs = staleMaxMillis();
        long reference = segment.getPulledAtMs() > 0 ? segment.getPulledAtMs() : segment.getExpireAt();
        return now - reference > staleMaxMs;
    }

    /** 日期 → 中文星期（解析失败返回 null，不让单项异常影响整段） */
    private String weekdayOf(String date) {
        try {
            return WEEKDAYS[LocalDate.parse(date).getDayOfWeek().getValue() - 1];
        } catch (Exception e) {
            return null;
        }
    }

    private static String textAt(JsonNode array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonNode node = array.get(index);
        if (node == null || node.isNull() || node.isContainerNode()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isEmpty() ? null : text;
    }

    /** 数组下标取小数：越界/JSON null/非数值返回 null（文本节点不得经 asDouble() 变成 0，与 current/hourly 同口径） */
    private static Double doubleAt(JsonNode array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonNode node = array.get(index);
        return node != null && node.isNumber() ? node.asDouble() : null;
    }

    /** 数组下标取整数：越界/JSON null/非数值返回 null */
    private static Integer intAt(JsonNode array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonNode node = array.get(index);
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private static Integer roundInt(Double value) {
        return value == null ? null : (int) Math.round(value);
    }
}
