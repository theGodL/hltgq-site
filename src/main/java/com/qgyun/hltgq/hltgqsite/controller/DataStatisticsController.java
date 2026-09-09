package com.qgyun.hltgq.hltgqsite.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.qgyun.hltgq.hltgqsite.mapper.DataStatsMapper;
import com.qgyun.hltgq.hltgqsite.stats.client.DeviceStatsClient;
import com.qgyun.hltgq.hltgqsite.stats.client.MqStatsClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据统计大屏（网关转发 hltgq-mq 内部统计接口 /api/report/* + hltgq-device 视频巡检统计 + 本地预警发布统计）。
 * <p>mq/device 统计接口均仅内网可达、不对外暴露，本层把上游响应中的 data 段原样透传，
 * 前端字段契约与 hltgq-mq 数据统计.md 一致；mq/device 不可达或返回业务错误时统一 HTTP 502。
 * <p>说明：到报/缺测口径由 mq 统一计算（mq 侧 60s 缓存），site 不另起口径、不做本地 COUNT；
 * 仅「信息发布情况（预警信息）」为 site 本地数据（本项目无发布动作，每次生成告警即发布）。
 */
@RestController
@RequestMapping("/data-statistics")
public class DataStatisticsController {

    @Autowired
    private MqStatsClient mqStatsClient;

    @Autowired
    private DeviceStatsClient deviceStatsClient;

    @Autowired
    private DataStatsMapper dataStatsMapper;

    /**
     * 统计卡片聚合（KPI）：stationTotal 站点总数 / todayArrivalRate 今日到报率 /
     * monthAvgArrivalRate 本月平均到报率 / todayMissRate 今日缺测率 /
     * statStartDate 统计起始日 / noReportSites 未到报站点列表 / missedSites 今日缺测站点列表
     */
    @GetMapping("/arrival-stats")
    public JsonNode arrivalStats() {
        return mqStatsClient.arrivalStats();
    }

    /**
     * 站点到报明细：每站一行，含站名/编号/报文类型/应到/实到/缺报/到报率
     * （"状态"档位属展示口径，mq 只给 arrivalRate 数字，由前端按档位规则渲染）
     */
    @GetMapping("/arrival-detail")
    public JsonNode arrivalDetail() {
        return mqStatsClient.arrivalDetail();
    }

    /** 缺测明细：连续缺测窗合并为段，含起止时间/时长/数据类型/当前状态 */
    @GetMapping("/miss-detail")
    public JsonNode missDetail() {
        return mqStatsClient.missDetail();
    }

    /** 数据采集状态统计：水位/流量/雨量/闸门开度/墒情各维度应采/实采/成功/失败/成功率/失败率 */
    @GetMapping("/collect-stats")
    public JsonNode collectStats() {
        return mqStatsClient.collectStats();
    }

    /**
     * 视频数据采集统计（数据采集状态统计的「视频数据」行，与 mq 五行合并渲染）：
     * 转发 hltgq-device /api/dahua/video/patrol-stats（device 响应 {success,code,desc,data}，
     * 与 mq 的 {code,msg,data} 结构不同，按 success 判定）；device 与 site 共用平台会话，
     * 出站自动透传当前登录会话 X-Session-Id 通过 device 登录校验。
     */
    @GetMapping("/video-collect")
    public JsonNode videoCollect() {
        return deviceStatsClient.videoPatrolStats();
    }

    /** 采集服务状态：mq 进程指标（启动时间/时长/CPU/内存）+ 数据接收/解析/存储三逻辑服务 */
    @GetMapping("/service-status")
    public JsonNode serviceStatus() {
        return mqStatsClient.serviceStatus();
    }

    /**
     * 信息发布情况（预警信息）：本项目无发布操作，业务口径「每次生成告警即发布」，
     * 统计告警表 t_auto_hltgq_water_alert 当日生成的告警数（生成即发布成功，失败恒 0）；
     * 业务已拍板仅保留「预警信息」一种发布类型（水情通报/调度指令/日报周报均不统计）。
     */
    @GetMapping("/publish-stats")
    public List<Map<String, Object>> publishStats() {
        Map<String, Object> row = new LinkedHashMap<>();
        Map<String, Object> agg = dataStatsMapper.selectTodayAlertPublish(LocalDate.now().atStartOfDay());
        long total = agg == null || agg.get("total") == null
                ? 0L : ((Number) agg.get("total")).longValue();
        row.put("type", "预警信息");
        row.put("count", total);
        row.put("ok", total);
        row.put("fail", 0);
        row.put("okRate", 100.0);
        row.put("latest", agg == null ? null : agg.get("latest"));
        row.put("status", "正常");
        return Collections.singletonList(row);
    }
}
