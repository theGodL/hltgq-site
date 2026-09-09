package com.qgyun.hltgq.hltgqsite.stats.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.auth.SessionContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * hltgq-device 视频巡检统计客户端（site 网关转发 device 内部统计接口，供数据统计大屏采集统计「视频数据」行）。
 * <p>device 统一响应为 {@code {success, code, desc, data}}（与 mq 的 {@code {code, msg, data}} 结构不同），
 * 按 {@code success == true} 判定成功并透传 data 段；失败/不可达抛 {@link DeviceStatsCallException} → HTTP 502。
 * <p>鉴权：device 与 site 共用 APaaS 平台会话（同 Redis 会话体系），device 统计接口需登录校验（无管理员角色要求），
 * 出站请求自动透传当前登录会话（X-Session-Id 头）；非请求线程（定时任务等）无会话可透传时不携带。
 */
@Component
public class DeviceStatsClient {

    private static final Logger log = LoggerFactory.getLogger(DeviceStatsClient.class);

    private static final String PATH_VIDEO_PATROL_STATS = "/api/dahua/video/patrol-stats";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final SessionContextService sessionContextService;

    public DeviceStatsClient(@Value("${device.base-url:http://10.68.18.4:18686}") String baseUrl,
                             @Value("${device.connect-timeout-ms:5000}") int connectTimeoutMs,
                             @Value("${device.read-timeout-ms:15000}") int readTimeoutMs,
                             ObjectMapper objectMapper,
                             SessionContextService sessionContextService) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        this.sessionContextService = sessionContextService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /** 当日视频采集统计：dataType/expected/collected/success/failed/successRate/failRate（与 mq collect-stats 行同构） */
    public JsonNode videoPatrolStats() {
        return get(PATH_VIDEO_PATROL_STATS);
    }

    /** GET 并解析 {success,code,desc,data}：success=true 返回 data 节点，否则抛异常（调用失败/非 200 一并包装） */
    private JsonNode get(String path) {
        try {
            HttpHeaders headers = new HttpHeaders();
            String sessionId = currentSessionId();
            if (StringUtils.hasText(sessionId)) {
                headers.set(SessionContextService.HEADER_SESSION_ID, sessionId);
            }
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(baseUrl + path, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            if (!root.path("success").asBoolean(false)) {
                String code = root.path("code").asText("?");
                String desc = root.path("desc").asText("未知错误");
                log.error("device stats {} business error, code={}: {}", path, code, desc);
                throw new DeviceStatsCallException("数据统计服务返回错误(" + path + "): " + desc);
            }
            return root.path("data");
        } catch (DeviceStatsCallException e) {
            throw e;
        } catch (Exception e) {
            log.error("device stats {} call failed: {}", path, e.getMessage());
            throw new DeviceStatsCallException("数据统计服务调用失败(" + path + "): " + e.getMessage(), e);
        }
    }

    /**
     * 当前登录用户会话 ID（X-Session-Id 透传 device 鉴权）；
     * 非请求线程（定时任务等）无请求上下文时返回 null，出站不携带凭证。
     */
    private String currentSessionId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            return sessionContextService.extractSessionId(request);
        }
        return null;
    }
}
