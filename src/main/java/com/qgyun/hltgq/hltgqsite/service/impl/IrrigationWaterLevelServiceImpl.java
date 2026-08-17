package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.mapper.IrrigationWaterLevelMapper;
import com.qgyun.hltgq.hltgqsite.service.IrrigationWaterLevelService;
import com.qgyun.hltgq.hltgqsite.vo.IrrigationWaterLevelChartVO;
import com.qgyun.hltgq.hltgqsite.vo.IrrigationWaterLevelHistoryVO;
import com.qgyun.hltgq.hltgqsite.vo.IrrigationWaterLevelVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterLevelTrendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 水位监测-灌区服务实现
 */
@Service
public class IrrigationWaterLevelServiceImpl implements IrrigationWaterLevelService {

    @Autowired
    private IrrigationWaterLevelMapper irrigationWaterLevelMapper;

    @Override
    public Page<IrrigationWaterLevelVO> page(Page<IrrigationWaterLevelVO> page,
                                              String stcd,
                                              LocalDateTime startTime,
                                              LocalDateTime endTime) {
        // ew1: 日期过滤（作用于子查询中确定"最新"记录的范围）
        QueryWrapper<?> dateWrapper = new QueryWrapper<>();
        if (startTime != null) {
            dateWrapper.ge("TM", Timestamp.valueOf(startTime));
        }
        if (endTime != null) {
            dateWrapper.le("TM", Timestamp.valueOf(endTime));
        }

        // ew2: 站点编号过滤（作用于外层结果）
        QueryWrapper<?> stcdWrapper = new QueryWrapper<>();
        if (stcd != null && !stcd.trim().isEmpty()) {
            stcdWrapper.eq("r.STCD", stcd.trim());
        }

        // 查询总数
        long total = irrigationWaterLevelMapper.selectCount(dateWrapper, stcdWrapper);
        page.setTotal(total);

        if (total == 0) {
            return page;
        }

        // 计算分页偏移
        int offset = (int) ((page.getCurrent() - 1) * page.getSize());
        int limit = (int) page.getSize();

        // 分页查询
        List<IrrigationWaterLevelVO> records = irrigationWaterLevelMapper.selectPage(
                dateWrapper, stcdWrapper, limit, offset);
        page.setRecords(records);

        return page;
    }

    @Override
    public IrrigationWaterLevelChartVO waterLevelChart(String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        // 1. 查询站点名称
        String stnm = null;
        List<IrrigationWaterLevelVO> siteRecords = irrigationWaterLevelMapper.selectPage(
                new QueryWrapper<>(), new QueryWrapper<Object>().eq("r.STCD", stcd), 1, 0);
        if (!siteRecords.isEmpty()) {
            stnm = siteRecords.get(0).getStnm();
        }

        // 2. 扩展查询范围（向前 1h，确保首小时有前值可对比）
        LocalDateTime queryStart = startTime.minusHours(1);
        String startStr = queryStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endStr = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 3. 查询原始记录
        List<Map<String, Object>> rawRecords = irrigationWaterLevelMapper.selectHistoryRaw(stcd, startStr, endStr);

        // 4. 按小时聚合：取整点小时桶内的最新一条水位值
        Map<String, BigDecimal> hourWaterLevel = new LinkedHashMap<>();
        Map<String, LocalDateTime> hourLatestTm = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRecords) {
            Object tmObj = row.get("TM");
            Object zObj = row.get("Z");
            if (tmObj == null || zObj == null) continue;

            LocalDateTime tm;
            if (tmObj instanceof Timestamp) {
                tm = ((Timestamp) tmObj).toLocalDateTime();
            } else if (tmObj instanceof LocalDateTime) {
                tm = (LocalDateTime) tmObj;
            } else {
                continue;
            }

            BigDecimal z;
            if (zObj instanceof BigDecimal) {
                z = (BigDecimal) zObj;
            } else {
                z = new BigDecimal(zObj.toString());
            }

            String hourKey = tm.truncatedTo(ChronoUnit.HOURS)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"));

            LocalDateTime prev = hourLatestTm.get(hourKey);
            if (prev == null || tm.isAfter(prev)) {
                hourLatestTm.put(hourKey, tm);
                hourWaterLevel.put(hourKey, z);
            }
        }

        // 5. 生成完整小时序列 + 水位变化
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        List<IrrigationWaterLevelChartVO.HourPoint> hours = new ArrayList<>();
        LocalDateTime hour = startTime.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime endHour = endTime.truncatedTo(ChronoUnit.HOURS);
        BigDecimal prevLevel = null;

        // 如果有前 1 小时数据，作为基准
        String prevKey = startTime.minusHours(1).truncatedTo(ChronoUnit.HOURS).format(fmt);
        prevLevel = hourWaterLevel.get(prevKey);

        while (!hour.isAfter(endHour)) {
            String key = hour.format(fmt);
            BigDecimal currentLevel = hourWaterLevel.get(key);

            IrrigationWaterLevelChartVO.HourPoint point = new IrrigationWaterLevelChartVO.HourPoint();
            point.setHour(key);
            point.setWaterLevel(currentLevel != null ? currentLevel.setScale(2, RoundingMode.DOWN) : null);

            // 计算水位变化 (cm)：当前小时水位 - 前一小时水位，乘以 100 转为 cm
            if (prevLevel != null && currentLevel != null) {
                BigDecimal change = currentLevel.subtract(prevLevel)
                        .multiply(new BigDecimal("100"))
                        .setScale(1, RoundingMode.HALF_UP);
                point.setChange(change);
            } else {
                point.setChange(null);
            }

            hours.add(point);

            // 当前小时水位成为下一个小时的前值
            if (currentLevel != null) {
                prevLevel = currentLevel;
            }
            // 如果当前小时无数据，prevLevel 保持（保持不变时水位变化为 null 或 0）

            hour = hour.plusHours(1);
        }

        // 6. 组装结果
        IrrigationWaterLevelChartVO vo = new IrrigationWaterLevelChartVO();
        vo.setStcd(stcd);
        vo.setStnm(stnm);
        vo.setStartTime(startTime);
        vo.setEndTime(endTime);
        vo.setHours(hours);
        return vo;
    }

    @Override
    public WaterLevelTrendVO waterLevelTrend(String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        // 1. 默认时间范围：近 7 天（与 FlowMonitorServiceImpl.trend 口径一致，服务内兜底）
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        if (endTime == null) {
            endTime = now;
        }
        if (startTime == null) {
            startTime = endTime.minusDays(7);
        }

        // 2. 查询站点名称（站点表查不到时回退为 stcd）
        String stnm = stcd;
        List<IrrigationWaterLevelVO> siteRecords = irrigationWaterLevelMapper.selectPage(
                new QueryWrapper<>(), new QueryWrapper<Object>().eq("r.STCD", stcd), 1, 0);
        if (!siteRecords.isEmpty() && siteRecords.get(0).getStnm() != null) {
            stnm = siteRecords.get(0).getStnm();
        }

        // 3. 查询原始记录
        String startStr = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endStr = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        List<Map<String, Object>> rawRecords = irrigationWaterLevelMapper.selectHistoryRaw(stcd, startStr, endStr);

        // 4. 按小时聚合：取整点小时桶内的最新一条水位值
        Map<String, BigDecimal> hourWaterLevel = new LinkedHashMap<>();
        Map<String, LocalDateTime> hourLatestTm = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRecords) {
            Object tmObj = row.get("TM");
            Object zObj = row.get("Z");
            if (tmObj == null || zObj == null) continue;

            LocalDateTime tm;
            if (tmObj instanceof Timestamp) {
                tm = ((Timestamp) tmObj).toLocalDateTime();
            } else if (tmObj instanceof LocalDateTime) {
                tm = (LocalDateTime) tmObj;
            } else {
                continue;
            }

            BigDecimal z;
            if (zObj instanceof BigDecimal) {
                z = (BigDecimal) zObj;
            } else {
                z = new BigDecimal(zObj.toString());
            }

            String hourKey = tm.truncatedTo(ChronoUnit.HOURS)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"));

            LocalDateTime prev = hourLatestTm.get(hourKey);
            if (prev == null || tm.isAfter(prev)) {
                hourLatestTm.put(hourKey, tm);
                hourWaterLevel.put(hourKey, z);
            }
        }

        // 5. 生成完整小时序列（严格 1h 步长，无数据小时水位为 null）
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        List<WaterLevelTrendVO.HourPoint> hours = new ArrayList<>();
        LocalDateTime hour = startTime.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime endHour = endTime.truncatedTo(ChronoUnit.HOURS);

        while (!hour.isAfter(endHour)) {
            String key = hour.format(fmt);
            BigDecimal level = hourWaterLevel.get(key);
            WaterLevelTrendVO.HourPoint point = new WaterLevelTrendVO.HourPoint();
            point.setHour(key);
            point.setWaterLevel(level != null ? level.setScale(2, RoundingMode.DOWN) : null);
            hours.add(point);
            hour = hour.plusHours(1);
        }

        // 6. 组装结果
        WaterLevelTrendVO vo = new WaterLevelTrendVO();
        vo.setStcd(stcd);
        vo.setStnm(stnm);
        vo.setStartTime(startTime);
        vo.setEndTime(endTime);
        vo.setHours(hours);
        return vo;
    }

    @Override
    public Page<IrrigationWaterLevelHistoryVO> waterLevelHistory(String stcd, LocalDateTime startTime, LocalDateTime endTime, long page, long size) {
        // 1. 扩展查询范围（向前 1h，确保首小时有前值可对比）
        LocalDateTime queryStart = startTime.minusHours(1);
        String startStr = queryStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endStr = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 2. 查询原始记录
        List<Map<String, Object>> rawRecords = irrigationWaterLevelMapper.selectHistoryRaw(stcd, startStr, endStr);

        // 3. 按小时聚合：取整点小时桶内的最新一条水位值
        Map<String, BigDecimal> hourWaterLevel = new LinkedHashMap<>();
        Map<String, LocalDateTime> hourLatestTm = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRecords) {
            Object tmObj = row.get("TM");
            Object zObj = row.get("Z");
            if (tmObj == null || zObj == null) continue;

            LocalDateTime tm;
            if (tmObj instanceof Timestamp) {
                tm = ((Timestamp) tmObj).toLocalDateTime();
            } else if (tmObj instanceof LocalDateTime) {
                tm = (LocalDateTime) tmObj;
            } else {
                continue;
            }

            BigDecimal z;
            if (zObj instanceof BigDecimal) {
                z = (BigDecimal) zObj;
            } else {
                z = new BigDecimal(zObj.toString());
            }

            String hourKey = tm.truncatedTo(ChronoUnit.HOURS)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"));

            LocalDateTime prev = hourLatestTm.get(hourKey);
            if (prev == null || tm.isAfter(prev)) {
                hourLatestTm.put(hourKey, tm);
                hourWaterLevel.put(hourKey, z);
            }
        }

        // 4. 生成完整小时序列 + 水位变化
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        List<IrrigationWaterLevelHistoryVO> allRows = new ArrayList<>();
        LocalDateTime hour = startTime.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime endHour = endTime.truncatedTo(ChronoUnit.HOURS);
        BigDecimal prevLevel = null;

        // 如果有前 1 小时数据，作为基准
        String prevKey = startTime.minusHours(1).truncatedTo(ChronoUnit.HOURS).format(fmt);
        prevLevel = hourWaterLevel.get(prevKey);

        while (!hour.isAfter(endHour)) {
            String key = hour.format(fmt);
            BigDecimal currentLevel = hourWaterLevel.get(key);

            IrrigationWaterLevelHistoryVO row = new IrrigationWaterLevelHistoryVO();
            row.setHour(key);
            row.setWaterLevel(currentLevel != null ? currentLevel.setScale(2, RoundingMode.DOWN) : null);

            if (prevLevel != null && currentLevel != null) {
                BigDecimal change = currentLevel.subtract(prevLevel)
                        .multiply(new BigDecimal("100"))
                        .setScale(1, RoundingMode.HALF_UP);
                row.setChange(change);
            } else {
                row.setChange(null);
            }

            allRows.add(row);

            if (currentLevel != null) {
                prevLevel = currentLevel;
            }

            hour = hour.plusHours(1);
        }

        // 5. 内存分页
        long total = allRows.size();
        int start = (int) ((page - 1) * size);
        int end = (int) Math.min(start + size, total);
        Page<IrrigationWaterLevelHistoryVO> result = new Page<>(page, size);
        result.setTotal(total);
        result.setRecords(start >= total ? Collections.emptyList() : allRows.subList(start, end));
        return result;
    }
}
