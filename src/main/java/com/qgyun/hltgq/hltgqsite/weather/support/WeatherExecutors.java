package com.qgyun.hltgq.hltgqsite.weather.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 天气模块命名线程池（评审 A5 约束）。
 * <p><b>严禁使用 {@code ForkJoinPool.commonPool}</b>（{@code CompletableFuture.supplyAsync} 的默认池）——
 * 境外慢源（EC46 / RainViewer）会阻塞全进程并行流。故所有上游调用与后台刷新均走本类提供的命名池：
 * <ul>
 *   <li>{@code weather-upstream}：请求链路内的并行上游调用（两段日级并行拉取），核心 2 / 最大 4 / 队列 64</li>
 *   <li>{@code weather-refresh}：stale 数据的后台异步刷新，核心 2 / 最大 4 / 队列 64</li>
 * </ul>
 */
@Component
public class WeatherExecutors {

    private static final Logger log = LoggerFactory.getLogger(WeatherExecutors.class);

    private final ThreadPoolExecutor upstreamPool;
    private final ThreadPoolExecutor refreshPool;

    public WeatherExecutors(@Value("${weather.upstream.pool-size:2}") int upstreamCore,
                            @Value("${weather.upstream.pool-max:4}") int upstreamMax,
                            @Value("${weather.upstream.pool-queue:64}") int upstreamQueue,
                            @Value("${weather.refresh.pool-size:2}") int refreshCore,
                            @Value("${weather.refresh.pool-max:4}") int refreshMax,
                            @Value("${weather.refresh.pool-queue:64}") int refreshQueue) {
        this.upstreamPool = new ThreadPoolExecutor(upstreamCore, upstreamMax,
                60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(upstreamQueue),
                namedFactory("weather-upstream"));
        this.upstreamPool.allowCoreThreadTimeOut(false);
        this.refreshPool = new ThreadPoolExecutor(refreshCore, refreshMax,
                60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(refreshQueue),
                namedFactory("weather-refresh"));
        this.refreshPool.allowCoreThreadTimeOut(false);
    }

    /** 请求链路内的并行上游调用池 */
    public ExecutorService upstream() {
        return upstreamPool;
    }

    /** stale 后台异步刷新池 */
    public ExecutorService refresh() {
        return refreshPool;
    }

    private ThreadFactory namedFactory(String prefix) {
        AtomicInteger seq = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + seq.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    public void shutdown() {
        shutdownQuietly("weather-upstream", upstreamPool);
        shutdownQuietly("weather-refresh", refreshPool);
    }

    private void shutdownQuietly(String name, ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("线程池 {} 已关闭", name);
    }
}
