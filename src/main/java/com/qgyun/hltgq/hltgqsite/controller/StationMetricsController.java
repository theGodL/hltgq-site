package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.entity.GateMonitor;
import com.qgyun.hltgq.hltgqsite.entity.StRiverR;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.mapper.GateMonitorMapper;
import com.qgyun.hltgq.hltgqsite.mapper.IrrigationWaterLevelMapper;
import com.qgyun.hltgq.hltgqsite.mapper.SoilMoistureMapper;
import com.qgyun.hltgq.hltgqsite.mapper.StPptnRMapper;
import com.qgyun.hltgq.hltgqsite.mapper.WaterFlowMapper;
import com.qgyun.hltgq.hltgqsite.service.StPptnRService;
import com.qgyun.hltgq.hltgqsite.service.StRiverRService;
import com.qgyun.hltgq.hltgqsite.service.StStinfoService;
import com.qgyun.hltgq.hltgqsite.vo.StationMetricsVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSitesVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/station-metrics")
public class StationMetricsController {

    @Autowired
    private StStinfoService stStinfoService;

    @Autowired
    private StRiverRService stRiverRService;

    @Autowired
    private StPptnRService stPptnRService;

    @Autowired
    private StPptnRMapper stPptnRMapper;

    @Autowired
    private IrrigationWaterLevelMapper irrigationWaterLevelMapper;

    @Autowired
    private GateMonitorMapper gateMonitorMapper;

    @Autowired
    private WaterFlowMapper waterFlowMapper;

    @Autowired
    private SoilMoistureMapper soilMoistureMapper;

    /**
     * 全量站点分类查询
     *
     * @param type 可选筛选：rainfall(雨量) / waterLevel(水位) / gate(闸门) / flow(流量)
     *             / gq-rainfall(灌区雨量，排除水库13站)。
     *             不传则返回全部分类，按 JSON key 分组。
     */
    @GetMapping("/sites")
    public Object sites(@RequestParam(required = false) String type) {
        // 指定类型 → 返回单一列表
        if (type != null && !type.isEmpty()) {
            switch (type) {
                case "rainfall":
                    List<String> rainfallStcds = stPptnRMapper.selectDistinctRainfallStcds();
                    List<StationSiteVO> rainfall = new ArrayList<>();
                    for (String stcd : rainfallStcds) {
                        StStinfo info = stStinfoService.getById(stcd);
                        StationSiteVO s = new StationSiteVO();
                        s.setCode(stcd);
                        s.setName(info != null ? info.getStnm() : stcd);
                        rainfall.add(s);
                    }
                    return rainfall;
                case "gq-rainfall":
                    // 灌区雨量：排除水库 13 站（STCD + 名称双重排除，见 StPptnRServiceImpl.resolveGqStcds）
                    return stPptnRService.gqRainfallSites();
                case "waterLevel":
                    return irrigationWaterLevelMapper.selectWaterLevelStations();
                case "gate":
                    List<GateMonitor> gateSites = gateMonitorMapper.selectGateSites();
                    return gateSites.stream().map(g -> {
                        StationSiteVO s = new StationSiteVO();
                        s.setCode(g.getSite());
                        s.setName(g.getSiteName());
                        return s;
                    }).collect(Collectors.toList());
                case "flow":
                    return waterFlowMapper.selectFlowStations();
                case "moisture":
                    return soilMoistureMapper.selectMoistureStations();
                default:
                    throw new IllegalArgumentException("无效的 type 值: " + type + "，可选: rainfall / gq-rainfall / waterLevel / gate / flow / moisture");
            }
        }

        // 不传 type → 返回全量分组
        StationSitesVO vo = new StationSitesVO();
        vo.setRainfall(stPptnRMapper.selectDistinctRainfallStcds().stream().map(stcd -> {
            StStinfo info = stStinfoService.getById(stcd);
            StationSiteVO s = new StationSiteVO();
            s.setCode(stcd);
            s.setName(info != null ? info.getStnm() : stcd);
            return s;
        }).collect(Collectors.toList()));
        vo.setWaterLevel(irrigationWaterLevelMapper.selectWaterLevelStations());
        vo.setGate(gateMonitorMapper.selectGateSites().stream().map(g -> {
            StationSiteVO s = new StationSiteVO();
            s.setCode(g.getSite());
            s.setName(g.getSiteName());
            return s;
        }).collect(Collectors.toList()));
        vo.setFlow(waterFlowMapper.selectFlowStations());
        vo.setMoisture(soilMoistureMapper.selectMoistureStations());
        return vo;
    }

    @GetMapping
    public List<StationMetricsVO> list() {
        List<StStinfo> stations = stStinfoService.list();

        Map<String, StRiverR> riverMap = stRiverRService.latestPerStation()
                .stream()
                .filter(r -> r.getStcd() != null)
                .collect(Collectors.toMap(r -> r.getStcd().trim(), r -> r));

        // 有雨量数据的站点集合（只要 rain_info 表中有记录即为雨量站）
        Set<String> pptnStcds = stPptnRMapper.selectDistinctRainfallStcds()
                .stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        // 当前水文日边界：标签 D 的水文日区间为 (D-1日 08:00:00, D日 08:00:00]（左开右闭）
        // 8 点整归当日标签（与 getHydroDayLabel 的 tm-1s 规则一致）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime t0 = now.minusSeconds(1);
        LocalDateTime hydroStart;
        if (t0.getHour() >= 8) {
            hydroStart = t0.toLocalDate().atTime(8, 0, 0);
        } else {
            hydroStart = t0.toLocalDate().minusDays(1).atTime(8, 0, 0);
        }

        // 当前水文日各站累计降雨量（DYP 正向增量，花凉亭 DRP 恒 0 亦能正确反映）
        Map<String, BigDecimal> todayRainMap = stPptnRService.currentHydroDayRainfall();

        return stations.stream()
                .filter(s -> s.getStcd() != null)
                .map(s -> {
            String stcd = s.getStcd().trim();
            StationMetricsVO vo = new StationMetricsVO();
            vo.setStcd(stcd);
            vo.setStnm(s.getStnm());

            StRiverR river = riverMap.get(stcd);
            if (river != null) {
                vo.setZ(river.getZ() != null ? river.getZ().setScale(2, RoundingMode.DOWN) : null);
                vo.setRiverTm(river.getTm());
            }

            if (pptnStcds.contains(stcd)) {
                BigDecimal rain = todayRainMap.get(stcd);
                vo.setDrp(rain != null ? rain.setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(1));
                vo.setPptnTm(hydroStart.toLocalDate());
            }

            if (river != null && pptnStcds.contains(stcd)) vo.setType("all");
            else if (river != null)                         vo.setType("water");
            else if (pptnStcds.contains(stcd))              vo.setType("rain");

            return vo;
        }).collect(Collectors.toList());
    }
}
