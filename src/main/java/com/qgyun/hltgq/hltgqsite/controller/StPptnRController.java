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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
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
}
