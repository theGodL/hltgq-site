package com.qgyun.hltgq.hltgqsite.weather.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.weather.client.WeatherCallException;
import com.qgyun.hltgq.hltgqsite.weather.client.WeatherOpenMeteoClient;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherCardVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherListItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 天气数据服务：Open-Meteo 代理 + Redis 缓存 + 单飞防击穿 + 限流。
 * <p>缓存：Redis 集群（key=weather:{now|hourly}:{lon},{lat}），TTL 10~13 分钟随机抖动，
 * 避免多坐标集体失效；Redis 不可达时自动降级为直连上游（读/写均 catch，不影响主链路）。
 * <p>单飞：缓存未命中时同 key 并发仅首个请求访问上游，其余等待（默认 3s，超时降级）。
 * <p>限流：进程内固定窗口计数器（默认 10 QPS），超限抛 429。
 * <p>失败策略：天气为非关键旁路数据，上游失败返回降级数据（卡片 default、列表空数组），
 * 不向上抛 5xx，保证大屏页面常驻可用。
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private static final String CACHE_KEY_PREFIX = "weather:";
    private static final Random RANDOM = new Random();

    @Autowired
    private WeatherOpenMeteoClient client;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /** 缓存 TTL 基数（分钟） */
    @Value("${weather.cache.ttl-minutes:10}")
    private int cacheTtlMinutes;

    /** 缓存 TTL 随机抖动上限（分钟），实际 TTL = 基数 + [0, 抖动] */
    @Value("${weather.cache.ttl-jitter-minutes:3}")
    private int cacheTtlJitterMinutes;

    /** 单飞等待超时（秒），超过则等待方直接降级 */
    @Value("${weather.single-flight.timeout-seconds:3}")
    private int singleFlightTimeoutSeconds;

    /** 限流阈值（QPS） */
    @Value("${weather.rate-limit.qps:10}")
    private int rateLimitQps;

    /** 逐小时拉取历史天数（覆盖前端默认 7 天筛选） */
    @Value("${weather.hourly.past-days:7}")
    private int hourlyPastDays;

    /** 逐小时拉取预报天数 */
    @Value("${weather.hourly.forecast-days:3}")
    private int hourlyForecastDays;

    /** 缺省站点名（请求未传 location 时使用） */
    @Value("${weather.default-location:}")
    private String defaultLocation;

    /** 单飞进行中请求：key → 首个请求的 CompletableFuture */
    private final ConcurrentHashMap<String, CompletableFuture<?>> pendingRequests = new ConcurrentHashMap<>();

    /** 限流窗口起始毫秒（固定窗口 1s） */
    private final AtomicLong rateWindowStartMs = new AtomicLong(System.currentTimeMillis());

    /** 当前窗口计数 */
    private final AtomicInteger rateCounter = new AtomicInteger();

    /**
     * 实时天气（卡片展示）
     */
    public WeatherCardVO currentWeather(double lon, double lat, String location) {
        checkRateLimit();
        String loc = resolveLocation(location);
        String key = buildCacheKey("now", lon, lat);
        return load(key, WeatherCardVO.class, () -> buildCard(lon, lat, loc), defaultCard(loc));
    }

    /**
     * 逐小时天气列表（弹窗展示）：缓存全量窗口数据，按日期范围筛选后重新编号返回
     */
    public List<WeatherListItemVO> hourlyWeather(double lon, double lat,
                                                 String startDate, String endDate, String location) {
        checkRateLimit();
        String loc = resolveLocation(location);
        String key = buildCacheKey("hourly", lon, lat);
        List<WeatherListItemVO> full = loadList(key, WeatherListItemVO.class,
                () -> buildHourly(lon, lat, loc), Collections.emptyList());
        return filterByDateRange(full, startDate, endDate);
    }

    // ==================== 缓存 + 单飞 ====================

    /**
     * 通用加载：缓存命中直接返回；未命中走单飞（同 key 并发仅首个请求访问上游）。
     * 上游失败时降级 fallback（不写缓存，下次请求重试），等待方同步拿到降级值而非超时。
     */
    @SuppressWarnings("unchecked")
    private <T> T load(String key, Class<T> clazz, Supplier<T> loader, T fallback) {
        T cached = readFromCache(key, clazz);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        CompletableFuture<?> existing = pendingRequests.putIfAbsent(key, future);
        if (existing != null) {
            try {
                return (T) existing.get(singleFlightTimeoutSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                return fallback;
            }
        }
        try {
            T result;
            try {
                result = loader.get();
                writeToCache(key, result);
            } catch (WeatherCallException e) {
                log.warn("获取天气数据失败 key={}: {}", key, e.getMessage());
                result = fallback;
            } catch (RuntimeException e) {
                // 上游数据格式意外变化（解析/转换异常）：旁路数据降级兜底，不抛 5xx
                log.error("获取天气数据异常 key={}", key, e);
                result = fallback;
            }
            future.complete(result);
            return result;
        } finally {
            pendingRequests.remove(key);
        }
    }

    /** 列表版通用加载（缓存反序列化为 List） */
    @SuppressWarnings("unchecked")
    private <T> List<T> loadList(String key, Class<T> clazz, Supplier<List<T>> loader, List<T> fallback) {
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
        List<T> cached = readListFromCache(key, listType);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<List<T>> future = new CompletableFuture<>();
        CompletableFuture<?> existing = pendingRequests.putIfAbsent(key, future);
        if (existing != null) {
            try {
                return (List<T>) existing.get(singleFlightTimeoutSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                return fallback;
            }
        }
        try {
            List<T> result;
            try {
                result = loader.get();
                writeToCache(key, result);
            } catch (WeatherCallException e) {
                log.warn("获取天气数据失败 key={}: {}", key, e.getMessage());
                result = fallback;
            } catch (RuntimeException e) {
                // 上游数据格式意外变化（解析/转换异常）：旁路数据降级兜底，不抛 5xx
                log.error("获取天气数据异常 key={}", key, e);
                result = fallback;
            }
            future.complete(result);
            return result;
        } finally {
            pendingRequests.remove(key);
        }
    }

    private <T> T readFromCache(String key, Class<T> clazz) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            return objectMapper.readValue(value, clazz);
        } catch (Exception e) {
            // Redis 不可达或数据损坏：降级为缓存未命中，直连上游
            log.warn("缓存读取失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private <T> List<T> readListFromCache(String key, JavaType listType) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            return objectMapper.readValue(value, listType);
        } catch (Exception e) {
            log.warn("缓存读取失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeToCache(String key, Object value) {
        try {
            long ttlMinutes = cacheTtlMinutes + RANDOM.nextInt(cacheTtlJitterMinutes + 1);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 写缓存失败不影响主链路（仅影响下一次命中率）
            log.warn("缓存写入失败 key={}: {}", key, e.getMessage());
        }
    }

    // ==================== Open-Meteo 响应转换 ====================

    private WeatherCardVO buildCard(double lon, double lat, String location) {
        JsonNode data = client.current(lon, lat);
        JsonNode current = data == null ? null : data.get("current");
        if (current == null || current.isNull()) {
            throw new WeatherCallException("响应缺少 current 节点");
        }
        JsonNode tempNode = current.get("temperature_2m");
        JsonNode feelNode = current.get("apparent_temperature");
        JsonNode codeNode = current.get("weather_code");
        if (tempNode == null || feelNode == null || codeNode == null) {
            throw new WeatherCallException("current 节点字段缺失");
        }
        double temp = tempNode.asDouble();
        double feelTemp = feelNode.asDouble();
        int weatherCode = codeNode.asInt();

        WeatherCardVO vo = new WeatherCardVO();
        vo.setTemperature(String.format(Locale.US, "%.0f°C", temp));
        vo.setWeatherDesc(translateWeatherCode(weatherCode));
        vo.setFeelTemperature(String.format(Locale.US, "体感温度 %.0f°", feelTemp));
        vo.setWeatherIcon(String.valueOf(weatherCode));
        vo.setLocation(location);
        vo.setUpdateTime(current.path("time").asText(null));
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
            LocalDateTime dateTime = LocalDateTime.parse(times.get(i).asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            int code = codes.get(i).asInt();

            WeatherListItemVO vo = new WeatherListItemVO();
            vo.setDate(dateTime.format(dateFormat));
            vo.setHour(dateTime.format(hourFormat));
            vo.setLocation(location);
            vo.setWeather(translateWeatherCode(code));
            vo.setTemperature((int) Math.round(temps.get(i).asDouble()));
            vo.setRainfall(precips.get(i).asDouble());
            vo.setWindDirection(translateWindDirection(windDirs.get(i).asInt()));
            vo.setWindLevel(calculateWindLevel(windSpeeds.get(i).asDouble()));
            vo.setWindSpeed((int) Math.round(windSpeeds.get(i).asDouble()));
            vo.setHumidity((int) Math.round(humidities.get(i).asDouble()));
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
     * <p>日期字符串 yyyy-MM-dd 字典序即时间序，直接 compareTo 比较。
     */
    private List<WeatherListItemVO> filterByDateRange(List<WeatherListItemVO> list, String startDate, String endDate) {
        if (list.isEmpty()) {
            return list;
        }
        if ((startDate == null || startDate.isEmpty()) && (endDate == null || endDate.isEmpty())) {
            return list;
        }
        String start = (startDate == null || startDate.isEmpty()) ? "0000-01-01" : startDate;
        String end = (endDate == null || endDate.isEmpty()) ? "9999-12-31" : endDate;

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

    private String resolveLocation(String location) {
        return (location == null || location.trim().isEmpty()) ? defaultLocation : location.trim();
    }

    private String buildCacheKey(String type, double lon, double lat) {
        // 坐标归一 6 位小数：同站点微小误差共用缓存；Locale.US 避免小数点为逗号的 Locale 破坏 key 格式
        return CACHE_KEY_PREFIX + type + ":" + String.format(Locale.US, "%.6f,%.6f", lon, lat);
    }

    /** 固定窗口限流（1s 窗口，阈值 rateLimitQps），超限抛 429 */
    private void checkRateLimit() {
        long now = System.currentTimeMillis();
        while (true) {
            long windowStart = rateWindowStartMs.get();
            if (now - windowStart >= 1000) {
                if (rateWindowStartMs.compareAndSet(windowStart, now)) {
                    rateCounter.set(1);
                    return;
                }
            } else {
                if (rateCounter.incrementAndGet() <= rateLimitQps) {
                    return;
                }
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁");
            }
        }
    }

    // WMO 天气代码 → 中文描述（全量 28 项，含方案文档 1.3 节未列出的代码）
    private static final Map<Integer, String> WEATHER_CODE_MAP = new HashMap<>();

    static {
        WEATHER_CODE_MAP.put(0, "晴朗");
        WEATHER_CODE_MAP.put(1, "主要晴朗");
        WEATHER_CODE_MAP.put(2, "多云");
        WEATHER_CODE_MAP.put(3, "阴天");
        WEATHER_CODE_MAP.put(45, "雾");
        WEATHER_CODE_MAP.put(48, "雾凇");
        WEATHER_CODE_MAP.put(51, "毛毛雨");
        WEATHER_CODE_MAP.put(53, "中度毛毛雨");
        WEATHER_CODE_MAP.put(55, "强毛毛雨");
        WEATHER_CODE_MAP.put(56, "冻雨");
        WEATHER_CODE_MAP.put(57, "强冻雨");
        WEATHER_CODE_MAP.put(61, "小雨");
        WEATHER_CODE_MAP.put(63, "中雨");
        WEATHER_CODE_MAP.put(65, "大雨");
        WEATHER_CODE_MAP.put(66, "冻雨");
        WEATHER_CODE_MAP.put(67, "强冻雨");
        WEATHER_CODE_MAP.put(71, "小雪");
        WEATHER_CODE_MAP.put(73, "中雪");
        WEATHER_CODE_MAP.put(75, "大雪");
        WEATHER_CODE_MAP.put(77, "雪粒");
        WEATHER_CODE_MAP.put(80, "阵雨");
        WEATHER_CODE_MAP.put(81, "中度阵雨");
        WEATHER_CODE_MAP.put(82, "强阵雨");
        WEATHER_CODE_MAP.put(85, "阵雪");
        WEATHER_CODE_MAP.put(86, "强阵雪");
        WEATHER_CODE_MAP.put(95, "雷雨");
        WEATHER_CODE_MAP.put(96, "雷雨伴冰雹");
        WEATHER_CODE_MAP.put(99, "强雷雨伴冰雹");
    }

    private String translateWeatherCode(int code) {
        return WEATHER_CODE_MAP.getOrDefault(code, "未知");
    }

    /** 16 方位风向 */
    private static final String[] WIND_DIRECTIONS = {"北", "北东北", "东北", "东东北", "东", "东东南", "东南", "南东南",
            "南", "南西南", "西南", "西西南", "西", "西西北", "西北", "北西北"};

    private String translateWindDirection(int degree) {
        int index = (int) Math.round(degree / 22.5) % 16;
        return WIND_DIRECTIONS[index] + "风";
    }

    /** 风速(km/h) → 蒲福风级 0~12 */
    private Integer calculateWindLevel(double speedKmh) {
        if (speedKmh < 1) return 0;
        if (speedKmh < 6) return 1;
        if (speedKmh < 12) return 2;
        if (speedKmh < 20) return 3;
        if (speedKmh < 29) return 4;
        if (speedKmh < 39) return 5;
        if (speedKmh < 50) return 6;
        if (speedKmh < 62) return 7;
        if (speedKmh < 75) return 8;
        if (speedKmh < 89) return 9;
        if (speedKmh < 103) return 10;
        if (speedKmh < 118) return 11;
        return 12;
    }
}
