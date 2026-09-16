package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.entity.StationSort;
import com.qgyun.hltgq.hltgqsite.mapper.StationSortMapper;
import com.qgyun.hltgq.hltgqsite.service.StationSiteService;
import com.qgyun.hltgq.hltgqsite.service.StationSortService;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSortVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 站点排序服务实现。
 * <p>存储：站点排序配置表（由平台模型创建），监测类型以平台单选枚举编码落库
 * （#1# 水位 / #2# 雨量 / #3# 流量 / #4# 闸门 / #5# 墒情），站点标识为站点管理主键；
 * 保存为整表覆盖（一次保存固化该类型全量站点顺序）。
 * <p>应用规则：已配置站点按配置序号升序，未配置站点保持各接口原有默认顺序并排在已配置之后；
 * 该类型未配置过顺序时原样返回，不影响既有展示。
 * <p>性能：配置量小（每类型数十行），进程内缓存 + 保存时主动失效，避免每次列表查询打库。
 */
@Service
public class StationSortServiceImpl implements StationSortService {

    private static final Logger log = LoggerFactory.getLogger(StationSortServiceImpl.class);

    /** 未配置站点的排序基准：配置序号从 1 起（正常远小于该值），未配置站点排在其后且保持默认顺序 */
    private static final int UNCONFIGURED_BASE = 100_000;

    /** 配置缓存有效期（毫秒）：站点排序列极少变动，保存时主动失效，此处仅兜底多实例部署场景 */
    private static final long CACHE_TTL_MS = 60_000L;

    /** 监测类型编码（metric_type 落库值，平台单选枚举） */
    private static final String METRIC_WATER_LEVEL = "#1#";
    private static final String METRIC_RAINFALL = "#2#";
    private static final String METRIC_FLOW = "#3#";
    private static final String METRIC_GATE = "#4#";
    private static final String METRIC_MOISTURE = "#5#";

    /** 站点顺序缓存（缓存键 = 监测类型编码，值 = 站点管理主键 → sort_no） */
    private final ConcurrentHashMap<String, CacheEntry> orderCache = new ConcurrentHashMap<>();

    @Autowired
    private StationSortMapper stationSortMapper;

    /**
     * 站点清单来源（与站点下拉同源）。
     * <p>@Lazy：站点集合服务依赖雨量服务，雨量服务又依赖本服务应用展示顺序，
     * 直接注入会形成循环依赖（Spring Boot 2.6+ 默认禁止循环引用），延迟注入打破环；
     * 仅在本服务 list/save 实际调用时才解析该依赖，不影响启动与性能。
     */
    @Lazy
    @Autowired
    private StationSiteService stationSiteService;

    @Override
    public List<StationSortVO> list(String metricType) {
        requireMetricCode(metricType);
        // 站点清单与站点下拉同源（StationSiteService），保证抽屉里列出的就是该类型页面能看到的站点
        List<StationSiteVO> sites = stationSiteService.sitesOfType(metricType);
        Map<String, Integer> order = orderOf(metricType);
        // 已配置站点按配置序号在前，未配置站点按默认顺序排在后面（无站点主键的站点一并排在其后）
        List<StationSiteVO> ordered = applyOrder(metricType, sites, StationSiteVO::getSiteId);
        List<StationSortVO> result = new ArrayList<>();
        int seq = 0;
        for (StationSiteVO site : ordered) {
            String siteId = trim(site.getSiteId());
            StationSortVO vo = new StationSortVO();
            vo.setSiteId(siteId);
            vo.setName(site.getName());
            vo.setSortNo(++seq);
            vo.setConfigured(siteId != null && order.containsKey(siteId));
            // 站点档案中登记的站点才可排序（配置表 site 存站点管理主键）
            vo.setSortable(siteId != null);
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int save(String metricType, List<String> siteIds) {
        String metricCode = requireMetricCode(metricType);
        List<StationSiteVO> sites = stationSiteService.sitesOfType(metricType);
        // 可排序白名单：站点管理主键 → 站名（站名仅用于日志核对，不落库，避免名称变更后失真）
        Map<String, String> nameBySiteId = new LinkedHashMap<>();
        for (StationSiteVO site : sites) {
            String siteId = trim(site.getSiteId());
            if (siteId != null) {
                nameBySiteId.putIfAbsent(siteId, site.getName());
            }
        }
        if (nameBySiteId.isEmpty()) {
            throw new IllegalArgumentException("监测类型 " + metricType + " 下站点均未在站点档案登记，无法保存排序");
        }

        // 提交顺序：去重 + 剔除不属于该类型或不可排序的标识（前端传错或历史脏数据不落库）
        int submitted = siteIds == null ? 0 : siteIds.size();
        List<String> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (siteIds != null) {
            for (String raw : siteIds) {
                String siteId = trim(raw);
                if (siteId == null || !nameBySiteId.containsKey(siteId)) continue;
                if (seen.add(siteId)) ordered.add(siteId);
            }
        }
        // 未提交的站点（新接入站点或前端漏传）按默认顺序追加在后，保证一次保存即固化该类型全量顺序
        for (String siteId : nameBySiteId.keySet()) {
            if (seen.add(siteId)) ordered.add(siteId);
        }

        // 整表覆盖：先清该类型旧配置，再按提交顺序写入
        stationSortMapper.delete(new QueryWrapper<StationSort>().eq("\"metric_type\"", metricCode));

        UserContext user = UserContextHolder.currentUser();
        LocalDateTime now = LocalDateTime.now();
        int seq = 0;
        for (String siteId : ordered) {
            StationSort entity = new StationSort();
            entity.setMetricType(metricCode);
            entity.setSite(siteId);
            entity.setSortNo(++seq);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            if (user != null) {
                entity.setCreatedBy(user.getUserId());
                entity.setUpdatedBy(user.getUserId());
                entity.setCorpCode(user.getCorpCode());
            }
            stationSortMapper.insert(entity);
        }
        orderCache.remove(metricCode);
        log.info("站点排序保存：type={}（metric_type={}），落库 {} 站（提交 {} 个标识，类型内可排序站点 {} 个）",
                metricType, metricCode, seq, submitted, nameBySiteId.size());
        return seq;
    }

    @Override
    public <T> List<T> applyOrder(String metricType, List<T> rows, Function<T, String> siteIdGetter) {
        if (rows == null || rows.size() < 2) {
            return rows;
        }
        Map<String, Integer> order = orderOf(metricType);
        if (order.isEmpty()) {
            // 该类型未配置过排序：保持各接口原有默认顺序
            return rows;
        }
        Map<String, Integer> defaultIndex = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            String siteId = trim(siteIdGetter.apply(rows.get(i)));
            if (siteId != null) {
                defaultIndex.putIfAbsent(siteId, i);
            }
        }
        List<T> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> Integer.compare(
                rankOf(a, order, defaultIndex, siteIdGetter),
                rankOf(b, order, defaultIndex, siteIdGetter)));
        return sorted;
    }

    @Override
    public List<String> configuredSiteIds(String metricType) {
        return new ArrayList<>(orderOf(metricType).keySet());
    }

    /** 排序键：已配置站点用配置序号；未配置站点用「基准 + 默认顺序索引」，排在已配置站点之后 */
    private <T> int rankOf(T row, Map<String, Integer> order, Map<String, Integer> defaultIndex,
                           Function<T, String> siteIdGetter) {
        String siteId = trim(siteIdGetter.apply(row));
        if (siteId != null) {
            Integer configured = order.get(siteId);
            if (configured != null) {
                return configured;
            }
            Integer index = defaultIndex.get(siteId);
            if (index != null) {
                return UNCONFIGURED_BASE + index;
            }
        }
        return UNCONFIGURED_BASE + defaultIndex.size();
    }

    /** 该类型当前顺序配置（站点管理主键 → sort_no）；未配置时返回空 Map */
    private Map<String, Integer> orderOf(String metricType) {
        String metricCode = metricCode(metricType);
        if (metricCode == null) {
            return Collections.emptyMap();
        }
        long now = System.currentTimeMillis();
        CacheEntry cached = orderCache.get(metricCode);
        if (cached != null && cached.expireAt > now) {
            return cached.order;
        }
        QueryWrapper<StationSort> wrapper = new QueryWrapper<>();
        wrapper.eq("\"metric_type\"", metricCode).orderByAsc("\"sort_no\"");
        Map<String, Integer> order = new LinkedHashMap<>();
        int fallbackSeq = 0;
        for (StationSort row : stationSortMapper.selectList(wrapper)) {
            String siteId = trim(row.getSite());
            if (siteId == null) continue;
            order.put(siteId, row.getSortNo() != null ? row.getSortNo() : ++fallbackSeq);
        }
        orderCache.put(metricCode, new CacheEntry(order, now + CACHE_TTL_MS));
        return order;
    }

    /** 语义类型 → 平台枚举编码；未知类型返回 null（应用顺序时按未配置处理） */
    private static String metricCode(String metricType) {
        if (metricType == null) return null;
        switch (metricType.trim()) {
            case "waterLevel":  return METRIC_WATER_LEVEL;
            // 雨量与灌区雨量共用同一序列（灌区站是雨量站子集，排序号在子集内保持相对次序）
            case "rainfall":
            case "gq-rainfall": return METRIC_RAINFALL;
            case "flow":        return METRIC_FLOW;
            case "gate":        return METRIC_GATE;
            case "moisture":    return METRIC_MOISTURE;
            default:            return null;
        }
    }

    /** 校验并返回平台枚举编码（外部接口入口用，未知类型报 400） */
    private static String requireMetricCode(String metricType) {
        String code = metricCode(metricType);
        if (code == null) {
            throw new IllegalArgumentException("无效的 type 值: " + metricType
                    + "，可选: waterLevel / rainfall / flow / gate / moisture");
        }
        return code;
    }

    /** 站点标识归一：去首尾空白，空串按 null 处理 */
    private static String trim(String code) {
        if (code == null) return null;
        String s = code.trim();
        return s.isEmpty() ? null : s;
    }

    /** 顺序缓存条目（含兜底过期时间） */
    private static class CacheEntry {
        final Map<String, Integer> order;
        final long expireAt;

        CacheEntry(Map<String, Integer> order, long expireAt) {
            this.order = order;
            this.expireAt = expireAt;
        }
    }
}
