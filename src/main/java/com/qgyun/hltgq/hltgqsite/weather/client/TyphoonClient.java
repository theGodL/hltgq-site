package com.qgyun.hltgq.hltgqsite.weather.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

/**
 * 中央气象台台风网（NMC）客户端（`typhoon.nmc.*`）。
 * <p>非官方文档化 API：响应为 <b>JSONP</b> 且数据以<b>数组下标</b>给出（方案 §2.6），
 * 故本类只做「JSONP 剥离 + JSON 解析」，下标取值与字段含义由 {@code TyphoonService} 负责（方案 §5.4 解析容错）。
 * <p>两个细节：
 * <ul>
 *   <li>响应体按 <b>UTF-8 显式解码</b>（NMC 的 Content-Type 不带 charset，用 String 接收会被按 ISO-8859-1 解析导致中文乱码）；</li>
 *   <li>携带常规 User-Agent（默认的 Java/1.8 容易被前置防护拦截）。</li>
 * </ul>
 * 任何失败统一抛 {@link WeatherCallException}，由 service 降级（不抛 5xx）。
 */
@Component
public class TyphoonClient {

    private static final Logger log = LoggerFactory.getLogger(TyphoonClient.class);

    /** 响应日志截断长度：列表约 1.8 KB、详情约 3 KB */
    private static final int RESPONSE_LOG_LIMIT = 1000;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) HltgqSiteWeather/1.0";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public TyphoonClient(@Value("${typhoon.nmc.base-url:https://typhoon.nmc.cn/weatherservice/typhoon/jsons}") String baseUrl,
                         @Value("${typhoon.connect-timeout-ms:3000}") int connectTimeoutMs,
                         @Value("${typhoon.read-timeout-ms:8000}") int readTimeoutMs,
                         ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 台风列表：返回根节点（含 `typhoonList`，元素为数组，下标含义见方案 §2.6）。
     */
    public JsonNode listDefault() {
        return getJsonpJson(baseUrl + "/list_default");
    }

    /**
     * 单个台风详情（实况轨迹 + 预报路径）：返回根节点（含 `typhoon`）。
     * <p>调用方须先校验 id 为纯数字（避免路径注入），本类不做业务校验。
     */
    public JsonNode view(String typhoonId) {
        return getJsonpJson(baseUrl + "/view_" + typhoonId);
    }

    private JsonNode getJsonpJson(String url) {
        String body = getBody(url);
        String payload = unwrapJsonp(body);
        try {
            JsonNode node = objectMapper.readTree(payload);
            log.info("nmc response: {}", summarize(payload));
            return node;
        } catch (Exception e) {
            throw new WeatherCallException("解析 NMC 响应失败: " + e.getMessage());
        }
    }

    /**
     * JSONP 剥离（方案 §5.4）：`typhoon_jsons_xxx({...})` → `{...}`。
     * <p>额外容错一层：若剥离后仍被括号包裹（上游偶发 `(({...}))`），继续剥离，避免整段解析失败。
     */
    private String unwrapJsonp(String body) {
        if (body == null || body.trim().isEmpty()) {
            throw new WeatherCallException("NMC 响应为空");
        }
        int start = body.indexOf('(');
        int end = body.lastIndexOf(')');
        if (start < 0 || end <= start) {
            throw new WeatherCallException("NMC 响应非 JSONP 格式（缺少括号包裹）");
        }
        String payload = body.substring(start + 1, end).trim();
        while (!payload.startsWith("{") && payload.length() > 1
                && payload.charAt(0) == '(' && payload.endsWith(")")) {
            payload = payload.substring(1, payload.length() - 1).trim();
        }
        return payload;
    }

    /** 取响应体：按字节接收后显式 UTF-8 解码，规避无 charset 时的中文乱码 */
    private String getBody(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ACCEPT, "application/javascript,text/javascript,application/json,*/*");
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), byte[].class);
            byte[] body = response.getBody();
            return body == null ? "" : new String(body, StandardCharsets.UTF_8);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new WeatherCallException("NMC HTTP 错误: " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new WeatherCallException("NMC 不可达或超时: " + rootMessage(e));
        } catch (Exception e) {
            throw new WeatherCallException("NMC 请求失败: " + e.getMessage());
        }
    }

    /** 响应日志截断：仅输出前 N 字符，兼顾字段契约核对与日志量控制 */
    private String summarize(String payload) {
        if (payload == null) {
            return "null";
        }
        if (payload.length() <= RESPONSE_LOG_LIMIT) {
            return payload;
        }
        return payload.substring(0, RESPONSE_LOG_LIMIT) + "...(截断" + payload.length() + "字符)";
    }

    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
