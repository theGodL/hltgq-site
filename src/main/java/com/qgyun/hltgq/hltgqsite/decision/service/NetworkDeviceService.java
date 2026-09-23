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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 网络设备监控总览：单次全量查设备表（千级），分类/状态聚合内存一次遍历。
 * <p>Redis 缓存 60s 防高频刷新击穿（大屏常驻页面）；Redis 不可达降级直查 DB。
 * <p>统计对象是「设备」（设备台账，一台算一台），与「站点」（站点档案）是两个概念：
 * 同一台设备只归入一个分类（多类型取首个命中分类的编码，见 {@link #primaryTypeCode}），
 * 台账未维护类型的设备计入 uncategorized，保证「各分类台数之和 + 未分类 = 设备总数」。
 * <p>分类固定 7 项、顺序固定；设备运行状态：设备表 status 优先（mq 报文入库时维护全部设备状态），
 * 为空时回退所属站点档案 zebpsu（与 /dashboard/overview 设备在线数、水位页同源），
 * 两处均无值或未知编码按 offline。
 * <p>告警：设备存在未关闭告警（告警表 status #1#/#2#/#3#，与 /dashboard/overview unhandledAlarmCount
 * 同表同过滤）即计 1 台，分类内按主类型归属统计；大屏算告警条数、本接口算告警设备台数。
 * 故障无数据源（告警表无故障维度、设备台账无故障标识）→ 响应置 null，前端不展示，不再用假 0 替代。
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
        // 告警设备集合：未关闭告警去重（同一台设备多条告警只算 1 台）；
        // 查询异常时降级为空集并告警日志（接口仍可用），避免告警库异常导致整页打不开
        Set<String> alertDeviceIds;
        try {
            List<String> alertIds = mapper.selectAlertDeviceIds();
            alertDeviceIds = alertIds == null ? Collections.<String>emptySet() : new HashSet<>(alertIds);
        } catch (Exception e) {
            alertDeviceIds = Collections.emptySet();
            log.warn("未关闭告警查询失败，本次告警计数降级为 0: {}", e.getMessage());
        }
        // 状态编码归一化：#1# → online、其余（#2#/未知/空）→ offline（契约：devices[].status 仅两种取值）；
        // 预处理后再聚合，保证分类计数与响应字段口径一致
        int fromSiteCount = 0;
        for (NetworkDeviceVO.Device d : devices) {
            if ("site".equals(d.getStatusFrom())) {
                fromSiteCount++;
            }
            d.setStatus(isOnline(d.getStatus()) ? "online" : "offline");
        }
        NetworkDeviceVO vo = new NetworkDeviceVO();
        vo.setRegionName(regionName);
        vo.setTotal(devices.size());

        // 设备按主类型唯一归类（type 多值取首个命中分类的编码），命中不到的计入「未分类」：
        // 保证「各分类台数之和 + 未分类 = 设备总数」，业主按分类相加能对上总数
        Map<String, List<NetworkDeviceVO.Device>> byTypeCode = new LinkedHashMap<>();
        for (String[] def : CATEGORY_DEFS) {
            byTypeCode.put(def[0], new ArrayList<>());
        }
        int uncategorized = 0;
        for (NetworkDeviceVO.Device d : devices) {
            String typeCode = primaryTypeCode(d.getType());
            if (typeCode == null) {
                uncategorized++;
            } else {
                byTypeCode.get(typeCode).add(d);
            }
        }

        int online = 0;
        int offline = 0;
        List<NetworkDeviceVO.Category> categories = new ArrayList<>(CATEGORY_DEFS.length);
        for (String[] def : CATEGORY_DEFS) {
            NetworkDeviceVO.Category cat = new NetworkDeviceVO.Category();
            cat.setKey(def[1]);
            cat.setName(def[2]);
            cat.setColor(def[3]);
            cat.setIcon(def[1]);
            List<NetworkDeviceVO.Device> list = byTypeCode.get(def[0]);
            NetworkDeviceVO.Counts counts = new NetworkDeviceVO.Counts();
            for (NetworkDeviceVO.Device d : list) {
                if ("online".equals(d.getStatus())) {
                    counts.setOnline(counts.getOnline() + 1);
                } else {
                    counts.setOffline(counts.getOffline() + 1);
                }
                // 告警与在线/离线正交：同一台设备可既在线又有未关闭告警
                if (alertDeviceIds.contains(d.getId())) {
                    counts.setAlarm(counts.getAlarm() + 1);
                }
            }
            cat.setTotal(list.size());
            cat.setCounts(counts);
            cat.setDevices(list);
            categories.add(cat);
        }
        // 顶部汇总按设备去重口径（= 各分类之和 + 未分类）；告警为存在未关闭告警的设备台数
        int alarmDevices = 0;
        for (NetworkDeviceVO.Device d : devices) {
            if ("online".equals(d.getStatus())) {
                online++;
            } else {
                offline++;
            }
            if (alertDeviceIds.contains(d.getId())) {
                alarmDevices++;
            }
        }
        vo.setUncategorized(uncategorized);
        vo.setCategories(categories);
        vo.setSummary(buildSummary(online, offline, alarmDevices, devices.size()));
        // 口径核对日志（联调）：设备 status 为空的台数按所属站点档案 zebpsu 回退判定；告警为未关闭告警去重台数
        log.info("网络设备状态口径：共 {} 台，在线 {} / 离线 {}；其中按站点 zebpsu 回退判定 {} 台；未分类（台账未维护类型）{} 台；告警设备 {} 台",
                devices.size(), online, offline, fromSiteCount, uncategorized, alarmDevices);
        return vo;
    }

    /** 在线编码兼容：#1# / #1 / 1（平台单选枚举在不同表下格式略有差异） */
    private static boolean isOnline(String status) {
        return "#1#".equals(status) || "#1".equals(status) || "1".equals(status);
    }

    /**
     * 设备主类型编码：type 多值（如 #1#|#2#）时取首个命中 7 类分类的编码，使设备唯一归属一个分类；
     * type 为空、或不含任何分类编码（如模型/气象）时返回 null，由调用方计入「未分类」。
     */
    private static String primaryTypeCode(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        for (String raw : type.split("\\|")) {
            String code = raw.trim();
            for (String[] def : CATEGORY_DEFS) {
                if (def[0].equals(code)) {
                    return code;
                }
            }
        }
        return null;
    }

    private NetworkDeviceVO.Summary buildSummary(int online, int offline, int alarm, int total) {
        NetworkDeviceVO.Summary summary = new NetworkDeviceVO.Summary();
        summary.setOnline(countPercent(online, total));
        summary.setOffline(countPercent(offline, total));
        summary.setAlarm(countPercent(alarm, total));
        // 故障：告警表无故障维度、设备台账无故障标识，暂无数据源 → 置空（前端隐藏该项），不用假 0 代替
        summary.setFault(null);
        return summary;
    }

    /** 计数 + 百分比：保留两位小数 HALF_UP，与 /dashboard/overview 同一规则；分母 0 → 0.00 */
    private NetworkDeviceVO.CountPercent countPercent(int count, int total) {
        NetworkDeviceVO.CountPercent cp = new NetworkDeviceVO.CountPercent();
        cp.setCount(count);
        cp.setPercent(total == 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
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
