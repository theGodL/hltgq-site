package com.qgyun.hltgq.hltgqsite.decision.controller;

import com.qgyun.hltgq.hltgqsite.decision.service.FloodDroughtService;
import com.qgyun.hltgq.hltgqsite.decision.vo.HydroChartVO;
import com.qgyun.hltgq.hltgqsite.decision.vo.HydroSubmitRequest;
import com.qgyun.hltgq.hltgqsite.decision.vo.HydroTaskVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 防洪抗旱决策接口（异步三连）：
 * <p>POST /flood-drought/hydro 提交（秒回 recordId）→ GET /hydro/status/{id} 轮询
 * → GET /hydro/{id} 图数据（仅 completed 后可用）。
 */
@RestController
@RequestMapping("/flood-drought")
public class FloodDroughtController {

    @Autowired
    private FloodDroughtService floodDroughtService;

    /** 提交防洪抗旱水文分析（秒回 recordId），后台异步取数 + 调模型。 */
    @PostMapping("/hydro")
    public Map<String, Object> submitHydro(@RequestBody HydroSubmitRequest req) {
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", floodDroughtService.submit(req));
        return result;
    }

    /** 轮询任务状态：{id, status, errorMsg}；无任务 404。 */
    @GetMapping("/hydro/status/{id}")
    public Map<String, Object> hydroStatus(@PathVariable String id) {
        HydroTaskVO task = floodDroughtService.require(id);
        Map<String, Object> result = new HashMap<>();
        result.put("id", task.getId());
        result.put("status", task.getStatus());
        result.put("errorMsg", task.getErrorMsg());
        return result;
    }

    /** 图数据：仅 completed 后可用；未完成 409、无任务 404。 */
    @GetMapping("/hydro/{id}")
    public HydroChartVO hydroDetail(@PathVariable String id) {
        return floodDroughtService.detail(id);
    }
}
