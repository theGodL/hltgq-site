package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.entity.GateMonitor;
import com.qgyun.hltgq.hltgqsite.mapper.GateMonitorMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/gate-monitor")
public class GateMonitorController {

    @Autowired
    private GateMonitorMapper gateMonitorMapper;

    /**
     * 有闸门数据的站点列表（返回 site + siteName）
     */
    @GetMapping("/sites")
    public List<GateMonitor> sites() {
        return gateMonitorMapper.selectGateSites();
    }

    /**
     * 指定站点下的闸孔列表
     */
    @GetMapping("/gates")
    public List<String> gates(@RequestParam String siteId) {
        return gateMonitorMapper.selectGatesBySite(siteId);
    }

    /**
     * 闸门历史数据（按站点 + 可选多闸孔，小时级聚合）
     *
     * @param siteId    站点 UUID
     * @param gateNos   闸孔编号列表（逗号分隔），空=全部
     * @param startTime 开始时间 yyyy-MM-dd HH:mm:ss
     * @param endTime   结束时间 yyyy-MM-dd HH:mm:ss
     */
    @GetMapping("/export")
    public List<GateMonitor> export(@RequestParam String siteId,
                                    @RequestParam(defaultValue = "") List<String> gateNos,
                                    @RequestParam String startTime,
                                    @RequestParam String endTime) {
        List<String> filtered = (gateNos.size() == 1 && gateNos.get(0).isEmpty())
                ? Collections.emptyList() : gateNos;
        return gateMonitorMapper.selectHourlyAggregated(siteId, filtered, startTime, endTime);
    }
}
