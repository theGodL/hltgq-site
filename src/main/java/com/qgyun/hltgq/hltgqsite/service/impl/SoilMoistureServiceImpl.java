package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.mapper.SoilMoistureMapper;
import com.qgyun.hltgq.hltgqsite.service.CanalService;
import com.qgyun.hltgq.hltgqsite.service.SoilMoistureService;
import com.qgyun.hltgq.hltgqsite.service.StationSortService;
import com.qgyun.hltgq.hltgqsite.vo.SoilMoistureTrendVO;
import com.qgyun.hltgq.hltgqsite.vo.SoilMoistureVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.qgyun.hltgq.hltgqsite.model.util.StationStatusUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ValueConvertUtils;

/**
 * 墒情监测服务实现
 */
@Service
public class SoilMoistureServiceImpl implements SoilMoistureService {

    @Autowired
    private SoilMoistureMapper soilMoistureMapper;

    @Autowired
    private StationSortService stationSortService;

    @Autowired
    private CanalService canalService;

    @Override
    public List<SoilMoistureVO> monitoring(List<String> stcds, String canalId, LocalDate date) {
        LocalDateTime startTime = date != null ? date.atStartOfDay() : null;
        LocalDateTime endTime = date != null ? date.plusDays(1).atStartOfDay() : null;
        // 渠系树过滤：canalId 非空时收集该渠系及所有子孙渠系 id（含自身）
        List<String> canalIds = canalService.collectDescendantCanalIds(canalId);
        List<SoilMoistureVO> rows = soilMoistureMapper.selectLatestPerStation(stcds, canalIds, startTime, endTime);
        // 在线状态：档案手动离线（zebpsu=#2#）优先判离线；否则按最新采集时间断联判定
        rows.forEach(r -> r.setIsOnline(StationStatusUtils.isZebpsuOnline(r.getZebpsu()) && !StationStatusUtils.isStale(r.getTm(), r.getStnm())));
        // 站点排序配置（墒情监测类型）：已配置站点按配置顺序，未配置站点保持 SQL 默认顺序排在其后；
        // 站点标识 = 站点管理主键（档案缺失时为 null，该行保持默认位置）
        return stationSortService.applyOrder("moisture", rows, SoilMoistureVO::getSiteId);
    }

    @Override
    public SoilMoistureTrendVO trend(String stcd, LocalDateTime startTime, LocalDateTime endTime, String granularity) {
        // 分钟级：按实际采集时刻逐条出点（图表 x 轴为真实时间）
        if ("minute".equalsIgnoreCase(granularity)) {
            return trendByMinute(stcd, startTime, endTime);
        }
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
                Collections.singletonList(stcd), null, null, null);
        if (!stationInfo.isEmpty() && stationInfo.get(0).getStnm() != null) {
            stnm = stationInfo.get(0).getStnm();
        }

        // 3. 查询小时级聚合（SQL 内已完成 -9991/-999 排除与 AVG）
        List<Map<String, Object>> rows = soilMoistureMapper.selectHourlyTrend(stcd, startTime, endTime);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        Map<String, Map<String, Object>> hourMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDateTime tm = ValueConvertUtils.dateTime(row.get("tm"));
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
                point.setMten(ValueConvertUtils.decimal(row.get("mten")));
                point.setMtwenty(ValueConvertUtils.decimal(row.get("mtwenty")));
                point.setMthirty(ValueConvertUtils.decimal(row.get("mthirty")));
                point.setMforty(ValueConvertUtils.decimal(row.get("mforty")));
                point.setMfifty(ValueConvertUtils.decimal(row.get("mfifty")));
                point.setMsixty(ValueConvertUtils.decimal(row.get("msixty")));
                point.setMeighty(ValueConvertUtils.decimal(row.get("meighty")));
                point.setMhundred(ValueConvertUtils.decimal(row.get("mhundred")));
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

    /**
     * 分钟级趋势：按实际采集时刻逐条输出原始记录，不做小时平均，也不补齐整点。
     */
    private SoilMoistureTrendVO trendByMinute(String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        if (startTime == null) {
            startTime = endTime.minusDays(7);
        }

        String stnm = stcd;
        List<SoilMoistureVO> stationInfo = soilMoistureMapper.selectLatestPerStation(
                Collections.singletonList(stcd), null, null, null);
        if (!stationInfo.isEmpty() && stationInfo.get(0).getStnm() != null) {
            stnm = stationInfo.get(0).getStnm();
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<SoilMoistureTrendVO.HourPoint> points = new ArrayList<>();
        for (Map<String, Object> row : soilMoistureMapper.selectTrendDetail(stcd, startTime, endTime)) {
            LocalDateTime tm = ValueConvertUtils.dateTime(row.get("tm"));
            if (tm == null) continue;
            SoilMoistureTrendVO.HourPoint point = new SoilMoistureTrendVO.HourPoint();
            point.setHour(tm.format(fmt));
            point.setMten(ValueConvertUtils.decimal(row.get("mten")));
            point.setMtwenty(ValueConvertUtils.decimal(row.get("mtwenty")));
            point.setMthirty(ValueConvertUtils.decimal(row.get("mthirty")));
            point.setMforty(ValueConvertUtils.decimal(row.get("mforty")));
            point.setMfifty(ValueConvertUtils.decimal(row.get("mfifty")));
            point.setMsixty(ValueConvertUtils.decimal(row.get("msixty")));
            point.setMeighty(ValueConvertUtils.decimal(row.get("meighty")));
            point.setMhundred(ValueConvertUtils.decimal(row.get("mhundred")));
            points.add(point);
        }

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
}
