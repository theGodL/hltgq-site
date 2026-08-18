package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.entity.GateMonitor;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.mapper.GateMonitorMapper;
import com.qgyun.hltgq.hltgqsite.mapper.StStinfoMapper;
import com.qgyun.hltgq.hltgqsite.mapper.WaterFlowMapper;
import com.qgyun.hltgq.hltgqsite.service.GateMonitorService;
import com.qgyun.hltgq.hltgqsite.vo.FlowMonitoringVO;
import com.qgyun.hltgq.hltgqsite.vo.GateHoleData;
import com.qgyun.hltgq.hltgqsite.vo.GateMonitoringVO;
import com.qgyun.hltgq.hltgqsite.vo.GateStationWaterLevelVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GateMonitorServiceImpl implements GateMonitorService {

    /**
     * 闸站图表固定七站（按展示顺序）：
     * 渠首进水闸、双庙湖节制闸、南山寺节制闸、毕岭节制闸、汪元节制闸、北干渠进水闸、南干渠进水闸
     * 值为站点表主键 iofhpi（测站编码）
     */
    private static final List<String> GATE_STATION_STCDS = Arrays.asList(
            "QSJSZ", "SMH", "NSS", "9000000005", "9000000006", "9000000001", "9000000002");

    @Autowired
    private GateMonitorMapper gateMonitorMapper;

    @Autowired
    private WaterFlowMapper waterFlowMapper;

    @Autowired
    private StStinfoMapper stStinfoMapper;

    @Override
    public List<GateMonitoringVO> monitoring(String site, LocalDateTime startTime, LocalDateTime endTime) {
        // 1. 查询各闸孔最新一条数据
        List<GateMonitor> rows = gateMonitorMapper.selectLatestPerHole(site, startTime, endTime);

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 按站点 (site) 分组
        Map<String, List<GateMonitor>> siteGroup = rows.stream()
                .collect(Collectors.groupingBy(GateMonitor::getSite, LinkedHashMap::new, Collectors.toList()));

        // 3. 每个站点构建一个 GateMonitoringVO
        List<GateMonitoringVO> result = new ArrayList<>();
        for (Map.Entry<String, List<GateMonitor>> entry : siteGroup.entrySet()) {
            String siteId = entry.getKey();
            List<GateMonitor> holes = entry.getValue();

            // 站点名称（取第一条记录的 siteName，各孔相同）
            String siteName = holes.get(0).getSiteName();

            // 监测时间：取各闸孔中最新者
            LocalDateTime latestTm = holes.stream()
                    .map(GateMonitor::getTm)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            // 闸前/闸后水位：取最新一条有值记录
            GateMonitor latestWithZ = holes.stream()
                    .filter(h -> h.getUpZ() != null || h.getDownZ() != null)
                    .max(Comparator.comparing(GateMonitor::getTm, Comparator.nullsLast(LocalDateTime::compareTo)))
                    .orElse(null);

            // 各闸孔开度与状态（按闸孔号排序）
            List<GateHoleData> holeDataList = holes.stream()
                    .sorted(Comparator.comparing(GateMonitor::getGateNo))
                    .map(h -> {
                        GateHoleData d = new GateHoleData();
                        d.setGateNo(h.getGateNo());
                        d.setOpenDegree(h.getOpenDegree());
                        d.setStatus(h.getStatus());
                        return d;
                    })
                    .collect(Collectors.toList());

            GateMonitoringVO vo = new GateMonitoringVO();
            vo.setSiteId(siteId);
            vo.setSiteName(siteName);
            vo.setTm(latestTm);
            vo.setUpZ(latestWithZ != null && latestWithZ.getUpZ() != null ? latestWithZ.getUpZ().setScale(2, java.math.RoundingMode.DOWN) : null);
            vo.setDownZ(latestWithZ != null && latestWithZ.getDownZ() != null ? latestWithZ.getDownZ().setScale(2, java.math.RoundingMode.DOWN) : null);
            // 流量、电压与经纬度为站点级数据，各孔子查询结果相同，取第一条非空值
            vo.setQ(holes.stream().map(GateMonitor::getQ).filter(Objects::nonNull).findFirst().orElse(null));
            vo.setVol(holes.stream().map(GateMonitor::getVol).filter(Objects::nonNull).findFirst().orElse(null));
            vo.setLon(holes.stream().map(GateMonitor::getLon).filter(Objects::nonNull).findFirst().orElse(null));
            vo.setLat(holes.stream().map(GateMonitor::getLat).filter(Objects::nonNull).findFirst().orElse(null));
            vo.setHoles(holeDataList);
            result.add(vo);
        }

        // 按站点名称排序
        result.sort(Comparator.comparing(GateMonitoringVO::getSiteName, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    @Override
    public Page<Map<String, Object>> history(String siteId, String type, LocalDateTime startTime, LocalDateTime endTime,
                                              long page, long size) {
        // 流量：数据来自流量表 t_auto_hltgq_water_wt_nfo（site=闸站UUID），与闸门表分页口径不同，单独处理
        if ("flow".equals(type)) {
            return flowHistory(siteId, startTime, endTime, page, size);
        }

        long total = gateMonitorMapper.selectHistoryTmCount(siteId, startTime, endTime);

        Page<Map<String, Object>> result = new Page<>(page, size);
        result.setTotal(total);

        if (total == 0) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        int offset = (int) ((page - 1) * size);
        int limit = (int) size;
        List<LocalDateTime> tms = gateMonitorMapper.selectHistoryTmPage(siteId, startTime, endTime, limit, offset);

        if (tms.isEmpty()) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        List<GateMonitor> rows = gateMonitorMapper.selectHistoryDetail(siteId, tms);

        if ("opening".equals(type)) {
            result.setRecords(pivotOpening(rows));
        } else if ("waterLevel".equals(type)) {
            result.setRecords(extractWaterLevel(rows));
        } else {
            result.setRecords(Collections.emptyList());
        }

        return result;
    }

    /**
     * 流量历史：数据来自流量表 t_auto_hltgq_water_wt_nfo（site=闸站UUID，stcd 或 site 匹配）
     * 每行返回 tm（监测日期）+ q（瞬时流量 m³/s）+ tf（累计流量 m³）
     */
    private Page<Map<String, Object>> flowHistory(String siteId, LocalDateTime startTime, LocalDateTime endTime,
                                                  long page, long size) {
        Page<Map<String, Object>> result = new Page<>(page, size);
        long total = waterFlowMapper.selectHistoryCount(siteId, startTime, endTime);
        result.setTotal(total);
        if (total == 0) {
            result.setRecords(Collections.emptyList());
            return result;
        }
        int offset = (int) ((page - 1) * size);
        List<FlowMonitoringVO> rows = waterFlowMapper.selectHistoryPage(
                siteId, startTime, endTime, (int) size, offset);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> records = new ArrayList<>();
        for (FlowMonitoringVO r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tm", r.getTm() != null ? r.getTm().format(fmt) : null);
            m.put("q", r.getQ());
            m.put("tf", r.getTf());
            records.add(m);
        }
        result.setRecords(records);
        return result;
    }

    /**
     * 开度透视：按监测时间分组，每个闸孔号作为独立列（open1, open2, open3...），q 为站点流量
     */
    private List<Map<String, Object>> pivotOpening(List<GateMonitor> rows) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Map<LocalDateTime, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (GateMonitor r : rows) {
            Map<String, Object> record = grouped.computeIfAbsent(r.getTm(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tm", k.format(fmt));
                return m;
            });
            record.put("open" + r.getGateNo(), r.getOpenDegree());
            // 流量为站点级数据，同一时刻各孔相同，仅放入一次
            if (!record.containsKey("q") && r.getQ() != null) {
                record.put("q", r.getQ());
            }
        }
        return new ArrayList<>(grouped.values());
    }

    /**
     * 水位提取：按监测时间分组，取闸前/闸后水位及流量 q（同一站点各闸孔水位相同，取首个非空值）
     */
    private List<Map<String, Object>> extractWaterLevel(List<GateMonitor> rows) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Map<LocalDateTime, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (GateMonitor r : rows) {
            Map<String, Object> record = grouped.computeIfAbsent(r.getTm(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tm", k.format(fmt));
                return m;
            });
            if (!record.containsKey("upZ") && r.getUpZ() != null) {
                record.put("upZ", r.getUpZ());
            }
            if (!record.containsKey("downZ") && r.getDownZ() != null) {
                record.put("downZ", r.getDownZ());
            }
            if (!record.containsKey("q") && r.getQ() != null) {
                record.put("q", r.getQ());
            }
        }
        return new ArrayList<>(grouped.values());
    }

    @Override
    public List<GateStationWaterLevelVO> stationWaterLevel(LocalDateTime time) {
        // 1. 固定七站站点信息（站点表主键 iofhpi = 测站编码）
        List<StStinfo> stations = stStinfoMapper.selectBatchIds(GATE_STATION_STCDS);
        Map<String, StStinfo> infoMap = stations.stream()
                .collect(Collectors.toMap(StStinfo::getStcd, s -> s, (a, b) -> a));

        // 2. 闸门表查询各站在 [time-30min, time+30min] 内距 time 最近的入库水位（每站一条）
        List<String> siteIds = GATE_STATION_STCDS.stream()
                .map(infoMap::get)
                .filter(Objects::nonNull)
                .map(StStinfo::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<Map<String, Object>> rows = siteIds.isEmpty() ? Collections.emptyList()
                : gateMonitorMapper.selectClosestWaterLevelBySites(siteIds, time,
                        time.minusMinutes(30), time.plusMinutes(30));
        Map<String, Map<String, Object>> rowBySite = rows.stream()
                .filter(r -> r.get("site") != null)
                .collect(Collectors.toMap(r -> String.valueOf(r.get("site")), r -> r, (a, b) -> a));

        // 3. 按固定顺序组装；半小时内无入库数据的水位为 null（该时间点无报文）
        List<GateStationWaterLevelVO> result = new ArrayList<>();
        for (String stcd : GATE_STATION_STCDS) {
            GateStationWaterLevelVO vo = new GateStationWaterLevelVO();
            vo.setStcd(stcd);
            StStinfo info = infoMap.get(stcd);
            if (info != null) {
                vo.setId(info.getId());
                vo.setName(info.getStnm());
            }
            Map<String, Object> row = info != null && info.getId() != null ? rowBySite.get(info.getId()) : null;
            if (row != null) {
                vo.setUpZ(toBigDecimal(row.get("up_z")));
                vo.setDownZ(toBigDecimal(row.get("down_z")));
            }
            result.add(vo);
        }
        return result;
    }

    /** Map 值 → BigDecimal（null 安全；-9991/-999 透传由前端分别展示 '--'/不展示） */
    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        try {
            return new BigDecimal(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
