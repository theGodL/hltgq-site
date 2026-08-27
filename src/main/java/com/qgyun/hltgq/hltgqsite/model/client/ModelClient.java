package com.qgyun.hltgq.hltgqsite.model.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

/**
 * 模型一体化服务客户端（model.base-url，默认 http://10.68.18.11:8000）。
 * <p>统一解析响应约定 {code,msg}：0=成功、1=参数错误、2=模型异常，非 0 一律抛 {@link ModelCallException}。
 * <p>计算类接口（/predict /allocate /decision）服务端全局锁串行执行，read 超时 310s（默认）。
 * <p>中文业务字段（如 "入库水量_万方"）原样解析，不转义不改名。
 */
@Component
public class ModelClient {

    public static final String PATH_HEALTH = "/health";
    public static final String PATH_FORECAST = "/forecast";
    public static final String PATH_PREDICT = "/predict";
    public static final String PATH_DEMAND = "/demand";
    public static final String PATH_LOSS = "/loss";
    public static final String PATH_ALLOCATE = "/allocate";
    public static final String PATH_DECISION = "/decision";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public ModelClient(@Value("${model.base-url}") String baseUrl,
                       @Value("${model.connect-timeout-ms:5000}") int connectTimeoutMs,
                       @Value("${model.read-timeout-ms:310000}") int readTimeoutMs,
                       ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * POST JSON，返回业务响应 JsonNode（已校验 code=0）。
     */
    public JsonNode postJson(String path, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String jsonBody = objectMapper.writeValueAsString(body == null ? Collections.emptyMap() : body);
            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + path,
                    new HttpEntity<>(jsonBody, headers), String.class);
            return parseOk(response.getBody(), path);
        } catch (ModelCallException e) {
            throw e;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw fromErrorBody(e.getResponseBodyAsString(), path);
        } catch (ResourceAccessException e) {
            throw new ModelCallException(-1, "模型服务不可达或超时: " + rootMessage(e));
        } catch (Exception e) {
            throw new ModelCallException(-1, "调用模型服务异常: " + e.getMessage());
        }
    }

    /**
     * GET，返回业务响应 JsonNode（已校验 code=0）。
     */
    public JsonNode getJson(String path) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + path, String.class);
            return parseOk(response.getBody(), path);
        } catch (ModelCallException e) {
            throw e;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw fromErrorBody(e.getResponseBodyAsString(), path);
        } catch (ResourceAccessException e) {
            throw new ModelCallException(-1, "模型服务不可达或超时: " + rootMessage(e));
        } catch (Exception e) {
            throw new ModelCallException(-1, "调用模型服务异常: " + e.getMessage());
        }
    }

    /**
     * GET 下载二进制文件（如 /decision/download 的 Excel），非 2xx 按 {code,msg} 解析抛异常。
     */
    public byte[] downloadBytes(String path) {
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(baseUrl + path, byte[].class);
            return response.getBody();
        } catch (ModelCallException e) {
            throw e;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw fromErrorBody(e.getResponseBodyAsString(), path);
        } catch (ResourceAccessException e) {
            throw new ModelCallException(-1, "模型服务不可达或超时: " + rootMessage(e));
        } catch (Exception e) {
            throw new ModelCallException(-1, "调用模型服务异常: " + e.getMessage());
        }
    }

    private JsonNode parseOk(String body, String path) {
        try {
            JsonNode node = objectMapper.readTree(body);
            int code = node.path("code").asInt(-1);
            if (code != 0) {
                throw new ModelCallException(code, node.path("msg").asText("未知错误"));
            }
            return node;
        } catch (ModelCallException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCallException(-1, "解析模型响应失败(" + path + "): " + e.getMessage());
        }
    }

    private ModelCallException fromErrorBody(String body, String path) {
        try {
            JsonNode node = objectMapper.readTree(body);
            int code = node.path("code").asInt(-1);
            String msg = node.path("msg").asText(null);
            return new ModelCallException(code, msg == null ? "HTTP 错误: " + path : msg);
        } catch (Exception e) {
            return new ModelCallException(-1, "HTTP 错误: " + path);
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
