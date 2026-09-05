package com.qgyun.hltgq.hltgqsite.external.controller;

import com.qgyun.hltgq.hltgqsite.external.service.ExternalService;
import com.qgyun.hltgq.hltgqsite.external.vo.ExternalVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 三维系统对接接口（/external）：面向第三方大屏/数字孪生的外部数据服务。
 * <p>契约见 src/main/resources/三维系统对接接口.md。全部为实时查询（无轮询任务）；
 * 参数越界由全局 IllegalArgumentException 处理返回 400；走现有登录会话鉴权。
 */
@RestController
@RequestMapping("/external")
public class ExternalController {

    @Autowired
    private ExternalService externalService;

    /**
     * 闸门类型数量：节制闸/分水口/退水闸/倒虹吸，count 暂恒 0（分类数据待三方收集补录）。
     */
    @GetMapping("/gate-type-count")
    public ExternalVO.GateTypeCount gateTypeCount() {
        return externalService.gateTypeCount();
    }

    /**
     * 渠首进水闸实时数据：闸前/闸后水位、流量、管理单位（花凉亭灌区）。
     */
    @GetMapping("/intake-gate")
    public ExternalVO.IntakeGate intakeGate() {
        return externalService.intakeGate();
    }

    /**
     * 巡检汇总：累计巡检次数、巡检计划数、完成巡检数、维养预算、维养成本。
     */
    @GetMapping("/patrol-summary")
    public ExternalVO.PatrolSummary patrolSummary() {
        return externalService.patrolSummary();
    }

    /**
     * 巡检/问题逐日趋势：日期区间聚合（yyyy-MM-dd），默认近 30 天，上限 366 天。
     */
    @GetMapping("/daily-trend")
    public ExternalVO.DailyTrend dailyTrend(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return externalService.dailyTrend(startDate, endDate);
    }

    /**
     * 问题状态统计：已处理/未整改/突发事件/已解除响应/未解除响应。
     */
    @GetMapping("/issue-stats")
    public ExternalVO.IssueStats issueStats() {
        return externalService.issueStats();
    }

    /**
     * 视频监测按管理所聚合：安装位置首段归组，总数/在线/离线。
     */
    @GetMapping("/video-summary")
    public List<ExternalVO.VideoItem> videoSummary() {
        return externalService.videoSummary();
    }
}
