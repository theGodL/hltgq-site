package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.qgyun.hltgq.hltgqsite.entity.StPptnR;
import com.qgyun.hltgq.hltgqsite.vo.GqDailyRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.GqRainfallChartVO;
import com.qgyun.hltgq.hltgqsite.vo.GqRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirExtremeRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirPeriodRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirRainfallBriefVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirTenDayRainfallVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface StPptnRService extends IService<StPptnR> {

    boolean saveOrUpdateByKey(StPptnR entity);

    List<StPptnR> latestPerStation();

    IPage<StPptnR> dailyPage(IPage<StPptnR> page, QueryWrapper<StPptnR> wrapper);

    List<StPptnR> todaySumPerStation(LocalDateTime start, LocalDateTime end);

    /**
     * 各雨量站最新观测所在水文日的累计降雨量（DYP 正向增量之和，mm）
     * <p>花凉亭雨量报文 DRP 恒为 0，仅 DYP 有值；灌区站 DRP 每日 8:00 归零亦不可靠，统一用 DYP 增量。
     */
    Map<String, BigDecimal> currentHydroDayRainfall();

    /**
     * 灌区雨量分页查询：每站点最新一条，含1h/3h/6h时段增量
     */
    IPage<GqRainfallVO> gqRainfallPage(long page, long size, String stcd, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 灌区雨量变化图表：单站点小时级增量+累计雨量
     */
    GqRainfallChartVO gqRainfallChart(String stcd, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 灌区雨量历史：单站点全部记录（含1h/3h/6h增量），支持时间范围筛选，分页
     */
    IPage<GqRainfallVO> gqRainfallHistoryPage(long page, long size, String stcd, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 灌区日雨情：非水库站点（排除水库 13 站），按水文日（8:00 切分）聚合逐日雨量透视表
     */
    GqDailyRainfallVO gqDailyRainfall(LocalDate startDate, LocalDate endDate);

    /**
     * 水库实时雨情：12 个固定站点，按水文日（8:00 切分）聚合日雨量
     */
    ReservoirRainfallVO reservoirRainfall(LocalDate startDate, LocalDate endDate);

    /**
     * 水库时段雨情：12 个固定站点，按可配时间间隔聚合时段雨量
     */
    ReservoirPeriodRainfallVO reservoirPeriodRainfall(LocalDate startDate, LocalDate endDate, int intervalMinutes);

    /**
     * 水库旬月雨情：12 个固定站点，按旬（上/中/下旬）聚合雨量
     * 支持年份 + 月份区间（如 08 月 ~ 08 月 单月或跨月区间）
     */
    ReservoirTenDayRainfallVO reservoirTenDayRainfall(int year, int startMonth, int endMonth);

    /**
     * 水库极值雨情：12 个站点各时间窗口（3h/6h/24h/2d/3d/7d）最大雨量
     */
    List<ReservoirExtremeRainfallVO> reservoirExtremeRainfall(LocalDate startDate, LocalDate endDate);

    /**
     * 水库雨情简报：指定日期各站点的日雨量/旬雨量/月雨量
     */
    List<ReservoirRainfallBriefVO> reservoirRainfallBrief(LocalDate date);
}
