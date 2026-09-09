package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.mapper.WaterQualityMapper;
import com.qgyun.hltgq.hltgqsite.service.WaterQualityService;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterQualityTrendVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterQualityVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterThresholdVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 水质监测服务实现
 */
@Service
public class WaterQualityServiceImpl implements WaterQualityService {

    @Autowired
    private WaterQualityMapper waterQualityMapper;

    @Override
    public List<WaterQualityVO> monitoring(List<String> stcds, LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime = startDate != null ? startDate.atStartOfDay() : null;
        // 截止日含当日全天：endDate +1 天 00:00（SQL 为 < 不含）
        LocalDateTime endTime = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;
        List<WaterQualityVO> records = waterQualityMapper.selectLatestPerStation(stcds, startTime, endTime);
        attachThresholds(records);
        return records;
    }

    @Override
    public WaterQualityTrendVO trend(String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        // 1. 默认时间范围：近 24 小时，起点/终点对齐偶数小时（桶边界与数据桶一致）
        LocalDateTime now = alignEvenHour(LocalDateTime.now());
        if (endTime == null) {
            endTime = now;
        } else {
            endTime = alignEvenHour(endTime);
        }
        if (startTime == null) {
            startTime = endTime.minusHours(24);
        } else {
            startTime = alignEvenHour(startTime);
        }
        if (startTime.isAfter(endTime)) {
            LocalDateTime t = startTime;
            startTime = endTime;
            endTime = t;
        }

        // 2. 获取站点名称与档案主键（阈值行关联用）
        String stnm = stcd;
        String siteId = null;
        List<WaterQualityVO> stationInfo = waterQualityMapper.selectLatestPerStation(
                Collections.singletonList(stcd), null, null);
        if (!stationInfo.isEmpty()) {
            WaterQualityVO row = stationInfo.get(0);
            if (row.getStnm() != null) {
                stnm = row.getStnm();
            }
            siteId = row.getSiteId();
        }

        // 3. 查询 2 小时级聚合（SQL 内已完成 -9991/-999 排除与 AVG）
        List<Map<String, Object>> rows = waterQualityMapper.selectTwoHourTrend(stcd, startTime, endTime);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        Map<String, Map<String, Object>> hourMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDateTime tm = toLocalDateTime(row.get("tm"));
            if (tm == null) continue;
            hourMap.put(tm.format(fmt), row);
        }

        // 4. 生成完整 2 小时序列（无数据桶各指标为 null，前端图表断线）
        List<WaterQualityTrendVO.HourPoint> points = new ArrayList<>();
        LocalDateTime hour = startTime;
        LocalDateTime endHour = endTime;
        while (!hour.isAfter(endHour)) {
            String key = hour.format(fmt);
            Map<String, Object> row = hourMap.get(key);
            WaterQualityTrendVO.HourPoint point = new WaterQualityTrendVO.HourPoint();
            point.setHour(key);
            if (row != null) {
                point.setNh3n(toBigDecimal(row.get("nh3n")));
                point.setCodcr(toBigDecimal(row.get("codcr")));
                point.setBod5(toBigDecimal(row.get("bod5")));
                point.setTp(toBigDecimal(row.get("tp")));
                point.setTn(toBigDecimal(row.get("tn")));
                point.setDox(toBigDecimal(row.get("dox")));
            }
            points.add(point);
            hour = hour.plusHours(2);
        }

        // 5. 组装结果（附该站水质阈值行供预警线）
        WaterQualityTrendVO vo = new WaterQualityTrendVO();
        vo.setStcd(stcd);
        vo.setStnm(stnm);
        vo.setStartTime(startTime);
        vo.setEndTime(endTime);
        vo.setPoints(points);
        vo.setThresholds(loadThresholds(siteId != null ? Collections.singletonList(siteId) : Collections.emptyList()));
        return vo;
    }

    @Override
    public Page<WaterQualityVO> history(String stcd, LocalDateTime startTime, LocalDateTime endTime,
                                        long page, long size) {
        long total = waterQualityMapper.selectHistoryCount(stcd, startTime, endTime);

        Page<WaterQualityVO> result = new Page<>(page, size);
        result.setTotal(total);

        if (total == 0) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        int offset = (int) ((page - 1) * size);
        int limit = (int) size;
        List<WaterQualityVO> records = waterQualityMapper.selectHistoryPage(stcd, startTime, endTime, limit, offset);
        result.setRecords(records);

        return result;
    }

    @Override
    public List<StationSiteVO> sites() {
        return waterQualityMapper.selectWaterQualityStations();
    }

    /**
     * 首页列表逐行装配阈值：按站点档案主键批量查 t_auto_hltgq_water_threshold（type 含 #8#）。
     * <p>站点行数据来自 nmisp_info，siteId 即 n.site（站点档案表主键），与阈值表 site 口径一致。
     */
    private void attachThresholds(List<WaterQualityVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (WaterQualityVO record : records) {
            record.setThresholds(Collections.emptyList());
        }
        List<String> siteIds = new ArrayList<>();
        for (WaterQualityVO record : records) {
            if (record.getSiteId() != null && !siteIds.contains(record.getSiteId())) {
                siteIds.add(record.getSiteId());
            }
        }
        if (siteIds.isEmpty()) {
            return;
        }
        Map<String, List<WaterThresholdVO>> grouped = new HashMap<>();
        for (WaterThresholdVO t : loadThresholds(siteIds)) {
            grouped.computeIfAbsent(t.getSite(), k -> new ArrayList<>()).add(t);
        }
        for (WaterQualityVO record : records) {
            if (record.getSiteId() == null) continue;
            record.setThresholds(grouped.getOrDefault(record.getSiteId(), Collections.emptyList()));
        }
    }

    /** 按站点档案主键列表批量查水质阈值行（空列表返回空） */
    private List<WaterThresholdVO> loadThresholds(List<String> siteIds) {
        if (siteIds == null || siteIds.isEmpty()) {
            return Collections.emptyList();
        }
        return waterQualityMapper.selectThresholdsBySites(siteIds);
    }

    /** 对齐偶数小时（趋势桶起点；奇数小时回退 1 小时，保证序列 00:00/02:00/...） */
    private LocalDateTime alignEvenHour(LocalDateTime time) {
        LocalDateTime truncated = time.truncatedTo(ChronoUnit.HOURS);
        return truncated.getHour() % 2 == 1 ? truncated.minusHours(1) : truncated;
    }

    /** Map 值 → LocalDateTime（Timestamp/LocalDateTime 兼容） */
    private LocalDateTime toLocalDateTime(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Timestamp) return ((Timestamp) obj).toLocalDateTime();
        if (obj instanceof LocalDateTime) return (LocalDateTime) obj;
        return null;
    }

    /** Map 值 → BigDecimal（null 安全） */
    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        try {
            return new BigDecimal(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
