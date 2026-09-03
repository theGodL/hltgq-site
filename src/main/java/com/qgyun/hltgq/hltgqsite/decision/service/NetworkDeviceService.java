package com.qgyun.hltgq.hltgqsite.decision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.decision.mapper.NetworkDeviceMapper;
import com.qgyun.hltgq.hltgqsite.decision.vo.NetworkDeviceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 网络设备监控总览：单次全量查设备表（千级），分类/状态聚合内存一次遍历。
 * <p>Redis 缓存 60s 防高频刷新击穿（大屏常驻页面）；Redis 不可达降级直查 DB。
 * <p>分类固定 7 项、顺序固定；设备运行状态仅 online/offline（#1#/#2#），未知编码按 offline。
 */
@Service
public class NetworkDeviceService {

    private static final Logger log = LoggerFactory.getLogger(NetworkDeviceService.class);

    private static final String CACHE_KEY = "decision:network-device:summary";
    private static final long CACHE_TTL_SECONDS = 60;

    /** 分类定义：顺序即返回顺序（类型编码 → key/名称/色值） */
    private static final String[][] CATEGORY_DEFS = {
            {"#1#", "waterLevel", "水位", "#2355D8"},
            {"#2#", "rainfall", "雨量", "#4EA450"},
            {"#3#", "flow", "流量", "#4497C9"},
            {"#4#", "gate", "闸门", "#6B3FDD"},
            {"#5#", "video", "视频", "#2354CD"},
            {"#7#", "soil", "墒情", "#99683E"},
            {"#8#", "quality", "水质", "#4EA7AD"},
    };

    @Autowired
    private NetworkDeviceMapper mapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /** 区域名 */
    @Value("${network-device.region-name:花凉亭灌区}")
    private String regionName;

    /** 网络设备监控总览 */
    public NetworkDeviceVO summary() {
        NetworkDeviceVO cached = readFromCache();
        if (cached != null) {
            return cached;
        }
        NetworkDeviceVO vo = build();
        writeToCache(vo);
        return vo;
    }

    private NetworkDeviceVO build() {
        List<NetworkDeviceVO.Device> devices = mapper.selectAllDevices();
        // 状态编码归一化：#1# → online、#2#/未知 → offline（契约：devices[].status 仅两种取值）；
        // 预处理后再聚合，保证分类计数与响应字段口径一致
        for (NetworkDeviceVO.Device d : devices) {
            d.setStatus("#1#".equals(d.getStatus()) ? "online" : "offline");
        }
        NetworkDeviceVO vo = new NetworkDeviceVO();
        vo.setRegionName(regionName);
        vo.setTotal(devices.size());

        int online = 0;
        int offline = 0;
        List<NetworkDeviceVO.Category> categories = new ArrayList<>(CATEGORY_DEFS.length);
        for (String[] def : CATEGORY_DEFS) {
            String typeCode = def[0];
            NetworkDeviceVO.Category cat = new NetworkDeviceVO.Category();
            cat.setKey(def[1]);
            cat.setName(def[2]);
            cat.setColor(def[3]);
            cat.setIcon(def[1]);
            List<NetworkDeviceVO.Device> list = new ArrayList<>();
            NetworkDeviceVO.Counts counts = new NetworkDeviceVO.Counts();
            for (NetworkDeviceVO.Device d : devices) {
                if (d.getType() == null || !d.getType().contains(typeCode)) {
                    continue;
                }
                list.add(d);
                if ("online".equals(d.getStatus())) {
                    counts.setOnline(counts.getOnline() + 1);
                } else {
                    counts.setOffline(counts.getOffline() + 1);
                }
            }
            cat.setTotal(list.size());
            cat.setCounts(counts);
            cat.setDevices(list);
            categories.add(cat);
        }
        // 顶部汇总按设备去重口径
        for (NetworkDeviceVO.Device d : devices) {
            if ("online".equals(d.getStatus())) {
                online++;
            } else {
                offline++;
            }
        }
        vo.setCategories(categories);
        vo.setSummary(buildSummary(online, offline, devices.size()));
        return vo;
    }

    private NetworkDeviceVO.Summary buildSummary(int online, int offline, int total) {
        NetworkDeviceVO.Summary summary = new NetworkDeviceVO.Summary();
        summary.setOnline(countPercent(online, total));
        summary.setOffline(countPercent(offline, total));
        summary.setAlarm(zero());
        summary.setFault(zero());
        return summary;
    }

    private NetworkDeviceVO.CountPercent countPercent(int count, int total) {
        NetworkDeviceVO.CountPercent cp = new NetworkDeviceVO.CountPercent();
        cp.setCount(count);
        cp.setPercent(total == 0 ? 0 : (int) Math.round(count * 100.0 / total));
        return cp;
    }

    private NetworkDeviceVO.CountPercent zero() {
        NetworkDeviceVO.CountPercent cp = new NetworkDeviceVO.CountPercent();
        cp.setCount(0);
        cp.setPercent(0);
        return cp;
    }

    private NetworkDeviceVO readFromCache() {
        try {
            String json = redisTemplate.opsForValue().get(CACHE_KEY);
            return json == null ? null : objectMapper.readValue(json, NetworkDeviceVO.class);
        } catch (Exception e) {
            log.debug("网络设备汇总缓存读取失败，降级直查: {}", e.getMessage());
            return null;
        }
    }

    private void writeToCache(NetworkDeviceVO vo) {
        try {
            redisTemplate.opsForValue().set(CACHE_KEY,
                    objectMapper.writeValueAsString(vo), CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("网络设备汇总缓存写入失败: {}", e.getMessage());
        }
    }
}
