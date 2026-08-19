package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.service.DashboardService;
import com.qgyun.hltgq.hltgqsite.vo.DashboardOverviewVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterAlertVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 设备状态概览（大屏统计）
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * 概览统计：设备总数/在线数/在线百分比、闸门总数/开启数/开启百分比、未处理告警数
     * <p>在线按站点状态字段 zebpsu（#1# 在线，由报文入库项目维护）；闸门开启按各闸孔最新开度 > 0
     * 判定（近 24h 有上报、排除站级行 gate_no='0' 与无信号/异常闸孔）；
     * 未处理告警 = 未关闭（#1# 未确认/#2# 已确认/#3# 处理中）。百分比 1 位小数，分母 0 时为 null。
     */
    @GetMapping("/overview")
    public DashboardOverviewVO overview() {
        return dashboardService.overview();
    }

    /**
     * 某站点未关闭的告警列表（#4# 已关闭不计），按发生时间倒序（最新在前）
     *
     * @param site 站点主键 ID（必填）
     */
    @GetMapping("/alerts")
    public List<WaterAlertVO> alerts(@RequestParam String site) {
        return dashboardService.activeAlerts(site);
    }
}
