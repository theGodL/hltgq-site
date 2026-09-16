package com.qgyun.hltgq.hltgqsite.service.impl;

import com.qgyun.hltgq.hltgqsite.entity.GateMonitor;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.mapper.GateMonitorMapper;
import com.qgyun.hltgq.hltgqsite.mapper.IrrigationWaterLevelMapper;
import com.qgyun.hltgq.hltgqsite.mapper.SoilMoistureMapper;
import com.qgyun.hltgq.hltgqsite.mapper.StPptnRMapper;
import com.qgyun.hltgq.hltgqsite.mapper.StStinfoMapper;
import com.qgyun.hltgq.hltgqsite.mapper.WaterFlowMapper;
import com.qgyun.hltgq.hltgqsite.service.StPptnRService;
import com.qgyun.hltgq.hltgqsite.service.StStinfoService;
import com.qgyun.hltgq.hltgqsite.service.StationSiteService;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSitesVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 监测类型站点集合实现：各类型的站点清单来源保持与既有接口完全一致
 * （雨量=雨量表 distinct STCD、灌区雨量=排除水库 13 站、水位=河道水文站点、
 * 闸门=闸门表站点、流量=流量表 COALESCE(stcd, site)、墒情=墒情表 COALESCE(stcd, site)）。
 */
@Service
public class StationSiteServiceImpl implements StationSiteService {

    /** 支持的监测类型（与 /station-metrics/sites?type= 值域一致） */
    private static final List<String> SUPPORTED_TYPES = Arrays.asList(
            "rainfall", "gq-rainfall", "waterLevel", "gate", "flow", "moisture");

    @Autowired
    private StStinfoService stStinfoService;

    @Autowired
    private StPptnRMapper stPptnRMapper;

    @Autowired
    private StPptnRService stPptnRService;

    @Autowired
    private IrrigationWaterLevelMapper irrigationWaterLevelMapper;

    @Autowired
    private GateMonitorMapper gateMonitorMapper;

    @Autowired
    private WaterFlowMapper waterFlowMapper;

    @Autowired
    private SoilMoistureMapper soilMoistureMapper;

    @Autowired
    private StStinfoMapper stStinfoMapper;

    @Override
    public List<String> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public List<StationSiteVO> sitesOfType(String metricType) {
        if (metricType == null || metricType.trim().isEmpty()) {
            throw new IllegalArgumentException("监测类型不能为空");
        }
        switch (metricType.trim()) {
            case "rainfall":
                return rainfallSites();
            case "gq-rainfall":
                // 灌区雨量：排除水库 13 站（STCD + 名称双重排除，见 StPptnRServiceImpl.resolveGqStcds）
                return attachSiteIds(stPptnRService.gqRainfallSites());
            case "waterLevel":
                return attachSiteIds(irrigationWaterLevelMapper.selectWaterLevelStations());
            case "gate":
                List<GateMonitor> gateSites = gateMonitorMapper.selectGateSites();
                List<StationSiteVO> gateList = attachSiteIds(gateSites.stream().map(g -> {
                    StationSiteVO s = new StationSiteVO();
                    s.setCode(g.getSite());
                    s.setName(g.getSiteName());
                    return s;
                }).collect(Collectors.toList()));
                // 默认顺序与闸门监测列表（/gate-monitor/monitoring）一致：置前站点按固定顺序在前，
                // 其余站点保持 SQL 的名称顺序（稳定排序，同组内相对顺序不变）
                gateList.sort(Comparator.comparingInt((StationSiteVO s) -> gatePriorityIndex(s.getName())));
                return gateList;
            case "flow":
                return attachSiteIds(waterFlowMapper.selectFlowStations());
            case "moisture":
                return attachSiteIds(soilMoistureMapper.selectMoistureStations());
            default:
                throw new IllegalArgumentException("无效的 type 值: " + metricType
                        + "，可选: " + String.join(" / ", SUPPORTED_TYPES));
        }
    }

    /**
     * 雨量站清单（全部雨量站，含花凉亭水库站点）：
     * 一次批量查站点档案表取站名与站点管理主键（排序配置的站点标识），避免逐站查询。
     */
    private List<StationSiteVO> rainfallSites() {
        List<String> stcds = stPptnRMapper.selectDistinctRainfallStcds().stream()
                .map(StationSiteServiceImpl::trim)
                .filter(s -> s != null)
                .distinct()
                .collect(Collectors.toList());
        Map<String, StStinfo> infoMap = new HashMap<>();
        if (!stcds.isEmpty()) {
            for (StStinfo info : stStinfoService.listByIds(stcds)) {
                String stcd = info != null ? trim(info.getStcd()) : null;
                if (stcd != null) {
                    infoMap.putIfAbsent(stcd, info);
                }
            }
        }
        List<StationSiteVO> result = new ArrayList<>();
        for (String stcd : stcds) {
            StStinfo info = infoMap.get(stcd);
            StationSiteVO s = new StationSiteVO();
            s.setCode(stcd);
            s.setName(info != null && info.getStnm() != null ? info.getStnm() : stcd);
            s.setSiteId(info != null ? trim(info.getId()) : null);
            result.add(s);
        }
        return result;
    }

    /**
     * 补齐站点管理主键（siteId）：站点排序配置以站点管理主键为站点标识。
     * <p>标识可能是测站编码（雨量/水位），也可能是站点管理主键本身（闸门/流量/墒情业务表 site 列），
     * 统一由档案表一次批量解析；档案中无对应记录的站点 siteId 为空，不参与排序。
     */
    private List<StationSiteVO> attachSiteIds(List<StationSiteVO> sites) {
        if (sites == null || sites.isEmpty()) {
            return sites;
        }
        List<String> codes = sites.stream().map(s -> trim(s.getCode()))
                .filter(s -> s != null).distinct().collect(Collectors.toList());
        if (codes.isEmpty()) {
            return sites;
        }
        Map<String, String> idByStcd = new HashMap<>();
        Set<String> archiveIds = new HashSet<>();
        for (Map<String, String> row : stStinfoMapper.selectIdByCodes(codes)) {
            String stcd = trim(row.get("iofhpi"));
            String id = trim(row.get("id"));
            if (id == null) continue;
            archiveIds.add(id);
            if (stcd != null) {
                idByStcd.putIfAbsent(stcd, id);
            }
        }
        for (StationSiteVO site : sites) {
            String code = trim(site.getCode());
            if (code == null) continue;
            String id = idByStcd.get(code);
            site.setSiteId(id != null ? id : (archiveIds.contains(code) ? code : null));
        }
        return sites;
    }

    /**
     * 闸门置前站点位次：不在置前清单（{@link StationSiteService#GATE_PRIORITY_STATIONS}）内的站点
     * 统一排在最后，组内保持原（名称）顺序。
     */
    private static int gatePriorityIndex(String name) {
        int index = name == null ? -1 : GATE_PRIORITY_STATIONS.indexOf(name);
        return index < 0 ? GATE_PRIORITY_STATIONS.size() : index;
    }

    /** 站点标识归一：去首尾空白，空串按 null 处理 */
    private static String trim(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    @Override
    public StationSitesVO allSites() {
        StationSitesVO vo = new StationSitesVO();
        vo.setRainfall(sitesOfType("rainfall"));
        vo.setWaterLevel(sitesOfType("waterLevel"));
        vo.setGate(sitesOfType("gate"));
        vo.setFlow(sitesOfType("flow"));
        vo.setMoisture(sitesOfType("moisture"));
        return vo;
    }
}
