package com.qgyun.hltgq.hltgqsite.h5.service;

import com.qgyun.hltgq.hltgqsite.h5.mapper.H5PatrolScheduleMapper;
import com.qgyun.hltgq.hltgqsite.h5.vo.H5PatrolSchedulePageVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.H5PatrolScheduleVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.H5PatrolStartVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.H5PatrolStationVO;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DictUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H5 巡检计划服务：列表查询 / 关联点位 / 开始巡检，均限定<b>当前登录人被指派</b>的计划。
 * <p>列表仅返回「已发布 #2# / 已完成 #3#」（他人计划不可见）；展示态与管理端同口径（按<b>巡检记录</b>推导）：
 * 已发布且计划尚无巡检记录=未开始 #5#、已有巡检记录=进行中 #2#；计划置已完成 #3# 即已完成；
 * #1# 草稿/#4# 已取消不参与推导。
 * <p>任务名称模糊；状态筛选支持中文与编码双写法（未开始/进行中/已完成，未知值 400）；
 * 计划类型/状态返回编码 + 权威中文双字段。
 * <p>关联点位（stations）：当前页计划一次 IN 批量查询巡查范围关系表 + 站点档案表
 * （名称/经纬度实时关联），按 nature_order 分组；无范围关系为空数组。
 * <p>开始巡检（start）：仅计划巡检人员本人、仅已发布计划可开始；写入一条草稿 #1# 占位记录
 * （巡检人强制为当前登录人，站点/设备/结果/内容留空）；计划已有记录时幂等返回原记录。
 * <p>分页：page 从 1 起（非正按 1）、size 默认 10 上限 100（非正按默认），
 * total 与列表同条件，空结果跳过明细查询。
 */
@Service
public class H5PatrolScheduleService {

    private static final Logger log = LoggerFactory.getLogger(H5PatrolScheduleService.class);

    /** 单页条数上限（防大分页拖慢查询） */
    private static final long MAX_PAGE_SIZE = 100;

    /** 巡检计划展示态编码 → 名称（#2# 为存储值，按巡检记录细分；#3# 已完成） */
    private static final Map<String, String> STATUS_LABELS = new LinkedHashMap<>();
    /** 计划类型编码 → 名称（xhonqv；#wavn# 为 2026-09 实网数据中的年度计划编码，平台字典待核对） */
    private static final Map<String, String> PLAN_TYPE_LABELS = new LinkedHashMap<>();

    static {
        STATUS_LABELS.put("#5#", "未开始");
        STATUS_LABELS.put("#2#", "进行中");
        STATUS_LABELS.put("#3#", "已完成");

        PLAN_TYPE_LABELS.put("#wavn#", "年度计划");
        PLAN_TYPE_LABELS.put("#1#", "年度计划");
        PLAN_TYPE_LABELS.put("#zjgg#", "月度计划");
    }

    @Autowired
    private H5PatrolScheduleMapper mapper;

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    /**
     * 我的巡检计划分页列表（仅当前登录人被指派的计划；按计划开始时间倒序，id 兜底排序稳定）。
     *
     * @param name   任务名称模糊（title），可选
     * @param status 展示态：未开始/进行中/已完成 或编码 #5#/#2#/#3#，可选（不传返回全部）
     * @param userId 当前登录人 id（主表 "user" 过滤：仅返回指派给自己的任务）
     * @param page   页码（从 1 起；非正按 1）
     * @param size   每页条数（默认 10；非正按 10，上限 100）
     */
    public H5PatrolSchedulePageVO list(String name, String status, String userId, long page, long size) {
        long current = page > 0 ? page : 1;
        long pageSize = Math.min(size > 0 ? size : 10, MAX_PAGE_SIZE);

        H5PatrolSchedulePageVO result = new H5PatrolSchedulePageVO();
        result.setCurrent(current);
        result.setSize(pageSize);

        String keyword = CheckUtils.trim(name);
        String displayStatus = resolveDisplayStatus(status);
        long total = mapper.countScheduleList(keyword, displayStatus, userId);
        result.setTotal(total);
        result.setPages((total + pageSize - 1) / pageSize);
        if (total == 0) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        List<H5PatrolScheduleVO> rows = mapper.selectScheduleList(keyword, displayStatus, userId,
                pageSize, (current - 1) * pageSize);
        for (H5PatrolScheduleVO row : rows) {
            row.setPlanTypeLabel(DictUtils.label(PLAN_TYPE_LABELS, row.getPlanType()));
            row.setStatusCode(deriveDisplayStatus(row.getStatusCode(), Boolean.TRUE.equals(row.getStarted())));
            row.setStatusLabel(DictUtils.label(STATUS_LABELS, row.getStatusCode()));
        }
        attachStations(rows);
        result.setRecords(rows);
        return result;
    }

    /**
     * 计划关联点位查询（添加巡检记录页点位选择）：按计划 id 返回该计划「巡查范围」设置的站点
     * （id/名称/经纬度，按 nature_order 顺序）；无范围关系返回空数组。
     * <p>归属校验：计划不存在或未指派给当前登录人一律 400，避免他人计划被越权读取/开始巡检。
     *
     * @param scheduleId 巡检计划 id（空 → 400）
     * @param userId     当前登录人 id
     */
    public List<H5PatrolStationVO> stations(String scheduleId, String userId) {
        String id = CheckUtils.trim(scheduleId);
        if (id == null) {
            throw new IllegalArgumentException("巡检计划 id 不能为空");
        }
        if (mapper.countScheduleOwnedBy(id, userId) == 0) {
            throw new IllegalArgumentException("巡检计划不存在或未指派给当前登录人");
        }
        return queryStationsGrouped(Collections.singletonList(id))
                .getOrDefault(id, Collections.emptyList());
    }

    /**
     * 开始巡检：校验计划归属与状态后写入一条草稿占位记录，使计划从「未开始」转为「进行中」。
     * <p>归属：仅计划巡检人员本人可开始（计划不存在/未指派给当前登录人 → 400）；
     * 状态：仅已发布 #2# 可开始（草稿 #1#/已完成 #3#/已取消 #4# → 400）。
     * <p>幂等：计划下已有巡检记录时直接返回最早一条记录 id，不重复写入；
     * 重复调用由写 SQL 的 NOT EXISTS 兜底，不会重复落开始记录。
     * <p>记录字段：巡检人强制为当前登录人（前端不可指定），状态草稿 #1#（不进入已提交统计），
     * 站点/设备/结果/内容留空；返回的 recordId 即当前空白占位草稿 id，
     * 后续提交（/patrol-record/update + /submit）优先用它填充+提交，不再另建记录，避免「草稿 + 正式」两条。
     *
     * @param scheduleId 巡检计划 id（必填）
     * @param userId     当前登录人 id（同时作为记录巡检人与审计列）
     */
    public H5PatrolStartVO start(String scheduleId, String userId) {
        String id = CheckUtils.trim(scheduleId);
        if (id == null) {
            throw new IllegalArgumentException("巡检计划 id 不能为空");
        }
        String status = mapper.selectOwnedScheduleStatus(id, userId);
        if (status == null) {
            throw new IllegalArgumentException("巡检计划不存在或未指派给当前登录人");
        }
        if ("#3#".equals(status)) {
            throw new IllegalArgumentException("巡检计划已完成，不能开始巡检");
        }
        if ("#4#".equals(status)) {
            throw new IllegalArgumentException("巡检计划已取消，不能开始巡检");
        }
        if (!"#2#".equals(status)) {
            throw new IllegalArgumentException("巡检计划未发布，不能开始巡检");
        }

        String recordId = mapper.selectFirstRecordId(id);
        if (recordId == null) {
            String newId = shortIdGenerator.nextUUID(null);
            int inserted = mapper.insertStartRecord(newId, id, userId, LocalDateTime.now());
            recordId = inserted > 0 ? newId : mapper.selectFirstRecordId(id);
        }

        H5PatrolStartVO result = new H5PatrolStartVO();
        result.setRecordId(recordId);
        result.setScheduleId(id);
        result.setStarted(true);
        result.setStatusCode("#2#");
        result.setStatusLabel(DictUtils.label(STATUS_LABELS, "#2#"));
        log.info("[H5巡检] 开始巡检：计划id={}, 记录id={}, 巡检人={}", id, recordId, userId);
        return result;
    }

    /**
     * 状态筛选归一为展示态编码；非白名单值（含草稿/已取消/已逾期）直接 400，
     * 避免静默返回空列表而难以排查。
     */
    private String resolveDisplayStatus(String status) {
        String code = DictUtils.resolveCode(STATUS_LABELS, status);
        if (code != null && !STATUS_LABELS.containsKey(code)) {
            throw new IllegalArgumentException("状态筛选仅支持 未开始/进行中/已完成");
        }
        return code;
    }

    /**
     * 展示态推导（与管理端 PatrolPlanService.deriveDisplayStatus 同口径）：
     * 已完成 #3# 原样返回；已发布 #2# 按该计划是否已开始巡检（存在巡检记录）——
     * 有记录=#2# 进行中、无记录=#5# 未开始。
     */
    private String deriveDisplayStatus(String storedStatus, boolean started) {
        if ("#2#".equals(storedStatus)) {
            return started ? "#2#" : "#5#";
        }
        return storedStatus;
    }

    /**
     * 关联点位装配：按当前页计划 id 一次 IN 批量查询（避免逐行 N+1），
     * 按 scheduleId 分组挂到各行；无范围关系的计划置空数组（与 rangeStationIds=null 对应）。
     */
    private void attachStations(List<H5PatrolScheduleVO> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<String> scheduleIds = new ArrayList<>(rows.size());
        for (H5PatrolScheduleVO row : rows) {
            scheduleIds.add(row.getId());
        }
        Map<String, List<H5PatrolStationVO>> grouped = queryStationsGrouped(scheduleIds);
        for (H5PatrolScheduleVO row : rows) {
            List<H5PatrolStationVO> stations = grouped.get(row.getId());
            row.setStations(stations == null ? Collections.emptyList() : stations);
        }
    }

    /** 关系行批量查询并按 scheduleId 分组（一次 IN；分组内元素保持查询行序，即 nature_order 顺序）。 */
    private Map<String, List<H5PatrolStationVO>> queryStationsGrouped(List<String> scheduleIds) {
        Map<String, List<H5PatrolStationVO>> grouped = new LinkedHashMap<>();
        for (H5PatrolScheduleMapper.ScheduleStationRow r : mapper.selectScheduleStations(scheduleIds)) {
            grouped.computeIfAbsent(r.getScheduleId(), k -> new ArrayList<>()).add(toStation(r));
        }
        return grouped;
    }

    /** 关系行 → 对外点位 VO（id/名称/经纬度）。 */
    private H5PatrolStationVO toStation(H5PatrolScheduleMapper.ScheduleStationRow r) {
        H5PatrolStationVO station = new H5PatrolStationVO();
        station.setId(r.getId());
        station.setName(r.getName());
        station.setLon(r.getLon());
        station.setLat(r.getLat());
        return station;
    }
}
