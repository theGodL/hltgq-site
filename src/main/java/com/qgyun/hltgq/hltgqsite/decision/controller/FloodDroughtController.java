package com.qgyun.hltgq.hltgqsite.decision.controller;

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
 * GET /flood-drought/history 历史实测块（同步：区间逐日 + 三序列，站点可切换）。
 * <p>预测块不在此控制器：前端下拉短期预报方案，复用 /water-forecast/short/list 与
 * /water-forecast/short/{id}（原 hydro 异步三连「一张图混拼实测/预测」已退役）。
 */
@RestController
@RequestMapping("/flood-drought")
public class FloodDroughtController {

    @Autowired
    private FloodDroughtService floodDroughtService;

    /** 可切换候选站：{ waterLevel: [...], rain: [...], flow: [...] }，元素 { stcd, stnm }。 */
    @GetMapping("/stations")
    public Map<String, Object> stations() {
        return floodDroughtService.stations();
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
