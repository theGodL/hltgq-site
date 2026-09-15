package com.qgyun.hltgq.hltgqsite.weather.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 雷达瓦片进程内 LRU 缓存（方案 §4.3）。
 * <p><b>为什么不入 Redis</b>：瓦片是二进制、key 高基数（含帧 path），入 Redis 既不经济也无收益——
 * 帧 path 不可变（同一帧的瓦片内容永不变化），进程内缓存即可长期复用。
 * <p><b>为什么 LRU 是关键</b>：上游免费层限流 100 请求/IP/分钟，而地图拖动/缩放会瞬时产生数十请求；
 * z≤7 下常用视野的瓦片总数有限（数个缩放级 × 13 帧 ≈ 数百张），LRU 命中即不消耗上游配额（方案 §4.4「强缓存优先」）。
 * <p>实现：{@code LinkedHashMap(accessOrder=true)} + 容量上限淘汰；TTL 到期的条目在读取时被动删除
 * （不自建清理线程——瓦片 key 随帧滚动自然更替，且容量上限已封顶内存）。
 */
@Component
public class RadarTileCache {

    /** 单条瓦片：字节 + 到期时刻（epoch 毫秒） */
    private static class Entry {

        private final byte[] data;
        private final long expireAtMs;

        Entry(byte[] data, long expireAtMs) {
            this.data = data;
            this.expireAtMs = expireAtMs;
        }
    }

    private final int maxEntries;
    private final long ttlMs;
    private final LinkedHashMap<String, Entry> cache;

    public RadarTileCache(@Value("${radar.tile.cache-max-entries:3000}") int maxEntries,
                          @Value("${radar.tile.cache-ttl-minutes:30}") int ttlMinutes) {
        this.maxEntries = Math.max(1, maxEntries);
        this.ttlMs = Math.max(1, ttlMinutes) * 60_000L;
        // 构造器内匿名子类读的是局部副本：字段赋值与 map 创建的顺序在语义上无依赖，但用副本可避免
        // removeEldestEntry 在构造期间读到未初始化字段（保持代码意图明确）
        final int limit = this.maxEntries;
        this.cache = new LinkedHashMap<String, Entry>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > limit;
            }
        };
    }

    /**
     * 读瓦片。
     *
     * @return 瓦片字节；未命中或已过期返回 null（过期条目顺手删除）
     */
    public byte[] get(String key) {
        synchronized (cache) {
            Entry entry = cache.get(key);
            if (entry == null) {
                return null;
            }
            if (System.currentTimeMillis() >= entry.expireAtMs) {
                cache.remove(key);
                return null;
            }
            return entry.data;
        }
    }

    /** 写瓦片（容量超限时淘汰最久未访问的一条） */
    public void put(String key, byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        synchronized (cache) {
            cache.put(key, new Entry(data, System.currentTimeMillis() + ttlMs));
        }
    }

    /** 当前缓存条数（仅用于启动日志与观测） */
    public int size() {
        synchronized (cache) {
            return cache.size();
        }
    }

    /** 容量上限（仅用于日志） */
    public int maxEntries() {
        return maxEntries;
    }

    /** 瓦片缓存 key（方案 §4.3：{@code {path}_{z}_{x}_{y}}）；path 不可变，故同一帧瓦片可长期复用 */
    public static String key(String path, int z, int x, int y) {
        return path + "_" + z + "_" + x + "_" + y;
    }
}
