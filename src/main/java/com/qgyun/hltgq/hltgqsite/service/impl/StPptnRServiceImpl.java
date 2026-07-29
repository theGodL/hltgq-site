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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StPptnRServiceImpl extends ServiceImpl<StPptnRMapper, StPptnR> implements StPptnRService {

    @Autowired
    private StStinfoMapper stStinfoMapper;

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
}
