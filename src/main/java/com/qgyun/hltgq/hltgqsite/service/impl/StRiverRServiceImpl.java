package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qgyun.hltgq.hltgqsite.entity.StRiverR;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.entity.WaterThreshold;
import com.qgyun.hltgq.hltgqsite.mapper.StRiverRMapper;
import com.qgyun.hltgq.hltgqsite.mapper.StStinfoMapper;
import com.qgyun.hltgq.hltgqsite.mapper.WaterThresholdMapper;
import com.qgyun.hltgq.hltgqsite.service.StRiverRService;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirRegimeVO;
import com.qgyun.hltgq.hltgqsite.vo.RiverRegimeVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterBriefVO;
import com.qgyun.hltgq.hltgqsite.vo.YearsRegimeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StRiverRServiceImpl extends ServiceImpl<StRiverRMapper, StRiverR> implements StRiverRService {

    @Autowired
    private StStinfoMapper stStinfoMapper;

    @Autowired
    private WaterThresholdMapper waterThresholdMapper;

    /** 河道水位站名称 */
    private static final Set<String> RIVER_STATION_NAMES = new HashSet<>(Arrays.asList("周家河", "花凉亭坝下"));

    /** 水库水位站名称 */
    private static final Set<String> RESERVOIR_STATION_NAMES = new HashSet<>(Collections.singletonList("花凉亭坝上"));

    /** 水情简报/多年同期水情覆盖的水情站（周家河、花凉亭坝下、花凉亭坝上），顺序即展示顺序 */
    private static final List<String> WATER_STATION_STCDS = Arrays.asList("3206400001", "320640000A", "3206400007");

    /**
     * 河道/水库水情表排序（业主口径）：站点权威顺序 周家河 > 花凉亭坝上 > 花凉亭坝下，
     * 同一站点数据连续成组不交错，组内按时间倒序（最新在前）。
     */
    private static final String REGIME_ORDER_BY =
            "ORDER BY CASE \"STCD\" WHEN '3206400001' THEN 1 WHEN '3206400007' THEN 2 WHEN '320640000A' THEN 3 ELSE 4 END, \"TM\" DESC";

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
     * 新 STCD → 站点名称（站点表接入过渡期，STCD 查不到时按名称反查）
     */
    private static final Map<String, String> STCD_TO_STNM = new HashMap<>();
    static {
        STCD_TO_STNM.put("3206400001", "周家河");
        STCD_TO_STNM.put("320640000A", "花凉亭坝下");
        STCD_TO_STNM.put("3206400007", "花凉亭坝上");
    }

    /**
     * -9991 设备异常、-999 设备不存在：仅用于比较类计算的排除判定（如与昨日 8 点差值）。
     * 展示口径：-999 后端转 null 不返回；-9991 保留透传由前端展示 '--'。
     */
    private static boolean isDeviceError(BigDecimal v) {
        return v != null && (v.compareTo(new BigDecimal("-9991")) == 0
                || v.compareTo(new BigDecimal("-999")) == 0);
    }

    /** -999（设备不存在）→ null；-9991（设备异常）保留透传由前端展示 '--' */
    private static BigDecimal nullIfMissing(BigDecimal v) {
        return (v != null && v.compareTo(new BigDecimal("-999")) == 0) ? null : v;
    }

    /**
     * 查询站点：先按 STCD 精确查询；查不到或名称不在白名单时，
     * 按 STCD 对应的站点名称反查（站点表接入过渡期主键可能未对齐）。
     */
    private StStinfo findStation(String stcd, Set<String> stationNames) {
        StStinfo byId = stStinfoMapper.selectById(stcd);
        if (byId != null && byId.getStnm() != null && stationNames.contains(byId.getStnm())) {
            return byId;
        }
        String stnm = STCD_TO_STNM.get(stcd);
        if (stnm == null) return null;
        QueryWrapper<StStinfo> wrapper = new QueryWrapper<>();
        wrapper.eq("zzkaec", stnm);
        wrapper.last("LIMIT 1");
        return stStinfoMapper.selectOne(wrapper);
    }

    /**
     * 查询水位阈值（警戒水位/保证水位）
     * @param siteId 站点 UUID（station_info.id）
     * @return [警戒水位, 保证水位]，无记录时均为 null
     */
    private BigDecimal[] queryThreshold(String siteId) {
        if (siteId == null) return new BigDecimal[]{null, null};
        QueryWrapper<WaterThreshold> wrapper = new QueryWrapper<>();
        wrapper.eq("site", siteId);
        wrapper.like("type", "#1#");  // 水位类型
        wrapper.last("LIMIT 1");
        WaterThreshold t = waterThresholdMapper.selectOne(wrapper);
        if (t == null) return new BigDecimal[]{null, null};
        return new BigDecimal[]{t.getThreshold(), t.getGuarantee()};
    }

    @Override
    public List<StRiverR> latestPerStation() {
        return baseMapper.selectLatestPerStation();
    }

    @Override
    public boolean saveOrUpdateByKey(StRiverR entity) {
        boolean exists = count(new QueryWrapper<StRiverR>()
                .eq("STCD", entity.getStcd())
                .eq("TM", Timestamp.valueOf(entity.getTm()))) > 0;
        if (exists) {
            return update(entity, new UpdateWrapper<StRiverR>()
                    .eq("STCD", entity.getStcd())
                    .eq("TM", Timestamp.valueOf(entity.getTm())));
        }
        return save(entity);
    }

    /**
     * 河道/水库水情站点解析：逐站旧编号映射 + 白名单校验 + 站名反查兜底。
     * 返回 数据表STCD → StStinfo（保序），用于数据查询与站名/阈值映射。
     */
    private Map<String, StStinfo> resolveRegimeStations(List<String> stcds, Set<String> stationNames, String typeDesc) {
        if (stcds == null || stcds.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个站点");
        }
        Map<String, StStinfo> map = new LinkedHashMap<>();
        for (String raw : stcds) {
            String stcd = raw == null ? "" : raw.trim();
            if (stcd.isEmpty()) continue;
            String resolvedInput = LEGACY_TO_NEW_STCD.getOrDefault(stcd, stcd);
            StStinfo stinfo = findStation(resolvedInput, stationNames);
            if (stinfo == null || stinfo.getStnm() == null || !stationNames.contains(stinfo.getStnm())) {
                throw new IllegalArgumentException(typeDesc + "（收到 stcd: " + stcd + "）");
            }
            map.put(stinfo.getStcd(), stinfo);
        }
        if (map.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个站点");
        }
        return map;
    }

    /** 逐站查询水位阈值：数据表STCD → [警戒水位, 保证水位] */
    private Map<String, BigDecimal[]> queryThresholdMap(Map<String, StStinfo> stations) {
        Map<String, BigDecimal[]> map = new HashMap<>();
        for (Map.Entry<String, StStinfo> e : stations.entrySet()) {
            map.put(e.getKey(), queryThreshold(e.getValue().getId()));
        }
        return map;
    }

    /** 河道记录 → VO（站名/阈值按记录所属站点映射） */
    private RiverRegimeVO toRiverVO(StRiverR r, Map<String, StStinfo> stations, Map<String, BigDecimal[]> thresholds) {
        String stcd = r.getStcd() != null ? r.getStcd().trim() : "";
        StStinfo stinfo = stations.get(stcd);
        BigDecimal[] ref = thresholds.get(stcd);
        RiverRegimeVO vo = new RiverRegimeVO();
        vo.setStcd(stcd);
        vo.setStnm(stinfo != null ? stinfo.getStnm() : null);
        vo.setTm(r.getTm());
        vo.setWarningLevel(ref != null && ref[0] != null ? ref[0].setScale(2, java.math.RoundingMode.DOWN) : null);
        vo.setGuaranteedLevel(ref != null && ref[1] != null ? ref[1].setScale(2, java.math.RoundingMode.DOWN) : null);
        // -999（设备不存在）转 null 不返回；-9991（设备异常）保留透传由前端展示 '--'
        BigDecimal z = nullIfMissing(r.getZ());
        vo.setZ(z != null ? z.setScale(2, java.math.RoundingMode.DOWN) : null);
        vo.setWptn(mapWptn(r.getWptn()));
        return vo;
    }

    /** 水库记录 → VO（站名/阈值按记录所属站点映射，入库/出库暂映射 Q 字段） */
    private ReservoirRegimeVO toReservoirVO(StRiverR r, Map<String, StStinfo> stations, Map<String, BigDecimal[]> thresholds) {
        String stcd = r.getStcd() != null ? r.getStcd().trim() : "";
        StStinfo stinfo = stations.get(stcd);
        BigDecimal[] ref = thresholds.get(stcd);
        ReservoirRegimeVO vo = new ReservoirRegimeVO();
        vo.setStcd(stcd);
        vo.setStnm(stinfo != null ? stinfo.getStnm() : null);
        vo.setTm(r.getTm());
        vo.setWarningLevel(ref != null && ref[0] != null ? ref[0].setScale(2, java.math.RoundingMode.DOWN) : null);
        vo.setGuaranteedLevel(ref != null && ref[1] != null ? ref[1].setScale(2, java.math.RoundingMode.DOWN) : null);
        // -999（设备不存在）转 null 不返回；-9991（设备异常）保留透传由前端展示 '--'
        BigDecimal z = nullIfMissing(r.getZ());
        vo.setZ(z != null ? z.setScale(2, java.math.RoundingMode.DOWN) : null);
        vo.setWptn(mapWptn(r.getWptn()));
        // 出入库流量：暂统一映射 Q 字段（待设备报文到位后区分字段映射），与接口文档十五节一致
        BigDecimal q = nullIfMissing(r.getQ());
        q = q != null ? q.setScale(3, java.math.RoundingMode.DOWN) : null;
        vo.setInq(q);
        vo.setOtq(q);
        return vo;
    }

    @Override
    public Page<RiverRegimeVO> riverRegime(List<String> stcds, LocalDateTime startTime, LocalDateTime endTime, long page, long size) {
        // 1. 站点解析（过渡期兼容：旧编号映射 + 站名反查兜底）
        Map<String, StStinfo> stations = resolveRegimeStations(stcds, RIVER_STATION_NAMES,
                "河道水位站仅支持: 周家河、花凉亭坝下");
        Map<String, BigDecimal[]> thresholds = queryThresholdMap(stations);

        // 2. 多站合并分页查询（站点权威顺序分组 + 组内时间倒序，同站数据连续不交错）
        QueryWrapper<StRiverR> wrapper = new QueryWrapper<>();
        wrapper.in("STCD", new ArrayList<>(stations.keySet()));
        if (startTime != null) wrapper.ge("TM", Timestamp.valueOf(startTime));
        if (endTime != null) wrapper.le("TM", Timestamp.valueOf(endTime));
        wrapper.last(REGIME_ORDER_BY);

        Page<StRiverR> rawPage = (Page<StRiverR>) this.page(
                new Page<StRiverR>(page, size), wrapper);

        // 3. 转换为 RiverRegimeVO
        List<RiverRegimeVO> records = rawPage.getRecords().stream()
                .map(r -> toRiverVO(r, stations, thresholds))
                .collect(Collectors.toList());

        Page<RiverRegimeVO> result = new Page<>(page, size);
        result.setTotal(rawPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public List<RiverRegimeVO> riverRegimeExport(List<String> stcds, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, StStinfo> stations = resolveRegimeStations(stcds, RIVER_STATION_NAMES,
                "河道水位站仅支持: 周家河、花凉亭坝下");
        Map<String, BigDecimal[]> thresholds = queryThresholdMap(stations);
        QueryWrapper<StRiverR> wrapper = new QueryWrapper<>();
        wrapper.in("STCD", new ArrayList<>(stations.keySet()));
        if (startTime != null) wrapper.ge("TM", Timestamp.valueOf(startTime));
        if (endTime != null) wrapper.le("TM", Timestamp.valueOf(endTime));
        wrapper.last(REGIME_ORDER_BY);
        return this.list(wrapper).stream()
                .map(r -> toRiverVO(r, stations, thresholds))
                .collect(Collectors.toList());
    }

    /** 水势代码 → 中文 */
    private String mapWptn(String wptn) {
        if (wptn == null || wptn.isEmpty()) return "无涨落信息";
        switch (wptn.trim()) {
            case "4":
            case "涨": return "涨";
            case "5":
            case "落": return "落";
            case "6":
            case "平": return "平";
            default:  return "无涨落信息";
        }
    }

    @Override
    public Page<ReservoirRegimeVO> reservoirRegime(List<String> stcds, LocalDateTime startTime, LocalDateTime endTime, long page, long size) {
        // 1. 站点解析（过渡期兼容：旧编号映射 + 站名反查兜底）
        Map<String, StStinfo> stations = resolveRegimeStations(stcds, RESERVOIR_STATION_NAMES,
                "水库水位站仅支持: 花凉亭坝上");
        Map<String, BigDecimal[]> thresholds = queryThresholdMap(stations);

        // 2. 多站合并分页查询（站点权威顺序分组 + 组内时间倒序，同站数据连续不交错）
        QueryWrapper<StRiverR> wrapper = new QueryWrapper<>();
        wrapper.in("STCD", new ArrayList<>(stations.keySet()));
        if (startTime != null) wrapper.ge("TM", Timestamp.valueOf(startTime));
        if (endTime != null) wrapper.le("TM", Timestamp.valueOf(endTime));
        wrapper.last(REGIME_ORDER_BY);

        Page<StRiverR> rawPage = (Page<StRiverR>) this.page(
                new Page<StRiverR>(page, size), wrapper);

        // 3. 转换为 ReservoirRegimeVO
        List<ReservoirRegimeVO> records = rawPage.getRecords().stream()
                .map(r -> toReservoirVO(r, stations, thresholds))
                .collect(Collectors.toList());

        Page<ReservoirRegimeVO> result = new Page<>(page, size);
        result.setTotal(rawPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public List<ReservoirRegimeVO> reservoirRegimeExport(List<String> stcds, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, StStinfo> stations = resolveRegimeStations(stcds, RESERVOIR_STATION_NAMES,
                "水库水位站仅支持: 花凉亭坝上");
        Map<String, BigDecimal[]> thresholds = queryThresholdMap(stations);
        QueryWrapper<StRiverR> wrapper = new QueryWrapper<>();
        wrapper.in("STCD", new ArrayList<>(stations.keySet()));
        if (startTime != null) wrapper.ge("TM", Timestamp.valueOf(startTime));
        if (endTime != null) wrapper.le("TM", Timestamp.valueOf(endTime));
        wrapper.last(REGIME_ORDER_BY);
        return this.list(wrapper).stream()
                .map(r -> toReservoirVO(r, stations, thresholds))
                .collect(Collectors.toList());
    }

    // ======================== 水情简报 / 多年同期水情 ========================

    /**
     * 解析三个水情站（周家河、花凉亭坝下、花凉亭坝上）：
     * 先按 STCD 精确查询；查不到时按名称反查（站点表接入过渡期主键可能未对齐）。
     * 返回 LinkedHashMap 保证顺序稳定。
     */
    private Map<String, StStinfo> resolveWaterStations() {
        Map<String, StStinfo> map = new LinkedHashMap<>();
        for (String stcd : WATER_STATION_STCDS) {
            StStinfo info = stStinfoMapper.selectById(stcd);
            if (info == null || info.getStnm() == null) {
                String stnm = STCD_TO_STNM.get(stcd);
                if (stnm != null) {
                    QueryWrapper<StStinfo> wrapper = new QueryWrapper<>();
                    wrapper.eq("zzkaec", stnm);
                    wrapper.last("LIMIT 1");
                    info = stStinfoMapper.selectOne(wrapper);
                }
            }
            if (info != null && info.getStcd() != null) {
                map.put(info.getStcd(), info);
            }
        }
        return map;
    }

    /** 水位值统一截断 2 位小数 */
    private BigDecimal zScale2(BigDecimal v) {
        return v != null ? v.setScale(2, java.math.RoundingMode.DOWN) : null;
    }

    /**
     * 整点水位取值（业主口径）：取 (slotTime-1h, slotTime] 左开右闭内 z 非空的最后一条采集。
     * 整点之前的最后一条采集作为该整点值，整点之后的采集归下一整点；
     * 一旦进入下一时段，本整点数值固定不再变动。
     * -999（设备不存在）视为无采集跳过继续向前找；-9991（设备异常）保留透传由前端展示 '--'。
     */
    private StRiverR latestUpTo(List<StRiverR> rowsAsc, LocalDateTime slotTime) {
        LocalDateTime floor = slotTime.minusHours(1);
        StRiverR best = null;
        for (StRiverR r : rowsAsc) {
            LocalDateTime tm = r.getTm();
            if (tm == null) continue;
            if (tm.isAfter(floor) && !tm.isAfter(slotTime)) {
                if (r.getZ() != null && r.getZ().compareTo(new BigDecimal("-999")) != 0) best = r;  // rowsAsc 升序，后扫到的即最新
            }
        }
        return best;
    }

    /** Map 中取出 LocalDateTime（兼容 Timestamp 驱动返回值） */
    private LocalDateTime toLocalDateTime(Object obj) {
        if (obj instanceof Timestamp) {
            return ((Timestamp) obj).toLocalDateTime();
        }
        if (obj instanceof LocalDateTime) {
            return (LocalDateTime) obj;
        }
        return null;
    }

    @Override
    public List<WaterBriefVO> waterBrief(LocalDate date) {
        Map<String, StStinfo> resolved = resolveWaterStations();
        List<WaterBriefVO> result = new ArrayList<>();
        if (resolved.isEmpty()) {
            return result;
        }

        // 8 点/20 点水位的整点时刻（查询日 2026-08-14 → 昨日=08-13）
        // 业主口径：整点值取整点之前最后一条采集，即 (整点-1h, 整点] 左开右闭内的最新记录
        LocalDateTime y8At = date.minusDays(1).atTime(8, 0);
        LocalDateTime y20At = date.minusDays(1).atTime(20, 0);
        LocalDateTime t8At = date.atTime(8, 0);

        // 1. 查询窗口内原始记录：昨日 00:00 ~ 查询日 23:59:59
        QueryWrapper<StRiverR> wrapper = new QueryWrapper<StRiverR>().orderByAsc("TM");
        wrapper.in("STCD", new ArrayList<>(resolved.keySet()));
        wrapper.ge("TM", Timestamp.valueOf(date.minusDays(1).atStartOfDay()));
        wrapper.le("TM", Timestamp.valueOf(date.atTime(23, 59, 59)));
        List<StRiverR> records = this.list(wrapper);

        Map<String, List<StRiverR>> byStation = new LinkedHashMap<>();
        for (StRiverR r : records) {
            String key = r.getStcd() != null ? r.getStcd().trim() : "";
            if (!resolved.containsKey(key)) continue;
            byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        for (List<StRiverR> rows : byStation.values()) {
            rows.sort(Comparator.comparing(StRiverR::getTm));
        }

        // 2. 逐站组装
        for (Map.Entry<String, StStinfo> entry : resolved.entrySet()) {
            String stcd = entry.getKey();
            StStinfo stinfo = entry.getValue();
            List<StRiverR> rows = byStation.getOrDefault(stcd, Collections.emptyList());

            StRiverR y8Rec = latestUpTo(rows, y8At);
            StRiverR y20Rec = latestUpTo(rows, y20At);
            StRiverR t8Rec = latestUpTo(rows, t8At);

            WaterBriefVO vo = new WaterBriefVO();
            vo.setStcd(stcd);
            vo.setStnm(stinfo.getStnm());

            // 阈值：threshold → 警戒水位，guarantee → 设防水位
            BigDecimal[] ref = queryThreshold(stinfo.getId());
            vo.setWrz(zScale2(ref[0]));
            vo.setDsflz(zScale2(ref[1]));

            vo.setY8(y8Rec != null ? zScale2(nullIfMissing(y8Rec.getZ())) : null);
            vo.setY20(y20Rec != null ? zScale2(nullIfMissing(y20Rec.getZ())) : null);
            vo.setT8(t8Rec != null ? zScale2(nullIfMissing(t8Rec.getZ())) : null);

            // 水势：优先今天 8 点记录，其次当日最新记录
            StRiverR wptnRec = t8Rec != null ? t8Rec : (rows.isEmpty() ? null : rows.get(rows.size() - 1));
            vo.setWptn(mapWptn(wptnRec != null ? wptnRec.getWptn() : null));

            // 与昨日 8 点比 = t8 - y8（任一为 -9991 设备异常或 -999 设备不存在时不比较）
            if (t8Rec != null && y8Rec != null
                    && t8Rec.getZ() != null && y8Rec.getZ() != null
                    && !isDeviceError(t8Rec.getZ()) && !isDeviceError(y8Rec.getZ())) {
                vo.setCmp(t8Rec.getZ().subtract(y8Rec.getZ()).setScale(2, java.math.RoundingMode.DOWN));
            }

            // 流量取今天 8 点记录 Q（-999 设备不存在转 null；-9991 设备异常保留透传）
            BigDecimal q = t8Rec != null ? nullIfMissing(t8Rec.getQ()) : null;
            vo.setQ(q != null ? q.setScale(2, java.math.RoundingMode.DOWN) : null);

            // 蓄水量：暂无数据源，恒为 null
            vo.setW(null);

            // 当年最高水位（1 月 1 日以来，截至查询日）
            Map<String, Object> maxRow = baseMapper.selectMaxZInRange(stcd,
                    date.withDayOfYear(1).atStartOfDay(), date.atTime(23, 59, 59));
            if (maxRow != null && maxRow.get("z") != null) {
                Object zObj = maxRow.get("z");
                vo.setMaxz(zScale2(zObj instanceof BigDecimal
                        ? (BigDecimal) zObj : new BigDecimal(zObj.toString())));
                vo.setMaxTm(toLocalDateTime(maxRow.get("tm")));
            }

            result.add(vo);
        }
        return result;
    }

    @Override
    public YearsRegimeVO yearsRegime(int startYear, int endYear, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("月份必须在 1-12 之间");
        }
        if (startYear > endYear) {
            throw new IllegalArgumentException("起始年份不能大于结束年份");
        }

        Map<String, StStinfo> resolved = resolveWaterStations();
        List<String> stations = new ArrayList<>();
        for (StStinfo info : resolved.values()) {
            stations.add(info.getStnm());
        }

        YearsRegimeVO vo = new YearsRegimeVO();
        vo.setStations(stations);
        List<YearsRegimeVO.YearRow> rows = new ArrayList<>();
        vo.setRows(rows);
        if (resolved.isEmpty()) {
            return vo;
        }

        // 1. 一次性查询年份区间内各站按年月的平均水位
        List<Map<String, Object>> avgs = baseMapper.selectMonthlyAvgZ(
                new ArrayList<>(resolved.keySet()),
                LocalDateTime.of(startYear, 1, 1, 0, 0),
                LocalDateTime.of(endYear, 12, 31, 23, 59, 59));

        Map<String, BigDecimal> avgMap = new HashMap<>();
        for (Map<String, Object> row : avgs) {
            Object stcdObj = row.get("stcd");
            Object yrObj = row.get("yr");
            Object monObj = row.get("mon");
            Object zObj = row.get("avgz");
            if (stcdObj == null || yrObj == null || monObj == null || zObj == null) continue;
            BigDecimal avgz = zObj instanceof BigDecimal
                    ? (BigDecimal) zObj : new BigDecimal(zObj.toString());
            avgMap.put(stcdObj.toString().trim() + "|" + yrObj + "|" + monObj, avgz);
        }

        // 2. 组装：每年一行，值顺序与 stations 对齐
        for (int year = startYear; year <= endYear; year++) {
            YearsRegimeVO.YearRow row = new YearsRegimeVO.YearRow();
            row.setTm(String.format("%04d-%02d", year, month));
            List<BigDecimal> values = new ArrayList<>();
            for (String stcd : resolved.keySet()) {
                values.add(avgMap.get(stcd + "|" + year + "|" + month));
            }
            row.setValues(values);
            rows.add(row);
        }
        return vo;
    }
}
