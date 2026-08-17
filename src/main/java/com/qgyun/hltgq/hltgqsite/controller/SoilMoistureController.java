package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.service.SoilMoistureService;
import com.qgyun.hltgq.hltgqsite.vo.SoilMoistureTrendVO;
import com.qgyun.hltgq.hltgqsite.vo.SoilMoistureVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 墒情监测接口
 */
@RestController
@RequestMapping("/soil-moisture")
public class SoilMoistureController {

    @Autowired
    private SoilMoistureService soilMoistureService;

    /**
     * 墒情监测-首页（每站点最新一条）
     * <p>包含站点名称、站点编号、时间、10/20/30/40/50/60/80/100 厘米含水量。
     * 支持根据站点（多选，逗号分隔）和监测日期筛选。
     *
     * @param stcds 站点编号列表，逗号分隔（可选，不传=全部）
     * @param date  监测日期，格式 yyyy-MM-dd（可选）
     */
    @GetMapping("/monitoring")
    public List<SoilMoistureVO> monitoring(
            @RequestParam(required = false) String stcds,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return soilMoistureService.monitoring(parseStcds(stcds), date);
    }

    /**
     * 墒情趋势图表
     * <p>小时级含水量曲线（8 个深度各一条线），默认近七天数据，支持根据日期区间查询。
     *
     * @param stcd      站点编号（必填）
     * @param startTime 起始时间，格式 yyyy-MM-dd HH:mm:ss，可选（默认 7 天前整点）
     * @param endTime   截止时间，格式 yyyy-MM-dd HH:mm:ss，可选（默认当前整点）
     */
    @GetMapping("/trend")
    public SoilMoistureTrendVO trend(
            @RequestParam String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return soilMoistureService.trend(stcd, startTime, endTime);
    }

    /**
     * 墒情历史数据
     * <p>分页返回指定站点的历史墒情记录（按监测时间倒序），支持根据日期区间查询。
     *
     * @param stcd      站点编号（必填）
     * @param startTime 起始时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param endTime   截止时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param page      页码，默认 1
     * @param size      每页条数，默认 10
     */
    @GetMapping("/history")
    public Page<SoilMoistureVO> history(
            @RequestParam String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return soilMoistureService.history(stcd, startTime, endTime, page, size);
    }

    /**
     * 墒情监测站点列表（表中有墒情数据的站点，供下拉选择）
     */
    @GetMapping("/sites")
    public List<StationSiteVO> sites() {
        return soilMoistureService.sites();
    }

    /**
     * 解析逗号分隔的站点编号字符串
     */
    private List<String> parseStcds(String stcds) {
        if (stcds == null || stcds.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(stcds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
