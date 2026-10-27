package com.qgyun.hltgq.hltgqsite.external.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.external.mapper.ExternalMapper;
import com.qgyun.hltgq.hltgqsite.external.vo.ExternalVO;
import com.qgyun.hltgq.hltgqsite.service.FlowMonitorService;
import com.qgyun.hltgq.hltgqsite.service.GateMonitorService;
import com.qgyun.hltgq.hltgqsite.service.IrrigationWaterLevelService;
import com.qgyun.hltgq.hltgqsite.service.SoilMoistureService;
import com.qgyun.hltgq.hltgqsite.service.StPptnRService;
import com.qgyun.hltgq.hltgqsite.service.StationSortService;
import com.qgyun.hltgq.hltgqsite.service.WaterQualityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ValueConvertUtils;

/**
 * 三维系统对接（/external）聚合逻辑。
 * <p>契约见 src/main/resources/三维系统对接接口.md。全部为实时查询（无异步），
 * 单次聚合 SQL + 内存整理，数据量小无需缓存。
 * <p>监测值清洗：-999（设备不存在）、-9991（设备异常）视为无数据 → null（契约：null=无数据）。
 */
@Service
public class ExternalService {

    /** 数据时间格式 */
    private static final DateTimeFormatter TM_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 无效监测值：-999 设备不存在、-9991 设备异常 */
    private static final double INVALID_LEVEL = -999.0;
    private static final double INVALID_LEVEL_1 = -9991.0;

    /** 逐日趋势区间上限（天） */
    private static final long MAX_TREND_DAYS = 366;

    /** 默认趋势天数（不传区间时取近 30 天） */
    private static final long DEFAULT_TREND_DAYS = 30;

    /** 监测站点列表全量上限（档案口径站点数量级为百以内，一次性返回不分页） */
    private static final int MAX_MONITOR_SITES = 1000;

    /** 监测行站点键字段（档案主键 id/siteId、站点编号 stcd/site；各监测表口径不一，取到即用） */
    private static final String[] STATION_KEYS = {"id", "siteId", "stcd", "site"};

    @Autowired
    private ExternalMapper mapper;

    @Autowired
    private StationSortService stationSortService;

    @Autowired
    private IrrigationWaterLevelService irrigationWaterLevelService;

    @Autowired
    private StPptnRService stPptnRService;

    @Autowired
    private FlowMonitorService flowMonitorService;

    @Autowired
    private GateMonitorService gateMonitorService;

    @Autowired
    private SoilMoistureService soilMoistureService;

    @Autowired
    private WaterQualityService waterQualityService;

    /** VO → Map 转换用（复用 Web 层 ObjectMapper，字段名/时间格式与业务接口 HTTP 输出一致） */
    @Autowired
    private ObjectMapper objectMapper;

    /** 渠首进水闸站点 ID = 档案表 id（不传 stcd 时的默认站，与页面闸门监测口径一致） */
    @Value("${external.intake-gate-site:CAYQ739MiBWMg9gQvyi}")
    private String intakeGateSite;

    /** 管理单位（固定：花凉亭灌区，配置可调） */
    @Value("${external.intake-gate-unit:花凉亭灌区}")
    private String intakeGateUnit;

    /** 闸门类型数量：固定 4 类（节制闸/分水口/退水闸/渡槽），数量为三方收集台账实数（2026-09-18 定稿） */
    public ExternalVO.GateTypeCount gateTypeCount() {
        ExternalVO.GateTypeCount vo = new ExternalVO.GateTypeCount();
        List<ExternalVO.GateTypeItem> items = new ArrayList<>(4);
        items.add(item("节制闸", 27));
        items.add(item("分水口", 36));
        items.add(item("退水闸", 10));
        items.add(item("渡槽", 6));
        vo.setItems(items);
        return vo;
    }

    private ExternalVO.GateTypeItem item(String type, int count) {
        ExternalVO.GateTypeItem item = new ExternalVO.GateTypeItem();
        item.setType(type);
        item.setCount(count);
        return item;
    }

    /**
     * 闸站实时数据：stcd 缺省/空时返回渠首进水闸（默认站 intakeGateSite，兼容原无参调用）；
     * 传 stcd 时按站点键查档案（stcd=iofhpi 或兼容 site UUID=id）取 site/站名，
     * 水位取 gate 表、流量取 wt_nfo 表（均按 site 关联），时间取两者较新；管理单位固定。
     * stcd 对应站点不存在抛 IllegalArgumentException → 全局 400。
     */
    public ExternalVO.IntakeGate intakeGate(String stcd) {
        String key = (stcd == null || stcd.trim().isEmpty()) ? intakeGateSite : stcd.trim();
        Map<String, Object> station = mapper.selectStationByKey(key);
        if (station == null) {
            throw new IllegalArgumentException("站点不存在: " + key);
        }
        String siteId = ValueConvertUtils.str(station.get("id"));

        ExternalVO.IntakeGate vo = new ExternalVO.IntakeGate();
        vo.setSite(siteId);
        vo.setStcd(ValueConvertUtils.str(station.get("iofhpi")));
        vo.setStnm(ValueConvertUtils.str(station.get("zzkaec")));
        vo.setManagementUnit(intakeGateUnit);

        Map<String, Object> gate = mapper.selectLatestGateLevel(siteId);
        LocalDateTime gateTm = null;
        if (gate != null) {
            gateTm = timeOf(gate.get("tm"));
            vo.setUpZ(cleanLevel(gate.get("up_z")));
            vo.setDownZ(cleanLevel(gate.get("down_z")));
        }

        Map<String, Object> flow = mapper.selectLatestFlow(siteId);
        LocalDateTime flowTm = null;
        if (flow != null) {
            flowTm = timeOf(flow.get("tm"));
            vo.setQ(truncate3(flow.get("q")));
        }

        LocalDateTime latest = laterOf(gateTm, flowTm);
        vo.setTm(latest == null ? null : TM_FORMAT.format(latest));
        return vo;
    }

    /** 巡检汇总：三项计数 + 维养预算/成本暂恒 0（数据源待确认） */
    public ExternalVO.PatrolSummary patrolSummary() {
        Map<String, Object> row = mapper.selectPatrolSummary();
        ExternalVO.PatrolSummary vo = new ExternalVO.PatrolSummary();
        vo.setPatrolCount(longOf(row.get("patrol_count")));
        vo.setScheduleCount(longOf(row.get("schedule_count")));
        vo.setFinishedCount(longOf(row.get("finished_count")));
        vo.setMaintenanceBudget(0);
        vo.setMaintenanceCost(0);
        return vo;
    }

    /** 巡检/突发事件逐日趋势：区间默认近 30 天，上限 366 天，逐日补 0 */
    public ExternalVO.DailyTrend dailyTrend(LocalDate startDate, LocalDate endDate) {
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDate start = startDate == null ? end.minusDays(DEFAULT_TREND_DAYS - 1) : startDate;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("startDate 晚于 endDate: " + start + " > " + end);
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_TREND_DAYS) {
            throw new IllegalArgumentException("区间超过 " + MAX_TREND_DAYS + " 天上限: " + days);
        }

        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.atTime(LocalTime.MAX);
        Map<String, Long> patrolMap = toDayCountMap(mapper.selectDailyPatrol(startTime, endTime));
        Map<String, Long> emergencyMap = toDayCountMap(mapper.selectDailyEmergency(startTime, endTime));

        ExternalVO.DailyTrend vo = new ExternalVO.DailyTrend();
        List<String> dates = new ArrayList<>();
        List<Long> patrol = new ArrayList<>();
        List<Long> emergency = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.toString();
            dates.add(key);
            patrol.add(patrolMap.getOrDefault(key, 0L));
            emergency.add(emergencyMap.getOrDefault(key, 0L));
        }
        vo.setDates(dates);
        vo.setPatrol(patrol);
        vo.setEmergency(emergency);
        return vo;
    }

    /**
     * 问题处理统计（问题表）+ 应急响应统计（告警表 type=#3# 智能分析）：
     * 已处理=已关闭、未整改=处理中+已转工单；突发事件=AI 告警总数、
     * 已解除响应=AI 告警已关闭数、未解除响应=AI 告警未关闭数。
     */
    public ExternalVO.IssueStats issueStats() {
        long handled = 0;
        long unrectified = 0;
        for (Map<String, Object> row : mapper.selectIssueStatus()) {
            String status = (String) row.get("name");
            long cnt = longOf(row.get("value"));
            if ("#4#".equals(status)) {
                handled += cnt;
            } else if ("#2#".equals(status) || "#3#".equals(status)) {
                unrectified += cnt;
            }
        }
        Map<String, Object> em = mapper.selectEmergencyStats();
        long emergencyTotal = longOf(em.get("total"));
        long resolved = longOf(em.get("closed"));
        ExternalVO.IssueStats vo = new ExternalVO.IssueStats();
        vo.setHandled(handled);
        vo.setUnrectified(unrectified);
        vo.setEmergencyTotal(emergencyTotal);
        vo.setResolved(resolved);
        vo.setUnresolved(emergencyTotal - resolved);
        return vo;
    }

    /** 视频按管理所聚合：总数/在线/离线 */
    public List<ExternalVO.VideoItem> videoSummary() {
        List<ExternalVO.VideoItem> list = new ArrayList<>();
        for (Map<String, Object> row : mapper.selectVideoSummary()) {
            ExternalVO.VideoItem item = new ExternalVO.VideoItem();
            String org = (String) row.get("org");
            item.setOrg(org == null || org.trim().isEmpty() ? "未知" : org);
            long total = longOf(row.get("total"));
            long online = longOf(row.get("online"));
            item.setTotal(total);
            item.setOnline(online);
            item.setOffline(total - online);
            list.add(item);
        }
        return list;
    }

    /**
     * 视频通道清单（设备级，全量）：一台视频设备一行，含通道号/名称/设备状态/管理所/
     * 安装位置/所属站点档案（主键/站名/编号/站点状态/站点经纬度/管理单位）/设备经纬度。
     * <p>siteName 非空时按站点名称模糊过滤（null/空白 = 全量，向后兼容）。
     * 口径与视频聚合接口一致：设备表 type 含 #5#；org = 安装位置首段（空归「未知」）；
     * isOnline = status #1#（status 原值同步返回）；站点状态取档案 zebpsu 原值（#1# 在线 / #2# 离线）；
     * 站点经纬度取所属站点档案 bviiio_x/y（未配置为 null）；
     * 设备经纬度取设备表 hkkwwa_x/y（预留坐标列、当前未登记为 null）。
     *
     * @param siteName 站点名称模糊词，null/空白表示不筛选
     */
    public List<ExternalVO.VideoDeviceItem> videoDevices(String siteName) {
        List<ExternalVO.VideoDeviceItem> list = new ArrayList<>();
        for (Map<String, Object> row : mapper.selectVideoDevices(CheckUtils.trim(siteName))) {
            ExternalVO.VideoDeviceItem item = new ExternalVO.VideoDeviceItem();
            item.setCode(ValueConvertUtils.str(row.get("code")));
            item.setName(ValueConvertUtils.str(row.get("name")));
            item.setIsOnline("#1#".equals(ValueConvertUtils.str(row.get("status"))));
            item.setStatus(ValueConvertUtils.str(row.get("status")));
            item.setLon(numberOrNull(row.get("lon")));
            item.setLat(numberOrNull(row.get("lat")));
            item.setDeviceLon(numberOrNull(row.get("device_lon")));
            item.setDeviceLat(numberOrNull(row.get("device_lat")));
            String org = ValueConvertUtils.str(row.get("org"));
            item.setOrg(org == null || org.trim().isEmpty() ? "未知" : org);
            item.setLocation(ValueConvertUtils.str(row.get("wlcvig")));
            item.setSiteId(ValueConvertUtils.str(row.get("site_id")));
            item.setSiteName(ValueConvertUtils.str(row.get("site_name")));
            item.setStcd(ValueConvertUtils.str(row.get("stcd")));
            item.setSiteStatus(ValueConvertUtils.str(row.get("site_status")));
            item.setManagementUnit(ValueConvertUtils.str(row.get("unit_name")));
            list.add(item);
        }
        return list;
    }

    /**
     * 监测站点列表（按监测类型合并）：type 与站点档案监测类型编码同口径
     * （1 水位 / 2 雨量 / 3 流量 / 4 闸门 / 5 视频 / 7 墒情 / 8 水质，另兼容 #N# 编码与语义串便于联调）。
     * <p>站点集合取站点档案（含当前无数据的站点，与原 qx-api 列表一致），name 非空时按站名模糊过滤
     * （先筛档案再查实时值，未命中站点时不触发实时值查询）；实时值按类型批量查一次（无逐站 N+1）；
     * 站点顺序按「站点排序」配置输出（已配置站点以配置序号为准，不按在线状态调整）；未配置排序的站点
     * 保持档案默认顺序、在线优先（在线/未设置在前、离线站在后，组内保持默认顺序）。
     * <p>isOnline 与 hasData 每行必有：有实时值站点按实时值字段输出；无实时值站点按档案状态兜底
     * （zebpsu 1=在线 / 2=离线 / 未设置按在线），保证人工设为离线的站点不会因字段缺失被误当作在线。
     * <p>coreIndicator（核心指标）每行必有，只有水位站与雨量站有：水位类型（#1#）取档案「核心指标-水位」列 ijzsby，
     * 雨量类型（#2#）取档案「核心指标-雨量」列 ccnhtm（两列分列存储，互不混用）；
     * 统一输出两位小数文本，档案未填按 0.00 输出；其余类型无核心指标固定为 null。
     * 行上同时保留 ccnhtm / ijzsby 两个原始列名（档案原值），供核对。
     * <p>视频类型（#5#）为站点档案清单、无实时监测值：不查各业务实时接口，行上 isOnline 按档案状态兜底；
     * 关联对象按原 qx-api 补齐 ahieto.zzkaec 与 ywvyds.gfaegg（仅视频类型输出，其余类型结构不变）。
     * <p>视频类型额外排序（仅 #5# 生效，其余类型排序不变）：在线优先 → 有站点编号 → 有经纬度
     * → 花凉亭水库站点（上游）在前 → 站点编号升序；缺编号/缺经纬度的站点整体靠后。
     * <p>非法 type 抛 IllegalArgumentException → 全局 400。
     *
     * @param type 监测类型：1/2/3/4/5/7/8（另兼容 #N# 编码与语义串）
     * @param name 站点名称模糊词，null/空白表示不筛选
     */
    public ExternalVO.StationMonitor stationMonitor(String type, String name) {
        String typeCode = monitorTypeCode(type);
        List<Map<String, Object>> archives = mapper.selectArchiveByType(typeCode, CheckUtils.trim(name));
        ExternalVO.StationMonitor vo = new ExternalVO.StationMonitor();
        if (archives.isEmpty()) {
            // 站名筛选无命中（或该类型下无档案站点）：不查实时值，直接返回空列表
            vo.setData(new ArrayList<>());
            return vo;
        }
        Map<String, Map<String, Object>> dataIndex = loadMonitorData(typeCode);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> archive : archives) {
            Map<String, Object> row = toArchiveRow(archive);
            // 核心指标：水位与雨量在档案里分列存储，按当前监测类型取各自列后统一字段名输出
            row.put("coreIndicator", coreIndicatorOf(archive, typeCode));
            if ("#5#".equals(typeCode)) {
                // 视频站点对齐原 qx-api：关联对象附带简称列（ahieto.zzkaec / ywvyds.gfaegg，与 dataTitle 同值）
                putRefExtra(row, "ahieto", "zzkaec", archive.get("ahieto_title"));
                putRefExtra(row, "ywvyds", "gfaegg", archive.get("ywvyds_title"));
            }
            // 实时值平铺（null 不覆盖档案列）+ data 子对象（原业务接口字段结构）+ hasData 标记
            Map<String, Object> data = matchMonitorData(dataIndex, row);
            if (data != null) {
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (entry.getValue() != null) {
                        row.put(entry.getKey(), entry.getValue());
                    }
                }
                // isOnline 每行必有：个别实时值来源缺该字段时按档案状态兜底（防御，正常不应触发）
                if (!row.containsKey("isOnline")) {
                    row.put("isOnline", !"2".equals(ValueConvertUtils.str(row.get("zebpsu"))));
                }
            } else {
                // 无实时值（hasData=false）的站点：isOnline 按档案状态兜底，1=在线 / 2=离线 / 未设置按在线
                // （isOnline 每行必有：字段缺失时前端无法与人工离线站区分；未设置按在线，与水位接口档案口径一致）
                row.put("isOnline", !"2".equals(ValueConvertUtils.str(row.get("zebpsu"))));
            }
            row.put("data", data);
            row.put("hasData", data != null);
            rows.add(row);
        }
        if ("#5#".equals(typeCode)) {
            // 视频站点专用排序：直接返回，不进入下方各监测类型的「站点排序配置 + 在线优先」分支，
            // 保证既有类型顺序完全不受影响（视频无实时值、无排序配置）
            sortVideoSites(rows);
            vo.setData(rows);
            return vo;
        }
        String sortType = monitorSortType(typeCode);
        List<Map<String, Object>> ordered = sortType == null ? rows
                : stationSortService.applyOrder(sortType, rows, item -> (String) item.get("id"));
        // 在线优先仅作用于未配置排序的站点（已配置站点以排序配置为准，不按在线状态调整）：
        // 未配置站点中在线（含未设置）在前、离线在后，组内保持默认顺序
        Set<String> configuredIds = sortType == null ? new HashSet<>()
                : new HashSet<>(stationSortService.configuredSiteIds(sortType));
        List<Map<String, Object>> result = new ArrayList<>(ordered.size());
        List<Map<String, Object>> unconfiguredOffline = new ArrayList<>();
        for (Map<String, Object> row : ordered) {
            if (configuredIds.contains(row.get("id"))) {
                result.add(row);
            } else if (Boolean.FALSE.equals(row.get("isOnline"))) {
                unconfiguredOffline.add(row);
            } else {
                result.add(row);
            }
        }
        result.addAll(unconfiguredOffline);
        vo.setData(result);
        return vo;
    }

    /**
     * 站点数量统计（大屏「站点数」卡片）：纯统计不返回明细，一条聚合 SQL。
     * <p>total = 档案全量（与 station-monitor 列表同源，不按类型过滤）；
     * typedTotal = 可进入 station-monitor 列表的站点并集（#1#/#2#/#3#/#4#/#5#/#7#/#8# 任一，按站去重），
     * 与「7 个类型分别调 station-monitor 后按 id 去重」完全一致；
     * #6#（模型类型，10 条，其中 7 条只带 #6#）不在 station-monitor 类型清单内，不计入 typedTotal，
     * 但仍出现在 byType 中供核对（byType 固定 1..8 八项，多类型站重复计）。
     * <p>传 type 时只统计该类型：total/typedTotal 均为该类型站数、byType 仅含该类型一项；
     * 非法 type 抛 IllegalArgumentException → 全局 400。
     *
     * @param type 监测类型，可选：1..8 / #N# / 语义串；null/空白 = 全量统计
     */
    public ExternalVO.StationCount stationCount(String type) {
        Map<String, Object> row = mapper.selectStationCount();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int i = 1; i <= 8; i++) {
            counts.put(String.valueOf(i), longOf(row.get("type_" + i)));
        }
        String typeKey = countTypeKey(type);
        ExternalVO.StationCountData data = new ExternalVO.StationCountData();
        if (typeKey == null) {
            data.setTotal(longOf(row.get("total")));
            data.setTypedTotal(longOf(row.get("typed_total")));
            data.setByType(counts);
        } else {
            long cnt = counts.get(typeKey);
            data.setTotal(cnt);
            data.setTypedTotal(cnt);
            Map<String, Long> single = new LinkedHashMap<>();
            single.put(typeKey, cnt);
            data.setByType(single);
        }
        ExternalVO.StationCount vo = new ExternalVO.StationCount();
        vo.setData(data);
        return vo;
    }

    /**
     * 站点下拉选项（轻量：id/名称/编号）：供告警筛选等下拉框一次拉取，前端无需按类型并发多次。
     * <p>站点集合与 {@link #stationCount(String)} 的 typedTotal 同口径（#1#/#2#/#3#/#4#/#5#/#7#/#8# 任一，
     * 按站去重）；传 type 时只返回该类型站点；非法 type 抛 IllegalArgumentException → 全局 400。
     *
     * @param type 监测类型，可选：1..8 / #N# / 语义串；null/空白 = 全类型并集
     */
    public ExternalVO.StationOptions stationOptions(String type) {
        String typeKey = countTypeKey(type);
        List<Map<String, Object>> rows = mapper.selectStationOptions(typeKey == null ? null : "#" + typeKey + "#");
        List<ExternalVO.StationOption> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            ExternalVO.StationOption item = new ExternalVO.StationOption();
            item.setId(ValueConvertUtils.str(row.get("id")));
            item.setName(ValueConvertUtils.str(row.get("name")));
            item.setStcd(ValueConvertUtils.str(row.get("stcd")));
            items.add(item);
        }
        ExternalVO.StationOptions vo = new ExternalVO.StationOptions();
        vo.setData(items);
        return vo;
    }

    /**
     * 统计入参 → byType 键（"1".."8"）；null/空白 = 不筛选；非法值 400。
     * <p>兼容数字、#N# 编码与 station-monitor 同套语义串；#6# 无语义串（模型类型，仅编码可用）。
     */
    private String countTypeKey(String type) {
        String key = type == null ? "" : type.trim();
        if (key.isEmpty()) {
            return null;
        }
        switch (key) {
            case "1":
            case "#1#":
            case "waterLevel":
                return "1";
            case "2":
            case "#2#":
            case "rainfall":
                return "2";
            case "3":
            case "#3#":
            case "flow":
                return "3";
            case "4":
            case "#4#":
            case "gate":
                return "4";
            case "5":
            case "#5#":
            case "video":
                return "5";
            case "6":
            case "#6#":
                return "6";
            case "7":
            case "#7#":
            case "moisture":
                return "7";
            case "8":
            case "#8#":
            case "waterQuality":
                return "8";
            default:
                throw new IllegalArgumentException("监测类型无效: " + type);
        }
    }

    /**
     * 视频站点排序（仅 type=5 使用，不影响其他监测类型）：调用方按固定顺序展示视频点位。
     * <p>键序（从左到右，全部为稳定排序，同键保持档案默认顺序 ORDER BY iofhpi）：
     * <ol>
     *   <li>在线优先：isOnline=true 在前（离线按档案 zebpsu 判定后回填的值）；</li>
     *   <li>有站点编号优先：iofhpi 为空的站点整体排在有编号站点之后；</li>
     *   <li>有经纬度优先：bviiio 为 null（经纬度均空）的站点整体排在有坐标站点之后；</li>
     *   <li>上下游：花凉亭水库站点（上游）整体在前、灌区站点（下游）在后
     *       （判定与灌区雨量排除同源：编号/站名双重识别）；</li>
     *   <li>站点编号升序：同组内按 iofhpi 升序（等长数字编号即数值升序，字母后缀编号保持稳定相对次序）。</li>
     * </ol>
     * 缺编号/缺经纬度的站点由第 2、3 键在进入序号/上下游比较前先分到同在线组的后面，
     * 即「没经纬度、没站点编号的排后面」。
     */
    private void sortVideoSites(List<Map<String, Object>> rows) {
        rows.sort((a, b) -> {
            int cmp = Boolean.compare(Boolean.TRUE.equals(b.get("isOnline")),
                    Boolean.TRUE.equals(a.get("isOnline")));
            if (cmp != 0) {
                return cmp;
            }
            String stcdA = trimOrNull(a.get("iofhpi"));
            String stcdB = trimOrNull(b.get("iofhpi"));
            if ((stcdA == null) != (stcdB == null)) {
                return stcdA == null ? 1 : -1;
            }
            boolean geoA = a.get("bviiio") != null;
            boolean geoB = b.get("bviiio") != null;
            if (geoA != geoB) {
                return geoA ? -1 : 1;
            }
            boolean reservoirA = isVideoReservoirSite(a);
            boolean reservoirB = isVideoReservoirSite(b);
            if (reservoirA != reservoirB) {
                return reservoirA ? -1 : 1;
            }
            if (stcdA != null) {
                cmp = stcdA.compareTo(stcdB);
                if (cmp != 0) {
                    return cmp;
                }
            }
            return 0;
        });
    }

    /** 视频站点是否花凉亭水库站点（上游）：与灌区雨量排除口径同源（编号/站名双重判定） */
    private boolean isVideoReservoirSite(Map<String, Object> row) {
        return stPptnRService.isReservoirSite(trimOrNull(row.get("iofhpi")), trimOrNull(row.get("zzkaec")));
    }

    /** 值 → 去首尾空白字符串；null/空白返回 null */
    private static String trimOrNull(Object value) {
        String text = ValueConvertUtils.str(value);
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 入参监测类型 → 站点档案监测类型编码（数字沿用原 qx-api/页面口径，兼容 #N# 编码与语义串） */
    private String monitorTypeCode(String type) {
        String key = type == null ? "" : type.trim();
        switch (key) {
            case "1":
            case "#1#":
            case "waterLevel":
                return "#1#";
            case "2":
            case "#2#":
            case "rainfall":
                return "#2#";
            case "3":
            case "#3#":
            case "flow":
                return "#3#";
            case "4":
            case "#4#":
            case "gate":
                return "#4#";
            case "5":
            case "#5#":
            case "video":
                return "#5#";
            case "7":
            case "#7#":
            case "moisture":
                return "#7#";
            case "8":
            case "#8#":
            case "waterQuality":
                return "#8#";
            default:
                throw new IllegalArgumentException("监测类型无效: " + type);
        }
    }

    /**
     * 站点档案监测类型编码 → 站点排序类型（站点排序表 metric_type 语义串）。
     * <p>两套编码不同需区分：档案 #7# 墒情对应排序表 #5#（语义串 moisture）；
     * 档案 #8# 水质在排序表中无该类型，返回 null 表示保持档案默认顺序；
     * 视频 #5# 无站点排序配置，同样返回 null（保持档案默认顺序 + 在线优先）。
     */
    private String monitorSortType(String typeCode) {
        switch (typeCode) {
            case "#1#":
                return "waterLevel";
            case "#2#":
                return "rainfall";
            case "#3#":
                return "flow";
            case "#4#":
                return "gate";
            case "#7#":
                return "moisture";
            default:
                return null;
        }
    }

    /**
     * 按类型批量查实时值并按站点键建索引：每类型只查一次（无逐站 N+1），
     * 复用各监测接口的服务方法（空参 = 全量最新值；站点排序由本接口统一在档案行上应用）。
     */
    private Map<String, Map<String, Object>> loadMonitorData(String typeCode) {
        List<?> rows;
        switch (typeCode) {
            case "#1#":
                rows = irrigationWaterLevelService.page(new Page<>(1, MAX_MONITOR_SITES), null, null, null).getRecords();
                break;
            case "#2#":
                rows = stPptnRService.gqRainfallMonitoringAll();
                break;
            case "#3#":
                rows = flowMonitorService.monitoring(null, null, null, null);
                break;
            case "#4#":
                rows = gateMonitorService.monitoring(null, null, null, null);
                break;
            case "#5#":
                // 视频类型是站点档案清单，无实时监测值：不查任何业务实时接口，返回空索引（行上 isOnline 按档案兜底）
                return new HashMap<>();
            case "#7#":
                rows = soilMoistureService.monitoring(null, null, null);
                break;
            default:
                rows = waterQualityService.monitoring(null, null, null);
                break;
        }
        Map<String, Map<String, Object>> index = new HashMap<>();
        for (Object row : rows) {
            indexMonitorData(index, toMap(row));
        }
        return index;
    }

    /** 实时值行按站点键建索引（先登记 id 后 stcd，档案行匹配时优先档案主键命中） */
    private void indexMonitorData(Map<String, Map<String, Object>> index, Map<String, Object> data) {
        for (String key : STATION_KEYS) {
            Object value = data.get(key);
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                index.putIfAbsent(String.valueOf(value).trim(), data);
            }
        }
    }

    /** 档案行匹配实时值：先按档案主键（id）匹配，再按站点编号（iofhpi）匹配，无数据返回 null */
    private Map<String, Object> matchMonitorData(Map<String, Map<String, Object>> index, Map<String, Object> row) {
        Map<String, Object> data = index.get(ValueConvertUtils.str(row.get("id")));
        return data != null ? data : index.get(ValueConvertUtils.str(row.get("iofhpi")));
    }

    /**
     * 档案行 → 站点行：字段名与结构对齐原 qx-api 列表（监测类型编码拆为数组、单选枚举去 # 包装、
     * 经纬度包成 bviiio、关联行包成 {dataTitle, id} 并保留 xxxId 平铺字段、时间空格格式）。
     */
    private Map<String, Object> toArchiveRow(Map<String, Object> archive) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ywvyds", refOf(archive.get("ywvyds"), archive.get("ywvyds_title")));
        row.put("ywvydsId", ValueConvertUtils.str(archive.get("ywvyds")));
        row.put("zzkaec", ValueConvertUtils.str(archive.get("zzkaec")));
        row.put("iofhpi", ValueConvertUtils.str(archive.get("iofhpi")));
        row.put("devicecode", ValueConvertUtils.str(archive.get("devicecode")));
        row.put("cadcode", ValueConvertUtils.str(archive.get("cadcode")));
        row.put("mivbcz", ValueConvertUtils.str(archive.get("mivbcz")));
        row.put("epjutj", codeArray(archive.get("epjutj")));
        row.put("bviiio", geoOf(archive));
        row.put("zebpsu", codeOf(archive.get("zebpsu")));
        row.put("ahieto", refOf(archive.get("ahieto"), archive.get("ahieto_title")));
        row.put("ahietoId", ValueConvertUtils.str(archive.get("ahieto")));
        row.put("ccnhtm", ValueConvertUtils.str(archive.get("ccnhtm")));
        row.put("ijzsby", ValueConvertUtils.str(archive.get("ijzsby")));
        row.put("waljdn", codeOf(archive.get("waljdn")));
        row.put("nxtggq", codeOf(archive.get("nxtggq")));
        row.put("bhsqxd", ValueConvertUtils.str(archive.get("bhsqxd")));
        row.put("lhwhuc", ValueConvertUtils.str(archive.get("lhwhuc")));
        row.put("cbitue", ValueConvertUtils.str(archive.get("cbitue")));
        row.put("viwmmc", richTextOf(archive.get("viwmmc")));
        row.put("dischargeType", ValueConvertUtils.str(archive.get("discharge_type")));
        row.put("badfhe", richTextOf(archive.get("badfhe")));
        // 闸门图片文件名需经文件服务换取，本期不做（结构保留，dataTitle 恒 null）
        row.put("nwbzla", refOf(archive.get("nwbzla"), null));
        row.put("nwbzlaId", ValueConvertUtils.str(archive.get("nwbzla")));
        row.put("id", ValueConvertUtils.str(archive.get("id")));
        row.put("corpCode", ValueConvertUtils.str(archive.get("corp_code")));
        row.put("createdBy", refOf(archive.get("created_by"), archive.get("created_by_title")));
        row.put("updatedBy", refOf(archive.get("updated_by"), archive.get("updated_by_title")));
        row.put("createdAt", timeText(archive.get("created_at")));
        row.put("updatedAt", timeText(archive.get("updated_at")));
        return row;
    }

    /** 监测类型编码串（#N#|#N#）→ 去 # 包装的编码数组，如 "#1#|#2#" → ["1","2"]；空值返回空数组 */
    private List<String> codeArray(Object codes) {
        List<String> list = new ArrayList<>();
        String text = ValueConvertUtils.str(codes);
        if (text == null) {
            return list;
        }
        for (String part : text.split("\\|")) {
            String code = part.trim().replace("#", "");
            if (!code.isEmpty()) {
                list.add(code);
            }
        }
        return list;
    }

    /** 单选枚举编码去 # 包装（#1# → 1）；空值返回 null */
    private String codeOf(Object code) {
        String text = ValueConvertUtils.str(code);
        return text == null ? null : text.trim().replace("#", "");
    }

    /**
     * 核心指标：平台站点档案把水位与雨量分列存储，按当前监测类型取对应列。
     * <p>列与业务对应关系：ijzsby =「核心指标-水位」、ccnhtm =「核心指标-雨量」
     * （注意与平台字段定义表的标题顺序相反，以业务核实结果为准）。
     * <p>输出统一为两位小数的文本（如 27.30 / 79.08），档案未填或空值按 0.00 输出——
     * 核心指标是人工维护的指标值而非采集读数，缺省即表示 0，调用方无需再判空。
     * <p>核心指标只属于水位站与雨量站：其余监测类型（流量 #3# / 闸门 #4# / 视频 #5# / 墒情 #7# / 水质 #8#）
     * 档案无对应列，返回 null（与水位/雨量站的 0.00 区分）。
     * 闸门尤其注意：虽有闸前 up_z / 闸后 down_z 两路实测水位，但核心指标不记录，同样返回 null，不要拿实测值顶替。
     *
     * @param archive  站点档案行（selectArchiveByType 结果）
     * @param typeCode 监测类型编码（#1# 水位 / #2# 雨量 …）
     */
    private String coreIndicatorOf(Map<String, Object> archive, String typeCode) {
        Object raw;
        if ("#1#".equals(typeCode)) {
            raw = archive.get("ijzsby");
        } else if ("#2#".equals(typeCode)) {
            raw = archive.get("ccnhtm");
        } else {
            return null;
        }
        BigDecimal value = ValueConvertUtils.decimal(raw);
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /** 经纬度 → bviiio 对象（x/y/geohash，与原 qx-api 一致）；经纬度均空返回 null */
    private Map<String, Object> geoOf(Map<String, Object> archive) {
        Object x = archive.get("bviiio_x");
        Object y = archive.get("bviiio_y");
        if (x == null && y == null) {
            return null;
        }
        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("x", numberOrNull(x));
        geo.put("y", numberOrNull(y));
        geo.put("geohash", ValueConvertUtils.str(archive.get("bviiio_geohash")));
        return geo;
    }

    /** 关联行 → {dataTitle, id}；两值均空返回 null */
    private Map<String, Object> refOf(Object id, Object title) {
        String idText = ValueConvertUtils.str(id);
        String titleText = ValueConvertUtils.str(title);
        if (idText == null && titleText == null) {
            return null;
        }
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("dataTitle", titleText);
        ref.put("id", idText);
        return ref;
    }

    /**
     * 给关联对象补一个附加属性（仅视频类型使用）：对象不存在或值为空时保持不变，
     * 用于对齐原 qx-api 的 ahieto.zzkaec / ywvyds.gfaegg（与 dataTitle 同源同值）。
     */
    @SuppressWarnings("unchecked")
    private void putRefExtra(Map<String, Object> row, String key, String extraKey, Object extraVal) {
        Object ref = row.get(key);
        if (!(ref instanceof Map)) {
            return;
        }
        String text = ValueConvertUtils.str(extraVal);
        if (text != null) {
            ((Map<String, Object>) ref).put(extraKey, text);
        }
    }

    /**
     * 富文本包装列（viwmmc/badfhe）：档案存平台富文本 JSON 文本（如 {"value":"&lt;div&gt;..."}），
     * 解析为对象输出（与原 qx-api 一致）；空值、非 JSON 文本原样返回。
     */
    private Object richTextOf(Object value) {
        String text = ValueConvertUtils.str(value);
        if (text == null || !text.trim().startsWith("{")) {
            return text;
        }
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (Exception e) {
            return text;
        }
    }

    /** 数值原样返回（BigDecimal 交给全局序列化保留 scale）；非数值返回 null */
    private BigDecimal numberOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return value instanceof Number ? new BigDecimal(value.toString()) : null;
    }

    /** 时间文本 yyyy-MM-dd HH:mm:ss（档案审计列与原 qx-api 同为空格格式） */
    private String timeText(Object value) {
        LocalDateTime tm = timeOf(value);
        return tm == null ? null : TM_FORMAT.format(tm);
    }

    /** VO → Map（字段名与时间格式同业务接口 HTTP 输出：复用 Web 层 ObjectMapper 转换） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, LinkedHashMap.class);
    }

    /** 聚合行 → {日期: 计数} */
    private Map<String, Long> toDayCountMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String day = (String) row.get("day");
            if (day != null) {
                map.put(day, longOf(row.get("cnt")));
            }
        }
        return map;
    }

    /** 水位清洗：-999/-9991 → null；2 位小数截断补零（业主口径：水位 2 位，不四舍五入） */
    private BigDecimal cleanLevel(Object value) {
        if (value == null) {
            return null;
        }
        double d = ((Number) value).doubleValue();
        if (d == INVALID_LEVEL || d == INVALID_LEVEL_1) {
            return null;
        }
        return BigDecimal.valueOf(d).setScale(2, RoundingMode.DOWN);
    }

    /** 瞬时流量：3 位小数截断（业主口径：瞬时流量 3 位，不四舍五入；SQL 层已过滤无效值） */
    private BigDecimal truncate3(Object value) {
        if (value == null) {
            return null;
        }
        double d = ((Number) value).doubleValue();
        return BigDecimal.valueOf(d).setScale(3, RoundingMode.DOWN);
    }

    private Long longOf(Object value) {
        if (value == null) {
            return 0L;
        }
        return value instanceof Long ? (Long) value : ((Number) value).longValue();
    }

    private LocalDateTime timeOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        String text = String.valueOf(value);
        if (text.length() >= 19) {
            text = text.substring(0, 19);
        }
        return LocalDateTime.parse(text, TM_FORMAT);
    }

    private LocalDateTime laterOf(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }
}
