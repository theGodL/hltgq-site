package com.qgyun.hltgq.hltgqsite.weather.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 上游维度令牌桶（方案 §4.4 / §8.1）。
 * <p>雷达瓦片上游 RainViewer 的限流口径是 <b>100 请求/IP/分钟，按我方服务器出口 IP 计</b>
 * （非终端用户 IP），因此必须在上游维度单独计数：配置 {@code radar.upstream.limit-per-minute} 默认 90（留 10% 余量）。
 * 超出即由调用方<b>直接降级 404</b>（不排队、不重试）——本类的作用是保住上游配额，而非提升吞吐。
 * <p>与 {@link RateLimiter} 的分工：{@code RateLimiter} 约束的是「对外 HTTP 调用」（10 QPS 全局窗口，
 * 瓦片接口按方案 §7.3 不进该窗口）；本类只统计**真正发往上游的调用**（LRU 命中不消耗配额），
 * 并额外维护累计调用数与低频计数日志（方案 §8.4 日志④，用于观察令牌桶余量）。
 * <p><b>多实例注意</b>：进程内计数，多实例部署时各实例独立计数，实际发出量为 N×90/min，
 * 会打穿上游 100/min 硬限——须按方案 §10.3「多实例适配方案」改为 Redis 共享计数（INCR + EXPIRE 60）。
 */
@Component
public class UpstreamTokenBucket {

    private static final Logger log = LoggerFactory.getLogger(UpstreamTokenBucket.class);

    /** 窗口长度（毫秒）：上游口径为「每分钟」 */
    private static final long WINDOW_MS = 60_000L;

    /** 计数日志最小间隔（毫秒）：拖动高峰下避免高频日志淹没有用信息 */
    private static final long LOG_INTERVAL_MS = 60_000L;

    /** 窗口内允许发往上游的最大请求数 */
    private final int limit;

    /** 当前窗口起始毫秒 */
    private final AtomicLong windowStartMs = new AtomicLong(System.currentTimeMillis());

    /** 当前窗口计数 */
    private final AtomicInteger counter = new AtomicInteger();

    /** 累计放行调用数（只增不减，仅用于观测） */
    private final AtomicLong totalCalls = new AtomicLong();

    /** 上次计数日志时刻（epoch 毫秒） */
    private final AtomicLong lastLogMs = new AtomicLong(System.currentTimeMillis());

    public UpstreamTokenBucket(@Value("${radar.upstream.limit-per-minute:90}") int limit) {
        this.limit = limit;
    }

    /**
     * 尝试获取一次上游调用许可（不抛异常、不阻塞）。
     *
     * @return true=放行（已计入上游调用数）；false=本窗口配额已用尽，调用方应降级
     */
    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        boolean allowed;
        while (true) {
            long windowStart = windowStartMs.get();
            if (now - windowStart >= WINDOW_MS) {
                if (windowStartMs.compareAndSet(windowStart, now)) {
                    counter.set(1);
                    allowed = true;
                    break;
                }
            } else {
                allowed = counter.incrementAndGet() <= limit;
                break;
            }
        }
        if (!allowed) {
            return false;
        }
        long total = totalCalls.incrementAndGet();
        long lastLog = lastLogMs.get();
        if (now - lastLog >= LOG_INTERVAL_MS && lastLogMs.compareAndSet(lastLog, now)) {
            log.info("雷达上游调用累计={} 次（配额 {}/分钟，本窗口已用 {}）", total, limit, counter.get());
        }
        return true;
    }

    /** 上游每分钟配额（仅用于日志） */
    public int limit() {
        return limit;
    }

    /** 累计上游调用数（仅用于观测/自检） */
    public long totalCalls() {
        return totalCalls.get();
    }
}
