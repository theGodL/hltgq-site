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
 * Open-Meteo Seasonal API（EC46）客户端（`weather.seasonal.*`，默认 https://seasonal-api.open-meteo.com/v1）。
 * <p>承担 N1 的 B 段（第 17~40 天延伸期趋势）：返回 50 个成员的逐日数据，
 * 由 {@code WeatherDailyService} 跨成员聚合（方案 §3.3），<b>本类只负责取数与解析，不做聚合</b>。
 * <p>与 {@link WeatherOpenMeteoClient} 分开的原因：端点不同（`/seasonal`）、模型参数不同（`models=ecmwf_ec46`）、
 * 超时更宽（全变量响应约 92 KB，读取默认 10 s，方案 §3.5）。
 * <p>任何失败统一抛 {@link WeatherCallException}，由 service 降级（不抛 5xx）。
 */
@Component
public class WeatherLongRangeClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherLongRangeClient.class);

    /** 响应日志截断长度：EC46 全变量约 92 KB，仅留头部供字段契约核对 */
    private static final int RESPONSE_LOG_LIMIT = 800;

    /**
     * B 段日级变量（方案 §2.4 实测 7 变量全部可用）。
     * <p>不含 `weather_code` / `precipitation_probability_max`：EC46 不提供概率字段，
     * 概率由成员降水比例推导、天气码由「概率 + 雨量 + 云量」三维度推导（方案 §3.3）。
     */
    private static final String DAILY_VARIABLES = "temperature_2m_max,temperature_2m_min,precipitation_sum,"
            + "cloud_cover_mean,wind_speed_10m_max,wind_direction_10m_dominant,relative_humidity_2m_mean";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String model;
    private final ObjectMapper objectMapper;

    public WeatherLongRangeClient(@Value("${weather.seasonal.base-url:https://seasonal-api.open-meteo.com/v1}") String baseUrl,
                                  @Value("${weather.seasonal.model:ecmwf_ec46}") String model,
                                  @Value("${weather.long-range.connect-timeout-ms:3000}") int connectTimeoutMs,
                                  @Value("${weather.long-range.read-timeout-ms:10000}") int readTimeoutMs,
                                  ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * B 段日级预报：返回响应根节点（成员数据在 `daily` 节点下，字段形如
     * `precipitation_sum` / `precipitation_sum_member01` ~ `_member50`）。
     *
     * @param forecastDays 上游请求天数（配置 `weather.seasonal.forecast-days`，默认 46，覆盖 17~40 天需求）
     */
    public JsonNode seasonalDaily(double lon, double lat, int forecastDays) {
        String url = baseUrl + "/seasonal?models=" + model
                + "&latitude=" + lat + "&longitude=" + lon
                + "&daily=" + DAILY_VARIABLES
                + "&timezone=Asia/Shanghai&forecast_days=" + forecastDays;
        return getJson(url);
    }

    private JsonNode getJson(String url) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            String body = response.getBody();
            JsonNode node = objectMapper.readTree(body);
            log.info("seasonal(EC46) response: {}", summarize(body));
            return node;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new WeatherCallException("Seasonal API HTTP 错误: " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new WeatherCallException("Seasonal API 不可达或超时: " + rootMessage(e));
        } catch (Exception e) {
            throw new WeatherCallException("解析 Seasonal API 响应失败: " + e.getMessage());
        }
    }

    /** 响应日志截断：EC46 响应体较大，仅输出前 N 字符 */
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
