package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.mapper.SoilMoistureMapper;
import com.qgyun.hltgq.hltgqsite.service.SoilMoistureService;
import com.qgyun.hltgq.hltgqsite.vo.SoilMoistureTrendVO;
import com.qgyun.hltgq.hltgqsite.vo.SoilMoistureVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 墒情监测服务实现
 */
@Service
public class SoilMoistureServiceImpl implements SoilMoistureService {

    @Autowired
    private SoilMoistureMapper soilMoistureMapper;

    @Override
    public List<SoilMoistureVO> monitoring(List<String> stcds, LocalDate date) {
        LocalDateTime startTime = date != null ? date.atStartOfDay() : null;
        LocalDateTime endTime = date != null ? date.plusDays(1).atStartOfDay() : null;
        return soilMoistureMapper.selectLatestPerStation(stcds, startTime, endTime);
    }

    @Override
    public SoilMoistureTrendVO trend(String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        // 1. 默认时间范围：近 7 天（与流量趋势一致）
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        if (endTime == null) {
            endTime = now;
        }
        if (startTime == null) {
            startTime = endTime.minusDays(7);
        }

        // 2. 获取站点名称
        String stnm = stcd;
        List<SoilMoistureVO> stationInfo = soilMoistureMapper.selectLatestPerStation(
                Collections.singletonList(stcd), null, null);
        if (!stationInfo.isEmpty() && stationInfo.get(0).getStnm() != null) {
            stnm = stationInfo.get(0).getStnm();
        }

        // 3. 查询小时级聚合（SQL 内已完成 -999 排除与 AVG）
        List<Map<String, Object>> rows = soilMoistureMapper.selectHourlyTrend(stcd, startTime, endTime);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        Map<String, Map<String, Object>> hourMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDateTime tm = toLocalDateTime(row.get("tm"));
            if (tm == null) continue;
            hourMap.put(tm.truncatedTo(ChronoUnit.HOURS).format(fmt), row);
        }

        // 4. 生成完整小时序列（无数据小时各字段为 null，前端图表断线）
        List<SoilMoistureTrendVO.HourPoint> points = new ArrayList<>();
        LocalDateTime hour = startTime.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime endHour = endTime.truncatedTo(ChronoUnit.HOURS);
        while (!hour.isAfter(endHour)) {
            String key = hour.format(fmt);
            Map<String, Object> row = hourMap.get(key);
            SoilMoistureTrendVO.HourPoint point = new SoilMoistureTrendVO.HourPoint();
            point.setHour(key);
            if (row != null) {
                point.setMten(toBigDecimal(row.get("mten")));
                point.setMtwenty(toBigDecimal(row.get("mtwenty")));
                point.setMthirty(toBigDecimal(row.get("mthirty")));
                point.setMforty(toBigDecimal(row.get("mforty")));
                point.setMfifty(toBigDecimal(row.get("mfifty")));
                point.setMsixty(toBigDecimal(row.get("msixty")));
                point.setMeighty(toBigDecimal(row.get("meighty")));
                point.setMhundred(toBigDecimal(row.get("mhundred")));
            }
            points.add(point);
            hour = hour.plusHours(1);
        }

        // 5. 组装结果
        SoilMoistureTrendVO vo = new SoilMoistureTrendVO();
        vo.setStcd(stcd);
        vo.setStnm(stnm);
        vo.setStartTime(startTime);
        vo.setEndTime(endTime);
        vo.setPoints(points);
        return vo;
    }

    @Override
    public Page<SoilMoistureVO> history(String stcd, LocalDateTime startTime, LocalDateTime endTime,
                                        long page, long size) {
        long total = soilMoistureMapper.selectHistoryCount(stcd, startTime, endTime);

        Page<SoilMoistureVO> result = new Page<>(page, size);
        result.setTotal(total);

        if (total == 0) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        int offset = (int) ((page - 1) * size);
        int limit = (int) size;
        List<SoilMoistureVO> records = soilMoistureMapper.selectHistoryPage(stcd, startTime, endTime, limit, offset);
        result.setRecords(records);

        return result;
    }

    @Override
    public List<StationSiteVO> sites() {
        return soilMoistureMapper.selectMoistureStations();
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
