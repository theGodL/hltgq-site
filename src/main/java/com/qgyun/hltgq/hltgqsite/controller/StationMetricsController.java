package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.entity.StRiverR;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.mapper.StPptnRMapper;
import com.qgyun.hltgq.hltgqsite.service.StPptnRService;
import com.qgyun.hltgq.hltgqsite.service.StRiverRService;
import com.qgyun.hltgq.hltgqsite.service.StStinfoService;
import com.qgyun.hltgq.hltgqsite.service.StationSiteService;
import com.qgyun.hltgq.hltgqsite.service.StationSortService;
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
    private StationSiteService stationSiteService;

    @Autowired
    private StationSortService stationSortService;

    /**
     * 全量站点分类查询
     *
     * @param type 可选筛选：rainfall(雨量) / waterLevel(水位) / gate(闸门) / flow(流量)
     *             / gq-rainfall(灌区雨量，排除水库13站) / moisture(墒情)。
     *             不传则返回全部分类，按 JSON key 分组。
     *             <p>展示顺序 = 「站点排序」配置（监测类型维度）优先：已配置站点按配置序号，
     *             未配置站点保持该类型默认顺序并排在其后。
     */
    @GetMapping("/sites")
    public Object sites(@RequestParam(required = false) String type) {
        // 指定类型 → 返回单一列表
        if (type != null && !type.isEmpty()) {
            return applyOrder(type, stationSiteService.sitesOfType(type));
        }

        // 不传 type → 返回全量分组
        StationSitesVO vo = stationSiteService.allSites();
        vo.setRainfall(applyOrder("rainfall", vo.getRainfall()));
        vo.setWaterLevel(applyOrder("waterLevel", vo.getWaterLevel()));
        vo.setGate(applyOrder("gate", vo.getGate()));
        vo.setFlow(applyOrder("flow", vo.getFlow()));
        vo.setMoisture(applyOrder("moisture", vo.getMoisture()));
        return vo;
    }

    /**
     * 按「站点排序」配置调整展示顺序（站点标识 = 站点管理主键；该类型未配置过排序时保持默认顺序）
     * <p>gq-rainfall（灌区雨量）与 rainfall（雨量）共用同一排序序列（灌区站是雨量站子集）。
     */
    private List<StationSiteVO> applyOrder(String metricType, List<StationSiteVO> sites) {
        return stationSortService.applyOrder(metricType, sites, StationSiteVO::getSiteId);
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
