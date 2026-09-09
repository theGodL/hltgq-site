package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.service.WaterQualityService;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterQualityTrendVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterQualityVO;
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
 * 水质监测接口
 */
@RestController
@RequestMapping("/water-quality")
public class WaterQualityController {

    @Autowired
    private WaterQualityService waterQualityService;

    /**
     * 水质监测-首页（每站点最新一条）
     * <p>含站点名称、站点编号、监测时间、氨氮/COD/BOD/TP/TN/溶解氧及该站水质阈值行。
     * 支持根据站点（多选，逗号分隔）和日期区间查询。
     *
     * @param stcds    站点标识列表，逗号分隔（可选，不传=全部水质站点）
     * @param startDate 起始日期，格式 yyyy-MM-dd（可选）
     * @param endDate   截止日期（含当日），格式 yyyy-MM-dd（可选）
     */
    @GetMapping("/monitoring")
    public List<WaterQualityVO> monitoring(
            @RequestParam(required = false) String stcds,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return waterQualityService.monitoring(parseStcds(stcds), startDate, endDate);
    }

    /**
     * 水质历史趋势（2 小时级六指标 + 预警阈值，三张图共用）
     * <p>有机污染指标用 codcr/bod5，营养盐占比用 nh3n/tn/tp（阈值换算），溶解氧用 dox。
     * x 轴小时 00:00/02:00/... 对齐偶数小时跨天连续；支持日期区间查询。
     *
     * @param stcd      站点编号或 site UUID（必填）
     * @param startTime 起始时间，格式 yyyy-MM-dd HH:mm:ss（可选，默认 24 小时前对齐偶数小时）
     * @param endTime   截止时间，格式 yyyy-MM-dd HH:mm:ss（可选，默认当前对齐偶数小时）
     */
    @GetMapping("/trend")
    public WaterQualityTrendVO trend(
            @RequestParam String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return waterQualityService.trend(stcd, startTime, endTime);
    }

    /**
     * 水质历史数据
     * <p>分页返回指定站点的历史记录（按监测时间倒序），字段同首页列表，支持日期区间查询。
     *
     * @param stcd      站点编号或 site UUID（必填）
     * @param startTime 起始时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param endTime   截止时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param page      页码，默认 1
     * @param size      每页条数，默认 10
     */
    @GetMapping("/history")
    public Page<WaterQualityVO> history(
            @RequestParam String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return waterQualityService.history(stcd, startTime, endTime, page, size);
    }

    /**
     * 水质监测站点列表（表中有水质数据的站点，供下拉选择）
     */
    @GetMapping("/sites")
    public List<StationSiteVO> sites() {
        return waterQualityService.sites();
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
