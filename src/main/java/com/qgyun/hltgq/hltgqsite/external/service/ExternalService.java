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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        String siteId = stringOf(station.get("id"));

        ExternalVO.IntakeGate vo = new ExternalVO.IntakeGate();
        vo.setSite(siteId);
        vo.setStcd(stringOf(station.get("iofhpi")));
        vo.setStnm(stringOf(station.get("zzkaec")));
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
     * 监测站点列表（按监测类型合并）：type 与站点档案监测类型编码同口径
     * （1 水位 / 2 雨量 / 3 流量 / 4 闸门 / 7 墒情 / 8 水质，另兼容 #N# 编码与语义串便于联调）。
     * <p>站点集合取站点档案（含当前无数据的站点，与原 qx-api 列表一致），name 非空时按站名模糊过滤
     * （先筛档案再查实时值，未命中站点时不触发实时值查询）；实时值按类型批量查一次（无逐站 N+1）；
     * 站点顺序按「站点排序」配置输出（未配置该类型排序时保持档案默认顺序）。
     * 非法 type 抛 IllegalArgumentException → 全局 400。
     *
     * @param type 监测类型：1/2/3/4/7/8（另兼容 #N# 编码与语义串）
     * @param name 站点名称模糊词，null/空白表示不筛选
     */
    public ExternalVO.StationMonitor stationMonitor(String type, String name) {
        String typeCode = monitorTypeCode(type);
        List<Map<String, Object>> archives = mapper.selectArchiveByType(typeCode, trimToNull(name));
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
            // 实时值平铺（null 不覆盖档案列）+ data 子对象（原业务接口字段结构）+ hasData 标记
            Map<String, Object> data = matchMonitorData(dataIndex, row);
            if (data != null) {
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    if (entry.getValue() != null) {
                        row.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            row.put("data", data);
            row.put("hasData", data != null);
            rows.add(row);
        }
        String sortType = monitorSortType(typeCode);
        vo.setData(sortType == null ? rows
                : stationSortService.applyOrder(sortType, rows, item -> (String) item.get("id")));
        return vo;
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
     * 档案 #8# 水质在排序表中无该类型，返回 null 表示保持档案默认顺序。
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
        Map<String, Object> data = index.get(stringOf(row.get("id")));
        return data != null ? data : index.get(stringOf(row.get("iofhpi")));
    }

    /**
     * 档案行 → 站点行：字段名与结构对齐原 qx-api 列表（监测类型编码拆为数组、单选枚举去 # 包装、
     * 经纬度包成 bviiio、关联行包成 {dataTitle, id} 并保留 xxxId 平铺字段、时间空格格式）。
     */
    private Map<String, Object> toArchiveRow(Map<String, Object> archive) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ywvyds", refOf(archive.get("ywvyds"), archive.get("ywvyds_title")));
        row.put("ywvydsId", stringOf(archive.get("ywvyds")));
        row.put("zzkaec", stringOf(archive.get("zzkaec")));
        row.put("iofhpi", stringOf(archive.get("iofhpi")));
        row.put("devicecode", stringOf(archive.get("devicecode")));
        row.put("mivbcz", stringOf(archive.get("mivbcz")));
        row.put("epjutj", codeArray(archive.get("epjutj")));
        row.put("bviiio", geoOf(archive));
        row.put("zebpsu", codeOf(archive.get("zebpsu")));
        row.put("ahieto", refOf(archive.get("ahieto"), archive.get("ahieto_title")));
        row.put("ahietoId", stringOf(archive.get("ahieto")));
        row.put("ccnhtm", stringOf(archive.get("ccnhtm")));
        row.put("ijzsby", stringOf(archive.get("ijzsby")));
        row.put("waljdn", codeOf(archive.get("waljdn")));
        row.put("nxtggq", codeOf(archive.get("nxtggq")));
        row.put("bhsqxd", stringOf(archive.get("bhsqxd")));
        row.put("lhwhuc", stringOf(archive.get("lhwhuc")));
        row.put("cbitue", stringOf(archive.get("cbitue")));
        row.put("viwmmc", richTextOf(archive.get("viwmmc")));
        row.put("dischargeType", stringOf(archive.get("discharge_type")));
        row.put("badfhe", richTextOf(archive.get("badfhe")));
        // 闸门图片文件名需经文件服务换取，本期不做（结构保留，dataTitle 恒 null）
        row.put("nwbzla", refOf(archive.get("nwbzla"), null));
        row.put("nwbzlaId", stringOf(archive.get("nwbzla")));
        row.put("id", stringOf(archive.get("id")));
        row.put("corpCode", stringOf(archive.get("corp_code")));
        row.put("createdBy", refOf(archive.get("created_by"), archive.get("created_by_title")));
        row.put("updatedBy", refOf(archive.get("updated_by"), archive.get("updated_by_title")));
        row.put("createdAt", timeText(archive.get("created_at")));
        row.put("updatedAt", timeText(archive.get("updated_at")));
        return row;
    }

    /** 监测类型编码串（#N#|#N#）→ 去 # 包装的编码数组，如 "#1#|#2#" → ["1","2"]；空值返回空数组 */
    private List<String> codeArray(Object codes) {
        List<String> list = new ArrayList<>();
        String text = stringOf(codes);
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
        String text = stringOf(code);
        return text == null ? null : text.trim().replace("#", "");
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
        geo.put("geohash", stringOf(archive.get("bviiio_geohash")));
        return geo;
    }

    /** 关联行 → {dataTitle, id}；两值均空返回 null */
    private Map<String, Object> refOf(Object id, Object title) {
        String idText = stringOf(id);
        String titleText = stringOf(title);
        if (idText == null && titleText == null) {
            return null;
        }
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("dataTitle", titleText);
        ref.put("id", idText);
        return ref;
    }

    /**
     * 富文本包装列（viwmmc/badfhe）：档案存平台富文本 JSON 文本（如 {"value":"&lt;div&gt;..."}），
     * 解析为对象输出（与原 qx-api 一致）；空值、非 JSON 文本原样返回。
     */
    private Object richTextOf(Object value) {
        String text = stringOf(value);
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

    /** Map 值转字符串：null 原样返回（避免 "null" 文本） */
    private String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 查询参数归一：去首尾空白，空串按 null 处理（SQL 侧 null 表示该条件不参与筛选） */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
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
