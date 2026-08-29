package com.qgyun.hltgq.hltgqsite.archive.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 浩微档案系统客户端（archive.base-url，默认 http://10.68.18.12:8090）。
 * <p>统一解析响应约定 {rc,msg,data}：rc=200 成功，非 200 抛 {@link ArchiveCallException}。
 * <p>Token 5 分钟有效，内存缓存复用，超时（默认 270 秒）自动重新获取；
 * 网络异常/5xx 重试 3 次间隔 3 秒。
 */
@Component
public class ArchiveClient {

    private static final Logger log = LoggerFactory.getLogger(ArchiveClient.class);

    private static final String PATH_GET_TOKEN = "/archive/autoForm/getToken";
    private static final String PATH_SYNC_DEPT = "/archive/sync/organizationByDept";
    private static final String PATH_SYNC_USER = "/archive/sync/organizationByUser";

    private static final int MAX_RETRY = 3;
    private static final long RETRY_INTERVAL_MS = 3000L;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String clientId;
    private final long tokenValidMillis;

    /** Token 缓存：非空且未过有效期则复用 */
    private volatile String cachedToken;
    private volatile long tokenObtainedAt;

    public ArchiveClient(@Value("${archive.base-url}") String baseUrl,
                         @Value("${archive.client-id}") String clientId,
                         @Value("${archive.token-valid-seconds:270}") long tokenValidSeconds,
                         @Value("${archive.connect-timeout-ms:5000}") int connectTimeoutMs,
                         @Value("${archive.read-timeout-ms:30000}") int readTimeoutMs,
                         ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.tokenValidMillis = tokenValidSeconds * 1000L;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /** 获取 Token：缓存未过期则复用，否则重新获取 */
    public String getToken() {
        String token = cachedToken;
        if (token != null && System.currentTimeMillis() - tokenObtainedAt < tokenValidMillis) {
            return token;
        }
        synchronized (this) {
            token = cachedToken;
            if (token != null && System.currentTimeMillis() - tokenObtainedAt < tokenValidMillis) {
                return token;
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("clientid", clientId);
            JsonNode node = postForm(PATH_GET_TOKEN, form);
            String newToken = node.path("data").path("token").asText();
            if (newToken == null || newToken.isEmpty()) {
                throw new ArchiveCallException("200", "获取Token失败：响应中无 token 字段");
            }
            cachedToken = newToken;
            tokenObtainedAt = System.currentTimeMillis();
            log.info("archive getToken success");
            return newToken;
        }
    }

    /** 强制失效缓存的 Token（同步接口返回鉴权错误时调用，下次重新获取） */
    public void invalidateToken() {
        cachedToken = null;
    }

    /** 同步组织架构：rows 按 dpLv 升序（父部门先于子部门） */
    public void syncDepts(String token, List<Map<String, Object>> rows) {
        Map<String, Object> body = buildRowsBody(token, rows);
        postJson(PATH_SYNC_DEPT, body);
        log.info("archive syncDepts success, rows={}", rows.size());
    }

    /** 同步用户基础信息 */
    public void syncUsers(String token, List<Map<String, Object>> rows) {
        Map<String, Object> body = buildRowsBody(token, rows);
        postJson(PATH_SYNC_USER, body);
        log.info("archive syncUsers success, rows={}", rows.size());
    }

    private Map<String, Object> buildRowsBody(String token, List<Map<String, Object>> rows) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("token", token);
        body.put("rows", rows);
        return body;
    }

    /** POST form-urlencoded，重试 3 次，rc=200 才返回 */
    private JsonNode postForm(String path, MultiValueMap<String, String> form) {
        return execute(path, () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + path, new HttpEntity<>(form, headers), String.class);
            return response.getBody();
        });
    }

    /** POST JSON，重试 3 次，rc=200 才返回；请求入参打印一次（含 token，截断），供三方排查契约核对 */
    private JsonNode postJson(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new ArchiveCallException("-1", "序列化请求体失败(" + path + "): " + e.getMessage());
        }
        log.info("archive {} request: {}", path, summarize(jsonBody));
        return execute(path, () -> {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + path, new HttpEntity<>(jsonBody, headers), String.class);
            return response.getBody();
        });
    }

    /** 请求日志截断：超长输出「前 N 字符 + 尾 M 字符」，兼顾头部字段结构与尾部行数据 */
    private String summarize(String body) {
        if (body == null) {
            return "null";
        }
        int limit = 4000;
        int tail = 3000;
        if (body.length() <= limit + tail) {
            return body;
        }
        int tailLen = Math.min(tail, body.length() - limit);
        return body.substring(0, limit)
                + "...(截断" + body.length() + "字符)..."
                + body.substring(body.length() - tailLen);
    }

    /** 统一重试与响应解析：网络异常/5xx 重试，业务 rc!=200 直接抛异常 */
    private JsonNode execute(String path, HttpCall call) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                String body = call.invoke();
                JsonNode node = objectMapper.readTree(body);
                String rc = node.path("rc").asText();
                if ("200".equals(rc)) {
                    return node;
                }
                throw new ArchiveCallException(rc,
                        "档案系统返回错误(" + path + "): " + node.path("msg").asText("未知错误"));
            } catch (ArchiveCallException e) {
                log.error("archive {} business error, rc={}, attempt={}: {}", path, e.getRc(), attempt, e.getMessage());
                throw e;
            } catch (Exception e) {
                lastError = e;
                log.error("archive {} call failed, attempt={}/{}: {}", path, attempt, MAX_RETRY, e.getMessage());
                if (attempt < MAX_RETRY) {
                    sleepRetry();
                }
            }
        }
        throw new ArchiveCallException("-1", "档案系统调用失败(" + path + ")，已重试" + MAX_RETRY + "次: "
                + (lastError == null ? "未知错误" : lastError.getMessage()));
    }

    private void sleepRetry() {
        try {
            Thread.sleep(RETRY_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface HttpCall {
        String invoke() throws Exception;
    }
}
