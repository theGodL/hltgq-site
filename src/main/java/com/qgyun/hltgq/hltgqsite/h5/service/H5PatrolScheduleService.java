package com.qgyun.hltgq.hltgqsite.h5.service;

import com.qgyun.hltgq.hltgqsite.h5.mapper.H5PatrolScheduleMapper;
import com.qgyun.hltgq.hltgqsite.h5.vo.H5PatrolSchedulePageVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.H5PatrolScheduleVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H5 巡检计划查询服务：仅「进行中 #2# / 已完成 #3#」两类计划，分页返回。
 * <p>任务名称模糊；状态筛选支持中文与编码双写法（进行中/已完成，未知值原样下传
 * 命中空结果）；计划类型/状态返回编码 + 权威中文双字段。
 * <p>分页：page 从 1 起（非正按 1）、size 默认 10 上限 100（非正按默认），
 * total 与列表同条件，空结果跳过明细查询。
 */
@Service
public class H5PatrolScheduleService {

    /** 单页条数上限（防大分页拖慢查询） */
    private static final long MAX_PAGE_SIZE = 100;

    /** 巡检计划状态编码 → 名称（status，仅列表可见的两档） */
    private static final Map<String, String> STATUS_LABELS = new LinkedHashMap<>();
    /** 计划类型编码 → 名称（xhonqv；#wavn# 为 2026-09 实网数据中的年度计划编码，平台字典待核对） */
    private static final Map<String, String> PLAN_TYPE_LABELS = new LinkedHashMap<>();

    static {
        STATUS_LABELS.put("#2#", "进行中");
        STATUS_LABELS.put("#3#", "已完成");

        PLAN_TYPE_LABELS.put("#wavn#", "年度计划");
        PLAN_TYPE_LABELS.put("#1#", "年度计划");
        PLAN_TYPE_LABELS.put("#zjgg#", "月度计划");
    }

    @Autowired
    private H5PatrolScheduleMapper mapper;

    /**
     * 巡检计划分页列表（按计划开始时间倒序，id 兜底排序稳定）。
     *
     * @param name   任务名称模糊（title），可选
     * @param status 状态：进行中/已完成 或编码 #2#/#3#，可选（不传返回两类全部）
     * @param page   页码（从 1 起；非正按 1）
     * @param size   每页条数（默认 10；非正按 10，上限 100）
     */
    public H5PatrolSchedulePageVO list(String name, String status, long page, long size) {
        long current = page > 0 ? page : 1;
        long pageSize = Math.min(size > 0 ? size : 10, MAX_PAGE_SIZE);

        H5PatrolSchedulePageVO result = new H5PatrolSchedulePageVO();
        result.setCurrent(current);
        result.setSize(pageSize);

        String keyword = trimToNull(name);
        String statusCode = resolveCode(STATUS_LABELS, status);
        long total = mapper.countScheduleList(keyword, statusCode);
        result.setTotal(total);
        result.setPages((total + pageSize - 1) / pageSize);
        if (total == 0) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        List<H5PatrolScheduleVO> rows = mapper.selectScheduleList(keyword, statusCode,
                pageSize, (current - 1) * pageSize);
        for (H5PatrolScheduleVO row : rows) {
            row.setPlanTypeLabel(label(PLAN_TYPE_LABELS, row.getPlanType()));
            row.setStatusLabel(label(STATUS_LABELS, row.getStatusCode()));
        }
        result.setRecords(rows);
        return result;
    }

    /** 单值编码 → 中文（未知编码原样返回） */
    private String label(Map<String, String> labels, String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        return labels.getOrDefault(code, code);
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
