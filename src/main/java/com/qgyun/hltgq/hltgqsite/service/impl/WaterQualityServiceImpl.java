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
import com.qgyun.hltgq.hltgqsite.model.util.StationStatusUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ValueConvertUtils;

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
        // 在线状态：档案手动离线（zebpsu=#2#）优先判离线；否则按最新采集时间断联判定
        records.forEach(r -> r.setIsOnline(StationStatusUtils.isZebpsuOnline(r.getZebpsu()) && !StationStatusUtils.isStale(r.getTm(), r.getStnm())));
        attachThresholds(records);
        return records;
    }

    @Override
    public WaterQualityTrendVO trend(String stcd, LocalDateTime startTime, LocalDateTime endTime, String granularity) {
        // 分钟级：按实际采集时刻逐条出点（图表 x 轴为真实时间）
        if ("minute".equalsIgnoreCase(granularity)) {
            return trendByMinute(stcd, startTime, endTime);
        }
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
        //    过滤上界取末桶终点 endTime+2h（不含），保证序列末桶聚合完整 2h 窗数据（如 22:00 桶含 23 点数据）
        List<Map<String, Object>> rows = waterQualityMapper.selectTwoHourTrend(stcd, startTime, endTime.plusHours(2));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        Map<String, Map<String, Object>> hourMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            LocalDateTime tm = ValueConvertUtils.dateTime(row.get("tm"));
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
                point.setNh3n(ValueConvertUtils.decimal(row.get("nh3n")));
                point.setCodmn(ValueConvertUtils.decimal(row.get("codmn")));
                point.setCodcr(ValueConvertUtils.decimal(row.get("codcr")));
                point.setBod5(ValueConvertUtils.decimal(row.get("bod5")));
                point.setTp(ValueConvertUtils.decimal(row.get("tp")));
                point.setTn(ValueConvertUtils.decimal(row.get("tn")));
                point.setDox(ValueConvertUtils.decimal(row.get("dox")));
                point.setWt(ValueConvertUtils.decimal(row.get("wt")));
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

    /**
     * 分钟级趋势：按实际采集时刻逐条输出原始记录，不做 2 小时平均，也不补齐桶。
     */
    private WaterQualityTrendVO trendByMinute(String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        if (startTime == null) {
            startTime = endTime.minusHours(24);
        }
        if (startTime.isAfter(endTime)) {
            LocalDateTime t = startTime;
            startTime = endTime;
            endTime = t;
        }

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

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<WaterQualityTrendVO.HourPoint> points = new ArrayList<>();
        for (Map<String, Object> row : waterQualityMapper.selectTrendDetail(stcd, startTime, endTime)) {
            LocalDateTime tm = ValueConvertUtils.dateTime(row.get("tm"));
            if (tm == null) continue;
            WaterQualityTrendVO.HourPoint point = new WaterQualityTrendVO.HourPoint();
            point.setHour(tm.format(fmt));
            point.setNh3n(ValueConvertUtils.decimal(row.get("nh3n")));
            point.setCodmn(ValueConvertUtils.decimal(row.get("codmn")));
            point.setCodcr(ValueConvertUtils.decimal(row.get("codcr")));
            point.setBod5(ValueConvertUtils.decimal(row.get("bod5")));
            point.setTp(ValueConvertUtils.decimal(row.get("tp")));
            point.setTn(ValueConvertUtils.decimal(row.get("tn")));
            point.setDox(ValueConvertUtils.decimal(row.get("dox")));
            point.setWt(ValueConvertUtils.decimal(row.get("wt")));
            points.add(point);
        }

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
}
