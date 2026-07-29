package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.entity.StPptnR;
import com.qgyun.hltgq.hltgqsite.service.StPptnRService;
import com.qgyun.hltgq.hltgqsite.vo.GqRainfallChartVO;
import com.qgyun.hltgq.hltgqsite.vo.GqRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirExtremeRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirPeriodRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirRainfallBriefVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirTenDayRainfallVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/st-pptn-r")
public class StPptnRController {

    @Autowired
    private StPptnRService stPptnRService;

    @GetMapping("/latest")
    public List<StPptnR> latest() {
        return stPptnRService.latestPerStation();
    }

    @GetMapping("/list")
    public List<StPptnR> list(@RequestParam(required = false) String stcd) {
        QueryWrapper<StPptnR> wrapper = new QueryWrapper<StPptnR>().orderByAsc("TM");
        if (stcd != null) wrapper.eq("STCD", stcd);
        return stPptnRService.list(wrapper);
    }

    @GetMapping("/page-daily")
    public IPage<StPptnR> pageDaily(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        QueryWrapper<StPptnR> wrapper = new QueryWrapper<>();
        if (stcd != null) wrapper.eq("STCD", stcd);
        if (startTime != null) wrapper.ge("TM", Timestamp.valueOf(startTime));
        if (endTime != null) wrapper.le("TM", Timestamp.valueOf(endTime));
        return stPptnRService.dailyPage(new Page<>(page, size), wrapper);
    }

    @GetMapping("/page")
    public Page<StPptnR> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        QueryWrapper<StPptnR> wrapper = new QueryWrapper<>();
        if (stcd != null) wrapper.eq("STCD", stcd);
        if (startTime != null) wrapper.ge("TM", Timestamp.valueOf(startTime));
        if (endTime != null) wrapper.le("TM", Timestamp.valueOf(endTime));
        return (Page<StPptnR>) stPptnRService.page(new Page<StPptnR>(page, size).addOrder(OrderItem.asc("TM")), wrapper);
    }

    @GetMapping("/export")
    public List<StPptnR> export(
            @RequestParam(required = false) String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        QueryWrapper<StPptnR> wrapper = new QueryWrapper<StPptnR>().orderByAsc("TM");
        if (stcd != null) wrapper.eq("STCD", stcd);
        if (startTime != null) wrapper.ge("TM", Timestamp.valueOf(startTime));
        if (endTime != null) wrapper.le("TM", Timestamp.valueOf(endTime));
        return stPptnRService.list(wrapper);
    }

    @GetMapping
    public StPptnR getOne(
            @RequestParam String stcd,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tm) {
        return stPptnRService.getOne(new QueryWrapper<StPptnR>()
                .eq("STCD", stcd)
                .eq("TM", Timestamp.valueOf(tm)));
    }

    @PostMapping
    public boolean save(@RequestBody StPptnR stPptnR) {
        return stPptnRService.saveOrUpdateByKey(stPptnR);
    }

    @PutMapping
    public boolean update(@RequestBody StPptnR stPptnR) {
        return stPptnRService.update(stPptnR, new UpdateWrapper<StPptnR>()
                .eq("STCD", stPptnR.getStcd())
                .eq("TM", Timestamp.valueOf(stPptnR.getTm())));
    }

    @DeleteMapping
    public boolean delete(
            @RequestParam String stcd,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime tm) {
        return stPptnRService.remove(new QueryWrapper<StPptnR>()
                .eq("STCD", stcd)
                .eq("TM", Timestamp.valueOf(tm)));
    }

    /**
     * 灌区雨量监测：每站点最新一条，含1h/3h/6h时段降雨增量
     * 支持按站点编号、监测日期范围筛选，自定义分页
     */
    @GetMapping("/gq-rainfall")
    public IPage<GqRainfallVO> gqRainfall(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return stPptnRService.gqRainfallPage(page, size, stcd, startTime, endTime);
    }

    /**
     * 灌区雨量变化图表：单站点小时级增量+累计雨量
     * stcd 必填；startTime/endTime 非必填，默认近 7 天
     */
    @GetMapping("/gq-rainfall-chart")
    public GqRainfallChartVO gqRainfallChart(
            @RequestParam String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        if (endTime == null) endTime = now;
        if (startTime == null) startTime = now.minusDays(7);
        return stPptnRService.gqRainfallChart(stcd, startTime, endTime);
    }

    /**
     * 灌区雨量历史：单站点全部记录（含1h/3h/6h时段增量），支持时间范围筛选
     * stcd 必填
     */
    @GetMapping("/gq-rainfall-history")
    public IPage<GqRainfallVO> gqRainfallHistory(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return stPptnRService.gqRainfallHistoryPage(page, size, stcd, startTime, endTime);
    }

    /**
     * 水库实时雨情 / 水库日雨情（双视角）
     * stations[]: 实时雨情 — 各站点最新观测快照（latestTm = 实际观测时间，latestDrp = 当前降雨量 DRP）
     * days[]:    日雨情 — 按水文日（8:00 切分）聚合的逐日雨量透视表
     * startDate/endDate 非必填，默认当天
     */
    @GetMapping("/reservoir-rainfall")
    public ReservoirRainfallVO reservoirRainfall(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        LocalDate now = LocalDate.now();
        if (startDate == null) startDate = now;
        if (endDate == null) endDate = now;
        return stPptnRService.reservoirRainfall(startDate, endDate);
    }

    /**
     * 水库时段雨情：12 个固定站点，按可配时间间隔聚合时段雨量
     * startDate/endDate 非必填默认当天，interval 默认 60（可选 15/30/45/60/120/180/240/360/480/720/1440）
     */
    @GetMapping("/reservoir-period-rainfall")
    public ReservoirPeriodRainfallVO reservoirPeriodRainfall(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "60") int interval) {
        LocalDate now = LocalDate.now();
        if (startDate == null) startDate = now;
        if (endDate == null) endDate = now;
        return stPptnRService.reservoirPeriodRainfall(startDate, endDate, interval);
    }

    /**
     * 水库旬月雨情：12 个固定站点，按旬（上/中/下旬）聚合雨量 + 平均值
     * yearMonth 必填，格式 yyyy-MM
     */
    @GetMapping("/reservoir-ten-day-rainfall")
    public ReservoirTenDayRainfallVO reservoirTenDayRainfall(
            @RequestParam String yearMonth) {
        return stPptnRService.reservoirTenDayRainfall(yearMonth);
    }

    /**
     * 水库极值雨情：12 个站点在各时间窗口内的最大雨量
     * startDate/endDate 非必填，默认当月
     */
    @GetMapping("/reservoir-extreme-rainfall")
    public List<ReservoirExtremeRainfallVO> reservoirExtremeRainfall(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        LocalDate now = LocalDate.now();
        if (startDate == null) startDate = now.withDayOfMonth(1);
        if (endDate == null) endDate = now;
        return stPptnRService.reservoirExtremeRainfall(startDate, endDate);
    }

    /**
     * 水库雨情简报：指定日期各站点日/旬/月三级雨量
     * date 非必填，默认当天
     */
    @GetMapping("/reservoir-rainfall-brief")
    public List<ReservoirRainfallBriefVO> reservoirRainfallBrief(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        if (date == null) date = LocalDate.now();
        return stPptnRService.reservoirRainfallBrief(date);
    }
}
