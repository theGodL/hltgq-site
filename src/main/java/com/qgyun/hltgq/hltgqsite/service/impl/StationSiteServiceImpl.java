package com.qgyun.hltgq.hltgqsite.service.impl;

import com.qgyun.hltgq.hltgqsite.entity.GateMonitor;
import com.qgyun.hltgq.hltgqsite.mapper.GateMonitorMapper;
import com.qgyun.hltgq.hltgqsite.mapper.IrrigationWaterLevelMapper;
import com.qgyun.hltgq.hltgqsite.mapper.SoilMoistureMapper;
import com.qgyun.hltgq.hltgqsite.mapper.StPptnRMapper;
import com.qgyun.hltgq.hltgqsite.mapper.StStinfoMapper;
import com.qgyun.hltgq.hltgqsite.mapper.WaterFlowMapper;
import com.qgyun.hltgq.hltgqsite.service.StPptnRService;
import com.qgyun.hltgq.hltgqsite.service.StationSiteService;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSitesVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 监测类型站点集合实现：各类型的站点清单来源保持与既有接口完全一致
 * （雨量=雨量表 distinct STCD、灌区雨量=排除水库 13 站、水位=河道水文站点、
 * 闸门=闸门表站点、流量=流量表 COALESCE(stcd, site)、墒情=墒情表 COALESCE(stcd, site)）。
 * <p>输出字段口径：code 保持各业务表原值（沿用既有契约，未做变更）；在此之上补充
 * stcd=站点编号（站点档案 iofhpi）、siteId=站点管理主键（档案 id）、lon/lat=经纬度，
 * 业务表标识为站点管理主键的类型（闸门/流量/墒情 MQTT 站）由档案反查编号，见 {@link #attachArchiveInfo}。
 */
@Service
public class StationSiteServiceImpl implements StationSiteService {

    /** 支持的监测类型（与 /station-metrics/sites?type= 值域一致） */
    private static final List<String> SUPPORTED_TYPES = Arrays.asList(
            "rainfall", "gq-rainfall", "waterLevel", "gate", "flow", "moisture");

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
                return attachArchiveInfo(stPptnRService.gqRainfallSites(), true);
            case "waterLevel":
                return attachArchiveInfo(irrigationWaterLevelMapper.selectWaterLevelStations(), true);
            case "gate":
                // 闸门业务表 site 列存站点管理主键（非站点编号）：先置该主键作为档案解析键，
                // 再由档案解析出站点编号与经纬度（闸门站清单 INNER JOIN 档案，必有档案行）
                List<GateMonitor> gateSites = gateMonitorMapper.selectGateSites();
                List<StationSiteVO> gateList = attachArchiveInfo(gateSites.stream().map(g -> {
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
                return attachArchiveInfo(waterFlowMapper.selectFlowStations());
            case "moisture":
                return attachArchiveInfo(soilMoistureMapper.selectMoistureStations());
            default:
                throw new IllegalArgumentException("无效的 type 值: " + metricType
                        + "，可选: " + String.join(" / ", SUPPORTED_TYPES));
        }
    }

    /**
     * 按站点范围过滤清单：reservoir 只留花凉亭水库站点（水库水位站 / 水库雨量站），
     * gq 只留灌区站点（非水库站点）；范围为空时不限制。
     */
    @Override
    public List<StationSiteVO> filterByScope(List<StationSiteVO> sites, String scope) {
        String normalized = normalizeScope(scope);
        if (sites == null || sites.isEmpty() || normalized == null) {
            return sites;
        }
        boolean reservoir = SCOPE_RESERVOIR.equals(normalized);
        List<StationSiteVO> result = new ArrayList<>();
        for (StationSiteVO site : sites) {
            if (isReservoirSite(site) == reservoir) {
                result.add(site);
            }
        }
        return result;
    }

    /** 是否花凉亭水库站点：判定口径与灌区雨量排除规则同源（测站编码 / 站名双重判定） */
    private boolean isReservoirSite(StationSiteVO site) {
        return site != null && stPptnRService.isReservoirSite(site.getCode(), site.getName());
    }

    /**
     * 雨量站清单（全部雨量站，含花凉亭水库站点）：站点编号取雨量表 distinct STCD，
     * 站名 / 站点管理主键 / 经纬度由站点档案一次批量补齐（避免逐站查询）。
     */
    private List<StationSiteVO> rainfallSites() {
        List<String> stcds = stPptnRMapper.selectDistinctRainfallStcds().stream()
                .map(StationSiteServiceImpl::trim)
                .filter(s -> s != null)
                .distinct()
                .collect(Collectors.toList());
        List<StationSiteVO> result = new ArrayList<>();
        for (String stcd : stcds) {
            StationSiteVO s = new StationSiteVO();
            s.setCode(stcd);
            // 档案无该站时站名兜底为编号（档案命中后由 attachArchiveInfo 覆盖为档案站名）
            s.setName(stcd);
            result.add(s);
        }
        return attachArchiveInfo(result, true);
    }

    private List<StationSiteVO> attachArchiveInfo(List<StationSiteVO> sites) {
        return attachArchiveInfo(sites, false);
    }

    /**
     * 补齐站点编号（stcd）、站点管理主键（siteId）与经纬度：站点清单派生自各业务表，
     * 其站点标识可能是站点编号（雨量/水位表 STCD），也可能是站点管理主键
     * （闸门/流量/墒情业务表的 site 列存站点管理主键），统一由站点档案表一次批量解析。
     * <p>解析规则：标识命中档案「站点编号 iofhpi」→ 直接补 stcd / siteId / 经纬度；
     * 命中档案「站点管理主键 id」→ 由该条档案反查站点编号（闸门站、无编号的流量/墒情 MQTT 站）；
     * 档案中无对应记录的站点 stcd / siteId 为空、经纬度为 null。
     * <p>code（业务表原值）不做改写，保证既有调用方行为不变。
     *
     * @param codeIsStcd 该来源的 code 本身即站点编号（雨量 / 水位表 STCD）时为 true：
     *                   档案缺失（或档案未填编号）时编号仍取 code 原值，避免误报空
     */
    private List<StationSiteVO> attachArchiveInfo(List<StationSiteVO> sites, boolean codeIsStcd) {
        if (sites == null || sites.isEmpty()) {
            return sites;
        }
        List<String> keys = sites.stream().map(s -> trim(s.getCode()))
                .filter(s -> s != null).distinct().collect(Collectors.toList());
        if (keys.isEmpty()) {
            return sites;
        }
        Map<String, StationSiteVO> archiveByStcd = new HashMap<>();
        Map<String, StationSiteVO> archiveById = new HashMap<>();
        for (StationSiteVO archive : stStinfoMapper.selectArchiveSites(keys)) {
            String archiveStcd = trim(archive.getStcd());
            String archiveId = trim(archive.getSiteId());
            if (archiveStcd != null) {
                archiveByStcd.putIfAbsent(archiveStcd, archive);
            }
            if (archiveId != null) {
                archiveById.putIfAbsent(archiveId, archive);
            }
        }
        for (StationSiteVO site : sites) {
            String key = trim(site.getCode());
            if (key == null) {
                continue;
            }
            StationSiteVO archive = archiveByStcd.get(key);
            if (archive == null) {
                archive = archiveById.get(key);
            }
            if (archive == null) {
                if (codeIsStcd) {
                    site.setStcd(key);
                }
                continue;
            }
            String name = trim(site.getName());
            if (archive.getName() != null && (name == null || name.equals(key))) {
                site.setName(trim(archive.getName()));
            }
            String archiveStcd = trim(archive.getStcd());
            site.setStcd(archiveStcd != null ? archiveStcd : (codeIsStcd ? key : null));
            site.setSiteId(archive.getSiteId());
            site.setLon(archive.getLon());
            site.setLat(archive.getLat());
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
