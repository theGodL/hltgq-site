package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.service.FlowMonitorService;
import com.qgyun.hltgq.hltgqsite.vo.FlowMonitoringVO;
import com.qgyun.hltgq.hltgqsite.vo.FlowTrendVO;
import com.qgyun.hltgq.hltgqsite.vo.PeriodRegimeVO;
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
 * 流量监测接口
 */
@RestController
@RequestMapping("/flow-monitor")
public class FlowMonitorController {

    @Autowired
    private FlowMonitorService flowMonitorService;

    /**
     * 流量监测-最新数据
     * <p>每个站点返回一条最新记录，包含站点名称、监测时间、流量。
     * 支持根据站点编号（多选，逗号分隔）和日期区间筛选。
     *
     * @param stcds     站点编号列表，逗号分隔（可选，不传=全部）
     * @param startTime 起始时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param endTime   截止时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     */
    @GetMapping("/monitoring")
    public List<FlowMonitoringVO> monitoring(
            @RequestParam(required = false) String stcds,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        List<String> stcdList = parseStcds(stcds);
        return flowMonitorService.monitoring(stcdList, startTime, endTime);
    }

    /**
     * 流量趋势图表
     * <p>小时级流量曲线，默认近七天数据，支持根据日期区间查询。
     *
     * @param stcd      站点编号（必填）
     * @param startTime 起始时间，格式 yyyy-MM-dd HH:mm:ss，可选（默认 7 天前整点）
     * @param endTime   截止时间，格式 yyyy-MM-dd HH:mm:ss，可选（默认当前整点）
     */
    @GetMapping("/trend")
    public FlowTrendVO trend(
            @RequestParam String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return flowMonitorService.trend(stcd, startTime, endTime);
    }

    /**
     * 流量历史数据
     * <p>分页返回指定站点的历史流量记录（按监测时间倒序），支持根据日期区间查询。
     *
     * @param stcd      站点编号（必填）
     * @param startTime 起始时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param endTime   截止时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param page      页码，默认 1
     * @param size      每页条数，默认 20
     */
    @GetMapping("/history")
    public Page<FlowMonitoringVO> history(
            @RequestParam String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return flowMonitorService.history(stcd, startTime, endTime, page, size);
    }

    /**
     * 日时段水情表（水位站点多选，按日期+时段生成时间槽位，匹配实测数据）
     *
     * @param date     选中日期，格式 yyyy-MM-dd
     * @param interval 时段间隔（小时），1/2/3/6/12，默认 1
     * @param stcds    站点编号，逗号分隔，必填
     */
    @GetMapping("/period-regime")
    public List<PeriodRegimeVO> periodRegime(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(defaultValue = "1") int interval,
            @RequestParam String stcds) {
        return flowMonitorService.periodRegime(date, interval,
                Arrays.asList(stcds.split(",")));
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
