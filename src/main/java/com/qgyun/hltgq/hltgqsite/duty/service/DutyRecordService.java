package com.qgyun.hltgq.hltgqsite.duty.service;

import com.qgyun.hltgq.hltgqsite.duty.mapper.DutyRecordMapper;
import com.qgyun.hltgq.hltgqsite.duty.vo.DutyRecordVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 值班记录查询服务：状态编码 → 中文（口径见《数据表结构汇总.md》第 11 节）。
 * <p>人员过滤：关键词先按用户 id/姓名解析出 id，SQL 同时按
 * 值班人员多选列（双路 LIKE：原文与解析出的用户 id，兼容两种存储形态）与带班领导匹配。
 */
@Service
public class DutyRecordService {

    /** 状态编码 → 名称（ylncfw，无 #1# 档） */
    private static final Map<String, String> STATUS_LABELS = new LinkedHashMap<>();

    static {
        STATUS_LABELS.put("#2#", "值班中");
        STATUS_LABELS.put("#3#", "已完成");
        STATUS_LABELS.put("#4#", "异常结束");
    }

    @Autowired
    private DutyRecordMapper mapper;

    /**
     * 值班记录列表。
     *
     * @param date   值班日期（yyyy-MM-dd，含当日），可选
     * @param shift  班次时间模糊（lzcjgq），可选
     * @param unit   所在单位模糊（sdcgli），可选
     * @param person 人员：姓名或用户 id（匹配值班人员/带班领导），可选
     */
    public List<DutyRecordVO> list(LocalDate date, String shift, String unit, String person) {
        String dayStart = date == null ? null : date.toString();
        String dayEnd = date == null ? null : date.plusDays(1).toString();
        String personKeyword = trimToNull(person);
        String personId = personKeyword == null ? null : mapper.selectUserId(personKeyword);

        List<DutyRecordVO> rows = mapper.selectDutyList(dayStart, dayEnd, trimToNull(shift),
                trimToNull(unit), personKeyword, personId);
        for (DutyRecordVO row : rows) {
            row.setStatus(STATUS_LABELS.getOrDefault(row.getStatusCode(), row.getStatusCode()));
            row.setDutyDate(normalizeDate(row.getDutyDateRaw()));
            row.setDutyDateRaw(null);
        }
        return rows;
    }

    /** 值班日期规范化：时间戳形态取前 10 位（如 2026-05-13 00:00:00 → 2026-05-13），其余原样 */
    private String normalizeDate(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return raw.length() > 10 ? raw.substring(0, 10) : raw;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
