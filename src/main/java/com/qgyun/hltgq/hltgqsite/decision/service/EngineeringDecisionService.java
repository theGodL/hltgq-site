package com.qgyun.hltgq.hltgqsite.decision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.decision.mapper.EngineeringDecisionMapper;
import com.qgyun.hltgq.hltgqsite.decision.vo.EngineeringSummaryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 工程管理决策总览：3 条聚合 SQL，固定项补齐 + 颜色内置。
 * <p>Redis 缓存 5 分钟（全量统计，刷新页面即最新，短 TTL 折中）；Redis 不可达降级直查。
 */
@Service
public class EngineeringDecisionService {

    private static final Logger log = LoggerFactory.getLogger(EngineeringDecisionService.class);

    private static final String CACHE_KEY = "decision:engineering:summary";
    private static final long CACHE_TTL_SECONDS = 300;

    /** 工单类型定义：编码 → 业务标准全称 + 饼图色值（顺序即返回顺序，null/未知归「其他」） */
    private static final String[][] ORDER_TYPE_DEFS = {
            {"#1#", "日常巡检", "#9B87F5"},
            {"#yjje#", "设备维护工单", "#F5C26B"},
            {"#hxqm#", "设备故障抢修工单", "#5ECBC8"},
            {"#zopb#", "水工建筑物维护工单", "#F08C8C"},
            {"#kjua#", "渠道及附属设施养护工单", "#5B6EE1"},
            {"#enak#", "计量与监测维护工单", "#73C892"},
            {"#bgtg#", "信息化与通信维护工单", "#8B6FD6"},
            {"#pfqj#", "安全隐患整改工单", "#6EC8F2"},
            {"#uvxb#", "应急处置工单", "#4A82E7"},
    };

    /** 问题状态定义：编码 → 名称 + 色值（顺序即返回顺序） */
    private static final String[][] ISSUE_STATUS_DEFS = {
            {"#1#", "待处理", "#7E76E6"},
            {"#2#", "处理中", "#EE8B88"},
            {"#3#", "已转工单", "#57BED3"},
            {"#4#", "已关闭", "#F9B552"},
            {"#5#", "已作废", "#4A82E7"},
    };

    @Autowired
    private EngineeringDecisionMapper mapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /** 工程管理决策总览 */
    public EngineeringSummaryVO summary() {
        EngineeringSummaryVO cached = readFromCache();
        if (cached != null) {
            return cached;
        }
        EngineeringSummaryVO vo = build();
        writeToCache(vo);
        return vo;
    }

    private EngineeringSummaryVO build() {
        EngineeringSummaryVO vo = new EngineeringSummaryVO();
        vo.setRiskStations(mapper.selectRiskStations());
        vo.setOrderTypes(buildOrderTypes(mapper.selectOrderTypes()));
        vo.setIssueStatus(buildIssueStatus(mapper.selectIssueStatus()));
        return vo;
    }

    /**
     * 工单类型固定 10 项补齐：9 类型 + 其他（null/未知编码归「其他」），无记录补 0。
     */
    private List<EngineeringSummaryVO.NamedValue> buildOrderTypes(List<EngineeringSummaryVO.NamedValue> rows) {
        Map<String, Long> countMap = new HashMap<>();
        long others = 0;
        for (EngineeringSummaryVO.NamedValue row : rows) {
            if (row.getName() == null) {
                others += row.getValue() == null ? 0 : row.getValue();
                continue;
            }
            if (isKnownOrderType(row.getName())) {
                countMap.put(row.getName(), row.getValue());
            } else {
                others += row.getValue() == null ? 0 : row.getValue();
            }
        }
        List<EngineeringSummaryVO.NamedValue> result = new ArrayList<>(ORDER_TYPE_DEFS.length + 1);
        for (String[] def : ORDER_TYPE_DEFS) {
            EngineeringSummaryVO.NamedValue item = new EngineeringSummaryVO.NamedValue();
            item.setName(def[1]);
            item.setValue(countMap.getOrDefault(def[0], 0L));
            item.setColor(def[2]);
            result.add(item);
        }
        EngineeringSummaryVO.NamedValue other = new EngineeringSummaryVO.NamedValue();
        other.setName("其他");
        other.setValue(others);
        other.setColor("#A8D8F0");
        result.add(other);
        return result;
    }

    private boolean isKnownOrderType(String code) {
        for (String[] def : ORDER_TYPE_DEFS) {
            if (def[0].equals(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 问题状态固定 5 项补齐，无记录补 0。
     */
    private List<EngineeringSummaryVO.NamedValue> buildIssueStatus(List<EngineeringSummaryVO.NamedValue> rows) {
        Map<String, Long> countMap = new HashMap<>();
        for (EngineeringSummaryVO.NamedValue row : rows) {
            if (row.getName() != null) {
                countMap.put(row.getName(), row.getValue());
            }
        }
        List<EngineeringSummaryVO.NamedValue> result = new ArrayList<>(ISSUE_STATUS_DEFS.length);
        for (String[] def : ISSUE_STATUS_DEFS) {
            EngineeringSummaryVO.NamedValue item = new EngineeringSummaryVO.NamedValue();
            item.setName(def[1]);
            item.setValue(countMap.getOrDefault(def[0], 0L));
            item.setColor(def[2]);
            result.add(item);
        }
        return result;
    }

    private EngineeringSummaryVO readFromCache() {
        try {
            String json = redisTemplate.opsForValue().get(CACHE_KEY);
            return json == null ? null : objectMapper.readValue(json, EngineeringSummaryVO.class);
        } catch (Exception e) {
            log.debug("工程管理汇总缓存读取失败，降级直查: {}", e.getMessage());
            return null;
        }
    }

    private void writeToCache(EngineeringSummaryVO vo) {
        try {
            redisTemplate.opsForValue().set(CACHE_KEY,
                    objectMapper.writeValueAsString(vo), CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("工程管理汇总缓存写入失败: {}", e.getMessage());
        }
    }
}
