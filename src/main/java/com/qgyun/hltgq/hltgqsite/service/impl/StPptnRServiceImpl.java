package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qgyun.hltgq.hltgqsite.entity.StPptnR;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.mapper.StPptnRMapper;
import com.qgyun.hltgq.hltgqsite.mapper.StStinfoMapper;
import com.qgyun.hltgq.hltgqsite.service.StPptnRService;
import com.qgyun.hltgq.hltgqsite.vo.GqDailyRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.GqRainfallChartVO;
import com.qgyun.hltgq.hltgqsite.vo.GqRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirExtremeRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirPeriodRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirRainfallBriefVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirTenDayRainfallVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StPptnRServiceImpl extends ServiceImpl<StPptnRMapper, StPptnR> implements StPptnRService {

    @Autowired
    private StStinfoMapper stStinfoMapper;

    // ======================== 灌区接口-水库站点排除 ========================
    // 13 个水库站点新 STCD 已全部确认

    /** 已确认的新表水库 STCD */
    private static final Set<String> RESERVOIR_STCD_NEW = new HashSet<>(Arrays.asList(
            "3206400001",  // 周家河
            "3206400002",  // 姜家寨
            "3206400003",  // 九田
            "3206400005",  // 牛镇
            "3206400006",  // 马嘶铺
            "3206400008",  // 寺前
            "3206400009",  // 河图铺
            "3206400004",  // 下前河
            "320640000B",  // 鲤鱼墩
            "320640000D",  // 白帽
            "3206400007",  // 花凉亭坝上
            "320640000A",  // 花凉亭坝下
            "320640000C"   // 弥陀
    ));

    /** 花凉亭水库 13 站点名称（雨量记录 STCD 未迁移时按名称兜底排除；含水情站花凉亭坝下，用于灌区接口排除） */
    private static final Set<String> RESERVOIR_STATION_NAMES = new HashSet<>(Arrays.asList(
            "周家河", "姜家寨", "九田", "牛镇", "马嘶铺", "寺前",
            "河图铺", "下前河", "鲤鱼墩", "弥陀", "白帽",
            "花凉亭坝上", "花凉亭坝下"
    ));

    /**
     * 水库雨量站点（按上线顺序排列）。
     * <p>花凉亭坝下为水情站、非雨量站，不参与雨情页面展示，故不在此列（13 水情/雨量站 - 坝下 = 12 雨量站）。
     */
    private static final List<String> RESERVOIR_RAIN_STATION_ORDER = Arrays.asList(
            "周家河", "姜家寨", "九田", "牛镇", "马嘶铺", "花凉亭坝上",
            "寺前", "河图铺", "下前河", "鲤鱼墩", "弥陀", "白帽"
    );

    /** MQTT 站点固定清单（站点名匹配，其余为 RabbitMQ）：与前端 isStaleTm 标红规则一致 */
    private static final Set<String> MQTT_STATION_NAMES = new HashSet<>(Arrays.asList(
            "南山寺节制闸", "渠首进水闸", "渠首电站防洪闸", "双庙湖节制闸"
    ));

    /** MQTT 站断联阈值：报文 10 分钟一次，30 分钟无更新判离线 */
    private static final long MQTT_STALE_MINUTES = 30;
    /** RabbitMQ 站断联阈值：报文 1 小时一次，70 分钟无更新判离线 */
    private static final long RBT_STALE_MINUTES = 70;

    @Override
    public List<StPptnR> latestPerStation() {
        return baseMapper.selectLatestPerStation();
    }

    @Override
    public IPage<StPptnR> dailyPage(IPage<StPptnR> page, QueryWrapper<StPptnR> wrapper) {
        List<StPptnR> all = baseMapper.selectDailyAll(wrapper);
        long total = all.size();
        int start = (int) ((page.getCurrent() - 1) * page.getSize());
        int end   = (int) Math.min(start + page.getSize(), total);
        page.setTotal(total);
        page.setRecords(start >= total ? Collections.emptyList() : all.subList(start, end));
        return page;
    }

    @Override
    public List<StPptnR> todaySumPerStation(LocalDateTime start, LocalDateTime end) {
        return baseMapper.selectTodaySumPerStation(Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    @Override
    public boolean saveOrUpdateByKey(StPptnR entity) {
        boolean exists = count(new QueryWrapper<StPptnR>()
                .eq("STCD", entity.getStcd())
                .eq("TM", Timestamp.valueOf(entity.getTm()))) > 0;
        if (exists) {
            return update(entity, new UpdateWrapper<StPptnR>()
                    .eq("STCD", entity.getStcd())
                    .eq("TM", Timestamp.valueOf(entity.getTm())));
        }
        return save(entity);
    }

    @Override
    public IPage<GqRainfallVO> gqRainfallPage(long page, long size, String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        // drp 基线 = 服务器当前所属水文日的 8 点起点（与 dailyDyp 同用 LocalDateTime.now() 时钟口径）：
        // "当前雨量"始终表示当前水文日累计，最新报文停留在上一水文日（如 8 点整点报文）时不会与"昨日雨量"重合
        String curLabel = getHydroDayLabel(LocalDateTime.now());
        LocalDateTime hydroBase = LocalDateTime.parse(curLabel,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).minusDays(1);
        List<Map<String, Object>> rows = baseMapper.selectGqRainfallList(stcd, startTime, endTime, hydroBase);
        // 未指定 stcd 时按灌区口径排除水库 13 站；显式指定 stcd 时放行该站（含水库站）
        List<GqRainfallVO> vos = rows.stream()
                .filter(row -> gqStationVisible(row, stcd))
                .map(this::toGqRainfallVO).collect(Collectors.toList());
        // 在线状态：水库站用站点表 zebpsu 状态（#1# 在线 / #2# 离线），不走时间断联（水库站报文频率特殊，水位页面为时段列表语义）；
        // 其余站按时间断联判定（MQTT 站 30 分钟、RabbitMQ 站 70 分钟无更新判离线）
        Map<String, Boolean> reservoirOnline = loadReservoirOnlineStatus(rows);
        vos.forEach(vo -> {
            String key = vo.getStcd() == null ? null : vo.getStcd().trim();
            if (key != null && reservoirOnline.containsKey(key)) {
                vo.setIsOnline(reservoirOnline.get(key));
            } else {
                vo.setIsOnline(!isStale(vo.getTm(), vo.getStnm()));
            }
        });
        // 填充昨日雨量 dailyDyp（分页前一次批量查询，避免翻页重复计算）
        fillDailyDyp(vos);
        return toPage(vos, page, size);
    }

    @Override
    public IPage<GqRainfallVO> gqRainfallHistoryPage(long page, long size, String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        // 分页下推：count + 当前页行（基线子查询仅对当前页行执行，避免全量行 × 4 次子查询导致接口慢）
        long total = baseMapper.countGqRainfallHistory(stcd, startTime, endTime);
        if (total == 0) {
            Page<GqRainfallVO> empty = new Page<>(page, size);
            empty.setTotal(0);
            empty.setRecords(Collections.emptyList());
            return empty;
        }
        int offset = (int) ((page - 1) * size);
        List<Map<String, Object>> rows = baseMapper.selectGqRainfallHistoryPage(stcd, startTime, endTime, (int) size, offset);
        // stcd 必填时放行该站（含水库站）；未指定 stcd 时按灌区口径排除水库 13 站
        List<GqRainfallVO> vos = rows.stream()
                .filter(row -> gqStationVisible(row, stcd))
                .map(this::toGqRainfallVO).collect(Collectors.toList());
        Page<GqRainfallVO> result = new Page<>(page, size);
        result.setTotal(total);
        result.setRecords(vos);
        return result;
    }

    private GqRainfallVO toGqRainfallVO(Map<String, Object> row) {
        GqRainfallVO vo = new GqRainfallVO();
        vo.setStcd((String) row.get("stcd"));
        vo.setId((String) row.get("id"));
        vo.setStnm((String) row.get("stnm"));
        vo.setLon(toBigDecimal(row.get("lon")));
        vo.setLat(toBigDecimal(row.get("lat")));
        Object tmObj = row.get("tm");
        if (tmObj instanceof LocalDateTime) {
            vo.setTm((LocalDateTime) tmObj);
        } else if (tmObj instanceof Timestamp) {
            vo.setTm(((Timestamp) tmObj).toLocalDateTime());
        }
        // -999 = 设备不存在：转 null 返回（-9991 设备异常保留透传由前端展示 '--'）
        vo.setDyp(nullIfMissing(toBigDecimal(row.get("dyp"))));
        // 时段增量计算：当前DYP - 历史DYP（DYP永不归零，计算结果准确）
        BigDecimal dypVal = toBigDecimal(row.get("dyp"));
        BigDecimal dyp1h = toBigDecimal(row.get("dyp_1h"));
        BigDecimal dyp3h = toBigDecimal(row.get("dyp_3h"));
        BigDecimal dyp6h = toBigDecimal(row.get("dyp_6h"));
        vo.setRain1h(subtractOrNull(dypVal, dyp1h));
        vo.setRain3h(subtractOrNull(dypVal, dyp3h));
        vo.setRain6h(subtractOrNull(dypVal, dyp6h));
        // 当前降雨量 = 当前水文日累计（DYP 增量：最新DYP - 当前水文日 8 点起点前基线DYP）
        // 基线随服务器当前时刻滑动（实时列表口径），与 dailyDyp（昨日雨量）错开，避免报文滞后时两值重合
        // 花凉亭 DRP 恒 0、灌区站 DRP 每日 8:00 归零不可靠，统一用 DYP 增量
        BigDecimal dypDay = toBigDecimal(row.get("dyp_day"));
        vo.setDrp(subtractOrNull(dypVal, dypDay));
        // 电压：取电压表最新一条（SQL 已关联）
        vo.setVol(toBigDecimal(row.get("vol")));
        return vo;
    }

    /**
     * 断联判定（与前端 isStaleTm 规则一致）：MQTT 站 30 分钟、RabbitMQ 站 70 分钟无更新判离线；
     * 无时间值（null）视为在线（不判离线）；时间在未来（时钟偏差）也视为在线
     */
    private boolean isStale(LocalDateTime tm, String stnm) {
        if (tm == null) return false;
        long threshold = MQTT_STATION_NAMES.contains(stnm) ? MQTT_STALE_MINUTES : RBT_STALE_MINUTES;
        // 毫秒级严格大于，与前端 Date.now() - t > staleMs 语义完全一致（toMinutes 向下取整会导致边界差一分钟误判）
        return Duration.between(tm, LocalDateTime.now()).toMillis() > threshold * 60_000L;
    }

    /**
     * 水库站在线状态：仅查本次行涉及的水库站（STCD/名称双重识别），取站点表 zebpsu 状态；
     * 站点表未查到状态记录的按在线处理（默认在线，不误报离线）
     */
    private Map<String, Boolean> loadReservoirOnlineStatus(List<Map<String, Object>> rows) {
        Set<String> stcds = new HashSet<>();
        for (Map<String, Object> row : rows) {
            if (isReservoirStation(row)) {
                Object v = row.get("stcd");
                if (v != null) stcds.add(String.valueOf(v).trim());
            }
        }
        Map<String, Boolean> map = new HashMap<>();
        if (stcds.isEmpty()) return map;
        QueryWrapper<StStinfo> wrapper = new QueryWrapper<>();
        wrapper.in("iofhpi", stcds);
        for (StStinfo s : stStinfoMapper.selectList(wrapper)) {
            if (s.getStcd() != null) {
                map.put(s.getStcd().trim(), isZebpsuOnline(s.getZebpsu()));
            }
        }
        return map;
    }

    /** zebpsu 站点状态：#1# 在线、#2# 离线；兼容 "#1#"/"1"/"#1" 格式，null 或未知值默认在线 */
    private boolean isZebpsuOnline(String zebpsu) {
        if (zebpsu == null) return true;
        return !"2".equals(zebpsu.trim().replace("#", ""));
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        return new BigDecimal(val.toString());
    }

    /**
     * -9991 表示设备异常，此类数值一律视为缺失，不参与展示与计算；
     * -999 表示设备不存在（报文原生），同样视为缺失
     */
    private boolean isDeviceError(BigDecimal v) {
        return v != null && v.compareTo(DEVICE_ERROR) == 0;
    }

    private boolean isDeviceMissing(BigDecimal v) {
        return v != null && v.compareTo(DEVICE_MISSING) == 0;
    }

    /** 设备异常标记（原 -999 语义迁移为 -9991） */
    private static final BigDecimal DEVICE_ERROR = new BigDecimal("-9991");
    /** 设备不存在标记（报文原生 -999，前端不展示） */
    private static final BigDecimal DEVICE_MISSING = new BigDecimal("-999");

    /** -999 = 设备不存在：转 null 返回（-9991 设备异常保留透传由前端展示 '--'） */
    private BigDecimal nullIfMissing(BigDecimal v) {
        return isDeviceMissing(v) ? null : v;
    }

    /** 计算增量，结果不小于0；任一为null则返回null；任一为-9991（设备异常）则返回-9991透传标记；任一为-999（设备不存在）则返回null（前端不展示） */
    private BigDecimal subtractOrNull(BigDecimal current, BigDecimal prev) {
        if (current == null || prev == null) return null;
        if (isDeviceError(current) || isDeviceError(prev)) return DEVICE_ERROR;
        if (isDeviceMissing(current) || isDeviceMissing(prev)) return null;
        BigDecimal diff = current.subtract(prev);
        return diff.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : diff;
    }

    /**
     * DYP 增量：max(0, cur - prev)；任一为 null、-9991（设备异常）或 -999（设备不存在）时按 0 处理，
     * 避免异常值污染时段/日雨量累计
     */
    private BigDecimal safeDypIncrement(BigDecimal curDyp, BigDecimal prevDyp) {
        if (curDyp == null || prevDyp == null) return BigDecimal.ZERO;
        if (isDeviceError(curDyp) || isDeviceError(prevDyp)) return BigDecimal.ZERO;
        if (isDeviceMissing(curDyp) || isDeviceMissing(prevDyp)) return BigDecimal.ZERO;
        return curDyp.compareTo(prevDyp) > 0 ? curDyp.subtract(prevDyp) : BigDecimal.ZERO;
    }

    /** 判断行是否属于水库站点（STCD 匹配 或 名称匹配） */
    private boolean isReservoirStation(Map<String, Object> row) {
        String stcd = (String) row.get("stcd");
        if (stcd != null && RESERVOIR_STCD_NEW.contains(stcd)) {
            return true;
        }
        String stnm = (String) row.get("stnm");
        if (stnm != null && RESERVOIR_STATION_NAMES.contains(stnm)) {
            return true;
        }
        return false;
    }

    /**
     * 灌区雨情接口站点可见性：
     * 未指定 stcd（全量查询）时排除水库 13 站，保持灌区口径；
     * 显式指定 stcd 时放行该站（含水库站），支持按站点编号单独查询库上站点数据。
     */
    private boolean gqStationVisible(Map<String, Object> row, String requestedStcd) {
        if (requestedStcd != null && !requestedStcd.trim().isEmpty()) {
            return true;
        }
        return !isReservoirStation(row);
    }

    private IPage<GqRainfallVO> toPage(List<GqRainfallVO> list, long page, long size) {
        long total = list.size();
        int start = (int) ((page - 1) * size);
        int end = (int) Math.min(start + size, total);
        Page<GqRainfallVO> result = new Page<>(page, size);
        result.setTotal(total);
        result.setRecords(start >= total ? Collections.emptyList() : list.subList(start, end));
        return result;
    }

    @Override
    public GqRainfallChartVO gqRainfallChart(String stcd, LocalDateTime startTime, LocalDateTime endTime) {
        // 1. 查询站点名称
        StStinfo stinfo = stStinfoMapper.selectById(stcd);

        // 2. 序列起点对齐查询起始日当天 8:00（早8点开始，而非0点）
        LocalDateTime hourStart = startTime.toLocalDate().atTime(8, 0);
        // 扩展查询范围（向前2h，确保首小时有基线数据可对比）
        LocalDateTime queryStart = hourStart.minusHours(2);

        // 3. 查询原始记录
        List<StPptnR> records = baseMapper.selectByStcdAndTimeRange(stcd, queryStart, endTime);

        // 4. 按小时汇总增量（hydro-monitor 规则：第一条 inc=0，后续 inc=max(0, cur-prev)）
        //    LinkedHashMap 保持时间顺序
        Map<String, BigDecimal> hourRainfall = new LinkedHashMap<>();
        if (!records.isEmpty()) {
            for (int i = 0; i < records.size(); i++) {
                StPptnR cur = records.get(i);
                BigDecimal inc;
                if (i == 0) {
                    inc = BigDecimal.ZERO; // 第一条记录无法确定增量，跳过
                } else {
                    StPptnR prev = records.get(i - 1);
                    BigDecimal curDyp = cur.getDyp() != null ? cur.getDyp() : BigDecimal.ZERO;
                    BigDecimal prevDyp = prev.getDyp() != null ? prev.getDyp() : BigDecimal.ZERO;
                    inc = safeDypIncrement(curDyp, prevDyp);
                }
                // 归属到小时桶
                String hourKey = cur.getTm().truncatedTo(ChronoUnit.HOURS)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"));
                hourRainfall.merge(hourKey, inc, BigDecimal::add);
            }
        }

        // 5. 生成完整小时序列 + 累计值（起点为水文日 8:00）
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        List<GqRainfallChartVO.HourPoint> hours = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        LocalDateTime hour = hourStart;
        LocalDateTime endHour = endTime.truncatedTo(ChronoUnit.HOURS);

        while (!hour.isAfter(endHour)) {
            String key = hour.format(fmt);
            BigDecimal rainfall = hourRainfall.getOrDefault(key, BigDecimal.ZERO);
            cumulative = cumulative.add(rainfall);

            GqRainfallChartVO.HourPoint point = new GqRainfallChartVO.HourPoint();
            point.setHour(key);
            point.setRainfall(rainfall);
            point.setCumulative(cumulative);
            hours.add(point);

            hour = hour.plusHours(1);
        }

        // 6. 组装结果
        GqRainfallChartVO vo = new GqRainfallChartVO();
        vo.setStcd(stcd);
        vo.setStnm(stinfo != null ? stinfo.getStnm() : null);
        vo.setStartTime(startTime);
        vo.setEndTime(endTime);
        vo.setHours(hours);
        return vo;
    }

    @Override
    public ReservoirRainfallVO reservoirRainfall(LocalDate startDate, LocalDate endDate) {
        // 1. 通过名称反查站点信息
        Map<String, StStinfo> resolved = resolveReservoirStcds();
        List<String> reservoirStcds = new ArrayList<>(resolved.keySet());

        // 2. 扩展查询范围（向前后各 1 天，确保水文日边界完整）
        //    水文日：8:00 ~ 次日 7:59:59
        LocalDateTime queryStart = startDate.atTime(8, 0).minusDays(1);
        LocalDateTime queryEnd = endDate.atTime(7, 59, 59).plusDays(1);

        // 3. 批量查询原始雨量记录（含 DRP 和 DYP）
        List<StPptnR> records = reservoirStcds.isEmpty()
                ? Collections.emptyList()
                : baseMapper.selectByStcdsAndTimeRange(reservoirStcds, queryStart, queryEnd);

        // 4. 按站点 + 水文日聚合增量（对齐 hydro-monitor.html buildPptnPivot 逻辑）
        //    Map<水文日标签, Map<stcd, 累加增量>>
        Map<String, Map<String, BigDecimal>> bucketMap = aggregateHydroDay(resolved, records);

        // 5. 生成完整的水文日序列
        List<String> allBuckets = new ArrayList<>();
        DateTimeFormatter bucketFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate d = startDate;
        while (!d.isAfter(endDate)) {
            allBuckets.add(d.format(bucketFmt) + " 08:00:00");
            d = d.plusDays(1);
        }

        // 6. 组装站点信息（实时雨情视角：当前降雨量 = 最新观测所在水文日的 DYP 累计增量）
        List<ReservoirRainfallVO.StationInfo> stations = new ArrayList<>();
        Map<String, BigDecimal> latestRain = latestHydroDayRain(resolved.keySet(), records);
        Map<String, StPptnR> latestPerStcd = new HashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            StPptnR existing = latestPerStcd.get(key);
            if (existing == null || r.getTm().isAfter(existing.getTm())) {
                latestPerStcd.put(key, r);
            }
        }
        for (Map.Entry<String, StStinfo> entry : resolved.entrySet()) {
            String stcd = entry.getKey();
            StStinfo info = entry.getValue();
            ReservoirRainfallVO.StationInfo si = new ReservoirRainfallVO.StationInfo();
            si.setStcd(stcd);
            si.setStnm(info.getStnm());
            si.setId(info.getId());
            StPptnR latest = latestPerStcd.get(stcd);
            if (latest != null) {
                si.setLatestTm(latest.getTm());
                si.setLatestDrp(latestRain.get(stcd));
            } else {
                // 无数据时返回当前时间，表示"截至此刻无测量值"，便于前端区分"无数据"与"接口异常"
                si.setLatestTm(LocalDateTime.now());
            }
            stations.add(si);
        }

        // 7. 组装逐日数据（日雨情视角）
        List<ReservoirRainfallVO.DayRainfall> days = new ArrayList<>();
        for (String bucket : allBuckets) {
            ReservoirRainfallVO.DayRainfall dr = new ReservoirRainfallVO.DayRainfall();
            dr.setDay(bucket);
            Map<String, BigDecimal> values = bucketMap.getOrDefault(bucket, Collections.emptyMap());
            // 补充缺失站点的 0 值，同时计算平均值；雨量统一 2 位小数（截断补零，业主口径）
            Map<String, BigDecimal> fullValues = new LinkedHashMap<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (Map.Entry<String, StStinfo> entry : resolved.entrySet()) {
                String stcd = entry.getKey();
                BigDecimal val = values.getOrDefault(stcd, BigDecimal.ZERO).setScale(2, BigDecimal.ROUND_DOWN);
                fullValues.put(stcd, val);
                sum = sum.add(val);
            }
            dr.setValues(fullValues);
            dr.setAvg(resolved.isEmpty() ? BigDecimal.ZERO
                    : sum.divide(new BigDecimal(resolved.size()), 2, BigDecimal.ROUND_HALF_EVEN)); // 平均值保留两位小数、银行家舍入（老系统口径：0.625→0.62、0.1667→0.17）
            days.add(dr);
        }

        // 8. 组装结果（双视角：stations=实时 + days=日雨情）
        ReservoirRainfallVO vo = new ReservoirRainfallVO();
        vo.setStations(stations);
        vo.setDays(days);
        return vo;
    }

    /**
     * 按站点 + 水文日聚合 DYP 正向增量（水库/灌区日雨情共用口径）
     * <p>返回 Map<水文日标签, Map<stcd, 累加增量>>；站点集合以 resolved 为准，未收录的 STCD 记录忽略。
     * 首条记录增量无法确定记为 0，后续取 max(0, cur - prev)。
     */
    private Map<String, Map<String, BigDecimal>> aggregateHydroDay(Map<String, StStinfo> resolved, List<StPptnR> records) {
        Map<String, Map<String, BigDecimal>> bucketMap = new LinkedHashMap<>();

        // 按站点分组
        Map<String, List<StPptnR>> byStation = new LinkedHashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            if (!resolved.containsKey(key)) continue;
            byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<String, List<StPptnR>> entry : byStation.entrySet()) {
            String stcd = entry.getKey();
            List<StPptnR> stationRows = entry.getValue();
            // 按时间升序
            stationRows.sort(Comparator.comparing(StPptnR::getTm));
            for (int i = 0; i < stationRows.size(); i++) {
                StPptnR cur = stationRows.get(i);
                BigDecimal inc;
                if (i == 0) {
                    inc = BigDecimal.ZERO; // 第一条记录无法确定增量
                } else {
                    StPptnR prev = stationRows.get(i - 1);
                    BigDecimal curDyp = cur.getDyp() != null ? cur.getDyp() : BigDecimal.ZERO;
                    BigDecimal prevDyp = prev.getDyp() != null ? prev.getDyp() : BigDecimal.ZERO;
                    inc = safeDypIncrement(curDyp, prevDyp);
                }
                // 归属到水文日桶
                String bucket = getHydroDayLabel(cur.getTm());
                bucketMap.computeIfAbsent(bucket, k -> new LinkedHashMap<>())
                        .merge(stcd, inc, BigDecimal::add);
            }
        }
        return bucketMap;
    }

    @Override
    public GqDailyRainfallVO gqDailyRainfall(LocalDate startDate, LocalDate endDate) {
        // 1. 非水库雨量站点（STCD 匹配 + 名称匹配双重排除）
        Map<String, StStinfo> resolved = resolveGqStcds();
        List<String> gqStcds = new ArrayList<>(resolved.keySet());

        // 2. 扩展查询范围（向前后各 1 天，确保水文日边界完整）
        //    水文日：8:00 ~ 次日 7:59:59
        LocalDateTime queryStart = startDate.atTime(8, 0).minusDays(1);
        LocalDateTime queryEnd = endDate.atTime(7, 59, 59).plusDays(1);

        // 3. 批量查询原始雨量记录（含 DRP 和 DYP）
        List<StPptnR> records = gqStcds.isEmpty()
                ? Collections.emptyList()
                : baseMapper.selectByStcdsAndTimeRange(gqStcds, queryStart, queryEnd);

        // 4. 水文日聚合（与水库日雨情同口径）
        Map<String, Map<String, BigDecimal>> bucketMap = aggregateHydroDay(resolved, records);

        // 5. 生成完整的水文日序列
        List<String> allBuckets = new ArrayList<>();
        DateTimeFormatter bucketFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate d = startDate;
        while (!d.isAfter(endDate)) {
            allBuckets.add(d.format(bucketFmt) + " 08:00:00");
            d = d.plusDays(1);
        }

        // 6. 组装站点信息（实时快照：最新观测时间 + 所在水文日累计雨量）
        List<ReservoirRainfallVO.StationInfo> stations = buildStationInfos(resolved, records);
        // 雨量数值统一 2 位小数（截断补零，业主口径）
        for (ReservoirRainfallVO.StationInfo si : stations) {
            if (si.getLatestDrp() != null) {
                si.setLatestDrp(si.getLatestDrp().setScale(2, BigDecimal.ROUND_DOWN));
            }
        }

        // 7. 组装逐日数据（日雨情视角）
        List<ReservoirRainfallVO.DayRainfall> days = new ArrayList<>();
        for (String bucket : allBuckets) {
            ReservoirRainfallVO.DayRainfall dr = new ReservoirRainfallVO.DayRainfall();
            dr.setDay(bucket);
            Map<String, BigDecimal> values = bucketMap.getOrDefault(bucket, Collections.emptyMap());
            // 补充缺失站点的 0 值，同时计算平均值；雨量统一 2 位小数（截断补零，业主口径）
            Map<String, BigDecimal> fullValues = new LinkedHashMap<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (Map.Entry<String, StStinfo> entry : resolved.entrySet()) {
                String stcd = entry.getKey();
                BigDecimal val = values.getOrDefault(stcd, BigDecimal.ZERO).setScale(2, BigDecimal.ROUND_DOWN);
                fullValues.put(stcd, val);
                sum = sum.add(val);
            }
            dr.setValues(fullValues);
            dr.setAvg(resolved.isEmpty() ? BigDecimal.ZERO
                    : sum.divide(new BigDecimal(resolved.size()), 2, BigDecimal.ROUND_HALF_EVEN)); // 平均值保留两位小数、银行家舍入（老系统口径：0.625→0.62、0.1667→0.17）
            days.add(dr);
        }

        // 8. 组装结果（双视角：stations=实时 + days=日雨情）
        GqDailyRainfallVO vo = new GqDailyRainfallVO();
        vo.setStations(stations);
        vo.setDays(days);
        return vo;
    }

    /**
     * 非水库雨量站点集合：雨量表 distinct STCD → 排除水库 STCD → 关联站点表排除水库名称
     * → 仅保留监测类型含雨量（#2#）的站点（epjutj 多类型以 | 分隔，如 #1#|#2#）。
     * 返回 LinkedHashMap 保证迭代顺序稳定。
     */
    private Map<String, StStinfo> resolveGqStcds() {
        List<String> allStcds = baseMapper.selectDistinctRainfallStcds();
        Map<String, StStinfo> map = new LinkedHashMap<>();
        for (String stcd : allStcds) {
            if (stcd == null) continue;
            String s = stcd.trim();
            if (s.isEmpty() || RESERVOIR_STCD_NEW.contains(s)) continue;
            StStinfo info = stStinfoMapper.selectById(s);
            if (info == null) continue;
            if (info.getStnm() != null && RESERVOIR_STATION_NAMES.contains(info.getStnm())) continue;
            if (info.getEpjutj() == null || !info.getEpjutj().contains("#2#")) continue;
            map.put(s, info);
        }
        return map;
    }

    /**
     * 灌区雨量站点：非水库站点（排除水库 13 站），用于灌区站点下拉
     */
    @Override
    public List<StationSiteVO> gqRainfallSites() {
        List<StationSiteVO> sites = new ArrayList<>();
        for (Map.Entry<String, StStinfo> entry : resolveGqStcds().entrySet()) {
            StationSiteVO s = new StationSiteVO();
            s.setCode(entry.getKey());
            s.setName(entry.getValue().getStnm());
            sites.add(s);
        }
        return sites;
    }

    /**
     * 水文日标签：标签 D 的水文日区间为 (D-1日 08:00:00, D日 08:00:00]（左开右闭）
     * <p>8 点整（08:00:00）属于当日标签（区间右端点）；08:00:01 起才归入次日标签。
     * 实现：先把时刻回退 1 秒，再按 8 点左闭右开切分。
     */
    private String getHydroDayLabel(LocalDateTime tm) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDateTime t = tm.minusSeconds(1);
        if (t.getHour() >= 8) {
            return t.toLocalDate().plusDays(1).format(fmt) + " 08:00:00";
        }
        return t.toLocalDate().format(fmt) + " 08:00:00";
    }

    /**
     * 为每站填充昨日雨量 dailyDyp（分页前调用，批量查询一次）
     */
    private void fillDailyDyp(List<GqRainfallVO> vos) {
        if (vos == null || vos.isEmpty()) return;
        List<String> stcds = vos.stream()
                .map(GqRainfallVO::getStcd)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        Map<String, BigDecimal> sums = yesterdayHydroDayRain(stcds);
        for (GqRainfallVO vo : vos) {
            vo.setDailyDyp(sums.getOrDefault(vo.getStcd(), BigDecimal.ZERO));
        }
    }

    /**
     * 昨日雨量：最近一个完整水文日的累计雨量（DYP 正向增量之和）
     * <p>口径：当前时刻 < 8 点 → 前日 08:00 ~ 昨日 08:00；当前时刻 >= 8 点 → 昨日 08:00 ~ 今日 08:00。
     * 等价于"当前所属水文日标签的前一天标签"对应区间。
     * 增量归属与日雨情聚合口径一致（时间升序、首条 inc=0、后续 max(0, cur-prev)、按记录所属水文日标签归属）。
     */
    private Map<String, BigDecimal> yesterdayHydroDayRain(List<String> stcds) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (stcds == null || stcds.isEmpty()) return result;
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // 昨日水文日区间右端点（标签时刻）：当前所属水文日标签 - 1 天
        String curLabel = getHydroDayLabel(LocalDateTime.now());
        LocalDateTime yEnd = LocalDateTime.parse(curLabel, labelFmt).minusDays(1);
        String yLabel = getHydroDayLabel(yEnd);
        // 查询范围前后各扩 1 天，确保首条记录有基线可对比
        List<StPptnR> records = baseMapper.selectByStcdsAndTimeRange(
                stcds, yEnd.minusDays(2), yEnd.plusDays(1));
        Map<String, List<StPptnR>> byStation = new LinkedHashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<String, List<StPptnR>> entry : byStation.entrySet()) {
            List<StPptnR> rows = entry.getValue();
            rows.sort(Comparator.comparing(StPptnR::getTm));
            BigDecimal sum = null;
            for (int i = 0; i < rows.size(); i++) {
                StPptnR cur = rows.get(i);
                BigDecimal inc;
                if (i == 0) {
                    inc = BigDecimal.ZERO;
                } else {
                    BigDecimal curDyp = cur.getDyp() != null ? cur.getDyp() : BigDecimal.ZERO;
                    BigDecimal prevDyp = rows.get(i - 1).getDyp() != null ? rows.get(i - 1).getDyp() : BigDecimal.ZERO;
                    inc = safeDypIncrement(curDyp, prevDyp);
                }
                if (yLabel.equals(getHydroDayLabel(cur.getTm()))) {
                    sum = (sum == null ? BigDecimal.ZERO : sum).add(inc);
                }
            }
            if (sum != null) {
                result.put(entry.getKey(), sum);
            }
        }
        return result;
    }

    /**
     * 各站点最新观测所在水文日的累计降雨量（DYP 正向增量之和）
     * <p>花凉亭雨量报文 DRP 恒为 0，仅 DYP 有值且持续累加，“当前降雨量”必须用 DYP 增量表示。
     */
    private Map<String, BigDecimal> latestHydroDayRain(Set<String> validStcds, List<StPptnR> records) {
        Map<String, BigDecimal> result = new HashMap<>();
        Map<String, List<StPptnR>> byStation = new LinkedHashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            if (!validStcds.contains(key)) continue;
            byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<String, List<StPptnR>> entry : byStation.entrySet()) {
            List<StPptnR> rows = entry.getValue();
            rows.sort(Comparator.comparing(StPptnR::getTm));
            StPptnR latest = rows.get(rows.size() - 1);
            String label = getHydroDayLabel(latest.getTm());
            BigDecimal sum = null;
            for (int i = 0; i < rows.size(); i++) {
                StPptnR cur = rows.get(i);
                if (!label.equals(getHydroDayLabel(cur.getTm()))) continue;
                BigDecimal inc = BigDecimal.ZERO;
                if (i > 0) {
                    BigDecimal curDyp = cur.getDyp() != null ? cur.getDyp() : BigDecimal.ZERO;
                    BigDecimal prevDyp = rows.get(i - 1).getDyp() != null ? rows.get(i - 1).getDyp() : BigDecimal.ZERO;
                    inc = safeDypIncrement(curDyp, prevDyp);
                }
                sum = (sum == null ? BigDecimal.ZERO : sum).add(inc);
            }
            if (sum != null) {
                result.put(entry.getKey(), sum);
            }
        }
        return result;
    }

    @Override
    public Map<String, BigDecimal> currentHydroDayRainfall() {
        List<String> stcds = baseMapper.selectDistinctRainfallStcds();
        if (stcds == null || stcds.isEmpty()) return Collections.emptyMap();
        Set<String> valid = new HashSet<>();
        for (String s : stcds) {
            if (s != null) valid.add(s.trim());
        }
        if (valid.isEmpty()) return Collections.emptyMap();
        // 查近 4 天数据，覆盖各站最新观测所在水文日的完整增量（含基线记录）
        LocalDateTime now = LocalDateTime.now();
        List<StPptnR> records = baseMapper.selectByStcdsAndTimeRange(
                new ArrayList<>(valid), now.minusDays(4), now);
        return latestHydroDayRain(valid, records);
    }

    @Override
    public ReservoirPeriodRainfallVO reservoirPeriodRainfall(LocalDate startDate, LocalDate endDate, int intervalMinutes) {
        // 1. 通过名称反查站点信息
        Map<String, StStinfo> resolved = resolveReservoirStcds();
        List<String> reservoirStcds = new ArrayList<>(resolved.keySet());

        // 2. 扩展查询范围（向前后各 1 天，确保时段边界完整）
        LocalDateTime queryStart = startDate.atTime(8, 0).minusDays(1);
        LocalDateTime queryEnd = endDate.atTime(7, 59, 59).plusDays(1);

        // 3. 批量查询原始雨量记录（含 DRP 和 DYP）
        List<StPptnR> records = reservoirStcds.isEmpty()
                ? Collections.emptyList()
                : baseMapper.selectByStcdsAndTimeRange(reservoirStcds, queryStart, queryEnd);

        // 4. 按站点 + 时间间隔聚合增量（对齐 hydro-monitor.html buildPeriodPivot 逻辑）
        //    Map<时段标签, Map<stcd, 累加增量>>
        Map<String, Map<String, BigDecimal>> bucketMap = new LinkedHashMap<>();

        Map<String, List<StPptnR>> byStation = new LinkedHashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            if (!resolved.containsKey(key)) continue;
            byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<String, List<StPptnR>> entry : byStation.entrySet()) {
            String stcd = entry.getKey();
            List<StPptnR> stationRows = entry.getValue();
            stationRows.sort(Comparator.comparing(StPptnR::getTm));
            for (int i = 0; i < stationRows.size(); i++) {
                StPptnR cur = stationRows.get(i);
                BigDecimal inc;
                if (i == 0) {
                    inc = BigDecimal.ZERO;
                } else {
                    StPptnR prev = stationRows.get(i - 1);
                    BigDecimal curDyp = cur.getDyp() != null ? cur.getDyp() : BigDecimal.ZERO;
                    BigDecimal prevDyp = prev.getDyp() != null ? prev.getDyp() : BigDecimal.ZERO;
                    inc = safeDypIncrement(curDyp, prevDyp);
                }
                // ceilToIntervalEnd：时段终点标注（左开右闭），11:06 的增量归 "12:00" 桶（区间 (11:00, 12:00]），
                // 与时段雨量报表惯例一致；桶内记录同属一个水文日标签，时段合计与日雨情一致
                String bucket = ceilToIntervalEnd(cur.getTm(), intervalMinutes);
                bucketMap.computeIfAbsent(bucket, k -> new LinkedHashMap<>())
                        .merge(stcd, inc, BigDecimal::add);
            }
        }

        // 5. 生成完整时段序列（桶标签为时段终点，对齐时段雨量报表口径）
        //    覆盖区间 (startDate-1 08:00, endDate 08:00]，桶标签从 (startDate-1) 09:00 到 endDate 08:00
        List<String> allBuckets = new ArrayList<>();
        LocalDateTime bucketStart = startDate.minusDays(1).atTime(8, 0);
        LocalDateTime bucketEnd = endDate.atTime(8, 0);
        DateTimeFormatter bucketFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime t = bucketStart;
        while (t.isBefore(bucketEnd)) {
            allBuckets.add(t.plusMinutes(intervalMinutes).format(bucketFmt));
            t = t.plusMinutes(intervalMinutes);
        }

        // 6. 组装站点信息（当前降雨量 = 最新观测所在水文日的 DYP 累计增量）
        List<ReservoirRainfallVO.StationInfo> stations = new ArrayList<>();
        Map<String, BigDecimal> latestRain = latestHydroDayRain(resolved.keySet(), records);
        Map<String, StPptnR> latestPerStcd = new HashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            StPptnR existing = latestPerStcd.get(key);
            if (existing == null || r.getTm().isAfter(existing.getTm())) {
                latestPerStcd.put(key, r);
            }
        }
        for (Map.Entry<String, StStinfo> entry : resolved.entrySet()) {
            String stcd = entry.getKey();
            StStinfo info = entry.getValue();
            ReservoirRainfallVO.StationInfo si = new ReservoirRainfallVO.StationInfo();
            si.setStcd(stcd);
            si.setStnm(info.getStnm());
            si.setId(info.getId());
            StPptnR latest = latestPerStcd.get(stcd);
            if (latest != null) {
                si.setLatestTm(latest.getTm());
                si.setLatestDrp(latestRain.get(stcd));
            }
            stations.add(si);
        }

        // 7. 组装时段数据（含平均值）
        List<ReservoirPeriodRainfallVO.BucketRow> buckets = new ArrayList<>();
        for (String bucketKey : allBuckets) {
            ReservoirPeriodRainfallVO.BucketRow row = new ReservoirPeriodRainfallVO.BucketRow();
            row.setTime(bucketKey);

            Map<String, BigDecimal> rawValues = bucketMap.getOrDefault(bucketKey, Collections.emptyMap());
            Map<String, BigDecimal> fullValues = new LinkedHashMap<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (Map.Entry<String, StStinfo> entry : resolved.entrySet()) {
                String stcd = entry.getKey();
                BigDecimal val = rawValues.getOrDefault(stcd, BigDecimal.ZERO);
                fullValues.put(stcd, val);
                sum = sum.add(val);
            }
            row.setValues(fullValues);
            row.setAvg(resolved.isEmpty() ? BigDecimal.ZERO
                    : sum.divide(new BigDecimal(resolved.size()), 2, BigDecimal.ROUND_HALF_EVEN)); // 平均值保留两位小数、银行家舍入（老系统口径：0.625→0.62、0.1667→0.17）
            buckets.add(row);
        }

        // 8. 组装结果
        ReservoirPeriodRainfallVO vo = new ReservoirPeriodRainfallVO();
        vo.setStations(stations);
        vo.setBuckets(buckets);
        return vo;
    }

    /**
     * 时段桶标签（时段终点标注、左开右闭，与水文日标签口径一致）：
     * 记录 tm 归入区间 (T - interval, T]，桶标签为 T。
     * 例：11:06 的增量 → "12:00" 桶；08:00:00 整点 → "08:00" 桶（与 getHydroDayLabel 右端点归属一致）。
     * 每个时段桶内记录同属一个水文日标签，时段合计与日雨情一致。
     */
    private String ceilToIntervalEnd(LocalDateTime tm, int intervalMinutes) {
        LocalDateTime hydroStart = tm.toLocalDate().atTime(LocalTime.of(8, 0));
        long diffSeconds = ChronoUnit.SECONDS.between(hydroStart, tm);
        long ceilSlots = (long) Math.ceil(diffSeconds / (intervalMinutes * 60.0));
        LocalDateTime bucketEnd = hydroStart.plusMinutes(ceilSlots * intervalMinutes);
        return bucketEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    // ======================== 旬月雨情 ========================

    @Override
    public ReservoirTenDayRainfallVO reservoirTenDayRainfall(int year, int startMonth, int endMonth) {
        // 月份区间归一化（支持 08 月 ~ 08 月 单月或跨月区间）
        int m1 = Math.min(startMonth, endMonth);
        int m2 = Math.max(startMonth, endMonth);
        YearMonth firstMonth = YearMonth.of(year, m1);
        YearMonth lastMonth = YearMonth.of(year, m2);

        // 通过名称反查站点信息
        Map<String, StStinfo> resolved = resolveReservoirStcds();
        List<String> reservoirStcds = new ArrayList<>(resolved.keySet());

        // 扩展查询范围（水文日边界：首月前一天 08:00 ~ 末月最后一天次日 07:59:59）
        LocalDateTime queryStart = firstMonth.atDay(1).atTime(8, 0).minusDays(1);
        LocalDateTime queryEnd = lastMonth.atEndOfMonth().plusDays(1).atTime(7, 59, 59);

        List<StPptnR> records = reservoirStcds.isEmpty()
                ? Collections.emptyList()
                : baseMapper.selectByStcdsAndTimeRange(reservoirStcds, queryStart, queryEnd);

        // 按站点分组 → 计算日雨量
        Map<String, Map<String, BigDecimal>> dailyRainfall = new LinkedHashMap<>();
        Map<String, List<StPptnR>> byStation = new LinkedHashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            if (!resolved.containsKey(key)) continue;
            byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<String, List<StPptnR>> entry : byStation.entrySet()) {
            String stcd = entry.getKey();
            List<StPptnR> stationRows = entry.getValue();
            stationRows.sort(Comparator.comparing(StPptnR::getTm));
            for (int i = 0; i < stationRows.size(); i++) {
                StPptnR cur = stationRows.get(i);
                BigDecimal inc = BigDecimal.ZERO;
                if (i > 0) {
                    StPptnR prev = stationRows.get(i - 1);
                    BigDecimal curDyp = cur.getDyp() != null ? cur.getDyp() : BigDecimal.ZERO;
                    BigDecimal prevDyp = prev.getDyp() != null ? prev.getDyp() : BigDecimal.ZERO;
                    inc = safeDypIncrement(curDyp, prevDyp);
                }
                String dayKey = getHydroDayLabel(cur.getTm());
                dailyRainfall.computeIfAbsent(dayKey, k -> new LinkedHashMap<>())
                        .merge(stcd, inc, BigDecimal::add);
            }
        }

        // 逐月按旬聚合：上旬(1-10)、中旬(11-20)、下旬(21-月末)
        List<ReservoirTenDayRainfallVO.TenDayRow> periods = new ArrayList<>();
        DateTimeFormatter ymFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int month = m1; month <= m2; month++) {
            YearMonth ym = YearMonth.of(year, month);
            String yearMonth = ym.format(ymFmt);
            String[][] tenDays = {{"上旬", "1", "10"}, {"中旬", "11", "20"},
                    {"下旬", "21", String.valueOf(ym.lengthOfMonth())}};

            for (String[] td : tenDays) {
                String name = td[0];
                int dStart = Integer.parseInt(td[1]);
                int dEnd = Integer.parseInt(td[2]);

                Map<String, BigDecimal> tenDayValues = new LinkedHashMap<>();
                BigDecimal sum = BigDecimal.ZERO;
                for (Map.Entry<String, StStinfo> entry : resolved.entrySet()) {
                    String stcd = entry.getKey();
                    BigDecimal stcdSum = BigDecimal.ZERO;
                    for (int day = dStart; day <= dEnd; day++) {
                        String hydroKey = String.format("%s-%02d-%02d 08:00:00",
                                ym.getYear(), ym.getMonthValue(), day);
                        Map<String, BigDecimal> dayValues = dailyRainfall.get(hydroKey);
                        if (dayValues != null) {
                            stcdSum = stcdSum.add(dayValues.getOrDefault(stcd, BigDecimal.ZERO));
                        }
                    }
                    tenDayValues.put(stcd, stcdSum);
                    sum = sum.add(stcdSum);
                }

                ReservoirTenDayRainfallVO.TenDayRow row = new ReservoirTenDayRainfallVO.TenDayRow();
                row.setYearMonth(yearMonth);
                row.setTenDay(name);
                row.setValues(tenDayValues);
                row.setAvg(resolved.isEmpty() ? BigDecimal.ZERO
                        : sum.divide(new BigDecimal(resolved.size()), 2, BigDecimal.ROUND_HALF_EVEN)); // 平均值保留两位小数、银行家舍入（老系统口径：0.625→0.62、0.1667→0.17）
                periods.add(row);
            }
        }

        // 组装站点信息
        List<ReservoirRainfallVO.StationInfo> stations = buildStationInfos(resolved, records);

        ReservoirTenDayRainfallVO vo = new ReservoirTenDayRainfallVO();
        vo.setStations(stations);
        vo.setPeriods(periods);
        return vo;
    }

    // ======================== 极值雨情 ========================

    @Override
    public List<ReservoirExtremeRainfallVO> reservoirExtremeRainfall(LocalDate startDate, LocalDate endDate) {
        // 通过名称反查站点信息
        Map<String, StStinfo> resolved = resolveReservoirStcds();
        List<String> reservoirStcds = new ArrayList<>(resolved.keySet());

        // 扩展查询范围（水文日边界：前扩 9 天覆盖 max7d 窗口及其基线，后扩 1 天覆盖 endDate 08:00 整点记录）
        LocalDateTime queryStart = startDate.atTime(8, 0).minusDays(9);
        LocalDateTime queryEnd = endDate.plusDays(1).atTime(7, 59, 59);

        List<StPptnR> records = reservoirStcds.isEmpty()
                ? Collections.emptyList()
                : baseMapper.selectByStcdsAndTimeRange(reservoirStcds, queryStart, queryEnd);

        // 按站点分组
        Map<String, List<StPptnR>> byStation = new LinkedHashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            if (!resolved.containsKey(key)) continue;
            byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        List<ReservoirExtremeRainfallVO> result = new ArrayList<>();
        for (Map.Entry<String, StStinfo> entry : resolved.entrySet()) {
            String stcd = entry.getKey();
            StStinfo info = entry.getValue();
            List<StPptnR> stationRows = byStation.getOrDefault(stcd, Collections.emptyList());
            stationRows.sort(Comparator.comparing(StPptnR::getTm));

            // 计算小时增量序列（水文日对齐：小时桶用 ceilToIntervalEnd 时段终点标注，
            // 8:00:00 整点增量归 08:00 桶，与时段雨情口径一致；
            // 日雨量直接按水文日标签聚合，与日雨情口径一致）
            Map<String, BigDecimal> hourlyInc = new LinkedHashMap<>();
            Map<String, BigDecimal> dailyInc = new LinkedHashMap<>();
            for (int i = 0; i < stationRows.size(); i++) {
                StPptnR cur = stationRows.get(i);
                BigDecimal inc = BigDecimal.ZERO;
                if (i > 0) {
                    StPptnR prev = stationRows.get(i - 1);
                    BigDecimal curDyp = cur.getDyp() != null ? cur.getDyp() : BigDecimal.ZERO;
                    BigDecimal prevDyp = prev.getDyp() != null ? prev.getDyp() : BigDecimal.ZERO;
                    inc = safeDypIncrement(curDyp, prevDyp);
                }
                hourlyInc.merge(ceilToIntervalEnd(cur.getTm(), 60), inc, BigDecimal::add);
                dailyInc.merge(getHydroDayLabel(cur.getTm()), inc, BigDecimal::add);
            }

            // 生成完整小时序列
            List<Map.Entry<String, BigDecimal>> hourList = new ArrayList<>(hourlyInc.entrySet());
            hourList.sort(Map.Entry.comparingByKey());

            // 滑动窗口求极值（小时桶已水文日对齐，max24h 与水文日日雨量口径一致）
            BigDecimal max3h = slidingMax(hourList, 3);
            BigDecimal max6h = slidingMax(hourList, 6);
            BigDecimal max24h = slidingMax(hourList, 24);

            // 日雨量序列（水文日标签排序）
            List<Map.Entry<String, BigDecimal>> dayList = new ArrayList<>(dailyInc.entrySet());
            dayList.sort(Map.Entry.comparingByKey());

            BigDecimal max2d = slidingMax(dayList, 2);
            BigDecimal max3d = slidingMax(dayList, 3);
            BigDecimal max7d = slidingMax(dayList, 7);

            ReservoirExtremeRainfallVO vo = new ReservoirExtremeRainfallVO();
            vo.setStcd(stcd);
            vo.setStnm(info.getStnm());
            vo.setMax3h(max3h);
            vo.setMax6h(max6h);
            vo.setMax24h(max24h);
            vo.setMax2d(max2d);
            vo.setMax3d(max3d);
            vo.setMax7d(max7d);
            result.add(vo);
        }
        return result;
    }

    /** 滑动窗口最大值 */
    private BigDecimal slidingMax(List<Map.Entry<String, BigDecimal>> sortedList, int windowSize) {
        if (sortedList.isEmpty()) return BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        for (int i = 0; i < sortedList.size(); i++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = i; j < Math.min(i + windowSize, sortedList.size()); j++) {
                sum = sum.add(sortedList.get(j).getValue());
            }
            if (sum.compareTo(max) > 0) max = sum;
        }
        return max;
    }

    // ======================== 雨情简报 ========================

    @Override
    public List<ReservoirRainfallBriefVO> reservoirRainfallBrief(LocalDate date) {
        // 确定旬段
        int day = date.getDayOfMonth();
        String tenDayName;
        int dStart, dEnd;
        if (day <= 10) { tenDayName = "上旬"; dStart = 1; dEnd = 10; }
        else if (day <= 20) { tenDayName = "中旬"; dStart = 11; dEnd = 20; }
        else { tenDayName = "下旬"; dStart = 21; dEnd = date.lengthOfMonth(); }

        // 通过名称反查站点信息
        Map<String, StStinfo> resolved = resolveReservoirStcds();
        List<String> reservoirStcds = new ArrayList<>(resolved.keySet());

        // 查询范围：该月完整数据
        LocalDate monthStart = date.withDayOfMonth(1);
        LocalDate monthEnd = date.withDayOfMonth(date.lengthOfMonth());
        LocalDateTime queryStart = monthStart.atTime(8, 0).minusDays(1);
        LocalDateTime queryEnd = monthEnd.plusDays(1).atTime(7, 59, 59);

        List<StPptnR> records = reservoirStcds.isEmpty()
                ? Collections.emptyList()
                : baseMapper.selectByStcdsAndTimeRange(reservoirStcds, queryStart, queryEnd);

        // 按站点 → 按日聚合
        Map<String, Map<String, BigDecimal>> dailyByStation = new LinkedHashMap<>();
        Map<String, List<StPptnR>> byStation = new LinkedHashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            if (!resolved.containsKey(key)) continue;
            byStation.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<String, List<StPptnR>> entry : byStation.entrySet()) {
            String stcd = entry.getKey();
            List<StPptnR> stationRows = entry.getValue();
            stationRows.sort(Comparator.comparing(StPptnR::getTm));
            for (int i = 0; i < stationRows.size(); i++) {
                StPptnR cur = stationRows.get(i);
                BigDecimal inc = BigDecimal.ZERO;
                if (i > 0) {
                    StPptnR prev = stationRows.get(i - 1);
                    BigDecimal curDyp = cur.getDyp() != null ? cur.getDyp() : BigDecimal.ZERO;
                    BigDecimal prevDyp = prev.getDyp() != null ? prev.getDyp() : BigDecimal.ZERO;
                    inc = safeDypIncrement(curDyp, prevDyp);
                }
                String dayKey = getHydroDayLabel(cur.getTm());
                dailyByStation.computeIfAbsent(stcd, k -> new LinkedHashMap<>())
                        .merge(dayKey, inc, BigDecimal::add);
            }
        }

        // 组装结果
        String targetDayKey = date.toString() + " 08:00:00";
        List<ReservoirRainfallBriefVO> result = new ArrayList<>();
        for (Map.Entry<String, StStinfo> entry : resolved.entrySet()) {
            String stcd = entry.getKey();
            StStinfo info = entry.getValue();
            ReservoirRainfallBriefVO vo = new ReservoirRainfallBriefVO();
            vo.setStcd(stcd);
            vo.setStnm(info.getStnm());

            Map<String, BigDecimal> dayMap = dailyByStation.getOrDefault(stcd, Collections.emptyMap());

            // 日雨量
            vo.setDailyRainfall(dayMap.getOrDefault(targetDayKey, BigDecimal.ZERO));

            // 旬雨量
            BigDecimal tenDaySum = BigDecimal.ZERO;
            for (int d = dStart; d <= dEnd; d++) {
                String hydroKey = String.format("%s-%02d-%02d 08:00:00",
                        monthStart.getYear(), monthStart.getMonthValue(), d);
                tenDaySum = tenDaySum.add(dayMap.getOrDefault(hydroKey, BigDecimal.ZERO));
            }
            vo.setTenDayRainfall(tenDaySum);

            // 月雨量
            BigDecimal monthSum = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> e : dayMap.entrySet()) {
                monthSum = monthSum.add(e.getValue());
            }
            vo.setMonthlyRainfall(monthSum);

            result.add(vo);
        }
        return result;
    }

    /**
     * 通过水库雨量站点名称反查 station_info 表，获取真实 STCD 及站点信息。
     * <p>站点集合为 12 个水库雨量站（不含水情站花凉亭坝下），按上线顺序排列（RESERVOIR_RAIN_STATION_ORDER）。
     * 返回 LinkedHashMap 保证迭代顺序稳定（前端透视表列序依赖此顺序）。
     */
    private Map<String, StStinfo> resolveReservoirStcds() {
        QueryWrapper<StStinfo> wrapper = new QueryWrapper<>();
        wrapper.in("zzkaec", RESERVOIR_RAIN_STATION_ORDER);
        List<StStinfo> list = stStinfoMapper.selectList(wrapper);
        Map<String, StStinfo> byName = new HashMap<>();
        for (StStinfo s : list) {
            if (s.getStnm() != null) {
                byName.put(s.getStnm(), s);
            }
        }
        Map<String, StStinfo> map = new LinkedHashMap<>();
        for (String name : RESERVOIR_RAIN_STATION_ORDER) {
            StStinfo s = byName.get(name);
            if (s != null && s.getStcd() != null) {
                map.put(s.getStcd(), s);
            }
        }
        return map;
    }

    /** 提取公共站点信息组装 */
    private List<ReservoirRainfallVO.StationInfo> buildStationInfos(Map<String, StStinfo> resolved, List<StPptnR> records) {
        List<ReservoirRainfallVO.StationInfo> stations = new ArrayList<>();
        Map<String, BigDecimal> latestRain = latestHydroDayRain(resolved.keySet(), records);
        Map<String, StPptnR> latestPerStcd = new HashMap<>();
        for (StPptnR r : records) {
            String key = (r.getStcd() != null) ? r.getStcd().trim() : "";
            StPptnR existing = latestPerStcd.get(key);
            if (existing == null || r.getTm().isAfter(existing.getTm())) {
                latestPerStcd.put(key, r);
            }
        }
        for (Map.Entry<String, StStinfo> entry : resolved.entrySet()) {
            String stcd = entry.getKey();
            StStinfo info = entry.getValue();
            ReservoirRainfallVO.StationInfo si = new ReservoirRainfallVO.StationInfo();
            si.setStcd(stcd);
            si.setStnm(info.getStnm());
            si.setId(info.getId());
            StPptnR latest = latestPerStcd.get(stcd);
            if (latest != null) {
                si.setLatestTm(latest.getTm());
                si.setLatestDrp(latestRain.get(stcd));
            } else {
                si.setLatestTm(LocalDateTime.now());
            }
            stations.add(si);
        }
        return stations;
    }
}
