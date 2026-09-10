package com.qgyun.hltgq.hltgqsite.stationdetail.client;

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
import org.springframework.web.client.RestTemplate;

/**
 * 企效文件服务客户端（fs.base-url，默认 http://10.68.18.9:8081/qx-api/qgyun-service-fs-manager）。
 * <p>GET /file/m/{fileId} 返回文件元数据与签名地址（preview 原图 / thumb 缩略图，24h 有效），
 * 须携带当前登录会话（authorization 头传 sessionId），每次实时获取不缓存（签名 URL 会过期）。
 * <p>响应约定 {success,data:{name,urlMap:{preview,thumb}}}：success 非 true 或网络异常抛
 * {@link FileCallException}，由调用方降级处理（巡检照片属增强信息，失败不阻断主数据）。
 */
@Component
public class FileClient {

    private static final Logger log = LoggerFactory.getLogger(FileClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public FileClient(@Value("${fs.base-url:http://10.68.18.9:8081/qx-api/qgyun-service-fs-manager}") String baseUrl,
                      @Value("${fs.connect-timeout-ms:3000}") int connectTimeoutMs,
                      @Value("${fs.read-timeout-ms:5000}") int readTimeoutMs,
                      ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 获取文件元数据与签名地址。
     *
     * @param fileId    文件 id（图片关联表 rel_id）
     * @param sessionId 当前登录会话 id（平台会话键，authorization 头透传）
     * @return 文件信息（name 文件名、url 原图、thumb 缩略图，均可能为空）
     */
    public FileInfo getFile(String fileId, String sessionId) {
        String path = "/file/m/" + fileId;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("authorization", sessionId);
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            String body = response.getBody();
            if (body == null) {
                throw new FileCallException("文件服务响应为空(" + path + ")");
            }
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("success").asBoolean(false)) {
                throw new FileCallException("文件服务返回失败(" + path + "): " + root.path("msg").asText("未知错误"));
            }
            JsonNode data = root.path("data");
            FileInfo info = new FileInfo();
            info.fileId = data.path("fileId").asText(fileId);
            info.name = data.path("name").asText(null);
            JsonNode urlMap = data.path("qgImg").path("urlMap");
            info.url = textOrNull(urlMap.path("preview"));
            info.thumb = textOrNull(urlMap.path("thumb"));
            log.info("file {} success, name={}, preview={}, thumb={}",
                    fileId, info.name, info.url != null, info.thumb != null);
            return info;
        } catch (FileCallException e) {
            log.warn("file {} call failed: {}", fileId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("file {} call exception: {}", fileId, e.getMessage());
            throw new FileCallException("文件服务调用失败(" + path + "): " + e.getMessage(), e);
        }
    }

    /** JsonNode 文本值（空/缺失归 null） */
    private String textOrNull(JsonNode node) {
        String v = node == null ? null : node.asText(null);
        return v == null || v.isEmpty() ? null : v;
    }

    /**
     * 文件信息（供巡检照片组装）
     */
    public static class FileInfo {

        /** 文件 id */
        public String fileId;

        /** 文件名 */
        public String name;

        /** 原图签名地址（preview） */
        public String url;

        /** 缩略图签名地址（thumb） */
        public String thumb;
    }
}
