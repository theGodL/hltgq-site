package com.qgyun.hltgq.hltgqsite.weather.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.weather.client.WeatherCallException;
import com.qgyun.hltgq.hltgqsite.weather.client.WeatherOpenMeteoClient;
import com.qgyun.hltgq.hltgqsite.weather.support.RateLimiter;
import com.qgyun.hltgq.hltgqsite.weather.support.StaleCacheSupport;
import com.qgyun.hltgq.hltgqsite.weather.support.WeatherConvertUtils;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherCardVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherListItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 天气数据服务：Open-Meteo 代理 + Redis 缓存 + 旧值兜底 + 单飞防击穿 + 限流。
 * <p>缓存：Redis 集群（key=weather:{now|hourly}:{lon},{lat}），信封结构
 * {@code {"updatedAt","pulledAtMs","expireAt","data"}}，新鲜期 10~13 分钟随机抖动；
 * key 级 TTL = 新鲜期 + 旧值上限，使过期值在窗口内仍可读（与 daily/typhoon 同一套支撑）。
 * <p>旧值兜底（stale-while-revalidate）：新鲜期内直接返回；过期但未超旧值上限
 * （卡片 {@code weather.card.stale-max-hours} / 逐小时 {@code weather.hourly.stale-max-hours}）时
 * <b>立即返回旧值并异步后台刷新</b>——上游抖动期间前端看到的是最近一次成功的天气，而不是空数据；
 * 超旧值上限或完全无缓存才同步补拉，补拉失败仍降级为占位（卡片 default、列表空数组），不抛 5xx。
 * <p>单飞：缓存未命中时同 key 并发仅首个请求访问上游，其余等待（默认 3s，超时降级）。
 * <p>限流：注入共享 {@link RateLimiter}（进程内固定窗口，阈值取 `weather.rate-limit.qps`，默认 10 QPS），
 * 超限抛 429。实时/逐小时与日级/雨情/台风<b>共用同一实例</b>；
 * <b>内部消费方</b>（模型计算的降雨输入）走 {@code hourlyWeatherInternal} 不占对外配额，避免两条链路互相挤占。
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private static final String CACHE_KEY_PREFIX = "weather:";
    private static final Random RANDOM = new Random();

    @Autowired
    private WeatherOpenMeteoClient client;

    /** 共享限流器（与日级/雨情/台风同一实例） */
    @Autowired
    private RateLimiter rateLimiter;

    /** stale-while-revalidate 公共支撑（信封缓存 + 单飞 + 异步刷新，与 daily/typhoon 共用） */
    @Autowired
    private StaleCacheSupport cache;

    @Autowired
    private ObjectMapper objectMapper;

    /** 缓存新鲜期基数（分钟） */
    @Value("${weather.cache.ttl-minutes:10}")
    private int cacheTtlMinutes;

    /** 缓存新鲜期随机抖动上限（分钟），实际 TTL = 基数 + [0, 抖动] */
    @Value("${weather.cache.ttl-jitter-minutes:3}")
    private int cacheTtlJitterMinutes;

    /** 单飞等待超时（秒），超过则等待方直接降级 */
    @Value("${weather.single-flight.timeout-seconds:3}")
    private int singleFlightTimeoutSeconds;

    /** 实时卡片旧值上限（小时）：超上限即视为不可用，宁可展示占位 */
    @Value("${weather.card.stale-max-hours:6}")
    private int cardStaleMaxHours;

    /** 逐小时旧值上限（小时）：窗口含过去 7 天历史段，滞后影响小 */
    @Value("${weather.hourly.stale-max-hours:24}")
    private int hourlyStaleMaxHours;

    /** 逐小时拉取历史天数（覆盖前端默认 7 天筛选） */
    @Value("${weather.hourly.past-days:7}")
    private int hourlyPastDays;

    /** 逐小时拉取预报天数（窗口 = past-days + forecast-days，默认 7 + 16，与接口文档同口径） */
    @Value("${weather.hourly.forecast-days:16}")
    private int hourlyForecastDays;

    /** 缺省站点名（请求未传 location 时使用） */
    @Value("${weather.default-location:}")
    private String defaultLocation;

    /**
     * 实时天气（卡片展示）
     * <p>上游抖动时返回最近一次成功的旧值（最多 {@code weather.card.stale-max-hours} 小时）
     * 并后台异步刷新；仅当完全没有可用值（含旧值）时才降级为占位卡片。
     */
    public WeatherCardVO currentWeather(double lon, double lat, String location) {
        rateLimiter.acquire();
        String loc = resolveLocation(location);
        String key = buildCacheKey("now", lon, lat);
        WeatherCardVO card = loadCard(key, lon, lat, loc);
        // 站点名以本次请求为准（缓存 key 不含 location，避免命中他人缓存时显示旧站名）
        card.setLocation(loc);
        return card;
    }

    /**
     * 逐小时天气列表（弹窗展示）：缓存全量窗口数据，按日期范围筛选后重新编号返回
     * <p>与实时卡片同为 stale-while-revalidate（旧值上限 {@code weather.hourly.stale-max-hours} 小时）。
     * <p><b>对外接口</b>：消耗全局限流令牌（超限 429）。
     */
    public List<WeatherListItemVO> hourlyWeather(double lon, double lat,
                                                 String startDate, String endDate, String location) {
        rateLimiter.acquire();
        return loadHourlyWeather(lon, lat, startDate, endDate, location);
    }

    /**
     * 逐小时天气（<b>内部消费方</b>，如模型计算的降雨输入）：<b>不消耗全局限流令牌</b>。
     * <p>原因：内部跑批与前端共用同一限流窗口会互相挤占——模型批量计算会把对外接口打到 429，
     * 而前端流量高时模型调用被限流后，其异常会被上层吞掉（静默按 0 处理），污染模型降雨输入。
     * <p>缓存、stale 兜底、单飞与上游拉取逻辑与对外接口完全一致（共用同一缓存 key）。
     */
    public List<WeatherListItemVO> hourlyWeatherInternal(double lon, double lat,
                                                         String startDate, String endDate) {
        return loadHourlyWeather(lon, lat, startDate, endDate, null);
    }

    /** 逐小时加载主体（不含限流）：对外接口与内部消费方共用，保证两条入口口径一致 */
    private List<WeatherListItemVO> loadHourlyWeather(double lon, double lat,
                                                      String startDate, String endDate, String location) {
        String loc = resolveLocation(location);
        String key = buildCacheKey("hourly", lon, lat);
        List<WeatherListItemVO> full = loadHourly(key, lon, lat, loc);
        for (WeatherListItemVO item : full) {
            item.setLocation(loc);
        }
        return filterByDateRange(full, startDate, endDate);
    }

    // ==================== 旧值兜底 + 单飞 ====================

    /**
     * 实时卡片加载：新鲜 → 旧值兜底 + 后台刷新 → 单飞补拉（失败降级占位）。
     */
    private WeatherCardVO loadCard(String key, double lon, double lat, String loc) {
        long now = StaleCacheSupport.nowMs();
        StaleCacheSupport.Envelope<WeatherCardVO> envelope = cache.read(key, WeatherCardVO.class);
        if (envelope != null && envelope.getData() != null) {
            if (now < envelope.getExpireAt()) {
                return envelope.getData();
            }
            if (!beyondStaleLimit(envelope, now, cardStaleMaxHours)) {
                cache.refreshAsync(key, "实时天气后台刷新", () -> pullCard(key, lon, lat, loc));
                return envelope.getData();
            }
        }
        return cache.singleFlight(key, "实时天气", () -> pullCard(key, lon, lat, loc),
                defaultCard(loc), singleFlightTimeoutSeconds);
    }

    /**
     * 逐小时加载：判定同实时卡片；缓存内容为空列表时按未命中处理。
     */
    private List<WeatherListItemVO> loadHourly(String key, double lon, double lat, String loc) {
        JavaType listType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, WeatherListItemVO.class);
        long now = StaleCacheSupport.nowMs();
        StaleCacheSupport.Envelope<List<WeatherListItemVO>> envelope = cache.read(key, listType);
        if (envelope != null && envelope.getData() != null && !envelope.getData().isEmpty()) {
            if (now < envelope.getExpireAt()) {
                return envelope.getData();
            }
            if (!beyondStaleLimit(envelope, now, hourlyStaleMaxHours)) {
                cache.refreshAsync(key, "逐小时天气后台刷新", () -> pullHourly(key, lon, lat, loc));
                return envelope.getData();
            }
        }
        return cache.singleFlight(key, "逐小时天气", () -> pullHourly(key, lon, lat, loc),
                Collections.emptyList(), singleFlightTimeoutSeconds);
    }

    /** 拉取实时卡片并写回（miss 补拉与 stale 后台刷新共用；失败抛 WeatherCallException 由单飞兜底） */
    private WeatherCardVO pullCard(String key, double lon, double lat, String loc) {
        WeatherCardVO card = buildCard(lon, lat, loc);
        cache.write(key, card, freshTtlSeconds(), staleMaxSeconds(cardStaleMaxHours));
        return card;
    }

    /** 拉取逐小时并写回；解析结果为空时不写缓存（视为异常，交由上层降级） */
    private List<WeatherListItemVO> pullHourly(String key, double lon, double lat, String loc) {
        List<WeatherListItemVO> list = buildHourly(lon, lat, loc);
        if (list.isEmpty()) {
            log.warn("逐小时天气解析结果为空 key={}", key);
            return list;
        }
        cache.write(key, list, freshTtlSeconds(), staleMaxSeconds(hourlyStaleMaxHours));
        return list;
    }

    /** 新鲜期（秒）：基数 + 随机抖动，避免多坐标集体失效 */
    private long freshTtlSeconds() {
        return (cacheTtlMinutes + RANDOM.nextInt(cacheTtlJitterMinutes + 1)) * 60L;
    }

    private long staleMaxSeconds(int hours) {
        return hours * 3600L;
    }

    /**
     * 是否已超旧值上限：以拉取时刻为准；信封缺失该字段（异常/历史数据）时
     * 退化为「过期时长超过上限」判定，宁可少用旧值。
     */
    private boolean beyondStaleLimit(StaleCacheSupport.Envelope<?> envelope, long now, int staleMaxHours) {
        long staleMaxMs = staleMaxSeconds(staleMaxHours) * 1000L;
        if (envelope.getPulledAtMs() > 0) {
            return now - envelope.getPulledAtMs() > staleMaxMs;
        }
        return now - envelope.getExpireAt() > staleMaxMs;
    }

    // ==================== Open-Meteo 响应转换 ====================

    private WeatherCardVO buildCard(double lon, double lat, String location) {
        JsonNode data = client.current(lon, lat);
        JsonNode current = data == null ? null : data.get("current");
        if (current == null || current.isNull()) {
            throw new WeatherCallException("响应缺少 current 节点");
        }
        // 字段安全取值：缺失/JSON null/非数值一律返回 null——缺失值不得当作 0，否则会伪造「0℃ 晴朗」
        Double temp = doubleField(current, "temperature_2m");
        Double feelTemp = doubleField(current, "apparent_temperature");
        Integer weatherCode = intField(current, "weather_code");
        if (temp == null || feelTemp == null || weatherCode == null) {
            throw new WeatherCallException("current 节点关键字段缺失或非数值");
        }

        WeatherCardVO vo = new WeatherCardVO();
        vo.setTemperature(String.format(Locale.US, "%.0f°C", temp));
        vo.setWeatherDesc(WeatherConvertUtils.translateWeatherCode(weatherCode));
        vo.setFeelTemperature(String.format(Locale.US, "体感温度 %.0f°", feelTemp));
        vo.setWeatherIcon(String.valueOf(weatherCode));
        vo.setLocation(location);
        vo.setUpdateTime(textField(current, "time"));
        vo.setSource("openmeteo");
        return vo;
    }

    private List<WeatherListItemVO> buildHourly(double lon, double lat, String location) {
        JsonNode data = client.hourly(lon, lat, hourlyPastDays, hourlyForecastDays);
        JsonNode hourly = data == null ? null : data.get("hourly");
        if (hourly == null || hourly.isNull()) {
            throw new WeatherCallException("响应缺少 hourly 节点");
        }
        JsonNode times = hourly.get("time");
        JsonNode temps = hourly.get("temperature_2m");
        JsonNode humidities = hourly.get("relative_humidity_2m");
        JsonNode precips = hourly.get("precipitation");
        JsonNode codes = hourly.get("weather_code");
        JsonNode windSpeeds = hourly.get("wind_speed_10m");
        JsonNode windDirs = hourly.get("wind_direction_10m");
        if (times == null || temps == null || humidities == null || precips == null
                || codes == null || windSpeeds == null || windDirs == null) {
            throw new WeatherCallException("hourly 节点字段缺失");
        }

        int size = times.size();
        List<WeatherListItemVO> list = new ArrayList<>(size);
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter hourFormat = DateTimeFormatter.ofPattern("HH:mm");
        for (int i = 0; i < size; i++) {
            // 边界安全取值：任一字段缺失/越界只跳过该小时，不因单条异常使整个列表降级为空（与日级同口径）
            String time = textAt(times, i);
            Integer code = intAt(codes, i);
            Double temperature = doubleAt(temps, i);
            Double rainfall = doubleAt(precips, i);
            Double humidity = doubleAt(humidities, i);
            Double windSpeed = doubleAt(windSpeeds, i);
            Integer windDegree = intAt(windDirs, i);
            if (time == null || code == null || temperature == null || rainfall == null
                    || humidity == null || windSpeed == null || windDegree == null) {
                continue;
            }
            LocalDateTime dateTime;
            try {
                dateTime = LocalDateTime.parse(time, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e) {
                continue;
            }

            WeatherListItemVO vo = new WeatherListItemVO();
            vo.setDate(dateTime.format(dateFormat));
            vo.setHour(dateTime.format(hourFormat));
            vo.setLocation(location);
            vo.setWeather(WeatherConvertUtils.translateWeatherCode(code));
            vo.setTemperature((int) Math.round(temperature));
            // 雨量 2 位小数截断（业主口径：雨量 2 位，不四舍五入）
            vo.setRainfall(WeatherConvertUtils.truncateRainfall(rainfall));
            vo.setWindDirection(WeatherConvertUtils.translateWindDirection(windDegree));
            vo.setWindLevel(WeatherConvertUtils.calculateWindLevel(windSpeed));
            vo.setWindSpeed((int) Math.round(windSpeed));
            vo.setHumidity((int) Math.round(humidity));
            vo.setWeatherIcon(String.valueOf(code));
            list.add(vo);
        }

        // 按时间倒序（最新在前），重新编号
        Collections.reverse(list);
        AtomicInteger counter = new AtomicInteger(1);
        for (WeatherListItemVO item : list) {
            item.setId((long) counter.getAndIncrement());
        }
        return list;
    }

    /**
     * 按日期范围筛选并重新编号（深拷贝，避免修改缓存对象）
     * <p>日期字符串 yyyy-MM-dd 字典序即时间序，直接 compareTo 比较；
     * 入参先 trim——带空白的串（如 `" 2026-09-15"`）与 yyyy-MM-dd 字典序失配，会把结果静默筛空。
     */
    private List<WeatherListItemVO> filterByDateRange(List<WeatherListItemVO> list, String startDate, String endDate) {
        if (list.isEmpty()) {
            return list;
        }
        String startText = startDate == null ? null : startDate.trim();
        String endText = endDate == null ? null : endDate.trim();
        if ((startText == null || startText.isEmpty()) && (endText == null || endText.isEmpty())) {
            return list;
        }
        String start = (startText == null || startText.isEmpty()) ? "0000-01-01" : startText;
        String end = (endText == null || endText.isEmpty()) ? "9999-12-31" : endText;

        List<WeatherListItemVO> filtered = new ArrayList<>();
        for (WeatherListItemVO item : list) {
            if (item.getDate() != null
                    && item.getDate().compareTo(start) >= 0
                    && item.getDate().compareTo(end) <= 0) {
                WeatherListItemVO copy = new WeatherListItemVO();
                copy.setDate(item.getDate());
                copy.setHour(item.getHour());
                copy.setLocation(item.getLocation());
                copy.setWeather(item.getWeather());
                copy.setTemperature(item.getTemperature());
                copy.setRainfall(item.getRainfall());
                copy.setWindDirection(item.getWindDirection());
                copy.setWindLevel(item.getWindLevel());
                copy.setWindSpeed(item.getWindSpeed());
                copy.setHumidity(item.getHumidity());
                copy.setWeatherIcon(item.getWeatherIcon());
                filtered.add(copy);
            }
        }

        // 重新编号（AtomicInteger 在循环外递增，保证连续）
        AtomicInteger counter = new AtomicInteger(1);
        for (WeatherListItemVO item : filtered) {
            item.setId((long) counter.getAndIncrement());
        }
        return filtered;
    }

    private WeatherCardVO defaultCard(String location) {
        WeatherCardVO vo = new WeatherCardVO();
        vo.setTemperature("--°C");
        vo.setWeatherDesc("数据暂不可用");
        vo.setFeelTemperature("体感温度 --°");
        vo.setWeatherIcon("");
        vo.setLocation(location);
        vo.setUpdateTime(LocalDateTime.now().toString());
        vo.setSource("default");
        return vo;
    }

    // ==================== 工具方法 ====================

    /** 字段安全取小数（字段名版）：缺失/JSON null/非数值返回 null（不得当作 0） */
    private static Double doubleField(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    /** 字段安全取整数（字段名版）：缺失/JSON null/非数值返回 null */
    private static Integer intField(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    /** 字段安全取字符串（字段名版）：缺失/JSON null/容器节点返回 null（避免出现字符串 "null" 或 ""） */
    private static String textField(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || value.isContainerNode()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isEmpty() ? null : text;
    }

    /** 数组下标取字符串：越界/空值返回 null（上游数组长度不一致时跳过单条，而非整段失败） */
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

    /** 数组下标取小数：越界/非数值返回 null（null 不得当作 0，否则会伪造缺失数据） */
    private static Double doubleAt(JsonNode array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonNode node = array.get(index);
        return node != null && node.isNumber() ? node.asDouble() : null;
    }

    /** 数组下标取整数：越界/非数值返回 null */
    private static Integer intAt(JsonNode array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonNode node = array.get(index);
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private String resolveLocation(String location) {
        return (location == null || location.trim().isEmpty()) ? defaultLocation : location.trim();
    }

    private String buildCacheKey(String type, double lon, double lat) {
        // 坐标归一 6 位小数：同站点微小误差共用缓存；Locale.US 避免小数点为逗号的 Locale 破坏 key 格式
        return CACHE_KEY_PREFIX + type + ":" + String.format(Locale.US, "%.6f,%.6f", lon, lat);
    }

}
