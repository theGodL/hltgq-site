package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qgyun.hltgq.hltgqsite.entity.StPptnR;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.mapper.StPptnRMapper;
import com.qgyun.hltgq.hltgqsite.mapper.StStinfoMapper;
import com.qgyun.hltgq.hltgqsite.service.StPptnRService;
import com.qgyun.hltgq.hltgqsite.vo.GqRainfallChartVO;
import com.qgyun.hltgq.hltgqsite.vo.GqRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirExtremeRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirPeriodRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirRainfallBriefVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirTenDayRainfallVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StPptnRServiceImpl extends ServiceImpl<StPptnRMapper, StPptnR> implements StPptnRService {

    @Autowired
    private StStinfoMapper stStinfoMapper;

    /** 水库 12 个固定站点编号 */
    private static final List<String> RESERVOIR_STCDS = Collections.unmodifiableList(Arrays.asList(
            "00000001", "00000002", "00000003",
            "00000005", "00000006", "00000007",
            "00000008", "00000009", "00000010",
            "00000011", "00000012", "00000013"
    ));

    @Override
    public List<StPptnR> latestPerStation() {
        return baseMapper.selectLatestPerStation();
    }

    @Override
    public IPage<StPptnR> dailyPage(IPage<StPptnR> page, QueryWrapper<StPptnR> wrapper) {
        List<StPptnR> all = baseMapper.selectDailyAll(wrapper);
        long total = all.size();
        int start = (int) ((page.getCurrent() - 1) * page.getSize());
        int end   = (int) Math.min(start + page.getSize(), total);
        page.setTotal(total);
        page.setRecords(start >= total ? Collections.emptyList() : all.subList(start, end));
        return page;
    }

    @Override
    public List<StPptnR> todaySumPerStation(LocalDateTime start, LocalDateTime end) {
        return baseMapper.selectTodaySumPerStation(Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    @Override
    public boolean saveOrUpdateByKey(StPptnR entity) {
        boolean exists = count(new QueryWrapper<StPptnR>()
                .eq("STCD", entity.getStcd())
                .eq("TM", Timestamp.valueOf(entity.getTm()))) > 0;
        if (exists) {
            return update(entity, new UpdateWrapper<StPptnR>()
                    .eq("STCD", entity.getStcd())
                    .eq("TM", Timestamp.valueOf(entity.getTm())));
        }
        return save(entity);
    }

    @Override
    public IPage<GqRainfallVO> gqRainfallPage(long page, long size, String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> rows = baseMapper.selectGqRainfallList(stcd, startTime, endTime);
        List<GqRainfallVO> vos = rows.stream().map(this::toGqRainfallVO).collect(Collectors.toList());
        return toPage(vos, page, size);
    }

    @Override
    public IPage<GqRainfallVO> gqRainfallHistoryPage(long page, long size, String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> rows = baseMapper.selectGqRainfallHistory(stcd, startTime, endTime);
        List<GqRainfallVO> vos = rows.stream().map(this::toGqRainfallVO).collect(Collectors.toList());
        return toPage(vos, page, size);
    }

    private GqRainfallVO toGqRainfallVO(Map<String, Object> row) {
        GqRainfallVO vo = new GqRainfallVO();
        vo.setStcd((String) row.get("stcd"));
        vo.setId((String) row.get("id"));
        vo.setStnm((String) row.get("stnm"));
        Object tmObj = row.get("tm");
        if (tmObj instanceof LocalDateTime) {
            vo.setTm((LocalDateTime) tmObj);
        } else if (tmObj instanceof Timestamp) {
            vo.setTm(((Timestamp) tmObj).toLocalDateTime());
        }
        BigDecimal drp = toBigDecimal(row.get("drp"));
        vo.setDrp(drp);
        // 时段增量计算：当前DRP - 历史DRP，结果非负
        BigDecimal drp1h = toBigDecimal(row.get("drp_1h"));
        BigDecimal drp3h = toBigDecimal(row.get("drp_3h"));
        BigDecimal drp6h = toBigDecimal(row.get("drp_6h"));
        vo.setRain1h(subtractOrNull(drp, drp1h));
        vo.setRain3h(subtractOrNull(drp, drp3h));
        vo.setRain6h(subtractOrNull(drp, drp6h));
        return vo;
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        return new BigDecimal(val.toString());
    }

    /** 计算增量，结果不小于0；任一为null则返回null */
    private BigDecimal subtractOrNull(BigDecimal current, BigDecimal prev) {
        if (current == null || prev == null) return null;
        BigDecimal diff = current.subtract(prev);
        return diff.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : diff;
    }

    private IPage<GqRainfallVO> toPage(List<GqRainfallVO> list, long page, long size) {
        long total = list.size();
        int start = (int) ((page - 1) * size);
        int end = (int) Math.min(start + size, total);
        Page<GqRainfallVO> result = new Page<>(page, size);
        result.setTotal(total);
        result.setRecords(start >= total ? Collections.emptyList() : list.subList(start, end));
        return result;
    }

    @Override
    public GqRainfallChartVO gqRainfallChart(String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        // 1. 查询站点名称
        StStinfo stinfo = stStinfoMapper.selectById(stcd);

        // 2. 扩展查询范围（向前2h，确保首小时有基线数据可对比）
        LocalDateTime queryStart = startTime.minusHours(2);

        // 3. 查询原始记录
        List<StPptnR> records = baseMapper.selectByStcdAndTimeRange(stcd, queryStart, endTime);

        // 4. 按小时汇总增量（hydro-monitor 规则：第一条 inc=0，后续 inc=max(0, cur-prev)）
        //    LinkedHashMap 保持时间顺序
        Map<String, BigDecimal> hourRainfall = new LinkedHashMap<>();
        if (!records.isEmpty()) {
            for (int i = 0; i < records.size(); i++) {
                StPptnR cur = records.get(i);
                BigDecimal inc;
                if (i == 0) {
                    inc = BigDecimal.ZERO; // 第一条记录无法确定增量，跳过
                } else {
                    StPptnR prev = records.get(i - 1);
                    BigDecimal curDrp = cur.getDrp() != null ? cur.getDrp() : BigDecimal.ZERO;
                    BigDecimal prevDrp = prev.getDrp() != null ? prev.getDrp() : BigDecimal.ZERO;
                    inc = curDrp.compareTo(prevDrp) > 0 ? curDrp.subtract(prevDrp) : BigDecimal.ZERO;
                }
                // 归属到小时桶
                String hourKey = cur.getTm().truncatedTo(ChronoUnit.HOURS)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"));
                hourRainfall.merge(hourKey, inc, BigDecimal::add);
            }
        }

        // 5. 生成完整小时序列 + 累计值
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        List<GqRainfallChartVO.HourPoint> hours = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        LocalDateTime hour = startTime.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime endHour = endTime.truncatedTo(ChronoUnit.HOURS);

        while (!hour.isAfter(endHour)) {
            String key = hour.format(fmt);
            BigDecimal rainfall = hourRainfall.getOrDefault(key, BigDecimal.ZERO);
            cumulative = cumulative.add(rainfall);

            GqRainfallChartVO.HourPoint point = new GqRainfallChartVO.HourPoint();
            point.setHour(key);
            point.setRainfall(rainfall);
            point.setCumulative(cumulative);
            hours.add(point);

            hour = hour.plusHours(1);
        }

        // 6. 组装结果
        GqRainfallChartVO vo = new GqRainfallChartVO();
        vo.setStcd(stcd);
        vo.setStnm(stinfo != null ? stinfo.getStnm() : null);
        vo.setStartTime(startTime);
        vo.setEndTime(endTime);
        vo.setHours(hours);
        return vo;
    }

    @Override
    public ReservoirRainfallVO reservoirRainfall(LocalDate startDate, LocalDate endDate) {
        // 1. 查询 12 个站点信息
        Map<String, StStinfo> stinfoMap = new LinkedHashMap<>();
        for (String stcd : RESERVOIR_STCDS) {
            StStinfo info = stStinfoMapper.selectById(stcd);
            if (info != null) {
                stinfoMap.put(stcd, info);
            }
        }

        // 2. 扩展查询范围（向前后各 1 天，确保水文日边界完整）
        //    水文日：8:00 ~ 次日 7:59:59
        LocalDateTime queryStart = startDate.atTime(8, 0).minusDays(1);
        LocalDateTime queryEnd = endDate.atTime(7, 59, 59).plusDays(1);

        // 3. 批量查询原始 DRP 记录
        List<StPptnR> records = baseMapper.selectByStcdsAndTimeRange(RESERVOIR_STCDS, queryStart, queryEnd);

        // 4. 按站点 + 水文日聚合增量（完全对齐 hydro-monitor.html buildPptnPivot 逻辑）
        //    Map<水文日标签, Map<stcd, 累加增量>>
        Map<String, Map<String, BigDecimal>> bucketMap = new LinkedHashMap<>();

        // 按站点分组
        Map<String, List<StPptnR>> byStation = new LinkedHashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            if (!RESERVOIR_STCDS.contains(key)) continue;
            byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        DateTimeFormatter bucketFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (Map.Entry<String, List<StPptnR>> entry : byStation.entrySet()) {
            String stcd = entry.getKey();
            List<StPptnR> stationRows = entry.getValue();
            // 按时间升序
            stationRows.sort(Comparator.comparing(StPptnR::getTm));
            for (int i = 0; i < stationRows.size(); i++) {
                StPptnR cur = stationRows.get(i);
                BigDecimal inc;
                if (i == 0) {
                    inc = BigDecimal.ZERO; // 第一条记录无法确定增量
                } else {
                    StPptnR prev = stationRows.get(i - 1);
                    BigDecimal curDrp = cur.getDrp() != null ? cur.getDrp() : BigDecimal.ZERO;
                    BigDecimal prevDrp = prev.getDrp() != null ? prev.getDrp() : BigDecimal.ZERO;
                    inc = curDrp.compareTo(prevDrp) > 0 ? curDrp.subtract(prevDrp) : BigDecimal.ZERO;
                }
                // 归属到水文日桶
                String bucket = getHydroDayLabel(cur.getTm());
                bucketMap.computeIfAbsent(bucket, k -> new LinkedHashMap<>())
                        .merge(stcd, inc, BigDecimal::add);
            }
        }

        // 5. 生成完整的水文日序列
        List<String> allBuckets = new ArrayList<>();
        LocalDate d = startDate;
        while (!d.isAfter(endDate)) {
            allBuckets.add(d.format(bucketFmt) + " 08:00:00");
            d = d.plusDays(1);
        }

        // 6. 组装站点信息（含最新 DRP）
        List<ReservoirRainfallVO.StationInfo> stations = new ArrayList<>();
        // 先收集每个站点的最新一条记录
        Map<String, StPptnR> latestPerStcd = new HashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            StPptnR existing = latestPerStcd.get(key);
            if (existing == null || r.getTm().isAfter(existing.getTm())) {
                latestPerStcd.put(key, r);
            }
        }
        for (String stcd : RESERVOIR_STCDS) {
            ReservoirRainfallVO.StationInfo si = new ReservoirRainfallVO.StationInfo();
            si.setStcd(stcd);
            StStinfo info = stinfoMap.get(stcd);
            if (info != null) {
                si.setStnm(info.getStnm());
                si.setId(info.getId());
            }
            StPptnR latest = latestPerStcd.get(stcd);
            if (latest != null) {
                si.setLatestTm(latest.getTm());
                si.setLatestDrp(latest.getDrp());
            }
            stations.add(si);
        }

        // 7. 组装逐日数据
        List<ReservoirRainfallVO.DayRainfall> days = new ArrayList<>();
        for (String bucket : allBuckets) {
            ReservoirRainfallVO.DayRainfall dr = new ReservoirRainfallVO.DayRainfall();
            dr.setDay(bucket);
            Map<String, BigDecimal> values = bucketMap.getOrDefault(bucket, Collections.emptyMap());
            // 补充缺失站点的 0 值，同时计算平均值
            Map<String, BigDecimal> fullValues = new LinkedHashMap<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (String stcd : RESERVOIR_STCDS) {
                BigDecimal val = values.getOrDefault(stcd, BigDecimal.ZERO);
                fullValues.put(stcd, val);
                sum = sum.add(val);
            }
            dr.setValues(fullValues);
            dr.setAvg(sum.divide(new BigDecimal(RESERVOIR_STCDS.size()), 2, BigDecimal.ROUND_HALF_UP));
            days.add(dr);
        }

        // 8. 组装结果
        ReservoirRainfallVO vo = new ReservoirRainfallVO();
        vo.setStations(stations);
        vo.setDays(days);
        return vo;
    }

    /** 水文日标签：早 8 点切分，标签日期为水文日结束日 */
    private String getHydroDayLabel(LocalDateTime tm) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        if (tm.getHour() >= 8) {
            return tm.toLocalDate().plusDays(1).format(fmt) + " 08:00:00";
        }
        return tm.toLocalDate().format(fmt) + " 08:00:00";
    }

    @Override
    public ReservoirPeriodRainfallVO reservoirPeriodRainfall(LocalDate startDate, LocalDate endDate, int intervalMinutes) {
        // 1. 查询 12 个站点信息
        Map<String, StStinfo> stinfoMap = new LinkedHashMap<>();
        for (String stcd : RESERVOIR_STCDS) {
            StStinfo info = stStinfoMapper.selectById(stcd);
            if (info != null) {
                stinfoMap.put(stcd, info);
            }
        }

        // 2. 扩展查询范围（向前后各 1 天，确保时段边界完整）
        LocalDateTime queryStart = startDate.atTime(8, 0).minusDays(1);
        LocalDateTime queryEnd = endDate.atTime(7, 59, 59).plusDays(1);

        // 3. 批量查询原始 DRP 记录
        List<StPptnR> records = baseMapper.selectByStcdsAndTimeRange(RESERVOIR_STCDS, queryStart, queryEnd);

        // 4. 按站点 + 时间间隔聚合增量（对齐 hydro-monitor.html buildPeriodPivot 逻辑）
        //    Map<时段标签, Map<stcd, 累加增量>>
        Map<String, Map<String, BigDecimal>> bucketMap = new LinkedHashMap<>();

        Map<String, List<StPptnR>> byStation = new LinkedHashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            if (!RESERVOIR_STCDS.contains(key)) continue;
            byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<String, List<StPptnR>> entry : byStation.entrySet()) {
            String stcd = entry.getKey();
            List<StPptnR> stationRows = entry.getValue();
            stationRows.sort(Comparator.comparing(StPptnR::getTm));
            for (int i = 0; i < stationRows.size(); i++) {
                StPptnR cur = stationRows.get(i);
                BigDecimal inc;
                if (i == 0) {
                    inc = BigDecimal.ZERO;
                } else {
                    StPptnR prev = stationRows.get(i - 1);
                    BigDecimal curDrp = cur.getDrp() != null ? cur.getDrp() : BigDecimal.ZERO;
                    BigDecimal prevDrp = prev.getDrp() != null ? prev.getDrp() : BigDecimal.ZERO;
                    inc = curDrp.compareTo(prevDrp) > 0 ? curDrp.subtract(prevDrp) : BigDecimal.ZERO;
                }
                // floorToInterval：分钟取整到间隔边界
                String bucket = floorToInterval(cur.getTm(), intervalMinutes);
                bucketMap.computeIfAbsent(bucket, k -> new LinkedHashMap<>())
                        .merge(stcd, inc, BigDecimal::add);
            }
        }

        // 5. 生成完整时段序列（对齐 generatePeriodBuckets）
        //    起点 = (startDate - 1) 08:00，终点 = endDate 08:00（不含）
        List<String> allBuckets = new ArrayList<>();
        LocalDateTime bucketStart = startDate.minusDays(1).atTime(8, 0);
        LocalDateTime bucketEnd = endDate.atTime(8, 0);
        DateTimeFormatter bucketFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime t = bucketStart;
        while (t.isBefore(bucketEnd)) {
            allBuckets.add(t.format(bucketFmt));
            t = t.plusMinutes(intervalMinutes);
        }

        // 6. 组装站点信息
        List<ReservoirRainfallVO.StationInfo> stations = new ArrayList<>();
        Map<String, StPptnR> latestPerStcd = new HashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            StPptnR existing = latestPerStcd.get(key);
            if (existing == null || r.getTm().isAfter(existing.getTm())) {
                latestPerStcd.put(key, r);
            }
        }
        for (String stcd : RESERVOIR_STCDS) {
            ReservoirRainfallVO.StationInfo si = new ReservoirRainfallVO.StationInfo();
            si.setStcd(stcd);
            StStinfo info = stinfoMap.get(stcd);
            if (info != null) {
                si.setStnm(info.getStnm());
                si.setId(info.getId());
            }
            StPptnR latest = latestPerStcd.get(stcd);
            if (latest != null) {
                si.setLatestTm(latest.getTm());
                si.setLatestDrp(latest.getDrp());
            }
            stations.add(si);
        }

        // 7. 组装时段数据（含平均值）
        List<ReservoirPeriodRainfallVO.BucketRow> buckets = new ArrayList<>();
        for (String bucketKey : allBuckets) {
            ReservoirPeriodRainfallVO.BucketRow row = new ReservoirPeriodRainfallVO.BucketRow();
            row.setTime(bucketKey);

            Map<String, BigDecimal> rawValues = bucketMap.getOrDefault(bucketKey, Collections.emptyMap());
            Map<String, BigDecimal> fullValues = new LinkedHashMap<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (String stcd : RESERVOIR_STCDS) {
                BigDecimal val = rawValues.getOrDefault(stcd, BigDecimal.ZERO);
                fullValues.put(stcd, val);
                sum = sum.add(val);
            }
            row.setValues(fullValues);
            row.setAvg(sum.divide(new BigDecimal(RESERVOIR_STCDS.size()), 2, BigDecimal.ROUND_HALF_UP));
            buckets.add(row);
        }

        // 8. 组装结果
        ReservoirPeriodRainfallVO vo = new ReservoirPeriodRainfallVO();
        vo.setStations(stations);
        vo.setBuckets(buckets);
        return vo;
    }

    /** 分钟取整到间隔边界，对齐 floorToInterval */
    private String floorToInterval(LocalDateTime tm, int intervalMinutes) {
        int totalMinutes = tm.getHour() * 60 + tm.getMinute();
        int floored = (totalMinutes / intervalMinutes) * intervalMinutes;
        int h = floored / 60;
        int m = floored % 60;
        return String.format("%s %02d:%02d",
                tm.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), h, m);
    }

    // ======================== 旬月雨情 ========================

    @Override
    public ReservoirTenDayRainfallVO reservoirTenDayRainfall(String yearMonth) {
        // 解析年月
        LocalDate monthStart = LocalDate.parse(yearMonth + "-01");
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

        // 扩展查询范围（水文日边界）
        LocalDateTime queryStart = monthStart.atTime(8, 0).minusDays(1);
        LocalDateTime queryEnd = monthEnd.plusDays(1).atTime(7, 59, 59);

        List<StPptnR> records = baseMapper.selectByStcdsAndTimeRange(RESERVOIR_STCDS, queryStart, queryEnd);

        // 按站点分组 → 计算日雨量
        Map<String, Map<String, BigDecimal>> dailyRainfall = new LinkedHashMap<>();
        Map<String, List<StPptnR>> byStation = new LinkedHashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            if (!RESERVOIR_STCDS.contains(key)) continue;
            byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<String, List<StPptnR>> entry : byStation.entrySet()) {
            String stcd = entry.getKey();
            List<StPptnR> stationRows = entry.getValue();
            stationRows.sort(Comparator.comparing(StPptnR::getTm));
            for (int i = 0; i < stationRows.size(); i++) {
                StPptnR cur = stationRows.get(i);
                BigDecimal inc = BigDecimal.ZERO;
                if (i > 0) {
                    StPptnR prev = stationRows.get(i - 1);
                    BigDecimal curDrp = cur.getDrp() != null ? cur.getDrp() : BigDecimal.ZERO;
                    BigDecimal prevDrp = prev.getDrp() != null ? prev.getDrp() : BigDecimal.ZERO;
                    inc = curDrp.compareTo(prevDrp) > 0 ? curDrp.subtract(prevDrp) : BigDecimal.ZERO;
                }
                String dayKey = getHydroDayLabel(cur.getTm());
                dailyRainfall.computeIfAbsent(dayKey, k -> new LinkedHashMap<>())
                        .merge(stcd, inc, BigDecimal::add);
            }
        }

        // 按旬聚合：上旬(1-10)、中旬(11-20)、下旬(21+)
        String[][] tenDays = {{"上旬", "1", "10"}, {"中旬", "11", "20"}, {"下旬", "21", String.valueOf(monthEnd.getDayOfMonth())}};
        List<ReservoirTenDayRainfallVO.TenDayRow> periods = new ArrayList<>();

        for (String[] td : tenDays) {
            String name = td[0];
            int dStart = Integer.parseInt(td[1]);
            int dEnd = Integer.parseInt(td[2]);

            Map<String, BigDecimal> tenDayValues = new LinkedHashMap<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (String stcd : RESERVOIR_STCDS) {
                BigDecimal stcdSum = BigDecimal.ZERO;
                for (int day = dStart; day <= dEnd; day++) {
                    String hydroKey = String.format("%s-%02d-%02d 08:00:00",
                            monthStart.getYear(), monthStart.getMonthValue(), day);
                    Map<String, BigDecimal> dayValues = dailyRainfall.get(hydroKey);
                    if (dayValues != null) {
                        stcdSum = stcdSum.add(dayValues.getOrDefault(stcd, BigDecimal.ZERO));
                    }
                }
                tenDayValues.put(stcd, stcdSum);
                sum = sum.add(stcdSum);
            }

            ReservoirTenDayRainfallVO.TenDayRow row = new ReservoirTenDayRainfallVO.TenDayRow();
            row.setYearMonth(yearMonth);
            row.setTenDay(name);
            row.setValues(tenDayValues);
            row.setAvg(sum.divide(new BigDecimal(RESERVOIR_STCDS.size()), 2, BigDecimal.ROUND_HALF_UP));
            periods.add(row);
        }

        // 组装站点信息
        List<ReservoirRainfallVO.StationInfo> stations = buildStationInfos(records);

        ReservoirTenDayRainfallVO vo = new ReservoirTenDayRainfallVO();
        vo.setStations(stations);
        vo.setPeriods(periods);
        return vo;
    }

    // ======================== 极值雨情 ========================

    @Override
    public List<ReservoirExtremeRainfallVO> reservoirExtremeRainfall(LocalDate startDate, LocalDate endDate) {
        // 扩展查询范围（向前多取几天确保窗口完整）
        LocalDateTime queryStart = startDate.atTime(8, 0).minusDays(8);
        LocalDateTime queryEnd = endDate.atTime(7, 59, 59);

        List<StPptnR> records = baseMapper.selectByStcdsAndTimeRange(RESERVOIR_STCDS, queryStart, queryEnd);

        // 按站点分组
        Map<String, List<StPptnR>> byStation = new LinkedHashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            if (!RESERVOIR_STCDS.contains(key)) continue;
            byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        // 查询站点名称
        Map<String, String> stnmMap = new HashMap<>();
        for (String stcd : RESERVOIR_STCDS) {
            StStinfo info = stStinfoMapper.selectById(stcd);
            if (info != null) stnmMap.put(stcd, info.getStnm());
        }

        List<ReservoirExtremeRainfallVO> result = new ArrayList<>();
        for (String stcd : RESERVOIR_STCDS) {
            List<StPptnR> stationRows = byStation.getOrDefault(stcd, Collections.emptyList());
            stationRows.sort(Comparator.comparing(StPptnR::getTm));

            // 计算小时增量序列
            Map<String, BigDecimal> hourlyInc = new LinkedHashMap<>();
            for (int i = 0; i < stationRows.size(); i++) {
                StPptnR cur = stationRows.get(i);
                BigDecimal inc = BigDecimal.ZERO;
                if (i > 0) {
                    StPptnR prev = stationRows.get(i - 1);
                    BigDecimal curDrp = cur.getDrp() != null ? cur.getDrp() : BigDecimal.ZERO;
                    BigDecimal prevDrp = prev.getDrp() != null ? prev.getDrp() : BigDecimal.ZERO;
                    inc = curDrp.compareTo(prevDrp) > 0 ? curDrp.subtract(prevDrp) : BigDecimal.ZERO;
                }
                String hourKey = cur.getTm().truncatedTo(ChronoUnit.HOURS)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"));
                hourlyInc.merge(hourKey, inc, BigDecimal::add);
            }

            // 生成完整小时序列
            List<Map.Entry<String, BigDecimal>> hourList = new ArrayList<>(hourlyInc.entrySet());
            hourList.sort(Map.Entry.comparingByKey());

            // 滑动窗口求极值
            BigDecimal max3h = slidingMax(hourList, 3);
            BigDecimal max6h = slidingMax(hourList, 6);
            BigDecimal max24h = slidingMax(hourList, 24);

            // 日雨量序列
            Map<String, BigDecimal> dailyInc = new LinkedHashMap<>();
            for (Map.Entry<String, BigDecimal> h : hourList) {
                String dayKey = h.getKey().substring(0, 10);
                dailyInc.merge(dayKey, h.getValue(), BigDecimal::add);
            }
            List<Map.Entry<String, BigDecimal>> dayList = new ArrayList<>(dailyInc.entrySet());
            dayList.sort(Map.Entry.comparingByKey());

            BigDecimal max2d = slidingMax(dayList, 2);
            BigDecimal max3d = slidingMax(dayList, 3);
            BigDecimal max7d = slidingMax(dayList, 7);

            ReservoirExtremeRainfallVO vo = new ReservoirExtremeRainfallVO();
            vo.setStcd(stcd);
            vo.setStnm(stnmMap.getOrDefault(stcd, stcd));
            vo.setMax3h(max3h);
            vo.setMax6h(max6h);
            vo.setMax24h(max24h);
            vo.setMax2d(max2d);
            vo.setMax3d(max3d);
            vo.setMax7d(max7d);
            result.add(vo);
        }
        return result;
    }

    /** 滑动窗口最大值 */
    private BigDecimal slidingMax(List<Map.Entry<String, BigDecimal>> sortedList, int windowSize) {
        if (sortedList.isEmpty()) return BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        for (int i = 0; i < sortedList.size(); i++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = i; j < Math.min(i + windowSize, sortedList.size()); j++) {
                sum = sum.add(sortedList.get(j).getValue());
            }
            if (sum.compareTo(max) > 0) max = sum;
        }
        return max;
    }

    // ======================== 雨情简报 ========================

    @Override
    public List<ReservoirRainfallBriefVO> reservoirRainfallBrief(LocalDate date) {
        // 确定旬段
        int day = date.getDayOfMonth();
        String tenDayName;
        int dStart, dEnd;
        if (day <= 10) { tenDayName = "上旬"; dStart = 1; dEnd = 10; }
        else if (day <= 20) { tenDayName = "中旬"; dStart = 11; dEnd = 20; }
        else { tenDayName = "下旬"; dStart = 21; dEnd = date.lengthOfMonth(); }

        // 查询范围：该月完整数据
        LocalDate monthStart = date.withDayOfMonth(1);
        LocalDate monthEnd = date.withDayOfMonth(date.lengthOfMonth());
        LocalDateTime queryStart = monthStart.atTime(8, 0).minusDays(1);
        LocalDateTime queryEnd = monthEnd.plusDays(1).atTime(7, 59, 59);

        List<StPptnR> records = baseMapper.selectByStcdsAndTimeRange(RESERVOIR_STCDS, queryStart, queryEnd);

        // 按站点 → 按日聚合
        Map<String, Map<String, BigDecimal>> dailyByStation = new LinkedHashMap<>();
        Map<String, List<StPptnR>> byStation = new LinkedHashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            if (!RESERVOIR_STCDS.contains(key)) continue;
            byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<String, List<StPptnR>> entry : byStation.entrySet()) {
            String stcd = entry.getKey();
            List<StPptnR> stationRows = entry.getValue();
            stationRows.sort(Comparator.comparing(StPptnR::getTm));
            for (int i = 0; i < stationRows.size(); i++) {
                StPptnR cur = stationRows.get(i);
                BigDecimal inc = BigDecimal.ZERO;
                if (i > 0) {
                    StPptnR prev = stationRows.get(i - 1);
                    BigDecimal curDrp = cur.getDrp() != null ? cur.getDrp() : BigDecimal.ZERO;
                    BigDecimal prevDrp = prev.getDrp() != null ? prev.getDrp() : BigDecimal.ZERO;
                    inc = curDrp.compareTo(prevDrp) > 0 ? curDrp.subtract(prevDrp) : BigDecimal.ZERO;
                }
                String dayKey = getHydroDayLabel(cur.getTm());
                dailyByStation.computeIfAbsent(stcd, k -> new LinkedHashMap<>())
                        .merge(dayKey, inc, BigDecimal::add);
            }
        }

        // 查询站点名称
        Map<String, String> stnmMap = new HashMap<>();
        for (String stcd : RESERVOIR_STCDS) {
            StStinfo info = stStinfoMapper.selectById(stcd);
            if (info != null) stnmMap.put(stcd, info.getStnm());
        }

        // 组装结果
        String targetDayKey = date.toString() + " 08:00:00";
        List<ReservoirRainfallBriefVO> result = new ArrayList<>();
        for (String stcd : RESERVOIR_STCDS) {
            ReservoirRainfallBriefVO vo = new ReservoirRainfallBriefVO();
            vo.setStcd(stcd);
            vo.setStnm(stnmMap.getOrDefault(stcd, stcd));

            Map<String, BigDecimal> dayMap = dailyByStation.getOrDefault(stcd, Collections.emptyMap());

            // 日雨量
            vo.setDailyRainfall(dayMap.getOrDefault(targetDayKey, BigDecimal.ZERO));

            // 旬雨量
            BigDecimal tenDaySum = BigDecimal.ZERO;
            for (int d = dStart; d <= dEnd; d++) {
                String hydroKey = String.format("%s-%02d-%02d 08:00:00",
                        monthStart.getYear(), monthStart.getMonthValue(), d);
                tenDaySum = tenDaySum.add(dayMap.getOrDefault(hydroKey, BigDecimal.ZERO));
            }
            vo.setTenDayRainfall(tenDaySum);

            // 月雨量
            BigDecimal monthSum = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> e : dayMap.entrySet()) {
                monthSum = monthSum.add(e.getValue());
            }
            vo.setMonthlyRainfall(monthSum);

            result.add(vo);
        }
        return result;
    }

    /** 提取公共站点信息组装 */
    private List<ReservoirRainfallVO.StationInfo> buildStationInfos(List<StPptnR> records) {
        List<ReservoirRainfallVO.StationInfo> stations = new ArrayList<>();
        Map<String, StPptnR> latestPerStcd = new HashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            StPptnR existing = latestPerStcd.get(key);
            if (existing == null || r.getTm().isAfter(existing.getTm())) {
                latestPerStcd.put(key, r);
            }
        }
        for (String stcd : RESERVOIR_STCDS) {
            ReservoirRainfallVO.StationInfo si = new ReservoirRainfallVO.StationInfo();
            si.setStcd(stcd);
            StStinfo info = stStinfoMapper.selectById(stcd);
            if (info != null) {
                si.setStnm(info.getStnm());
                si.setId(info.getId());
            }
            StPptnR latest = latestPerStcd.get(stcd);
            if (latest != null) {
                si.setLatestTm(latest.getTm());
                si.setLatestDrp(latest.getDrp());
            }
            stations.add(si);
        }
        return stations;
    }
}
