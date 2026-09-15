package com.qgyun.hltgq.hltgqsite.weather.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可参数化固定窗口限流器（评审 B2 要求：阈值不可硬编码）。
 * <p>由现有 {@code WeatherService.checkRateLimit()} 等价迁移而来——同一套 CAS 窗口轮转逻辑，
 * 仅把阈值与窗口长度改为构造入参，对外行为完全不变（默认 1s 窗口，超限抛 429）。
 * <p>以 Bean 形式注入：阈值取既有配置 {@code weather.rate-limit.qps}（10），
 * 实时/逐小时、日级、雨情、台风<b>共用同一实例</b>（方案 §3.5 / §5，评审 B2 —— "复用同一限流"）。
 * <p>注意：这是 <b>进程内</b> 限流，用于保护自身与上游；多实例部署时各实例独立计数。
 */
@Component
public class RateLimiter {

    /** 窗口内允许的最大请求数 */
    private final int limit;

    /** 窗口长度（毫秒） */
    private final long windowMs;

    /** 当前窗口起始毫秒 */
    private final AtomicLong windowStartMs = new AtomicLong(System.currentTimeMillis());

    /** 当前窗口计数 */
    private final AtomicInteger counter = new AtomicInteger();

    /** 共享实例：1 秒窗口，阈值来自 weather.rate-limit.qps（与现有接口口径一致） */
    @Autowired
    public RateLimiter(@Value("${weather.rate-limit.qps:10}") int limit) {
        this(limit, 1000L);
    }

    /** 自定义阈值与窗口（测试或独立限流通道用） */
    public RateLimiter(int limit, long windowMs) {
        this.limit = limit;
        this.windowMs = windowMs;
    }

    /**
     * 获取一次许可；超出阈值抛 429（调用方不吞异常，交由全局异常处理返回）。
     */
    public void acquire() {
        long now = System.currentTimeMillis();
        while (true) {
            long windowStart = windowStartMs.get();
            if (now - windowStart >= windowMs) {
                if (windowStartMs.compareAndSet(windowStart, now)) {
                    counter.set(1);
                    return;
                }
            } else {
                if (counter.incrementAndGet() <= limit) {
                    return;
                }
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁");
            }
        }
    }

    /**
     * 尝试获取一次许可（不抛异常）。
     *
     * @return true=放行，false=超限
     */
    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        while (true) {
            long windowStart = windowStartMs.get();
            if (now - windowStart >= windowMs) {
                if (windowStartMs.compareAndSet(windowStart, now)) {
                    counter.set(1);
                    return true;
                }
            } else {
                return counter.incrementAndGet() <= limit;
            }
        }
    }
}
