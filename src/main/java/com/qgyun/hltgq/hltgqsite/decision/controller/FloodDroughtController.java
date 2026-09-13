package com.qgyun.hltgq.hltgqsite.decision.controller;

import com.qgyun.hltgq.hltgqsite.decision.service.FloodDroughtOverviewService;
import com.qgyun.hltgq.hltgqsite.decision.service.FloodDroughtService;
import com.qgyun.hltgq.hltgqsite.decision.vo.HydroHistoryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 防洪抗旱决策接口（会议 2 定稿版）：
 * <p>GET /flood-drought/stations 可切换候选站（水位/雨量/流量三组下拉）；
 * GET /flood-drought/history 历史实测块（同步：区间逐日 + 三序列，站点可切换）；
 * GET /flood-drought/overview 默认区间概览（实测 + 预置预测，进页面自动加载，不触发模型）。
 * <p>预测块计算不在此控制器：前端下拉短期预报方案或 POST /water-forecast/short 提交计算，
 * 复用 /water-forecast/short/list 与 /water-forecast/short/{id}（原 hydro 异步三连已退役）。
 */
@RestController
@RequestMapping("/flood-drought")
public class FloodDroughtController {

    @Autowired
    private FloodDroughtService floodDroughtService;

    @Autowired
    private FloodDroughtOverviewService floodDroughtOverviewService;

    /** 可切换候选站：{ waterLevel: [...], rain: [...], flow: [...] }，元素 { stcd, stnm }。 */
    @GetMapping("/stations")
    public Map<String, Object> stations() {
        return floodDroughtService.stations();
    }

    /**
     * 默认区间概览（进页面自动加载）：实测段（今日-2 ~ 今日快查）+ 预置预测段
     * （窗口 [明日 08:00, 今日+13 08:00] 最新已完成短期预报记录的日聚合）。
     * <p>纯读组装，不触发模型计算；预测未就绪时 predMeta.tag=generating/none，前端降级提示
     * 点击「计算」获取最新。
     */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return floodDroughtOverviewService.overview();
    }

    /**
     * 历史实测数据（同步）：区间逐日 + 降雨/水位/流量三序列（各自站点可切换）。
     * 站点参数不传时按配置/类型自动选站；缺数据置 null 不补 0。
     */
    @GetMapping("/history")
    public HydroHistoryVO history(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String levelStcd,
            @RequestParam(required = false) String flowStcd,
            @RequestParam(required = false) String rainStcd) {
        return floodDroughtService.history(startDate, endDate, levelStcd, flowStcd, rainStcd);
    }
}
