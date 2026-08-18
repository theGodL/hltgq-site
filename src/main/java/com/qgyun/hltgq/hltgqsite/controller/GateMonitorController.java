package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.entity.GateMonitor;
import com.qgyun.hltgq.hltgqsite.mapper.GateMonitorMapper;
import com.qgyun.hltgq.hltgqsite.service.GateMonitorService;
import com.qgyun.hltgq.hltgqsite.vo.GateCumulativeFlowVO;
import com.qgyun.hltgq.hltgqsite.vo.GateDeviceVO;
import com.qgyun.hltgq.hltgqsite.vo.GateMonitoringVO;
import com.qgyun.hltgq.hltgqsite.vo.GateMonthCumulativeFlowVO;
import com.qgyun.hltgq.hltgqsite.vo.GateStationWaterLevelVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
     * <p>每个闸站返回一条记录，包含该站所有闸孔的最新开度、闸前/闸后水位、状态、流量 q 及站点经纬度。
     * 支持按站点、日期区间筛选（仅查询该时间范围内的最新数据）。
     *
     * @param site      站点 UUID（可选，不传=全部站点）
     * @param startTime 起始时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param endTime   截止时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @return 各闸站最新监测数据列表
     */
    @GetMapping("/monitoring")
    public List<GateMonitoringVO> monitoring(
            @RequestParam(required = false) String site,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return gateMonitorService.monitoring(site, startTime, endTime);
    }

    /**
     * 闸门历史数据
     * <p>按监测时间分页，根据 type 返回不同结构的数据。
     * <ul>
     *   <li>开度：每行包含 tm + 各闸孔开度（open1, open2...）</li>
     *   <li>水位：每行包含 tm + 闸前水位（upZ）、闸后水位（downZ）</li>
     *   <li>流量：每行包含 tm + 瞬时流量（q）、累计流量（tf），数据来自流量表 t_auto_hltgq_water_wt_nfo</li>
     * </ul>
     *
     * @param siteId    站点 UUID（可选，不传=全部站点）
     * @param type      数据类型："opening"（开度）、"waterLevel"（水位）或 "flow"（流量）
     * @param startTime 起始时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param endTime   截止时间（含），格式 yyyy-MM-dd HH:mm:ss，可选
     * @param page      页码，默认 1
     * @param size      每页条数，默认 10
     */
    @GetMapping("/history")
    public Page<Map<String, Object>> history(
            @RequestParam(required = false) String siteId,
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return gateMonitorService.history(siteId, type, startTime, endTime, page, size);
    }

    /**
     * 闸站图表-固定七站闸前/闸后水位（按日期时间点查询）
     * <p>返回固定顺序七站（渠首进水闸、双庙湖节制闸、南山寺节制闸、毕岭节制闸、
     * 汪元节制闸、北干渠进水闸、南干渠进水闸），每站取距选中时间点最近（±30 分钟内）
     * 的闸门入库数据的水位；半小时内无入库数据则该站水位为 null（该时间点无报文）。
     *
     * @param time 选中时间点，格式 yyyy-MM-dd HH:mm（半小时粒度，如 2026-08-16 17:30）
     */
    @GetMapping("/station-water-level")
    public List<GateStationWaterLevelVO> stationWaterLevel(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime time) {
        return gateMonitorService.stationWaterLevel(time);
    }

    /**
     * 闸站累计流量（月累计 + 年累计）
     * <p>月累计 = 当月 1日 0点起至最新数据时间 = ttf(最新) − ttf(monthStart 前最近行)；
     * 年累计 = 当年 1月1日 0点起至最新数据时间（流量表 ytf）。
     *
     * @param siteId     站点 UUID（必填），如渠首进水闸 CAYQ739MiBWMg9gQvyi
     * @param monthStart 月累计起点（可选，默认当月 1日 0点），格式 yyyy-MM-dd HH:mm:ss
     */
    @GetMapping("/cumulative-flow")
    public GateCumulativeFlowVO cumulativeFlow(
            @RequestParam String siteId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime monthStart) {
        return gateMonitorService.cumulativeFlow(siteId, monthStart);
    }

    /**
     * 闸站月度累计取水量趋势（近 months 个月，含当月，供月度趋势图表）
     * <p>月累计口径同 cumulativeFlow：当月 1日 0点 ≤ tm < 下月 1日 0点
     * = ttf(月内最新) − ttf(月初前最近)，当月累计截至最新数据时间。
     *
     * @param siteId 站点 UUID（必填），如渠首进水闸 CAYQ739MiBWMg9gQvyi
     * @param months 月数（默认 12，上限 24），当前月为最后一个月
     * @return 每月一个数据点（时间升序，最早在前），月内无 ttf 数据时累计为 null
     */
    @GetMapping("/monthly-cumulative-flow")
    public List<GateMonthCumulativeFlowVO> monthlyCumulativeFlow(
            @RequestParam String siteId,
            @RequestParam(defaultValue = "12") int months) {
        return gateMonitorService.monthlyCumulativeFlow(siteId, months);
    }
}
