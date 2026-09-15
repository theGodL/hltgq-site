package com.qgyun.hltgq.hltgqsite.weather.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Open-Meteo 天气 API 客户端（openmeteo.base-url，默认 https://api.open-meteo.com/v1）。
 * <p>免费 API 无认证无配额；timezone 固定 Asia/Shanghai，返回北京时间。
 * <p>自建 RestTemplate（JDK 自带 SimpleClientHttpRequestFactory，超时 3s/5s），
 * 与 ModelClient 同模式，不依赖全局 RestTemplateConfig。
 * <p>任何失败统一抛 {@link WeatherCallException}，由 WeatherService 降级。
 */
@Component
public class WeatherOpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherOpenMeteoClient.class);

    /** 响应日志截断长度：避免小时级长数组刷屏 */
    private static final int RESPONSE_LOG_LIMIT = 1500;

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public WeatherOpenMeteoClient(@Value("${openmeteo.base-url:https://api.open-meteo.com/v1}") String baseUrl,
                                  @Value("${openmeteo.connect-timeout-ms:3000}") int connectTimeoutMs,
                                  @Value("${openmeteo.read-timeout-ms:5000}") int readTimeoutMs,
                                  ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 实时天气：返回响应中 current 节点（卡片用）。
     */
    public JsonNode current(double lon, double lat) {
        String url = baseUrl + "/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m"
                + "&timezone=Asia/Shanghai";
        return getJson(url);
    }

    /**
     * 逐小时天气：返回响应中 hourly 节点（列表弹窗用）。
     * <p>窗口 = pastDays 历史天数 + forecastDays 预报天数（当前配置 7 + 16，约 550 条），
     * 前端只能在该窗口内按 startDate/endDate 筛选。
     */
    public JsonNode hourly(double lon, double lat, int pastDays, int forecastDays) {
        String url = baseUrl + "/forecast?latitude=" + lat + "&longitude=" + lon
                + "&hourly=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,wind_direction_10m"
                + "&timezone=Asia/Shanghai"
                + "&past_days=" + pastDays + "&forecast_days=" + forecastDays;
        return getJson(url);
    }

    /**
     * 逐日天气：返回响应中 daily 节点（N1 的 A 段 / 第 1~16 天，方案 §2.2）。
     * <p>含 `precipitation_probability_max`（上游预计算概率），与 B 段的成员比例口径不同（方案 §9.2）。
     * `forecast_days` 上游硬上限 16。
     */
    public JsonNode daily(double lon, double lat, int forecastDays) {
        String url = baseUrl + "/forecast?latitude=" + lat + "&longitude=" + lon
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,"
                + "precipitation_probability_max,weather_code,"
                + "wind_speed_10m_max,wind_direction_10m_dominant,relative_humidity_2m_mean"
                + "&timezone=Asia/Shanghai&forecast_days=" + forecastDays;
        return getJson(url);
    }

    private JsonNode getJson(String url) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            String body = response.getBody();
            JsonNode node = objectMapper.readTree(body);
            // 响应诊断日志：截断输出头部即可核对字段契约（Open-Meteo 响应不落库）
            log.info("openmeteo response: {}", summarize(body));
            return node;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new WeatherCallException("Open-Meteo HTTP 错误: " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new WeatherCallException("Open-Meteo 不可达或超时: " + rootMessage(e));
        } catch (Exception e) {
            throw new WeatherCallException("解析 Open-Meteo 响应失败: " + e.getMessage());
        }
    }

    /** 响应日志截断：仅输出前 N 字符，兼顾字段契约核对与日志量控制 */
    private String summarize(String body) {
        if (body == null) {
            return "null";
        }
        if (body.length() <= RESPONSE_LOG_LIMIT) {
            return body;
        }
        return body.substring(0, RESPONSE_LOG_LIMIT) + "...(截断" + body.length() + "字符)";
    }

    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
