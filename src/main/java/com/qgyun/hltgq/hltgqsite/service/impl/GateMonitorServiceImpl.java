package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.entity.GateMonitor;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.mapper.GateMonitorMapper;
import com.qgyun.hltgq.hltgqsite.mapper.StStinfoMapper;
import com.qgyun.hltgq.hltgqsite.mapper.WaterFlowMapper;
import com.qgyun.hltgq.hltgqsite.service.GateMonitorService;
import com.qgyun.hltgq.hltgqsite.vo.FlowMonitoringVO;
import com.qgyun.hltgq.hltgqsite.vo.GateCumulativeFlowVO;
import com.qgyun.hltgq.hltgqsite.vo.GateHoleData;
import com.qgyun.hltgq.hltgqsite.vo.GateMonitoringVO;
import com.qgyun.hltgq.hltgqsite.vo.GateMonthCumulativeFlowVO;
import com.qgyun.hltgq.hltgqsite.vo.GateStationWaterLevelVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
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

    /** -999 = 设备不存在：视为缺失不返回（-9991 设备异常保留，透传由前端展示 '--'） */
    private static final BigDecimal DEVICE_MISSING = new BigDecimal("-999");
    /** -9991 = 设备异常：透传由前端展示 '--'；与 -999 一样视为无有效值 */
    private static final BigDecimal DEVICE_ERROR = new BigDecimal("-9991");

    /** 流量同批次对齐窗口（分钟）：报文按批次入库，流量 tm 应在开度/水位最新时刻 ±20 分钟内 */
    private static final long FLOW_ALIGN_MINUTES = 20;

    /** MQTT 站点固定清单（站点名匹配，其余为 RabbitMQ）：与前端 isStaleTm 标红规则一致 */
    private static final Set<String> MQTT_STATION_NAMES = new HashSet<>(Arrays.asList(
            "南山寺节制闸", "渠首进水闸", "渠首电站防洪闸", "双庙湖节制闸"
    ));

    /** MQTT 站断联阈值：报文 10 分钟一次，30 分钟无更新判离线 */
    private static final long MQTT_STALE_MINUTES = 30;
    /** RabbitMQ 站断联阈值：报文 1 小时一次，70 分钟无更新判离线 */
    private static final long RBT_STALE_MINUTES = 70;

    /**
     * 断联判定（与前端 isStaleTm 规则一致）：MQTT 站 30 分钟、RabbitMQ 站 70 分钟无更新判离线；
     * 无时间值（null）视为在线；时间在未来（时钟偏差）也视为在线
     */
    private boolean isStale(LocalDateTime tm, String siteName) {
        if (tm == null) return false;
        long threshold = MQTT_STATION_NAMES.contains(siteName) ? MQTT_STALE_MINUTES : RBT_STALE_MINUTES;
        // 毫秒级严格大于，与前端 Date.now() - t > staleMs 语义一致（toMinutes 向下取整会导致边界差一分钟误判）
        return Duration.between(tm, LocalDateTime.now()).toMillis() > threshold * 60_000L;
    }

    private static boolean isMissing(BigDecimal v) {
        return v != null && v.compareTo(DEVICE_MISSING) == 0;
    }

    /** 是否有效值：null、-999（设备不存在）、-9991（设备异常）、0 均视为无效（0 多为设备异常兜底上报） */
    private static boolean isEffective(BigDecimal v) {
        return v != null && v.signum() != 0 && !isMissing(v) && v.compareTo(DEVICE_ERROR) != 0;
    }

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
            // -999 = 闸孔不存在：过滤后不参与返回（-9991 设备异常保留，透传由前端展示 '--'）
            List<GateMonitor> holes = entry.getValue().stream()
                    .filter(h -> h.getOpenDegree() == null || !isMissing(h.getOpenDegree()))
                    .collect(Collectors.toList());
            if (holes.isEmpty()) {
                continue;
            }

            // 站点名称（取第一条记录的 siteName，各孔相同）
            String siteName = holes.get(0).getSiteName();

            // 监测时间：取各闸孔中最新者
            LocalDateTime latestTm = holes.stream()
                    .map(GateMonitor::getTm)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            // 最新时刻开度/水位有效性：latestTm 时刻的开度、闸前/闸后水位全部无有效值时
            // 视为该时刻设备监测数据不可用（null/-999/-9991/0），流量一并置空展示，
            // 避免客户看到"最新时刻开度水位为 0 或 -- 而流量有值"误判设备数据采集不准
            boolean latestDataValid = holes.stream()
                    .filter(h -> h.getTm() != null && h.getTm().equals(latestTm))
                    .anyMatch(h -> isEffective(h.getOpenDegree())
                            || isEffective(h.getUpZ()) || isEffective(h.getDownZ()));

            // 流量同批次取值：报文按批次入库，流量 tm 应在开度/水位最新时刻 ±20 分钟窗口内；
            // 窗口内取最新一条（受 startTime/endTime 范围约束），窗口外无记录视为该批次无流量数据
            Map<String, Object> flowRow = latestTm == null ? null
                    : waterFlowMapper.selectLatestInWindow(siteId,
                            latestTm.minusMinutes(FLOW_ALIGN_MINUTES),
                            latestTm.plusMinutes(FLOW_ALIGN_MINUTES),
                            startTime, endTime);
            BigDecimal flowQ = flowRow == null ? null : toBigDecimal(flowRow.get("q"));
            BigDecimal flowYtf = flowRow == null ? null : toBigDecimal(flowRow.get("ytf"));
            BigDecimal flowTtf = flowRow == null ? null : toBigDecimal(flowRow.get("ttf"));

            // 闸前/闸后水位：分别取最新一条有效值（-999 设备不存在视为无值跳过；-9991 设备异常保留）
            Comparator<GateMonitor> byTmDesc = Comparator.comparing(GateMonitor::getTm,
                    Comparator.nullsLast(LocalDateTime::compareTo));
            GateMonitor latestUpZ = holes.stream()
                    .filter(h -> h.getUpZ() != null && !isMissing(h.getUpZ()))
                    .max(byTmDesc)
                    .orElse(null);
            GateMonitor latestDownZ = holes.stream()
                    .filter(h -> h.getDownZ() != null && !isMissing(h.getDownZ()))
                    .max(byTmDesc)
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
            vo.setUpZ(latestUpZ != null ? latestUpZ.getUpZ().setScale(2, java.math.RoundingMode.DOWN) : null);
            vo.setDownZ(latestDownZ != null ? latestDownZ.getDownZ().setScale(2, java.math.RoundingMode.DOWN) : null);
            // 流量（瞬时/累计）取同批次窗口内的流量表记录（-999 设备不存在视为无值跳过；-9991 设备异常保留）；
            // 电压与经纬度为站点级数据，各孔子查询结果相同，取第一条有效值
            vo.setQ(flowQ == null || isMissing(flowQ) ? null : flowQ);
            vo.setVol(holes.stream().map(GateMonitor::getVol)
                    .filter(v -> v != null && !isMissing(v)).findFirst().orElse(null));
            // 累计流量（站点级）：默认（无起始时间）= 同批次 ytf（当年 1月1日 0点起至最新数据时间）；
            // 指定起始时间 = 时间框范围累计 = ttf(范围内末行) − ttf(起点前最近一行)，起点前无积分行基准按 0
            BigDecimal ytf = flowYtf == null || isMissing(flowYtf) ? null : flowYtf;
            BigDecimal ttf = flowTtf == null || isMissing(flowTtf) ? null : flowTtf;
            BigDecimal prevTtf = holes.stream().map(GateMonitor::getPrevTtf)
                    .filter(v -> v != null && !isMissing(v)).findFirst().orElse(null);
            BigDecimal cumulativeFlow;
            if (startTime == null) {
                cumulativeFlow = ytf;
            } else if (ttf == null) {
                cumulativeFlow = null;
            } else {
                cumulativeFlow = ttf.subtract(prevTtf != null ? prevTtf : BigDecimal.ZERO);
            }
            vo.setCumulativeFlow(cumulativeFlow != null
                    ? cumulativeFlow.setScale(2, java.math.RoundingMode.DOWN) : null);
            // 最新时刻开度/水位无有效值 → 瞬时/累计流量不展示，与开度/水位显示保持一致
            if (!latestDataValid) {
                vo.setQ(null);
                vo.setCumulativeFlow(null);
            }
            vo.setLon(holes.stream().map(GateMonitor::getLon).filter(Objects::nonNull).findFirst().orElse(null));
            vo.setLat(holes.stream().map(GateMonitor::getLat).filter(Objects::nonNull).findFirst().orElse(null));
            // 在线状态：最新采集时间断联判定（MQTT 站 30 分钟、RabbitMQ 站 70 分钟无更新判离线）
            vo.setIsOnline(!isStale(latestTm, siteName));
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
            // -999 = 设备不存在：转 null 返回（-9991 设备异常保留透传由前端展示 '--'）
            m.put("q", isMissing(r.getQ()) ? null : r.getQ());
            m.put("tf", isMissing(r.getTf()) ? null : r.getTf());
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
            // -999 = 闸孔不存在：该时刻该孔不返回列（-9991 设备异常保留透传由前端展示 '--'）
            BigDecimal od = r.getOpenDegree();
            if (od == null || !isMissing(od)) {
                record.put("open" + r.getGateNo(), od);
            }
            // 流量为站点级数据，同一时刻各孔相同，仅放入一次（-999 设备不存在跳过）
            if (!record.containsKey("q") && r.getQ() != null && !isMissing(r.getQ())) {
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
            // -999 = 设备不存在视为无值跳过（-9991 设备异常保留透传由前端展示 '--'），取首个有效值
            if (!record.containsKey("upZ") && r.getUpZ() != null && !isMissing(r.getUpZ())) {
                record.put("upZ", r.getUpZ());
            }
            if (!record.containsKey("downZ") && r.getDownZ() != null && !isMissing(r.getDownZ())) {
                record.put("downZ", r.getDownZ());
            }
            if (!record.containsKey("q") && r.getQ() != null && !isMissing(r.getQ())) {
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

    /** Map 值 → BigDecimal（null 安全；-9991 设备异常/-999 设备不存在由前端转 null 不绘制） */
    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        try {
            return new BigDecimal(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public GateCumulativeFlowVO cumulativeFlow(String siteId, LocalDateTime monthStart) {
        // 月累计起点默认当月 1日 0点（与年累计口径对称：当年 1月1日 0点起）
        if (monthStart == null) {
            monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        }
        Map<String, Object> row = waterFlowMapper.selectCumulativeFlow(siteId, monthStart);
        GateCumulativeFlowVO vo = new GateCumulativeFlowVO();
        vo.setSiteId(siteId);
        if (row != null && row.get("site_name") != null) {
            vo.setSiteName(String.valueOf(row.get("site_name")));
        }
        // 累计流量取值（-999 设备不存在 → null；-9991 异常保留透传由前端展示）
        BigDecimal yearFlow = flowValue(row, "year_flow");
        BigDecimal totalFlow = flowValue(row, "total_flow");
        BigDecimal monthPrev = flowValue(row, "month_prev_ttf");
        // 年累计 = ytf（当年 1月1日 0点起至最新数据时间，改造前无数据为 null）
        vo.setYearCumulativeFlow(scale2(yearFlow));
        // 月累计 = ttf(最新) − ttf(monthStart 前最近行)，起点前无积分行基准按 0
        BigDecimal monthFlow = totalFlow == null ? null
                : totalFlow.subtract(monthPrev != null ? monthPrev : BigDecimal.ZERO);
        vo.setMonthCumulativeFlow(scale2(monthFlow));
        return vo;
    }

    /** 累计流量 Map 值 → BigDecimal（null 安全；-999 设备不存在 → null） */
    private BigDecimal flowValue(Map<String, Object> row, String key) {
        BigDecimal v = toBigDecimal(row != null ? row.get(key) : null);
        return isMissing(v) ? null : v;
    }

    @Override
    public List<GateMonthCumulativeFlowVO> monthlyCumulativeFlow(String siteId, int months) {
        // 月份数防御：非法值抛参数异常，超上限截断
        if (months <= 0) {
            throw new IllegalArgumentException("月份数必须为正整数");
        }
        if (months > 24) {
            months = 24;
        }
        // 近 months 个月：当前月为最后一个月（当月累计截至最新数据时间），最早月起点 = 当前月 − (months−1)
        LocalDate now = LocalDate.now();
        LocalDateTime curMonthStart = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime firstMonthStart = curMonthStart.minusMonths(months - 1L);

        List<Map<String, Object>> rows = waterFlowMapper.selectMonthlyCumulativeFlow(
                siteId, firstMonthStart, curMonthStart);

        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        List<GateMonthCumulativeFlowVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            GateMonthCumulativeFlowVO vo = new GateMonthCumulativeFlowVO();
            Object ms = row.get("month_start");
            LocalDateTime msLdt = ms instanceof Timestamp
                    ? ((Timestamp) ms).toLocalDateTime()
                    : ms instanceof LocalDateTime ? (LocalDateTime) ms : null;
            vo.setMonth(msLdt != null ? msLdt.format(monthFmt) : null);
            // 月累计 = ttf(月内最新) − ttf(月初前最近)，与 cumulativeFlow 接口月累计口径一致；
            // 月初前无积分行（最早月无历史数据）基准按 0；月内无 ttf 数据 → null
            BigDecimal endTtf = flowValue(row, "end_ttf");
            BigDecimal startTtf = flowValue(row, "start_ttf");
            BigDecimal monthFlow = endTtf == null ? null
                    : endTtf.subtract(startTtf != null ? startTtf : BigDecimal.ZERO);
            vo.setCumulativeFlow(scale2(monthFlow));
            result.add(vo);
        }
        return result;
    }

    /** 2 位小数截断（null 安全，累计流量统一 2 位精度） */
    private BigDecimal scale2(BigDecimal v) {
        return v != null ? v.setScale(2, java.math.RoundingMode.DOWN) : null;
    }
}
