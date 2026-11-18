package com.qgyun.hltgq.hltgqsite.service.impl;

import com.qgyun.hltgq.hltgqsite.entity.Canal;
import com.qgyun.hltgq.hltgqsite.mapper.CanalMapper;
import com.qgyun.hltgq.hltgqsite.service.IrrigationScopeService;
import com.qgyun.hltgq.hltgqsite.vo.SiteScopeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 灌区归属范围实现：渠系表全量几十行，内存建子树即可（与 {@code CanalServiceImpl} 同源同口径）。
 * <p>顶级灌区按渠系名称定位（麻塘湖 / 花凉亭），名称缺失时回退生产渠系 id，
 * 避免渠系被改名后整个 Tab 站点清空。
 */
@Service
public class IrrigationScopeServiceImpl implements IrrigationScopeService {

    private static final Logger log = LoggerFactory.getLogger(IrrigationScopeServiceImpl.class);

    /** 麻塘湖灌区顶级渠系兜底 id（渠系名称匹配失败时使用） */
    private static final String CANAL_MTH_FALLBACK_ID = "grTcKYo1RogxZCVkd5H";

    /** 花凉亭灌区顶级渠系兜底 id（渠系名称匹配失败时使用） */
    private static final String CANAL_HLT_FALLBACK_ID = "TYeWxr64qRDPZ6tFCy0";

    /** 顶级灌区名称关键字 */
    private static final String CANAL_MTH_KEYWORD = "麻塘湖";
    private static final String CANAL_HLT_KEYWORD = "花凉亭";

    @Autowired
    private CanalMapper canalMapper;

    /**
     * 麻塘湖兜底站（档案未挂渠系、按业务口径归麻塘湖）：默认麻塘湖水库 8000000001、
     * 麻塘湖水库自计井 0062803835；档案补齐渠系后可从此配置移除
     */
    @Value("${hltgq.mth-extra-stcds:8000000001,0062803835}")
    private String mthExtraStcds;

    @Override
    public SiteScopeFilter resolve(String zone) {
        String normalized = normalize(zone);
        if (normalized == null) {
            return null;
        }
        List<Canal> canals = canalMapper.selectAll();
        String mthTopId = findTopCanalId(canals, CANAL_MTH_KEYWORD, CANAL_MTH_FALLBACK_ID);
        String hltTopId = findTopCanalId(canals, CANAL_HLT_KEYWORD, CANAL_HLT_FALLBACK_ID);
        List<String> mthCanalIds = collectSubtreeCanalIds(canals, mthTopId);

        SiteScopeFilter filter = new SiteScopeFilter();
        filter.setZone(normalized);
        if (ZONE_MTH.equals(normalized)) {
            // 麻塘湖：只含麻塘湖子树 + 兜底站；空渠系站点不归麻塘湖
            filter.setCanalIds(mthCanalIds);
            filter.setIncludeUnassigned(false);
            filter.setIncludeStcds(mthFallbackStcds());
        } else {
            // 花凉亭：花凉亭子树（去掉麻塘湖子树，防止麻塘湖灌区被挂在花凉亭下时重复归属）
            // + 空渠系站点，再排除麻塘湖兜底站
            List<String> hltCanalIds = collectSubtreeCanalIds(canals, hltTopId);
            hltCanalIds.removeAll(mthCanalIds);
            filter.setCanalIds(hltCanalIds);
            filter.setIncludeUnassigned(true);
            filter.setExcludeStcds(mthFallbackStcds());
        }
        // 渠系列表为空时 SQL 侧无法构造有效条件（IN 空列表），按不过滤处理并告警
        if (filter.getCanalIds().isEmpty()) {
            log.warn("灌区范围 {} 未解析到任何渠系，忽略范围过滤", normalized);
            return null;
        }
        return filter;
    }

    @Override
    public boolean matches(SiteScopeFilter filter, String canalId, String... stationKeys) {
        if (filter == null) {
            return true;
        }
        List<String> keys = normalizeKeys(stationKeys);
        // 1. 显式包含优先（麻塘湖兜底站即使档案挂了别的渠系，也归麻塘湖）
        if (intersects(filter.getIncludeStcds(), keys)) {
            return true;
        }
        // 2. 显式排除（花凉亭范围排除麻塘湖兜底站，含空渠系兜底）
        if (intersects(filter.getExcludeStcds(), keys)) {
            return false;
        }
        // 3. 渠系命中（含子孙渠系）
        String cid = trim(canalId);
        if (cid != null && filter.getCanalIds() != null && filter.getCanalIds().contains(cid)) {
            return true;
        }
        // 4. 空渠系归属：花凉亭包含，麻塘湖不包含
        return filter.isIncludeUnassigned() && cid == null;
    }

    @Override
    public String normalize(String zone) {
        if (zone == null) {
            return null;
        }
        String value = zone.trim();
        if (value.isEmpty() || "all".equalsIgnoreCase(value)) {
            return null;
        }
        if (ZONE_HLT.equalsIgnoreCase(value)) {
            return ZONE_HLT;
        }
        if (ZONE_MTH.equalsIgnoreCase(value)) {
            return ZONE_MTH;
        }
        throw new IllegalArgumentException("无效的 zone 值: " + zone
                + "，可选: hlt(花凉亭灌区) / mth(麻塘湖灌区)");
    }

    /** 麻塘湖兜底站编号清单：解析配置（逗号分隔），去空白去重 */
    private List<String> mthFallbackStcds() {
        List<String> stcds = new ArrayList<>();
        if (mthExtraStcds == null) {
            return stcds;
        }
        for (String raw : mthExtraStcds.split(",")) {
            String stcd = trim(raw);
            if (stcd != null && !stcds.contains(stcd)) {
                stcds.add(stcd);
            }
        }
        return stcds;
    }

    /**
     * 定位顶级灌区渠系 id：先按「顶级节点 + 名称包含关键字」匹配，未命中再放宽到全部节点
     * （渠系层级被调整时仍可用），最后回退生产渠系 id。
     */
    private String findTopCanalId(List<Canal> canals, String keyword, String fallbackId) {
        for (Canal canal : canals) {
            String id = trim(canal.getId());
            String name = trim(canal.getName());
            if (id != null && name != null && trim(canal.getParentId()) == null && name.contains(keyword)) {
                return id;
            }
        }
        for (Canal canal : canals) {
            String id = trim(canal.getId());
            String name = trim(canal.getName());
            if (id != null && name != null && name.contains(keyword)) {
                return id;
            }
        }
        log.warn("渠系树中未找到名称含「{}」的灌区节点，回退渠系 id {}", keyword, fallbackId);
        return fallbackId;
    }

    /** BFS 收集渠系自身 + 全部子孙渠系 id（保序去重） */
    private List<String> collectSubtreeCanalIds(List<Canal> canals, String rootId) {
        List<String> result = new ArrayList<>();
        if (rootId == null) {
            return result;
        }
        Map<String, List<String>> childrenMap = new HashMap<>();
        for (Canal canal : canals) {
            String id = trim(canal.getId());
            if (id == null) {
                continue;
            }
            childrenMap.computeIfAbsent(trim(canal.getParentId()), k -> new ArrayList<>()).add(id);
        }
        Deque<String> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current == null || result.contains(current)) {
                continue;
            }
            result.add(current);
            List<String> children = childrenMap.get(current);
            if (children != null) {
                queue.addAll(children);
            }
        }
        return result;
    }

    /** 站点标识归一：去首尾空白、去空值 */
    private static List<String> normalizeKeys(String... stationKeys) {
        List<String> keys = new ArrayList<>();
        if (stationKeys == null) {
            return keys;
        }
        for (String key : stationKeys) {
            String value = trim(key);
            if (value != null && !keys.contains(value)) {
                keys.add(value);
            }
        }
        return keys;
    }

    /** 两个标识清单是否有交集（站点编号 / 站点管理主键任一命中即可） */
    private static boolean intersects(List<String> configured, List<String> keys) {
        if (configured == null || configured.isEmpty() || keys.isEmpty()) {
            return false;
        }
        for (String key : keys) {
            if (configured.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** 标识归一：去首尾空白，空串按 null 处理 */
    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }
}
