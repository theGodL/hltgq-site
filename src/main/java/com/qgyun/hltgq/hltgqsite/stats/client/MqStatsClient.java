package com.qgyun.hltgq.hltgqsite.stats.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * hltgq-mq 数据统计服务客户端（mq.base-url，默认 http://10.68.18.4:8081，与召测服务同进程同地址）。
 * <p>mq 统计接口不对外暴露（仅内网），site 作为网关层透传其 {code,msg,data} 响应中的 data 段：
 * code=0 返回 data 节点（对象或数组），code!=0 抛 {@link MqStatsCallException}。
 * <p>接口契约见 hltgq-mq 数据统计.md：arrival-stats（KPI 卡片，含 stationTotal）/
 * arrival-detail（站点到报明细）/ miss-detail（缺测明细）/ collect-stats（采集状态）/
 * service-status（mq 自身进程 + 接收/解析/存储三服务）。
 * <p>mq 侧统计结果带 60 秒内存缓存，高频轮询不重复打库；本客户端不做重试——
 * 大屏轮询场景下失败由下一轮请求自然恢复，避免叠加压力。
 */
@Component
public class MqStatsClient {

    private static final Logger log = LoggerFactory.getLogger(MqStatsClient.class);

    private static final String PATH_ARRIVAL_STATS = "/api/report/arrival-stats";
    private static final String PATH_ARRIVAL_DETAIL = "/api/report/arrival-detail";
    private static final String PATH_MISS_DETAIL = "/api/report/miss-detail";
    private static final String PATH_COLLECT_STATS = "/api/report/collect-stats";
    private static final String PATH_SERVICE_STATUS = "/api/report/service-status";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public MqStatsClient(@Value("${mq.base-url:http://10.68.18.4:8081}") String baseUrl,
                         @Value("${mq.connect-timeout-ms:5000}") int connectTimeoutMs,
                         @Value("${mq.read-timeout-ms:15000}") int readTimeoutMs,
                         ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /** 统计卡片聚合：stationTotal/todayArrivalRate/monthAvgArrivalRate/todayMissRate/statStartDate/noReportSites/missedSites */
    public JsonNode arrivalStats() {
        return get(PATH_ARRIVAL_STATS);
    }

    /** 站点到报明细：每站一行（siteId/siteName/stcd/msgType/expected/arrived/missed/arrivalRate） */
    public JsonNode arrivalDetail() {
        return get(PATH_ARRIVAL_DETAIL);
    }

    /** 缺测明细：连续缺测窗合并为段（startTm/endTm/missMinutes/dataTypes/status） */
    public JsonNode missDetail() {
        return get(PATH_MISS_DETAIL);
    }

    /** 数据采集状态统计：水位/流量/雨量/闸门开度/墒情五维度聚合 */
    public JsonNode collectStats() {
        return get(PATH_COLLECT_STATS);
    }

    /** mq 自身服务状态：进程指标 + 数据接收/解析/存储三逻辑服务（信息发布/告警推送由 site 侧提供） */
    public JsonNode serviceStatus() {
        return get(PATH_SERVICE_STATUS);
    }

    /** GET 并解析 {code,msg,data}：code=0 返回 data 节点，否则抛异常（调用失败/非 200 一并包装） */
    private JsonNode get(String path) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + path, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("msg").asText("未知错误");
                log.error("mq stats {} business error, code={}: {}", path, code, msg);
                throw new MqStatsCallException("数据统计服务返回错误(" + path + "): " + msg);
            }
            return root.path("data");
        } catch (MqStatsCallException e) {
            throw e;
        } catch (Exception e) {
            log.error("mq stats {} call failed: {}", path, e.getMessage());
            throw new MqStatsCallException("数据统计服务调用失败(" + path + "): " + e.getMessage(), e);
        }
    }
}
