package com.qgyun.hltgq.hltgqsite.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 三维系统单点登录服务：封装客户 ssoLogin 接口（POST {base-url}/prod-api/ssoLogin）。
 * <p>入参 username = t_apaas_uc_user.login_name（当前登录人），返回三维系统 token。
 * <p>token 按 loginName 本地缓存（TTL 短于 JWT 有效期），避免高频调用客户接口。
 */
@Service
public class ThreeDimensionalSsoService {

    private static final Logger log = LoggerFactory.getLogger(ThreeDimensionalSsoService.class);

    @Value("${sso3d.base-url}")
    private String baseUrl;

    @Value("${sso3d.login-path:/prod-api/ssoLogin}")
    private String loginPath;

    /** token 本地缓存 TTL（秒），默认 1800，小于三维 JWT 有效期 */
    @Value("${sso3d.token-cache-seconds:1800}")
    private long tokenCacheSeconds;

    /** 三维接口独立 RestTemplate（带超时，不复用全局无超时 Bean） */
    private final RestTemplate ssoRestTemplate;

    private final ObjectMapper objectMapper;

    /** loginName → (token, 过期时间戳) */
    private final ConcurrentHashMap<String, TokenEntry> tokenCache = new ConcurrentHashMap<>();

    @Autowired
    public ThreeDimensionalSsoService(@Value("${sso3d.connect-timeout-ms:3000}") int connectTimeoutMs,
                                      @Value("${sso3d.read-timeout-ms:8000}") int readTimeoutMs,
                                      ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.ssoRestTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
    }

    /**
     * 获取三维系统 token：缓存命中直接返回，未命中调用客户 ssoLogin 接口。
     *
     * @param loginName 当前登录人 login_name
     * @return 三维系统 token
     */
    public String getToken(String loginName) {
        TokenEntry cached = tokenCache.get(loginName);
        if (cached != null && cached.expireAt > System.currentTimeMillis()) {
            return cached.token;
        }

        String token = callSsoLogin(loginName);
        tokenCache.put(loginName, new TokenEntry(token, System.currentTimeMillis() + tokenCacheSeconds * 1000L));
        return token;
    }

    /**
     * 调用客户 ssoLogin 接口，解析 code=200 时返回 token 字段
     */
    private String callSsoLogin(String loginName) {
        String url = baseUrl + loginPath;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("username", loginName);

            log.info("调用三维 SSO 接口 url={}, username={}", url, loginName);
            ResponseEntity<String> resp = ssoRestTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            if (resp.getBody() == null) {
                log.error("三维 SSO 接口返回空 body, url={}", url);
                throw new IllegalStateException("三维 SSO 接口返回空响应");
            }

            JsonNode root = objectMapper.readTree(resp.getBody());
            int code = root.path("code").asInt(-1);
            if (code == 200) {
                String token = root.path("token").asText();
                if (token == null || token.isEmpty()) {
                    log.error("三维 SSO 接口 code=200 但 token 为空, body={}", resp.getBody());
                    throw new IllegalStateException("三维 SSO 接口未返回 token");
                }
                return token;
            }
            String msg = root.path("msg").asText("");
            log.error("三维 SSO 接口返回失败 code={}, msg={}, body={}", code, msg, resp.getBody());
            throw new IllegalStateException("三维 SSO 登录失败：" + (msg.isEmpty() ? "code=" + code : msg));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用三维 SSO 接口异常 url={}, loginName={}", url, loginName, e);
            throw new IllegalStateException("三维 SSO 接口调用异常：" + e.getMessage(), e);
        }
    }

    /**
     * token 本地缓存条目
     */
    private static class TokenEntry {
        final String token;
        final long expireAt;

        TokenEntry(String token, long expireAt) {
            this.token = token;
            this.expireAt = expireAt;
        }
    }
}
