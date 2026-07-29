package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.entity.GateMonitor;
import com.qgyun.hltgq.hltgqsite.mapper.GateMonitorMapper;
import com.qgyun.hltgq.hltgqsite.service.GateMonitorService;
import com.qgyun.hltgq.hltgqsite.vo.GateDeviceVO;
import com.qgyun.hltgq.hltgqsite.vo.GateHistoryVO;
import com.qgyun.hltgq.hltgqsite.vo.GateMonitoringVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/gate-monitor")
public class GateMonitorController {

    @Autowired
    private GateMonitorMapper gateMonitorMapper;

    @Autowired
    private GateMonitorService gateMonitorService;

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
     * 指定站点下的闸孔设备列表（设备名称、ID、闸孔编号）
     */
    @GetMapping("/devices")
    public List<GateDeviceVO> devices(@RequestParam String siteId) {
        return gateMonitorMapper.selectDevicesBySite(siteId);
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

    /**
     * 闸门监测-最新数据
     * <p>每个闸站返回一条记录，包含该站所有闸孔的最新开度、闸前/闸后水位及状态。
     * 支持按日期区间筛选（仅查询该时间范围内的最新数据）。
     *
     * @param startTime 起始时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param endTime   截止时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @return 各闸站最新监测数据列表
     */
    @GetMapping("/monitoring")
    public List<GateMonitoringVO> monitoring(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return gateMonitorService.monitoring(startTime, endTime);
    }

    /**
     * 闸门历史数据
     * <p>分页返回每个闸孔的原始监测记录，含设备名称、开度、闸前后水位。
     * 支持按站点、闸孔编号和日期区间筛选。
     *
     * @param siteId    站点 UUID（可选，不传=全部站点）
     * @param gateNo    闸孔编号（可选，如 "1"、"2"），不传=全部闸孔
     * @param startTime 起始时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param endTime   截止时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param page      页码，默认 1
     * @param size      每页条数，默认 20
     */
    @GetMapping("/history")
    public Page<GateHistoryVO> history(
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String gateNo,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return gateMonitorService.history(siteId, gateNo, startTime, endTime, page, size);
    }
}
