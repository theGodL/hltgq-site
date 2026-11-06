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
     * 概览统计：设备总数/在线数/在线百分比、闸门设备总数/开启数/开启百分比、未处理告警数
     * <p>两个统计对象都在设备台账，与 /network-device/summary 同源：设备数按台账全部行（设备 status 优先、
     * 空回退所属站点 zebpsu，#1# 在线）；闸门按台账 type 含 #4# 的闸孔设备（开启 = 近 24h 最新开度 > 0，
     * 排除站级行 gate_no='0' 与无信号/异常哨兵值），两接口的闸门数必然同值。
     * <p>未处理告警 = 未关闭（#1# 未确认/#2# 已确认/#3# 处理中）。百分比 2 位小数，分母 0 时为 null。
     * <p>site 与 /dashboard/alerts 同为双键口径（2026-11-06 起）：站点档案主键 id 或站点编号 iofhpi，
     * 两项查的是同一组站点，同一 site 参数下概览卡片与告警列表不会口径不一致。
     *
     * @param site 站点标识：站点档案主键 ID 或站点编号 iofhpi（可选；不传 = 全部站点）；
     *             传了但站点不存在/无设备无闸门无告警时各计数为 0、百分比为 null
     */
    @GetMapping("/overview")
    public DashboardOverviewVO overview(@RequestParam(required = false) String site) {
        return dashboardService.overview(site);
    }

    /**
     * 某站点未关闭的告警列表（#4# 已关闭不计），按发生时间倒序（最新在前）
     * <p>site 兼容站点档案主键 id 与站点编号 iofhpi（如流量站 9000000003），传任一即可；
     * 按站码反查档案主键，同时兜底「告警行 `site` 直接存站码」的历史数据。
     *
     * @param site 站点标识：站点档案主键 ID 或站点编号 iofhpi（必填）
     */
    @GetMapping("/alerts")
    public List<WaterAlertVO> alerts(@RequestParam String site) {
        return dashboardService.activeAlerts(site);
    }
}
