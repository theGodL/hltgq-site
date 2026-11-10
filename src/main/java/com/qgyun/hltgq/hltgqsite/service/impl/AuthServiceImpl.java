package com.qgyun.hltgq.hltgqsite.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.auth.RequestOriginResolver;
import com.qgyun.hltgq.hltgqsite.service.AuthService;
import com.qgyun.hltgq.hltgqsite.vo.AuthResponseVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录鉴权服务实现
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${auth.api.key}")
    private String validKey;

    @Value("${auth.api.secret}")
    private String validSecret;

    /** 平台登录账号（服务端登录代理使用，生产建议用环境变量 AUTH_LOGIN_USERNAME 覆盖） */
    @Value("${auth.login.username}")
    private String loginUsername;

    /** 平台登录密码密文（平台加密后的密文，生产建议用环境变量 AUTH_LOGIN_PASSWORD 覆盖） */
    @Value("${auth.login.password}")
    private String loginPassword;

    /** 外部安全码接口路径（平台网关 qx-api 前缀） */
    private static final String SECURITY_IMG_PATH = "/qx-api/qx-apaas-uc/user/securityImg";

    /** 外部登录接口路径（平台网关 qx-api 前缀） */
    private static final String LOGIN_PATH = "/qx-api/qx-apaas-auth/server/auth/login";

    /**
     * 平台网关基址（qx-api 所在，如 http://host:port）；
     * 留空 = 跟随浏览器实际访问域名（同域部署无需配置，换域名无需改配置）
     */
    @Value("${auth.platform-base-url:}")
    private String platformBaseUrl;

    public AuthServiceImpl(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuthResponseVO login(String key, String secret) {
        AuthResponseVO response = new AuthResponseVO();

        // 1. 校验 key 和 secret
        if (validKey == null || !validKey.equals(key)) {
            log.warn("登录鉴权失败：key 不匹配");
            response.setSuccess(false);
            response.setMessage("鉴权失败：key 不正确");
            return response;
        }
        if (validSecret == null || !validSecret.equals(secret)) {
            log.warn("登录鉴权失败：secret 不匹配");
            response.setSuccess(false);
            response.setMessage("鉴权失败：secret 不正确");
            return response;
        }

        try {
            // 2. 获取安全码（平台网关地址：配置优先，未配置跟随浏览器实际访问域名）
            String baseUrl = resolvePlatformBaseUrl();
            String securityKey = fetchSecurityKey(baseUrl);
            if (securityKey == null) {
                response.setSuccess(false);
                response.setMessage("获取安全码失败");
                return response;
            }

            // 3. 调用外部登录接口
            String sessionId = doLogin(baseUrl, securityKey);
            if (sessionId == null) {
                response.setSuccess(false);
                response.setMessage("外部登录接口调用失败");
                return response;
            }

            response.setSuccess(true);
            response.setMessage("登录成功");
            response.setSessionId(sessionId);
            log.info("登录成功，sessionId: {}", sessionId);
        } catch (Exception e) {
            log.error("登录流程异常", e);
            response.setSuccess(false);
            response.setMessage("登录异常：" + e.getMessage());
        }

        return response;
    }

    /**
     * 平台网关基址：配置 auth.platform-base-url 优先；未配置时按当前请求的
     * 协议 + 主机 + 端口动态拼接（支持反向代理头）；均无法解析时抛错。
     */
    private String resolvePlatformBaseUrl() {
        if (StringUtils.hasText(platformBaseUrl)) {
            String configured = platformBaseUrl.trim();
            return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
        }
        HttpServletRequest request = currentRequest();
        String origin = request == null ? null : RequestOriginResolver.resolveOrigin(request);
        if (origin == null) {
            throw new IllegalStateException("平台网关地址未配置（auth.platform-base-url）且无法从当前请求解析");
        }
        return origin;
    }

    /** 当前请求（登录接口为同步调用，可在请求线程内取到；无上下文返回 null） */
    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes
                ? ((ServletRequestAttributes) attributes).getRequest() : null;
    }

    /**
     * 调用外部接口获取安全码 key
     */
    private String fetchSecurityKey(String baseUrl) {
        String url = baseUrl + SECURITY_IMG_PATH;
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                if (root.path("success").asBoolean()) {
                    String key = root.path("data").path("key").asText();
                    log.info("获取安全码成功，key: {}", key);
                    return key;
                }
            }
            log.error("获取安全码返回异常，url: {}, status: {}, body: {}", url, resp.getStatusCode(), resp.getBody());
        } catch (Exception e) {
            log.error("获取安全码请求异常，url: " + url, e);
        }
        return null;
    }

    /**
     * 调用外部登录接口
     */
    private String doLogin(String baseUrl, String securityKey) {
        String url = baseUrl + LOGIN_PATH;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("username", loginUsername);
            body.put("password", loginPassword);
            body.put("autoLogin", false);
            body.put("loginType", "PWD");
            body.put("key", securityKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> resp = restTemplate.postForEntity(url, request, String.class);
            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                if (root.path("success").asBoolean()) {
                    String sessionId = root.path("data").asText();
                    log.info("外部登录成功，sessionId: {}", sessionId);
                    return sessionId;
                }
            }
            log.error("外部登录返回异常，url: {}, status: {}, body: {}", url, resp.getStatusCode(), resp.getBody());
        } catch (Exception e) {
            log.error("外部登录请求异常，url: " + url, e);
        }
        return null;
    }
}
