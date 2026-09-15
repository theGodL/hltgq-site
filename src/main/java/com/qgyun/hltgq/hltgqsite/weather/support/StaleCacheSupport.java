package com.qgyun.hltgq.hltgqsite.weather.support;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.weather.client.WeatherCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * stale-while-revalidate 公共支撑（方案 §7.1）。
 * <p>提供四件事，供新接口（daily / typhoon）共用：
 * <ol>
 *   <li><b>单值信封缓存</b>：{@code {"updatedAt":"…","expireAt":<epochMs>,"data":{…}}}；
 *       Redis key 级 TTL = 新鲜期 + 旧值上限，使 stale 值在窗口内仍可读（超出即自然淘汰）。</li>
 *   <li><b>单飞</b>（miss 场景）：同 key 并发仅首个执行加载，其余等待同一结果，超时返回调用方给的兜底值。</li>
 *   <li><b>异步刷新去重</b>（stale 场景）：同 key 已有加载/刷新在跑则跳过，任务提交到 {@code weather-refresh} 命名池。</li>
 *   <li><b>Redis 异常降级</b>：读写失败一律降级为"未命中"，不影响主链路。</li>
 * </ol>
 * <p>单飞与异步刷新<b>共用同一 pending 表</b>（评审 A5 要求）：stale 场景的刷新不会被 miss 场景重复触发，反之亦然。
 */
@Component
public class StaleCacheSupport {

    private static final Logger log = LoggerFactory.getLogger(StaleCacheSupport.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WeatherExecutors executors;

    /** 进行中的加载/刷新：key → 首个执行者的 future（异步刷新为无结果占位） */
    private final ConcurrentHashMap<String, CompletableFuture<?>> pending = new ConcurrentHashMap<>();

    /**
     * 异步刷新占位：表示「该 key 有刷新任务在跑，但<b>没有可等待的结果</b>」。
     * <p>等待方（singleFlight）识别到本标记时立即返回兜底值——否则会白等满整个超时窗口
     * （本 future 只在刷新任务结束后才完成，最长可达上游读超时）。
     * <p><b>本标记永不 complete</b>，仅作引用比较，不得对其调用 get()。
     */
    private static final CompletableFuture<Object> REFRESH_PLACEHOLDER = new CompletableFuture<>();

    /** 单值缓存信封：data 为业务对象，expireAt 为新鲜期截止（epoch 毫秒） */
    public static class Envelope<T> {

        private String updatedAt;

        /** 拉取时刻（epoch 毫秒）：用于旧值上限判定（方案 §7.1） */
        private long pulledAtMs;

        private long expireAt;

        private T data;

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }

        public long getPulledAtMs() {
            return pulledAtMs;
        }

        public void setPulledAtMs(long pulledAtMs) {
            this.pulledAtMs = pulledAtMs;
        }

        public long getExpireAt() {
            return expireAt;
        }

        public void setExpireAt(long expireAt) {
            this.expireAt = expireAt;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }
    }

    /** 读信封；未命中或 Redis 异常返回 null（调用方按 miss 处理） */
    public <T> Envelope<T> read(String key, Class<T> dataType) {
        return read(key, objectMapper.getTypeFactory().constructType(dataType));
    }

    /**
     * 读信封（参数化类型版）：data 为 {@code List<X>} 等泛型集合时必须用本重载，
     * 否则集合元素会退化为 {@code Map}，调用方取字段将失败。
     */
    public <T> Envelope<T> read(String key, JavaType dataType) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            JavaType type = objectMapper.getTypeFactory().constructParametricType(Envelope.class, dataType);
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            log.warn("缓存读取失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 写信封。Redis key 级 TTL = ttlSeconds + staleMaxSeconds，
     * 保证 stale 值在旧值上限内仍可被读取用于兜底展示。
     */
    public void write(String key, Object data, long ttlSeconds, long staleMaxSeconds) {
        try {
            Envelope<Object> envelope = new Envelope<>();
            envelope.setUpdatedAt(LocalDateTime.now().toString());
            envelope.setPulledAtMs(nowMs());
            envelope.setExpireAt(nowMs() + ttlSeconds * 1000L);
            envelope.setData(data);
            long redisTtl = ttlSeconds + staleMaxSeconds;
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(envelope), redisTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("缓存写入失败 key={}: {}", key, e.getMessage());
        }
    }

    /** 立即失效（刷新写回前的清理，可选） */
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("缓存删除失败 key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 读原始值（<b>无信封</b>）：用于自带时间戳的段级缓存（方案 §3.4 规则二 `weather:daily:*`）。
     * <p>该结构的新鲜度由各段自己的 {@code pulledAt/expireAt} 判定，不能用顶层信封时钟。
     * 未命中或 Redis 异常返回 null（调用方按 miss 处理）。
     */
    public <T> T readRaw(String key, Class<T> type) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            return objectMapper.readValue(value, type);
        } catch (Exception e) {
            log.warn("缓存读取失败 key={}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 写原始值（<b>无信封</b>）：key 级 TTL 由调用方按段级 expireAt 计算
     * （方案 §3.4：key TTL = max(各段 expireAt) + 旧值上限）。
     */
    public void writeRaw(String key, Object value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value),
                    Math.max(1L, ttlSeconds), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("缓存写入失败 key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 单飞加载：同 key 并发仅首个执行 loader。
     * <p>等待方遇到以下两种情况均返回 fallback（调用方传入旧缓存数据，保证 stale 场景不因等待而丢数据）：
     * ① pending 中的持有者是「异步刷新占位」（无结果）——立即兜底，不白等；
     * ② 等待超时或异常。
     */
    @SuppressWarnings("unchecked")
    public <T> T singleFlight(String key, String label, Supplier<T> loader, T fallback, long waitSeconds) {
        CompletableFuture<T> future = new CompletableFuture<>();
        CompletableFuture<?> existing = pending.putIfAbsent(key, future);
        if (existing != null) {
            if (existing == REFRESH_PLACEHOLDER) {
                // 后台刷新正在进行：其占位无结果可复用，直接兜底而非等满超时（否则请求线程被白等占用）
                log.info("{} 后台刷新进行中（等待方不阻塞，直接兜底）key={}", label, key);
                return fallback;
            }
            try {
                Object result = existing.get(waitSeconds, TimeUnit.SECONDS);
                if (result != null) {
                    return (T) result;
                }
                log.info("{} 已有其他加载/刷新在跑（等待方复用兜底值）key={}", label, key);
            } catch (Exception e) {
                log.warn("{} 等待同 key 加载超时或异常 key={}: {}", label, key, e.getMessage());
            }
            return fallback;
        }
        try {
            T result;
            try {
                result = loader.get();
            } catch (WeatherCallException e) {
                log.warn("{} 上游失败 key={}: {}", label, key, e.getMessage());
                result = fallback;
            } catch (RuntimeException e) {
                // 上游格式意外变化（解析/转换异常）：旁路数据降级兜底，不抛 5xx
                log.error("{} 加载异常 key={}", label, key, e);
                result = fallback;
            }
            future.complete(result);
            return result;
        } finally {
            // 两参数版：只移除自己放入的 future，避免误删后继者（进程内竞态防护）
            pending.remove(key, future);
        }
    }

    /**
     * 异步刷新（stale 场景）：同 key 已有加载/刷新在跑则跳过；否则提交到 weather-refresh 池。
     * <p>刷新任务异常只打日志，不影响请求链路。
     * <p>占位用共享标记（{@link #REFRESH_PLACEHOLDER}）：等待方据此立即兜底，不必等满超时。
     */
    public void refreshAsync(String key, String label, Runnable task) {
        CompletableFuture<?> existing = pending.putIfAbsent(key, REFRESH_PLACEHOLDER);
        if (existing != null) {
            log.debug("{} 已有加载/刷新在跑，跳过本次异步刷新 key={}", label, key);
            return;
        }
        try {
            executors.refresh().execute(() -> {
                try {
                    task.run();
                    log.info("{} 后台刷新完成 key={}", label, key);
                } catch (Exception e) {
                    log.warn("{} 后台刷新失败 key={}: {}", label, key, e.getMessage());
                } finally {
                    // 只移除自己放入的占位
                    pending.remove(key, REFRESH_PLACEHOLDER);
                }
            });
        } catch (RejectedExecutionException e) {
            pending.remove(key, REFRESH_PLACEHOLDER);
            log.warn("{} 刷新池已满，跳过本次异步刷新 key={}", label, key);
        }
    }

    public static long nowMs() {
        return System.currentTimeMillis();
    }
}
