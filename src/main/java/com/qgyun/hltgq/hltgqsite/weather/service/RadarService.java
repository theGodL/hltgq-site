package com.qgyun.hltgq.hltgqsite.weather.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.qgyun.hltgq.hltgqsite.weather.client.RadarClient;
import com.qgyun.hltgq.hltgqsite.weather.client.WeatherCallException;
import com.qgyun.hltgq.hltgqsite.weather.support.RadarTileCache;
import com.qgyun.hltgq.hltgqsite.weather.support.RateLimiter;
import com.qgyun.hltgq.hltgqsite.weather.support.StaleCacheSupport;
import com.qgyun.hltgq.hltgqsite.weather.support.UpstreamTokenBucket;
import com.qgyun.hltgq.hltgqsite.weather.vo.RadarFrameVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.RadarFramesVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * N2 雷达图服务（方案 §4）：帧列表缓存 + 瓦片代理 + 进程内 LRU + 上游令牌桶 + 并发信号量 + 终端限流。
 * <p><b>能力边界（免费源硬限制，方案 §4.1）</b>：仅过去约 2 小时回放（13 帧、10 分钟/帧，nowcast 已全球停）、
 * 最大 z=7（z≥8 上游返回 200 + 占位图，故后端主动拦截为 400）、色板固定 4。前端据此限制图层 maxZoom。
 * <p><b>降级语义</b>：帧列表上游失败 → 返回 stale（上限 1 小时）/ 空 frames；瓦片任一环节失败 → 404
 * （前端该瓦片空白，不影响底图与其余瓦片），<b>不抛 5xx</b>。
 * <p><b>限流三层</b>（方案 §4.4）：① 进程内 LRU 强缓存优先（命中不消耗上游配额）；
 * ② 终端 IP 固定窗口 {@code radar.client.limit-per-minute}（默认 600/min，防单用户刷爆缓存与配额）；
 * ③ 上游令牌桶 90/min + 并发信号量 8（保护上游 100/min 硬限）。瓦片路径<b>不进</b>全局 10 QPS 限流
 * （地图拖动瞬时数十请求，会被误伤，方案 §7.3）。
 */
@Service
public class RadarService {

    private static final Logger log = LoggerFactory.getLogger(RadarService.class);

    /** 帧列表缓存 key（方案 §7.2） */
    private static final String CACHE_KEY = "weather:radar:frames";

    /**
     * frame 合法格式（方案 §4.2，评审 B4）：{@link java.util.regex.Matcher#matches()} 全匹配。
     * <p>已删除原 `^\d{10}$` 纯数字分支（复审 B9）——时间戳式 path 天然命中 `[0-9a-f]`，独立分支冗余。
     */
    private static final Pattern FRAME_PATTERN = Pattern.compile("^/v2/radar/[0-9a-fA-F]{8,16}$");

    /** frame 长度上限：正则匹配前的廉价短路（挡住超长输入） */
    private static final int FRAME_MAX_LENGTH = 32;

    /** 色板固定 4 = Universal Blue（上游仅保留该色板，非配置项） */
    private static final int COLOR = 4;

    /** 瓦片模板（相对路径：避免把上下文前缀 /hltgq-site 硬编码进后端响应） */
    private static final String TILE_TEMPLATE_PREFIX = "weather/radar/tile/{z}/{x}/{y}?frame=";

    /** 帧时间输出格式（北京时间；JVM 默认时区由 Dockerfile 固化为 Asia/Shanghai） */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** 终端限流窗口（毫秒）：与配置口径一致（每分钟） */
    private static final long CLIENT_WINDOW_MS = 60_000L;

    /** 终端限流表整体换表的计数阈值（防 IP 基数无限增长，不自建清理线程） */
    private static final int CLIENT_LIMITER_CLEANUP_THRESHOLD = 4096;

    /** 上游并发许可等待上限（毫秒）：地图拖动会成批请求，有限等待使突发按波次完成，而非成片丢瓦片 */
    private static final long UPSTREAM_ACQUIRE_TIMEOUT_MS = 3000L;

    private static final Random RANDOM = new Random();

    @Autowired
    private RadarClient client;

    /** 全局限流（与其他 JSON 接口共用 10 QPS 窗口）；瓦片路径不使用（方案 §7.3） */
    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private StaleCacheSupport cache;

    @Autowired
    private RadarTileCache tileCache;

    @Autowired
    private UpstreamTokenBucket upstreamBucket;

    /** 帧列表新鲜期基数（分钟） */
    @Value("${radar.frames.cache-ttl-minutes:5}")
    private int framesTtlMinutes;

    /** 帧列表新鲜期抖动上限（分钟）：实际 TTL = 基数 + [0, 抖动] */
    @Value("${radar.frames.cache-jitter-minutes:1}")
    private int framesTtlJitterMinutes;

    /** 帧列表旧值上限（小时）：超限即视为不可用（上游 10 分钟滚动一帧） */
    @Value("${radar.frames.stale-max-hours:1}")
    private int framesStaleMaxHours;

    /** 单飞等待超时（秒）：与既有接口同一配置口径 */
    @Value("${weather.single-flight.timeout-seconds:3}")
    private int singleFlightWaitSeconds;

    /** 最大缩放级（上游免费层硬限制 7，经配置下发并用于入参校验） */
    @Value("${radar.tile.max-zoom:7}")
    private int maxZoom;

    /** 上游并发上限（信号量许可数） */
    @Value("${radar.upstream.max-concurrent:8}")
    private int upstreamMaxConcurrent;

    /** 终端 IP 限流阈值（每分钟）；≤0 表示关闭 */
    @Value("${radar.client.limit-per-minute:600}")
    private int clientLimitPerMinute;

    /** 上游并发信号量（公平模式：避免个别请求长期饿死） */
    private Semaphore upstreamSemaphore;

    /** 终端 IP 限流器表（仅瓦片路径使用） */
    private final ConcurrentHashMap<String, RateLimiter> clientLimiters = new ConcurrentHashMap<>();

    /** 终端限流表新增计数（触发整体换表清理） */
    private final AtomicLong clientLimiterCount = new AtomicLong();

    @PostConstruct
    void init() {
        this.upstreamSemaphore = new Semaphore(Math.max(1, upstreamMaxConcurrent), true);
        log.info("雷达服务初始化: 最大缩放级={} 上游配额={}/分钟 上游并发={} 终端限流={} 瓦片缓存={} 条 帧列表TTL={}分钟(+{}抖动)",
                maxZoom, upstreamBucket.limit(), upstreamMaxConcurrent,
                clientLimitPerMinute <= 0 ? "关闭" : clientLimitPerMinute + "/分钟/IP",
                tileCache.maxEntries(), framesTtlMinutes, framesTtlJitterMinutes);
    }

    /**
     * 帧列表（方案 §4.2 ①）：fresh 直返 → stale 立即返回旧值并后台刷新 → miss 单飞补拉（失败空 frames）。
     */
    public RadarFramesVO frames() {
        rateLimiter.acquire();
        long now = StaleCacheSupport.nowMs();
        StaleCacheSupport.Envelope<RadarFramesVO> envelope = cache.read(CACHE_KEY, RadarFramesVO.class);
        // 空帧列表视为未命中：降级结果不得当作有效缓存长期复用（否则上游恢复后前端仍拿不到帧）
        if (envelope != null && envelope.getData() != null && !envelope.getData().getFrames().isEmpty()) {
            if (now < envelope.getExpireAt()) {
                return envelope.getData();
            }
            if (!beyondStaleLimit(envelope, now)) {
                cache.refreshAsync(CACHE_KEY, "雷达帧列表后台刷新", this::pullFrames);
                return envelope.getData();
            }
        }
        return cache.singleFlight(CACHE_KEY, "雷达帧列表", this::pullFrames,
                RadarFramesVO.degraded(maxZoom, COLOR), singleFlightWaitSeconds);
    }

    /**
     * 瓦片代理（方案 §4.2 ② / §4.4）。
     * <p>顺序：参数校验（非法 400，由全局异常处理）→ LRU 命中直返 → 终端 IP 限流 → 上游并发信号量 →
     * 上游令牌桶 → 上游拉取 → 写回 LRU。
     * <p>除参数校验外的一切失败（限流/配额/并发/超时/上游错误）<b>统一返回 null</b>，由控制器转 404
     * （方案 §4.6：前端该瓦片空白，不影响底图与其他瓦片）。
     *
     * @param clientIp 终端 IP（用于限流；网关场景取 X-Forwarded-For 首段，见控制器）
     * @return PNG 字节；降级时 null
     */
    public byte[] tile(int z, int x, int y, String frame, String clientIp) {
        validateTileRequest(z, x, y, frame);

        String key = RadarTileCache.key(frame, z, x, y);
        byte[] cached = tileCache.get(key);
        if (cached != null) {
            return cached;
        }

        if (!passClientLimit(clientIp)) {
            log.warn("雷达瓦片终端限流（{}次/分钟）: ip={} key={}", clientLimitPerMinute, clientIp, key);
            // 终端维度超限属「调用方行为异常」，与全局限流同口径返回 429（区分于上游侧降级 404）
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁");
        }

        if (!acquireUpstreamPermit(key)) {
            return null;
        }
        try {
            // 令牌桶：超出即放弃本次（不排队不重试），避免打穿上游 100/min 硬限
            if (!upstreamBucket.tryAcquire()) {
                log.warn("雷达上游配额已用尽（{}/分钟），本次降级 404: key={}", upstreamBucket.limit(), key);
                return null;
            }
            byte[] data = client.tile(frame, z, x, y);
            if (data.length == 0) {
                log.warn("雷达瓦片上行为空: key={}", key);
                return null;
            }
            tileCache.put(key, data);
            return data;
        } catch (WeatherCallException e) {
            log.warn("雷达瓦片拉取失败: key={} 原因={}", key, e.getMessage());
            return null;
        } finally {
            upstreamSemaphore.release();
        }
    }

    // ==================== 帧列表加载 ====================

    /** 拉取帧列表并写回缓存（miss 补拉与 stale 后台刷新共用；失败抛 WeatherCallException 交由上层兜底） */
    private RadarFramesVO pullFrames() {
        JsonNode root = client.frames();
        JsonNode radar = root.get("radar");
        JsonNode past = radar == null ? null : radar.get("past");
        if (past == null || !past.isArray()) {
            throw new WeatherCallException("帧列表响应缺少 radar.past 数组");
        }

        List<RadarFrameVO> frames = new ArrayList<>(past.size());
        collectFrames(past, "past", frames);
        // nowcast 官方已于 2026-01-01 全球停止；上游若恢复则自动带出，无需改造（方案 §4.2）
        JsonNode nowcast = radar.get("nowcast");
        if (nowcast != null && nowcast.isArray() && nowcast.size() > 0) {
            collectFrames(nowcast, "nowcast", frames);
            log.info("雷达帧列表出现 nowcast 段（上游已恢复外推）: 帧数={}", nowcast.size());
        }
        if (frames.isEmpty()) {
            // 不写缓存：避免把"上游结构异常"固化为有效缓存
            throw new WeatherCallException("帧列表解析结果为空（上游结构可能变化）");
        }
        // 显式按时间升序（不依赖上游顺序）：前端时间轴直接按数组顺序播放
        frames.sort(Comparator.comparingLong(RadarFrameVO::getTimestamp));

        RadarFramesVO vo = new RadarFramesVO();
        vo.setMaxZoom(maxZoom);
        vo.setColor(COLOR);
        vo.setGenerated(formatTime(longField(root, "generated")));
        vo.setFrames(frames);
        cache.write(CACHE_KEY, vo, freshTtlSeconds(), staleMaxSeconds());

        RadarFrameVO latest = frames.get(frames.size() - 1);
        log.info("雷达帧列表更新: 帧数={} 起={} 止={} 生成时间={}", frames.size(),
                frames.get(0).getTime(), latest.getTime(), vo.getGenerated());
        return vo;
    }

    /** 解析一帧数组（单帧异常只跳过该帧，不整段失败——与既有接口的边界安全取值同口径） */
    private void collectFrames(JsonNode array, String type, List<RadarFrameVO> target) {
        for (JsonNode item : array) {
            Long timestamp = longField(item, "time");
            String path = textField(item, "path");
            if (timestamp == null || path == null) {
                log.warn("雷达帧字段缺失，已跳过: time={} path={}", timestamp, path);
                continue;
            }
            if (!FRAME_PATTERN.matcher(path).matches() || path.length() > FRAME_MAX_LENGTH) {
                // 上游 path 是拼上游 URL 的输入之一：格式不符即丢弃（防 SSRF，方案 §4.2）
                log.warn("雷达帧 path 格式不符，已跳过: {}", path);
                continue;
            }
            RadarFrameVO frame = new RadarFrameVO();
            frame.setTimestamp(timestamp);
            frame.setTime(formatTime(timestamp));
            frame.setPath(path);
            frame.setType(type);
            frame.setTileUrlTemplate(TILE_TEMPLATE_PREFIX + path);
            target.add(frame);
        }
    }

    // ==================== 入参校验（方案 §4.2） ====================

    /**
     * 瓦片入参校验：不满足即抛 {@link IllegalArgumentException}（全局异常处理转 400）。
     * <p><b>frame 不做 trim</b>：尾随空白必须判非法（如 `...5d%20` 解码后带尾随空格，trim 后会误判为合法，
     * 与方案 §10.2 验证项 3e 的预期冲突）。
     */
    private void validateTileRequest(int z, int x, int y, String frame) {
        if (z < 0 || z > maxZoom) {
            // 主动拦截 z≥8：上游该级返回 200 + 统一占位图（"永远无回波"的假图），不得透传（方案 §4.1）
            throw new IllegalArgumentException("z 超出范围 [0," + maxZoom + "]: " + z);
        }
        int maxIndex = (1 << z) - 1;
        if (x < 0 || x > maxIndex) {
            throw new IllegalArgumentException("x 超出 z=" + z + " 的有效范围 [0," + maxIndex + "]: " + x);
        }
        if (y < 0 || y > maxIndex) {
            throw new IllegalArgumentException("y 超出 z=" + z + " 的有效范围 [0," + maxIndex + "]: " + y);
        }
        if (frame == null || frame.isEmpty()) {
            throw new IllegalArgumentException("frame 不能为空（取 /weather/radar/frames 的 frames[].path）");
        }
        if (frame.length() > FRAME_MAX_LENGTH) {
            throw new IllegalArgumentException("frame 长度超出上限 " + FRAME_MAX_LENGTH + ": " + frame.length());
        }
        if (!FRAME_PATTERN.matcher(frame).matches()) {
            throw new IllegalArgumentException("frame 格式非法（须形如 /v2/radar/4f4ae5d6985d）: " + frame);
        }
    }

    // ==================== 限流与并发 ====================

    /** 终端 IP 固定窗口限流：阈值 ≤0 视为关闭；命中即拒绝（瓦片返回 404 降级） */
    private boolean passClientLimit(String clientIp) {
        if (clientLimitPerMinute <= 0) {
            return true;
        }
        String ip = (clientIp == null || clientIp.trim().isEmpty()) ? "unknown" : clientIp.trim();
        RateLimiter limiter = clientLimiters.get(ip);
        if (limiter == null) {
            if (clientLimiterCount.incrementAndGet() > CLIENT_LIMITER_CLEANUP_THRESHOLD) {
                // 整体换表：清空窗口计数换取内存有界（阈值宽松，重置不影响正常用户）
                clientLimiters.clear();
                clientLimiterCount.set(0);
                log.info("雷达瓦片终端限流表已清理（IP 数超过 {}）", CLIENT_LIMITER_CLEANUP_THRESHOLD);
            }
            limiter = clientLimiters.computeIfAbsent(ip, key -> new RateLimiter(clientLimitPerMinute, CLIENT_WINDOW_MS));
        }
        return limiter.tryAcquire();
    }

    /** 上游并发许可：有限等待（突发按波次完成）；超时/中断即降级，不占用请求线程 */
    private boolean acquireUpstreamPermit(String key) {
        try {
            if (!upstreamSemaphore.tryAcquire(UPSTREAM_ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                log.warn("雷达上游并发已满（{}）且等待超时，本次降级 404: key={}", upstreamMaxConcurrent, key);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("雷达上游并发等待被中断，本次降级 404: key={}", key);
            return false;
        }
    }

    // ==================== 缓存工具 ====================

    /** 新鲜期（秒）：基数 + 随机抖动，避免多实例/多请求同时失效（上游 10 分钟滚动一帧） */
    private long freshTtlSeconds() {
        return (framesTtlMinutes + RANDOM.nextInt(framesTtlJitterMinutes + 1)) * 60L;
    }

    private long staleMaxSeconds() {
        return framesStaleMaxHours * 3600L;
    }

    /** 是否已超旧值上限（口径与既有接口一致：以拉取时刻为准，缺字段时退化为过期时长判定） */
    private boolean beyondStaleLimit(StaleCacheSupport.Envelope<?> envelope, long now) {
        long staleMaxMs = staleMaxSeconds() * 1000L;
        if (envelope.getPulledAtMs() > 0) {
            return now - envelope.getPulledAtMs() > staleMaxMs;
        }
        return now - envelope.getExpireAt() > staleMaxMs;
    }

    // ==================== 取值工具 ====================

    /** epoch 秒 → 北京时间字符串；null 返回 null */
    private static String formatTime(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault())
                .format(TIME_FORMATTER);
    }

    /** 字段安全取长整数：缺失/JSON null/非数值返回 null（不得当作 0——0 会被格式化成 1970 年） */
    private static Long longField(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isNumber() ? value.asLong() : null;
    }

    /** 字段安全取字符串：缺失/JSON null/容器节点返回 null */
    private static String textField(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || value.isContainerNode()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isEmpty() ? null : text;
    }
}
