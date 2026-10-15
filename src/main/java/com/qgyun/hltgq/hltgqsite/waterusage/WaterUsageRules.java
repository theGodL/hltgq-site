package com.qgyun.hltgq.hltgqsite.waterusage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 灌区用水规则同源读取（设计方案 2.4：前端 static/lib/rules.js 与 static/lib/rules.json 成对维护）。
 * <p>本类在运行时读取 classpath:static/lib/rules.json，向三页面 meta 输出 {@code rulesVersion}；
 * 前端加载 rules.js 后对比自身 RULES_VERSION 与 meta 返回值，不一致则页面顶部黄条提示（常驻）并在控制台告警。
 * <p>规则值本身（换算/舍入/周期公式）以后端 Java 实现为准（{@link WaterUsageSupport}/{@link WaterUsageDict}），
 * 启动后首次读取时做一次轻量一致性核对（万m³ 换算基数、存储/展示位数），不一致仅告警不中断。
 */
public final class WaterUsageRules {

    private static final Logger log = LoggerFactory.getLogger(WaterUsageRules.class);

    /** 规则文件路径（classpath；与前端 lib/rules.js 同目录同版本） */
    private static final String RESOURCE = "static/lib/rules.json";

    private static volatile JsonNode cache;

    private static volatile boolean consistencyChecked;

    private WaterUsageRules() {
    }

    /** 规则版本号（读取失败返回空串；页面据此比对，不一致黄条提示） */
    public static String version() {
        JsonNode node = load();
        if (node == null) {
            return "";
        }
        return node.path("rules_version").asText("");
    }

    /** classpath 读取 rules.json（缓存；失败返回 null 并告警） */
    private static JsonNode load() {
        JsonNode node = cache;
        if (node != null) {
            return node;
        }
        try (InputStream in = WaterUsageRules.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("[灌区用水] 规则文件不存在：{}（页面版本比对降级为跳过）", RESOURCE);
                return null;
            }
            node = new ObjectMapper().readTree(in);
            cache = node;
            checkConsistency(node);
            return node;
        } catch (Exception e) {
            log.warn("[灌区用水] 规则文件读取失败：{}（{}）", RESOURCE, e.getMessage());
            return null;
        }
    }

    /** 一致性核对（仅告警）：万m³ 换算基数与后端常量、存储/展示位数与实现口径 */
    private static void checkConsistency(JsonNode node) {
        if (consistencyChecked) {
            return;
        }
        consistencyChecked = true;
        try {
            Map<String, Object> units = new ObjectMapper().convertValue(node.path("units"), Map.class);
            Object factor = units == null ? null : units.get("wan_factor");
            if (factor == null || new BigDecimal(String.valueOf(factor))
                    .compareTo(WaterUsageSupport.WAN) != 0) {
                log.warn("[灌区用水] 规则文件 wan_factor 与后端换算基数不一致：{}", factor);
            }
            Object storeScale = units == null ? null : units.get("store_scale");
            if (!"2".equals(String.valueOf(storeScale))) {
                log.warn("[灌区用水] 规则文件 store_scale 与后端存储口径（2 位）不一致：{}", storeScale);
            }
            Object displayScale = units == null ? null : units.get("display_scale");
            if (!"1".equals(String.valueOf(displayScale))) {
                log.warn("[灌区用水] 规则文件 display_scale 与后端展示口径（1 位）不一致：{}", displayScale);
            }
        } catch (Exception e) {
            log.warn("[灌区用水] 规则文件一致性核对失败：{}", e.getMessage());
        }
    }
}
