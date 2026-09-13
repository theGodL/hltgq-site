package com.qgyun.hltgq.hltgqsite.stats.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.stats.client.MqStatsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 数据统计接口响应缓存（site 网关层，2026-09-13）。
 * <p>背景：数据统计大屏 5 个 mq 统计接口单次响应 8s+（mq 侧慢查询，修复清单已交付 mq 团队），
 * 首屏体验差。本层把「默认今日口径」的响应定时预热进 Redis：页面进入直接命中缓存秒开；
 * 缓存未命中（服务刚启动/预热失败/首次查历史区间）回退 mq 实时查询并回填缓存。
 * <p>缓存键归一化（与 mq 区间语义一致）：无参=今日（与显式传今天日期同键）；只传 start=[start,今日]；
 * 只传 end=单日 [end,end]；都传=闭区间。仅归一化成功的查询参与缓存，参数非法直接透传（上游校验）。
 * <p>TTL：区间含今日（endDate ≥ 今日）用短 TTL（默认 20 分钟 = 预热间隔 10 分钟的 2 倍，
 * 正常运行下缓存被预热滚动刷新、页面不再触发冷查询，且连续 1~2 轮预热失败期间仍可读旧值）；
 * 纯历史区间用长 TTL（默认 60 分钟，历史数据基本稳定，晚补报修正最多滞后 1 小时）。
 * <p>预热：定时任务直接调 mq 统计接口（mq 不需要会话），单接口失败仅告警并保留旧缓存等下一轮；
 * video-collect（device 接口）依赖当前登录会话，不参与预热，仅请求路径命中/回填。
 * <p>并发：缓存未命中时同键单飞合并（同一 key 同时只放行一次上游查询，其余请求等待复用结果），
 * 多人同时首访/服务刚启动窗口不会放大上游压力；leader 失败时等待方透传同源异常（保持 502 语义）。
 */
@Service
public class StatsCacheService {

    private static final Logger log = LoggerFactory.getLogger(StatsCacheService.class);

    /** 缓存键前缀：stats:api:{path}:{start}:{end} */
    private static final String CACHE_PREFIX = "stats:api:";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 缓存路径段（与 Controller 路由、预热任务共用，避免字面量漂移） */
    public static final String PATH_ARRIVAL_STATS = "/arrival-stats";
    public static final String PATH_ARRIVAL_DETAIL = "/arrival-detail";
    public static final String PATH_MISS_DETAIL = "/miss-detail";
    public static final String PATH_COLLECT_STATS = "/collect-stats";
    public static final String PATH_SERVICE_STATUS = "/service-status";
    public static final String PATH_VIDEO_COLLECT = "/video-collect";

    @Value("${stats.cache.enabled:true}")
    private boolean enabled;

    @Value("${stats.cache.today-ttl-minutes:20}")
    private long todayTtlMinutes;

    @Value("${stats.cache.past-ttl-minutes:60}")
    private long pastTtlMinutes;

    @Value("${stats.cache.merge-wait-seconds:20}")
    private long mergeWaitSeconds;

    /** 同键单飞进行中的加载：key → leader 的 CompletableFuture（完成即被复用，finally 移除） */
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pendingLoads = new ConcurrentHashMap<>();

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MqStatsClient mqStatsClient;

    /**
     * 读缓存（命中直接返回）；未命中走 loader（上游实时查询）并回填缓存。
     * <p>loader 抛异常（mq 502）时不写缓存、异常原样传播；参数非法不缓存直接透传。
     */
    public JsonNode getOrLoad(String path, String startDate, String endDate, Supplier<JsonNode> loader) {
        if (!enabled) {
            return loader.get();
        }
        String[] range = normalizeRange(startDate, endDate);
        if (range == null) {
            return loader.get();
        }
        String key = key(path, range[0], range[1]);
        JsonNode cached = readCache(key);
        if (cached != null) {
            log.debug("stats cache hit key={}", key);
            return cached;
        }
        // 未命中：单飞合并回填（多人同时首访同一区间只放行一次上游查询）
        CompletableFuture<JsonNode> mine = new CompletableFuture<>();
        CompletableFuture<JsonNode> existing = pendingLoads.putIfAbsent(key, mine);
        if (existing != null) {
            return awaitMerged(key, existing, loader);
        }
        try {
            long beginMs = System.currentTimeMillis();
            JsonNode data = loader.get();
            long costMs = System.currentTimeMillis() - beginMs;
            if (data != null && !data.isNull()) {
                writeCache(key, data, ttlMinutes(range[1]));
            }
            mine.complete(data);
            log.info("stats cache fill key={} upstreamCost={}ms", key, costMs);
            return data;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            pendingLoads.remove(key, mine);
        }
    }

    /**
     * 单飞等待：复用 leader 的加载结果；leader 失败时透传同源异常
     * （保持 502 语义，并避免失败时各等待方并发重试放大上游压力）；
     * 等待超时（理论不会：mq/device 读超时 15s &lt; 合并等待 20s）时本轮自行查询兜底。
     */
    private JsonNode awaitMerged(String key, CompletableFuture<JsonNode> existing, Supplier<JsonNode> loader) {
        try {
            return existing.get(mergeWaitSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("数据统计缓存合并加载失败: " + key, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("数据统计缓存合并加载被中断: " + key, e);
        } catch (TimeoutException e) {
            log.warn("stats cache merge wait timeout key={}，本轮自行查询", key);
            return loader.get();
        }
    }

    /**
     * 预热默认今日口径（页面首屏）：每轮 fixed delay（默认 10 分钟，与数据 10 分钟窗粒度对齐；
     * 一轮串行 5 个接口，对 mq 为匀速低速负载）刷新 5 个 mq 统计接口缓存。
     * 失败仅告警并保留旧缓存（旧缓存 TTL = 2× 预热间隔，可撑过 1~2 轮失败）；单接口失败不影响其他接口。
     */
    @Scheduled(initialDelayString = "${stats.cache.warm-initial-delay-ms:10000}",
            fixedDelayString = "${stats.cache.warm-delay-ms:600000}")
    public void warmToday() {
        if (!enabled) {
            return;
        }
        String today = LocalDate.now().format(DATE_FMT);
        warmOne(PATH_ARRIVAL_STATS, today, () -> mqStatsClient.arrivalStats(today, today));
        warmOne(PATH_ARRIVAL_DETAIL, today, () -> mqStatsClient.arrivalDetail(today, today));
        warmOne(PATH_MISS_DETAIL, today, () -> mqStatsClient.missDetail(today, today));
        warmOne(PATH_COLLECT_STATS, today, () -> mqStatsClient.collectStats(today, today));
        warmOne(PATH_SERVICE_STATUS, today, () -> mqStatsClient.serviceStatus(today, today));
    }

    private void warmOne(String path, String day, Supplier<JsonNode> loader) {
        long beginMs = System.currentTimeMillis();
        try {
            JsonNode data = loader.get();
            if (data == null || data.isNull()) {
                log.warn("stats warm {} {} 返回空，保留旧缓存", path, day);
                return;
            }
            writeCache(key(path, day, day), data, todayTtlMinutes);
            log.info("stats warm {} {} OK cost={}ms", path, day, System.currentTimeMillis() - beginMs);
        } catch (Exception e) {
            log.warn("stats warm {} {} 失败（保留旧缓存等下一轮）：{}", path, day, e.getMessage());
        }
    }

    /**
     * 区间归一化（与 mq 约定一致）：无参=今日；只传 start=[start,今日]；只传 end=单日；
     * 都传=闭区间。含空串/非法日期（非 yyyy-MM-dd 可解析）返回 null（不缓存，原样透传上游）。
     */
    private String[] normalizeRange(String startDate, String endDate) {
        boolean sBlank = !StringUtils.hasText(startDate);
        boolean eBlank = !StringUtils.hasText(endDate);
        String today = LocalDate.now().format(DATE_FMT);
        if (sBlank && eBlank) {
            return new String[]{today, today};
        }
        String s = sBlank ? null : parseDate(startDate);
        String e = eBlank ? null : parseDate(endDate);
        if ((!sBlank && s == null) || (!eBlank && e == null)) {
            return null;
        }
        if (sBlank) {
            return new String[]{e, e};
        }
        if (eBlank) {
            return new String[]{s, today};
        }
        return new String[]{s, e};
    }

    /** 解析并归一化日期（yyyy-MM-dd，宽容格式统一到补零形式）；无法解析返回 null */
    private String parseDate(String value) {
        try {
            return LocalDate.parse(value.trim(), DATE_FMT).format(DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    /** TTL：归一化 endDate ≥ 今日用短 TTL（数据仍在增长），否则用历史长 TTL */
    private long ttlMinutes(String normalizedEnd) {
        try {
            return LocalDate.parse(normalizedEnd, DATE_FMT).isBefore(LocalDate.now())
                    ? pastTtlMinutes : todayTtlMinutes;
        } catch (Exception e) {
            return todayTtlMinutes;
        }
    }

    private String key(String path, String start, String end) {
        return CACHE_PREFIX + path + ":" + start + ":" + end;
    }

    private JsonNode readCache(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                return null;
            }
            return objectMapper.readTree(value);
        } catch (Exception e) {
            // Redis 不可达或数据损坏：降级为未命中，走上游实时查询
            log.debug("stats cache read failed key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, JsonNode data, long ttlMinutes) {
        try {
            redisTemplate.opsForValue().set(key, data.toString(), ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 写缓存失败不影响主链路（仅影响命中率）
            log.debug("stats cache write failed key={}: {}", key, e.getMessage());
        }
    }
}
