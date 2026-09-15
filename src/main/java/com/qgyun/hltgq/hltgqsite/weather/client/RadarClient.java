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
 * RainViewer 雷达客户端（`radar.*`，方案 §4）。
 * <p>两个上游：
 * <ul>
 *   <li><b>帧列表</b> `https://api.rainviewer.com/public/weather-maps.json`：返回
 *       {@code {version, generated, host, radar:{past:[{time,path}], nowcast:[]}, satellite:{infrared:[]}}}
 *       —— `time` 为 epoch 秒、`path` 形如 `/v2/radar/4f4ae5d6985d`；</li>
 *   <li><b>瓦片</b> `{radar.tile.host}{path}/256/{z}/{x}/{y}/4/1_1.png`（size / 色板 / 平滑参数固定，
 *       不接受客户端自定义）。</li>
 * </ul>
 * <p><b>host 只取配置、绝不采用上游响应里的 host</b>（复审 R2）：否则 path + host 双外部输入构成 SSRF 面；
 * 上游报告的 host 仅由上层打日志对照，用于发现上游漂移。
 * <p>任何失败统一抛 {@link WeatherCallException}，由 service 降级（帧列表空数组 / 瓦片 404），不抛 5xx。
 */
@Component
public class RadarClient {

    private static final Logger log = LoggerFactory.getLogger(RadarClient.class);

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) HltgqSiteWeather/1.0";

    /** 瓦片尺寸（上游固定 256） */
    private static final String TILE_SIZE = "256";

    /** 平滑与降雪参数（上游固定 1_1） */
    private static final String TILE_SMOOTH_SNOW = "1_1";

    /** 色板（上游仅保留 Universal Blue，非配置项，方案 §4.1/§8.3） */
    private static final String TILE_COLOR = "4";

    private final RestTemplate restTemplate;
    private final String framesUrl;
    private final String tileHost;
    private final ObjectMapper objectMapper;

    public RadarClient(@Value("${radar.frames-url:https://api.rainviewer.com/public/weather-maps.json}") String framesUrl,
                       @Value("${radar.tile.host:https://tilecache.rainviewer.com}") String tileHost,
                       @Value("${radar.connect-timeout-ms:3000}") int connectTimeoutMs,
                       @Value("${radar.read-timeout-ms:8000}") int readTimeoutMs,
                       ObjectMapper objectMapper) {
        this.framesUrl = framesUrl;
        this.tileHost = tileHost.endsWith("/") ? tileHost.substring(0, tileHost.length() - 1) : tileHost;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 帧列表原始响应（weather-maps.json 根节点），字段解析由 {@code RadarService} 负责。
     */
    public JsonNode frames() {
        byte[] body = getBytes(framesUrl, "application/json,*/*");
        try {
            String text = new String(body, StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(text);
            // 上游报告的 host 只作对照（配置漂移可发现），不参与任何 URL 拼接
            JsonNode host = node.get("host");
            if (host != null && !host.isNull()) {
                log.info("雷达帧列表：上游报告 host={}（配置 radar.tile.host={}）", host.asText(), tileHost);
            }
            return node;
        } catch (Exception e) {
            throw new WeatherCallException("解析雷达帧列表失败: " + e.getMessage());
        }
    }

    /**
     * 拉取单个瓦片（PNG 字节）。
     * <p>调用方须先校验 `path` 与 `z/x/y`（本类不做业务校验，避免绕过服务的入参校验）。
     */
    public byte[] tile(String path, int z, int x, int y) {
        String url = tileHost + path + "/" + TILE_SIZE + "/" + z + "/" + x + "/" + y + "/" + TILE_COLOR + "/" + TILE_SMOOTH_SNOW + ".png";
        return getBytes(url, "image/png,image/*,*/*");
    }

    /** 上游瓦片 host（仅用于日志/文档，不从上游响应动态取） */
    public String tileHost() {
        return tileHost;
    }

    private byte[] getBytes(String url, String accept) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.set(HttpHeaders.ACCEPT, accept);
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), byte[].class);
            byte[] body = response.getBody();
            return body == null ? new byte[0] : body;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new WeatherCallException("雷达 HTTP 错误: " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new WeatherCallException("雷达不可达或超时: " + rootMessage(e));
        } catch (Exception e) {
            throw new WeatherCallException("雷达请求失败: " + e.getMessage());
        }
    }

    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
