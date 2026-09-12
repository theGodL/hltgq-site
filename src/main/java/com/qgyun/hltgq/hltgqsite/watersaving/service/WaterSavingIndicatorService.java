package com.qgyun.hltgq.hltgqsite.watersaving.service;

import com.qgyun.hltgq.hltgqsite.watersaving.mapper.WaterSavingIndicatorMapper;
import com.qgyun.hltgq.hltgqsite.watersaving.vo.WaterSavingIndicatorVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节水体系查询服务：状态编码 → 中文（口径见《数据表结构汇总.md》第 9.1 节）。
 * <p>年度规范化：'2026-…' 时间戳形态取前 4 位，文本原样；
 * 过滤参数支持中文与编码双写法。
 */
@Service
public class WaterSavingIndicatorService {

    /** 状态编码 → 名称（zkcllb，5 档） */
    private static final Map<String, String> STATUS_LABELS = new LinkedHashMap<>();

    static {
        STATUS_LABELS.put("#1#", "待填报");
        STATUS_LABELS.put("#vctx#", "已填报");
        STATUS_LABELS.put("#qwhw#", "审核中");
        STATUS_LABELS.put("#fnwg#", "已退回");
        STATUS_LABELS.put("#ylil#", "已归档");
    }

    @Autowired
    private WaterSavingIndicatorMapper mapper;

    /**
     * 节水体系列表。
     *
     * @param name   指标名称模糊，可选
     * @param year   年度（YYYY），可选
     * @param status 状态：待填报/已填报/审核中/已退回/已归档 或编码，可选
     */
    public List<WaterSavingIndicatorVO> list(String name, String year, String status) {
        List<WaterSavingIndicatorVO> rows = mapper.selectIndicatorList(trimToNull(name),
                trimToNull(year), resolveCode(STATUS_LABELS, status));
        for (WaterSavingIndicatorVO row : rows) {
            row.setStatus(STATUS_LABELS.getOrDefault(row.getStatusCode(), row.getStatusCode()));
            row.setYear(normalizeYear(row.getYearRaw()));
            row.setYearRaw(null);
        }
        return rows;
    }

    /** 年度规范化：时间戳形态取前 4 位（如 2026-01-01 00:00:00 → 2026），其余原样 */
    private String normalizeYear(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return raw.length() > 4 && raw.charAt(4) == '-' ? raw.substring(0, 4) : raw;
    }

    /** 过滤值归一：空 → null；编码（#...#）原样；中文 → 编码；未知原样下传 */
    private String resolveCode(Map<String, String> labels, String value) {
        String v = trimToNull(value);
        if (v == null || v.startsWith("#")) {
            return v;
        }
        for (Map.Entry<String, String> e : labels.entrySet()) {
            if (e.getValue().equals(v)) {
                return e.getKey();
            }
        }
        return v;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
