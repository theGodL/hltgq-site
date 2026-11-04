package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.entity.GateMonitor;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.mapper.GateMonitorMapper;
import com.qgyun.hltgq.hltgqsite.mapper.StStinfoMapper;
import com.qgyun.hltgq.hltgqsite.mapper.WaterFlowMapper;
import com.qgyun.hltgq.hltgqsite.model.util.WaterVolumeUtils;
import com.qgyun.hltgq.hltgqsite.service.CanalService;
import com.qgyun.hltgq.hltgqsite.service.FlowMonitorService;
import com.qgyun.hltgq.hltgqsite.service.StRiverRService;
import com.qgyun.hltgq.hltgqsite.service.StationSortService;
import com.qgyun.hltgq.hltgqsite.vo.FlowMonitoringVO;
import com.qgyun.hltgq.hltgqsite.vo.FlowStationVO;
import com.qgyun.hltgq.hltgqsite.vo.FlowTrendVO;
import com.qgyun.hltgq.hltgqsite.vo.PeriodRegimeVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import com.qgyun.hltgq.hltgqsite.model.util.StationStatusUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ValueConvertUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DictUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DeviceValueUtils;

/**
 * 流量监测服务实现
 */
@Service
public class FlowMonitorServiceImpl implements FlowMonitorService {

    private static final Logger log = LoggerFactory.getLogger(FlowMonitorServiceImpl.class);

    @Autowired
    private WaterFlowMapper waterFlowMapper;

    @Autowired
    private StStinfoMapper stStinfoMapper;

    @Autowired
    private GateMonitorMapper gateMonitorMapper;

    @Autowired
    private StationSortService stationSortService;

    @Autowired
    private StRiverRService stRiverRService;

    @Autowired
    private CanalService canalService;

    /**
     * 流量图表固定八站（按展示顺序）：渠首进水闸、双庙湖节制闸、南山寺节制闸、太怀干渠进水闸、
     * 毕岭节制闸、汪元渡槽、南干渠进水闸、北干渠进水闸；值为站点表主键 iofhpi（测站编码）
     */
    private static final List<String> FLOW_STATION_STCDS = Arrays.asList(
            "QSJSZ", "SMH", "NSS", "9000000004", "9000000005", "9000000027", "9000000002", "9000000001");

    /**
     * 流量图表取数窗口（分钟）：选中时间点前后各取该窗口内距时间点最近的一条流量。
     * 与页面时间点粒度（20 分钟）一致：窗口 ≤ 半粒度，避免相邻时间点重复命中同一条数据。
     */
    private static final long FLOW_WINDOW_MINUTES = 20;

    /**
     * 旧 STCD → 新 STCD（过渡期兼容：客户端可能仍持有旧页面/旧缓存，收到旧编号时自动映射到新编号）
     */
    private static final Map<String, String> LEGACY_TO_NEW_STCD = new HashMap<>();
    static {
        LEGACY_TO_NEW_STCD.put("00000001", "3206400001");
        LEGACY_TO_NEW_STCD.put("00000004", "320640000A");
        LEGACY_TO_NEW_STCD.put("00000007", "3206400007");
    }

    /**
     * 水位水情站默认展示顺序（业主口径）：周家河 > 花凉亭坝上 > 花凉亭坝下。
     * 日时段水情表默认按此顺序分组输出（不受前端传入 stcds 顺序影响）；
     * 已配置「水位」站点排序时由配置顺序覆盖（见 periodRegime）。
     */
    private static final List<String> WATER_STATION_ORDER = Arrays.asList("周家河", "花凉亭坝上", "花凉亭坝下");

    private static int stationRank(String stnm) {
        int idx = WATER_STATION_ORDER.indexOf(stnm);
        return idx >= 0 ? idx : WATER_STATION_ORDER.size();
    }

    /** 站点在已配置顺序中的位次；未配置到的站点返回配置长度（排在其后） */
    private static int orderedIndex(List<String> configuredStcds, String stcd) {
        if (stcd != null) {
            int idx = configuredStcds.indexOf(stcd);
            if (idx < 0) {
                idx = configuredStcds.indexOf(stcd.trim());
            }
            if (idx >= 0) {
                return idx;
            }
        }
        return configuredStcds.size();
    }

    /**
     * 累计流量展示换算：m³ → 万m³（3 位小数截断）。
     * <p>-999（设备不存在）→ null；-9991（设备异常）保留原值透传（前端展示 '--'），不参与换算。
     */
    private static BigDecimal toWanFlow(BigDecimal v) {
        BigDecimal m3 = DeviceValueUtils.nullIfMissing(v);
        if (m3 == null || DeviceValueUtils.isError(m3)) {
            return m3;
        }
        return WaterVolumeUtils.m3ToWan(m3);
    }

    /**
     * 新 STCD → 站点名称（站点表接入过渡期，STCD 查不到时按名称反查）
     */
    private static final Map<String, String> STCD_TO_STNM = new HashMap<>();
    static {
        STCD_TO_STNM.put("3206400001", "周家河");
        STCD_TO_STNM.put("320640000A", "花凉亭坝下");
        STCD_TO_STNM.put("3206400007", "花凉亭坝上");
    }

    /**
     * 解析站点：先按 STCD 精确查询；查不到时按名称反查（过渡期主键可能未对齐）。
     * 返回站点表记录，查不到返回 null。
     */
    private StStinfo resolveStation(String stcd) {
        String resolved = LEGACY_TO_NEW_STCD.getOrDefault(stcd, stcd);
        StStinfo byId = stStinfoMapper.selectById(resolved);
        if (byId != null && byId.getStnm() != null) {
            return byId;
        }
        String stnm = STCD_TO_STNM.get(resolved);
        if (stnm == null) return null;
        QueryWrapper<StStinfo> wrapper = new QueryWrapper<>();
        wrapper.eq("zzkaec", stnm);
        wrapper.last("LIMIT 1");
        return stStinfoMapper.selectOne(wrapper);
    }

    /**
     * 通过 stcd 查找站点名称（站点表查不到时返回 stcd 自身）
     */
    private String resolveStnm(String stcd) {
        StStinfo stinfo = resolveStation(stcd);
        return stinfo != null ? stinfo.getStnm() : stcd;
    }

    @Override
    public List<FlowMonitoringVO> monitoring(List<String> stcds, String canalId,
                                             LocalDateTime startTime, LocalDateTime endTime) {
        // 渠系树过滤：canalId 非空时收集该渠系及所有子孙渠系 id（含自身）
        List<String> canalIds = canalService.collectDescendantCanalIds(canalId);
        List<FlowMonitoringVO> rows = waterFlowMapper.selectLatestPerStation(stcds, canalIds, startTime, endTime);
        // -999 = 设备不存在：转 null 返回（-9991 设备异常保留，透传由前端展示 '--'）
        rows.forEach(r -> {
            r.setQ(DeviceValueUtils.nullIfMissing(r.getQ()));
            r.setTf(toWanFlow(r.getTf()));
            // 累计流量（站点级，与闸门监测同口径）：默认（无起始时间）= 末行 ytf（当年 1月1日 0点起至最新数据时间）；
            // 指定起始时间 = 时间框范围累计 = ttf(范围内末行) − ttf(起点前最近一行)，起点前无积分行基准按 0；
            // 先按 m³ 原值相减，最后一步再换算为万m³（缩放后相减会放大误差）
            BigDecimal ytf = DeviceValueUtils.nullIfMissing(r.getYtf());
            BigDecimal ttf = DeviceValueUtils.nullIfMissing(r.getTtf());
            BigDecimal prevTtf = DeviceValueUtils.nullIfMissing(r.getPrevTtf());
            BigDecimal cumulativeFlow;
            if (startTime == null) {
                cumulativeFlow = ytf;
            } else if (ttf == null) {
                cumulativeFlow = null;
            } else {
                cumulativeFlow = ttf.subtract(prevTtf != null ? prevTtf : BigDecimal.ZERO);
            }
            r.setCumulativeFlow(toWanFlow(cumulativeFlow));
            // 在线状态：档案手动离线（zebpsu=#2#）优先判离线；否则按最新采集时间断联判定
            boolean online = StationStatusUtils.isZebpsuOnline(r.getZebpsu())
                    && !StationStatusUtils.isStale(r.getTm(), r.getStnm());
            r.setIsOnline(online);
            // 断联站点瞬时流量不展示（历史值不能冒充当前值）；累计保留，其统计截止时间由 flowTm 承载
            // （采集时间对齐闸门监测页时，见下方）。
            // 三维等外部接口复用本方法，口径统一收在后端，避免各消费方自行判断
            if (!online) {
                r.setQ(null);
            }
        });
        // 闸站采集时间对齐闸门监测页：仅「各站最新一条」口径（未传时间范围）适用；
        // 带时间范围时 tm 是区间内末条，两页所选区间未必相同，不做替换
        if (startTime == null && endTime == null) {
            applyGateSiteCollectTime(rows);
        }
        // 站点排序配置（流量监测类型）：已配置站点按配置顺序，未配置站点保持 SQL 默认顺序排在其后；
        // 站点标识 = 站点管理主键（档案缺失时为 null，该行保持默认位置）
        return stationSortService.applyOrder("flow", rows, FlowMonitoringVO::getSiteId);
    }

    /**
     * 闸站采集时间对齐：在闸门监测页同时出现的站点（档案监测类型含闸门 #4# 且闸孔最新记录未被
     * -999 全部剔除），采集时间直接取闸门监测页同一口径的最新时刻，保证两个页面显示一致。
     * <p>背景：平台侧流量计算停摆时（如双庙湖节制闸、南山寺节制闸），流量表不再新增而设备仍在正常上报，
     * 采集时间会长期停在最后一次成功计算的时刻，与闸门监测页明显不一致，容易被误读为站点离线。
     * <p>纯流量站（闸门监测页不显示）保持流量表时间不变。
     * <p>代价：闸门表停更时采集时间会跟随闸门页回退到更早的时刻（例如闸门 11:56:46、流量 11:57:00，
     * 显示 11:56:46），不做「只向前对齐」——两页一致优先，且该列已作为设备最近上报时间展示。
     * <p>流量表已停更时，原流量时间转入 flowTm，供前端标注累计水量的统计截止时刻；
     * 流量正常时两表时间通常相同或仅差几十秒，累计仍是当前值，不标注。
     */
    private void applyGateSiteCollectTime(List<FlowMonitoringVO> rows) {
        Map<String, LocalDateTime> gateTm = gateCollectTm();
        if (gateTm.isEmpty()) {
            return;
        }
        rows.forEach(r -> {
            String siteId = r.getSiteId();
            if (siteId == null || siteId.trim().isEmpty()) {
                return;
            }
            LocalDateTime tm = gateTm.get(siteId);
            if (tm == null || tm.equals(r.getTm())) {
                return;
            }
            // 断联判定基于替换前的流量表时间（r.tm 此时仍是流量时间；isOnline 已在更早处按同一时间算过）
            if (StationStatusUtils.isStale(r.getTm(), r.getStnm())) {
                r.setFlowTm(r.getTm());
            }
            r.setTm(tm);
        });
    }

    /**
     * 闸门采集时间缓存快照：取数结果与本次取数时刻整体替换（volatile 安全发布，读侧只会看到
     * 「旧结果+旧时间」或「新结果+新时间」），避免 Map 与时间戳分两个 volatile 更新被并发读到不一致的组合。
     */
    private static final class GateCollectTmSnapshot {
        static final GateCollectTmSnapshot EMPTY =
                new GateCollectTmSnapshot(Collections.emptyMap(), 0L);

        final Map<String, LocalDateTime> tmBySite;
        final long fetchedAt;

        GateCollectTmSnapshot(Map<String, LocalDateTime> tmBySite, long fetchedAt) {
            this.tmBySite = tmBySite;
            this.fetchedAt = fetchedAt;
        }
    }

    /** 闸门数据按 10 分钟批次入库，60 秒缓存不影响展示时效；避免每次列表请求都全量聚合闸门表 33 万行（单次约 0.5s） */
    private static final long GATE_COLLECT_TM_CACHE_MS = 60_000L;

    private volatile GateCollectTmSnapshot gateCollectTmSnapshot = GateCollectTmSnapshot.EMPTY;

    /** 刷新锁：缓存过期时只放一个请求回源，其余请求等锁后直接复用新快照（避免并发峰值同时全表聚合） */
    private final Object gateCollectTmLock = new Object();

    /**
     * 闸门监测页各站最新采集时间（60 秒缓存，过期后单线程回源）。
     * <p>取数口径与闸门监测列表完全同构：各闸孔最新一条 → 整孔剔除开度 -999（设备不存在）→ 取各站最大 tm；
     * 全部闸孔都被剔除的站点与闸门页一样不返回（该站在流量页保持流量表时间）。
     * <p>回源异常时发布空快照并同样缓存 60 秒：故障期间采集时间保持流量表时间、列表照常返回，
     * 且不会每个请求都重试一次失败的全表聚合（WARN 每 60 秒最多一条）。
     */
    private Map<String, LocalDateTime> gateCollectTm() {
        long now = System.currentTimeMillis();
        GateCollectTmSnapshot snapshot = gateCollectTmSnapshot;
        if (now - snapshot.fetchedAt < GATE_COLLECT_TM_CACHE_MS) {
            return snapshot.tmBySite;
        }
        synchronized (gateCollectTmLock) {
            now = System.currentTimeMillis();
            snapshot = gateCollectTmSnapshot;
            if (now - snapshot.fetchedAt < GATE_COLLECT_TM_CACHE_MS) {
                return snapshot.tmBySite;
            }
            Map<String, LocalDateTime> latest = new HashMap<>();
            try {
                for (GateMonitor hole : gateMonitorMapper.selectLatestHoleTm()) {
                    // -999 = 闸孔不存在：整孔剔除（与闸门监测列表同一判定）
                    if (hole.getTm() == null || DeviceValueUtils.isMissing(hole.getOpenDegree())) {
                        continue;
                    }
                    String site = hole.getSite();
                    if (site == null) {
                        continue;
                    }
                    LocalDateTime prev = latest.get(site);
                    if (prev == null || hole.getTm().isAfter(prev)) {
                        latest.put(site, hole.getTm());
                    }
                }
            } catch (Exception ex) {
                // 闸门表不可用（未建表/无权限/类型差异）时降级：采集时间保持流量表时间，列表照常返回
                log.warn("闸门采集时间取数失败，采集时间保持流量表时间：{}", ex.toString());
                gateCollectTmSnapshot = new GateCollectTmSnapshot(Collections.emptyMap(), now);
                return Collections.emptyMap();
            }
            // 空结果同样发布：闸门表确实无数据时不必每个请求都重查一遍
            Map<String, LocalDateTime> result = Collections.unmodifiableMap(latest);
            gateCollectTmSnapshot = new GateCollectTmSnapshot(result, now);
            return result;
        }
    }

    @Override
    public FlowTrendVO trend(String stcd, LocalDateTime startTime, LocalDateTime endTime, String granularity) {
        // 分钟级：按实际采集时刻逐条出点（图表 x 轴为真实时间）
        if ("minute".equalsIgnoreCase(granularity)) {
            return trendByMinute(stcd, startTime, endTime);
        }
        // 1. 默认时间范围：近 7 天
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        if (endTime == null) {
            endTime = now;
        }
        if (startTime == null) {
            startTime = endTime.minusDays(7);
        }

        // 2. 获取站点名称
        String stnm = stcd;
        List<FlowMonitoringVO> stationInfo = waterFlowMapper.selectLatestPerStation(
                Collections.singletonList(stcd), null, null, null);
        if (!stationInfo.isEmpty()) {
            stnm = stationInfo.get(0).getStnm();
        }

        // 3. 查询原始记录（时间升序）
        List<Map<String, Object>> rawRecords = waterFlowMapper.selectRawByStcd(stcd, startTime, endTime);

        // 4. 按小时聚合：同一小时内取平均流量
        Map<String, List<BigDecimal>> hourFlows = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRecords) {
            Object tmObj = row.get("tm");
            Object qObj = row.get("q");
            if (tmObj == null || qObj == null) continue;

            LocalDateTime tm;
            if (tmObj instanceof Timestamp) {
                tm = ((Timestamp) tmObj).toLocalDateTime();
            } else if (tmObj instanceof LocalDateTime) {
                tm = (LocalDateTime) tmObj;
            } else {
                continue;
            }

            BigDecimal q;
            if (qObj instanceof BigDecimal) {
                q = (BigDecimal) qObj;
            } else {
                q = new BigDecimal(qObj.toString());
            }

            // -9991 设备异常、-999 设备不存在，均视为缺失不参与聚合
            if (DeviceValueUtils.isError(q)
                    || DeviceValueUtils.isMissing(q)) continue;

            String hourKey = tm.truncatedTo(ChronoUnit.HOURS)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"));
            hourFlows.computeIfAbsent(hourKey, k -> new ArrayList<>()).add(q);
        }

        // 5. 计算每小时平均流量
        Map<String, BigDecimal> hourAvgFlow = new LinkedHashMap<>();
        for (Map.Entry<String, List<BigDecimal>> entry : hourFlows.entrySet()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (BigDecimal v : entry.getValue()) {
                sum = sum.add(v);
            }
            BigDecimal avg = sum.divide(new BigDecimal(entry.getValue().size()), 3, RoundingMode.HALF_UP);
            hourAvgFlow.put(entry.getKey(), avg);
        }

        // 6. 生成完整小时序列
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        List<FlowTrendVO.HourPoint> hours = new ArrayList<>();
        LocalDateTime hour = startTime.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime endHour = endTime.truncatedTo(ChronoUnit.HOURS);

        while (!hour.isAfter(endHour)) {
            String key = hour.format(fmt);
            FlowTrendVO.HourPoint point = new FlowTrendVO.HourPoint();
            point.setHour(key);
            point.setFlow(hourAvgFlow.get(key));
            hours.add(point);
            hour = hour.plusHours(1);
        }

        // 7. 组装结果
        FlowTrendVO vo = new FlowTrendVO();
        vo.setStcd(stcd);
        vo.setStnm(stnm);
        vo.setStartTime(startTime);
        vo.setEndTime(endTime);
        vo.setHours(hours);
        return vo;
    }

    /**
     * 分钟级趋势：按实际采集时刻逐条输出原始记录，不做小时平均，也不补齐整点。
     * <p>设备异常（-9991）/设备不存在（-999）的流量置空，曲线在该时刻断线。
     */
    private FlowTrendVO trendByMinute(String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        if (startTime == null) {
            startTime = endTime.minusDays(7);
        }

        String stnm = stcd;
        List<FlowMonitoringVO> stationInfo = waterFlowMapper.selectLatestPerStation(
                Collections.singletonList(stcd), null, null, null);
        if (!stationInfo.isEmpty()) {
            stnm = stationInfo.get(0).getStnm();
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<FlowTrendVO.HourPoint> points = new ArrayList<>();
        for (Map<String, Object> row : waterFlowMapper.selectRawByStcd(stcd, startTime, endTime)) {
            LocalDateTime tm = toLocalDateTime(row.get("tm"));
            if (tm == null) continue;
            BigDecimal q = ValueConvertUtils.decimal(row.get("q"));
            if (q != null && (DeviceValueUtils.isError(q) || DeviceValueUtils.isMissing(q))) {
                q = null;
            }
            FlowTrendVO.HourPoint point = new FlowTrendVO.HourPoint();
            point.setHour(tm.format(fmt));
            point.setFlow(q);
            points.add(point);
        }

        FlowTrendVO vo = new FlowTrendVO();
        vo.setStcd(stcd);
        vo.setStnm(stnm);
        vo.setStartTime(startTime);
        vo.setEndTime(endTime);
        vo.setHours(points);
        return vo;
    }

    @Override
    public Page<FlowMonitoringVO> history(String stcd, LocalDateTime startTime, LocalDateTime endTime,
                                           long page, long size) {
        // 查询总数
        long total = waterFlowMapper.selectHistoryCount(stcd, startTime, endTime);

        Page<FlowMonitoringVO> result = new Page<>(page, size);
        result.setTotal(total);

        if (total == 0) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        // 分页查询（-999 = 设备不存在：转 null 返回；-9991 设备异常保留透传）
        int offset = (int) ((page - 1) * size);
        int limit = (int) size;
        List<FlowMonitoringVO> records = waterFlowMapper.selectHistoryPage(stcd, startTime, endTime, limit, offset);
        records.forEach(r -> {
            r.setQ(DeviceValueUtils.nullIfMissing(r.getQ()));
            r.setTf(toWanFlow(r.getTf()));
        });
        result.setRecords(records);

        return result;
    }

    @Override
    public List<FlowStationVO> stationFlow(LocalDateTime time) {
        // 1. 固定八站站点信息（站点表主键 iofhpi = 测站编码）
        List<StStinfo> stations = stStinfoMapper.selectBatchIds(FLOW_STATION_STCDS);
        Map<String, StStinfo> infoMap = stations.stream()
                .collect(Collectors.toMap(StStinfo::getStcd, s -> s, (a, b) -> a));

        // 2. 候选站点标识 = 测站编码 + 站点 UUID（流量表 skey = COALESCE(stcd, site)：
        //    老站点用编号、MQTT 站点无 stcd 时回退到 site UUID，两种存储形态都试）
        List<String> codes = new ArrayList<>();
        for (String stcd : FLOW_STATION_STCDS) {
            codes.add(stcd);
            StStinfo info = infoMap.get(stcd);
            if (info != null && info.getId() != null && !info.getId().isEmpty()) {
                codes.add(info.getId());
            }
        }

        // 3. 查询各标识在 [time−20min, time+20min] 内距时间点最近的一条瞬时流量
        List<Map<String, Object>> rows = waterFlowMapper.selectClosestFlowBySites(
                codes, time, time.minusMinutes(FLOW_WINDOW_MINUTES), time.plusMinutes(FLOW_WINDOW_MINUTES));

        // 4. 标识 → 站点下标；同一站点多标识命中时（编号与站点 UUID 均落在窗口内）取距时间点最近的一条
        Map<String, Integer> codeToIndex = new HashMap<>();
        for (int i = 0; i < FLOW_STATION_STCDS.size(); i++) {
            codeToIndex.put(FLOW_STATION_STCDS.get(i), i);
            StStinfo info = infoMap.get(FLOW_STATION_STCDS.get(i));
            if (info != null && info.getId() != null) {
                codeToIndex.put(info.getId(), i);
            }
        }
        FlowStationVO[] hitByIndex = new FlowStationVO[FLOW_STATION_STCDS.size()];
        long[] hitDistance = new long[FLOW_STATION_STCDS.size()];
        Arrays.fill(hitDistance, Long.MAX_VALUE);
        for (Map<String, Object> row : rows) {
            Integer idx = codeToIndex.get(String.valueOf(row.get("skey")));
            BigDecimal q = ValueConvertUtils.decimal(row.get("q"));
            if (idx == null || q == null) continue;
            LocalDateTime tm = toLocalDateTime(row.get("tm"));
            long distance = tm == null ? Long.MAX_VALUE : Math.abs(Duration.between(time, tm).toMillis());
            if (hitByIndex[idx] != null && distance >= hitDistance[idx]) continue;
            FlowStationVO vo = new FlowStationVO();
            vo.setQ(q);
            hitByIndex[idx] = vo;
            hitDistance[idx] = distance;
        }

        // 5. 按固定顺序组装；20 分钟内无入库数据的站点流量为 null（该时间点无报文）
        List<FlowStationVO> result = new ArrayList<>();
        int hit = 0;
        for (int i = 0; i < FLOW_STATION_STCDS.size(); i++) {
            String stcd = FLOW_STATION_STCDS.get(i);
            FlowStationVO vo = hitByIndex[i] != null ? hitByIndex[i] : new FlowStationVO();
            StStinfo info = infoMap.get(stcd);
            if (info != null) {
                vo.setId(info.getId());
                vo.setName(info.getStnm());
            }
            vo.setStcd(stcd);
            if (hitByIndex[i] != null) hit++;
            result.add(vo);
        }
        log.info("流量图表查询：time={}，窗口 ±{} 分钟，命中 {}/{} 站", time, FLOW_WINDOW_MINUTES, hit, FLOW_STATION_STCDS.size());
        return result;
    }

    @Override
    public List<PeriodRegimeVO> periodRegime(LocalDate date, int interval, List<String> stcds) {
        // 防御：interval 非法（<=0）会导致槽位生成死循环
        if (interval <= 0) {
            throw new IllegalArgumentException("时段间隔必须为正整数（1/2/3/6/12）");
        }
        // 1. 解析站点（过渡期按站名反查），建立真实 stcd → stnm 映射
        Map<String, String> stcdToName = new LinkedHashMap<>();
        for (String raw : stcds) {
            String stcd = raw.trim();
            if (stcd == null || stcd.isEmpty()) continue;
            StStinfo info = resolveStation(stcd);
            String realStcd = info != null && info.getStcd() != null ? info.getStcd() : stcd;
            String stnm = info != null && info.getStnm() != null ? info.getStnm() : stcd;
            stcdToName.putIfAbsent(realStcd, stnm);
        }

        if (stcdToName.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> resolvedStcds = new ArrayList<>(stcdToName.keySet());

        // 2. 生成时间槽位：选中日期 08:00 起，按 interval 小时递增，到次日 07:00 止；
        // 最新槽位不得超过「当前最近整点」（如现在 18:27 → 18:00）：未来时段尚无采集数据，
        // 若照旧生成到次日 07:00 会产生大量全空白槽位行（选中今天时尤为明显）
        LocalDateTime slotStart = date.atTime(LocalTime.of(8, 0));
        LocalDateTime slotEnd = date.plusDays(1).atTime(LocalTime.of(7, 0));
        LocalDateTime nowHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        if (slotEnd.isAfter(nowHour)) {
            slotEnd = nowHour;
        }
        // 选中今天且当前尚未到 08:00，或选中未来日期：无任何已到时段的槽位，返回空
        if (slotStart.isAfter(slotEnd)) {
            return Collections.emptyList();
        }
        List<LocalDateTime> slots = new ArrayList<>();
        LocalDateTime t = slotStart;
        while (!t.isAfter(slotEnd)) {
            slots.add(t);
            t = t.plusHours(interval);
        }

        // 3. 批量查询所有选中站点在时间窗口内的原始水位记录（按 stcd, tm 升序）
        // 业主口径：整点水位取「整点之前的最后一条采集」（如 11 点值 = 10:00~11:00 间最新一条），
        // 整点之后的采集归下一整点，一旦进入下一时段，前一时段数值固定不再变动。
        // 因此窗口从首个槽位的前一时刻 slotStart-interval 开始，到 slotEnd 止（多取无害，
        // 恰好等于首槽位前一整点的记录会被槽位匹配的左开右闭规则忽略）
        List<Map<String, Object>> rawRecords = waterFlowMapper.selectPeriodRawRecords(
                resolvedStcds, slotStart.minusHours(interval), slotEnd);

        // 4. 构建结果：N站 × M槽 = 完整 VO 列表
        // 站点按展示顺序分组，同站数据连续不交错；组内槽位按时间降序（最新在前）。
        // 站点顺序：已配置「水位」站点排序时按配置顺序（未在配置内的站点排在其后），
        // 未配置时沿用业主权威顺序（周家河 > 花凉亭坝上 > 花凉亭坝下）
        // 槽位匹配策略（业主口径）：记录 tm ∈ (prevSlot, slot] 左开右闭 → 归属 slot；
        // 同槽位多条取 tm 最大的（最新）。整点整点的记录归该整点槽位（如 11:00:00 归 11 点），
        // 整点之后归下一整点，保证前一时段数值在进入下一时段后固定不动
        List<Map.Entry<String, String>> orderedStations = new ArrayList<>(stcdToName.entrySet());
        List<String> configuredStcds = stRiverRService.orderedWaterStcds();
        if (configuredStcds.isEmpty()) {
            orderedStations.sort(Comparator.comparingInt(e -> stationRank(e.getValue())));
        } else {
            orderedStations.sort(Comparator
                    .comparingInt((Map.Entry<String, String> e) -> orderedIndex(configuredStcds, e.getKey()))
                    .thenComparingInt(e -> stationRank(e.getValue())));
        }

        List<PeriodRegimeVO> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : orderedStations) {
            String stcd = entry.getKey();
            String stnm = entry.getValue();

            // 为该站点构建：槽位 → 最新有效记录（z/wptn/q）
            Map<LocalDateTime, SlotVal> slotBest = new LinkedHashMap<>();
            for (Map<String, Object> row : rawRecords) {
                if (!stcd.equals(String.valueOf(row.get("stcd")))) continue;

                Object tmObj = row.get("tm");
                if (tmObj == null) continue;

                LocalDateTime recordTm;
                if (tmObj instanceof Timestamp) {
                    recordTm = ((Timestamp) tmObj).toLocalDateTime();
                } else if (tmObj instanceof LocalDateTime) {
                    recordTm = (LocalDateTime) tmObj;
                } else {
                    continue;
                }

                // 找到该记录归属的槽位：(prevSlot, slot] 左开右闭
                for (int i = 0; i < slots.size(); i++) {
                    LocalDateTime slot = slots.get(i);
                    LocalDateTime prevSlot = slot.minusHours(interval);
                    if (recordTm.isAfter(prevSlot) && !recordTm.isAfter(slot)) {
                        SlotVal v = new SlotVal();
                        // -999 = 设备不存在：转 null 返回（-9991 设备异常保留透传由前端展示 '--'）
                        v.z = DeviceValueUtils.nullIfMissing(ValueConvertUtils.decimal(row.get("z")));
                        v.wptn = row.get("wptn") != null ? String.valueOf(row.get("wptn")) : null;
                        v.q = DeviceValueUtils.nullIfMissing(ValueConvertUtils.decimal(row.get("q")));
                        // 同槽位多条时，后扫描的（tm 更大）覆盖前者
                        slotBest.put(slot, v);
                        break;
                    }
                }
            }

            // 为该站点的每个槽位生成 VO（时间降序：从次日 07:00 最新槽位到 08:00 最旧槽位）
            for (int i = slots.size() - 1; i >= 0; i--) {
                LocalDateTime slot = slots.get(i);
                PeriodRegimeVO vo = new PeriodRegimeVO();
                vo.setStcd(stcd);
                vo.setStnm(stnm);
                vo.setTm(slot);
                SlotVal v = slotBest.get(slot);
                vo.setZ(v != null ? v.z : null);
                vo.setWptn(v != null ? DictUtils.mapWptn(v.wptn) : null);
                vo.setQ(v != null ? v.q : null);
                // msqmt/msamt/msvmt 无数据源，留空
                result.add(vo);
            }
        }

        return result;
    }

    /** 槽位内最新有效记录（z/wptn/q） */
    private static class SlotVal {
        BigDecimal z;
        String wptn;
        BigDecimal q;
    }

    /** Map 值 → LocalDateTime（null 安全，兼容驱动返回 Timestamp / LocalDateTime 两种类型） */
    private LocalDateTime toLocalDateTime(Object obj) {
        if (obj instanceof Timestamp) return ((Timestamp) obj).toLocalDateTime();
        if (obj instanceof LocalDateTime) return (LocalDateTime) obj;
        return null;
    }

}
