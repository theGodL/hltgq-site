package com.qgyun.hltgq.hltgqsite.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.service.AuthService;
import com.qgyun.hltgq.hltgqsite.vo.AuthResponseVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    /** 外部安全码获取地址 */
    private static final String SECURITY_IMG_URL = "http://220.179.1.110:8081/qx-api/qx-apaas-uc/user/securityImg";

    /** 外部登录地址 */
    private static final String LOGIN_URL = "http://220.179.1.110:8081/qx-api/qx-apaas-auth/server/auth/login";

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
            // 2. 获取安全码
            String securityKey = fetchSecurityKey();
            if (securityKey == null) {
                response.setSuccess(false);
                response.setMessage("获取安全码失败");
                return response;
            }

            // 3. 调用外部登录接口
            String sessionId = doLogin(securityKey);
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
     * 调用外部接口获取安全码 key
     */
    private String fetchSecurityKey() {
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(SECURITY_IMG_URL, String.class);
            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                if (root.path("success").asBoolean()) {
                    String key = root.path("data").path("key").asText();
                    log.info("获取安全码成功，key: {}", key);
                    return key;
                }
            }
            log.error("获取安全码返回异常，status: {}, body: {}", resp.getStatusCode(), resp.getBody());
        } catch (Exception e) {
            log.error("获取安全码请求异常", e);
        }
        return null;
    }

    /**
     * 调用外部登录接口
     */
    private String doLogin(String securityKey) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("username", "15212773620");
            body.put("password", "KUGKYloUqmCIe7oJ_7tzNA==");
            body.put("autoLogin", false);
            body.put("loginType", "PWD");
            body.put("key", securityKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> resp = restTemplate.postForEntity(LOGIN_URL, request, String.class);
            if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                if (root.path("success").asBoolean()) {
                    String sessionId = root.path("data").asText();
                    log.info("外部登录成功，sessionId: {}", sessionId);
                    return sessionId;
                }
            }
            log.error("外部登录返回异常，status: {}, body: {}", resp.getStatusCode(), resp.getBody());
        } catch (Exception e) {
            log.error("外部登录请求异常", e);
        }
        return null;
    }
}
