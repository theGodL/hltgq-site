package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.service.IrrigationWaterLevelService;
import com.qgyun.hltgq.hltgqsite.vo.IrrigationWaterLevelChartVO;
import com.qgyun.hltgq.hltgqsite.vo.IrrigationWaterLevelHistoryVO;
import com.qgyun.hltgq.hltgqsite.vo.IrrigationWaterLevelVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 水位监测-灌区接口
 * <p>
 * 返回所有站点的最新一条水位数据，每个站点只有一条。
 * 返回字段：站点名称、站点ID、站点编号、监测日期、水位值(m)、1h水位涨幅(cm)。
 * 支持根据站点和监测日期查询，支持自定义分页。
 */
@RestController
@RequestMapping("/irrigation-water-level")
public class IrrigationWaterLevelController {

    @Autowired
    private IrrigationWaterLevelService irrigationWaterLevelService;

    /**
     * 分页查询灌区水位数据
     *
     * @param page      页码（默认1）
     * @param size      每页条数（默认20）
     * @param stcd      站点编号（可选）
     * @param startTime 监测开始时间（可选，格式：yyyy-MM-dd HH:mm:ss）
     * @param endTime   监测结束时间（可选，格式：yyyy-MM-dd HH:mm:ss）
     * @return 分页结果
     */
    @GetMapping("/page")
    public Page<IrrigationWaterLevelVO> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return irrigationWaterLevelService.page(
                new Page<>(page, size), stcd, startTime, endTime);
    }

    /**
     * 水位变化图表：单站点小时级水位值 + 水位变化（用于水位统计曲线图）
     * stcd 必填；startTime/endTime 非必填，默认近 7 天
     */
    @GetMapping("/chart")
    public IrrigationWaterLevelChartVO waterLevelChart(
            @RequestParam String stcd,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        if (endTime == null) endTime = now;
        if (startTime == null) startTime = now.minusDays(7);
        return irrigationWaterLevelService.waterLevelChart(stcd, startTime, endTime);
    }

    /**
     * 水位历史数据（分页）：单站点小时级水位值 + 1h涨幅
     * stcd 必填；startTime/endTime 非必填，默认近 7 天
     */
    @GetMapping("/history")
    public Page<IrrigationWaterLevelHistoryVO> history(
            @RequestParam String stcd,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        if (endTime == null) endTime = now;
        if (startTime == null) startTime = now.minusDays(7);
        return irrigationWaterLevelService.waterLevelHistory(stcd, startTime, endTime, page, size);
    }
}
