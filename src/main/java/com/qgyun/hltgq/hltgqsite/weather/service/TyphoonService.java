package com.qgyun.hltgq.hltgqsite.weather.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.qgyun.hltgq.hltgqsite.weather.client.TyphoonClient;
import com.qgyun.hltgq.hltgqsite.weather.client.WeatherCallException;
import com.qgyun.hltgq.hltgqsite.weather.support.RateLimiter;
import com.qgyun.hltgq.hltgqsite.weather.support.StaleCacheSupport;
import com.qgyun.hltgq.hltgqsite.weather.support.WeatherConvertUtils;
import com.qgyun.hltgq.hltgqsite.weather.vo.TyphoonActiveVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.TyphoonDetailVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.TyphoonItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * N3 台风观测服务（方案 §5）。
 * <p>上游为中央气象台台风网（NMC）的 JSONP + <b>数组下标</b>结构，非契约化接口，
 * 故解析全程按「关键下标缺失/类型异常则跳过该点」容错（方案 §5.4），单个坏点不影响整份响应。
 * <p><b>缓存</b>：列表 `weather:typhoon:active`、详情 `weather:typhoon:detail:{id}`，
 * TTL 30 分钟（+10 分钟抖动）、旧值上限 6 小时；上游失败 → 返回 stale 缓存，无缓存 → 空列表/空对象（不抛 5xx）。
 * <p><b>距离口径</b>（评审 B5）：`distanceKm` 为台风中心到灌区参考中心点（`weather.site-*`）的球面直线距离，
 * 仅供前端排序/初筛参考，<b>不作为影响判定依据</b>。
 */
@Service
public class TyphoonService {

    private static final Logger log = LoggerFactory.getLogger(TyphoonService.class);

    private static final String CACHE_KEY_ACTIVE = "weather:typhoon:active";
    private static final String CACHE_KEY_DETAIL_PREFIX = "weather:typhoon:detail:";

    /** 活跃状态标记（上游列表项下标 7） */
    private static final String STATUS_ACTIVE = "start";

    /** 默认预报机构：BABJ=中央气象台 */
    private static final String AGENCY_BABJ = "BABJ";

    /** 英文名缺省归一（上游 `nameless`） */
    private static final String NAME_UNNAMED = "未命名";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** 上游强度缩写 → 中文（方案 §5.2） */
    private static final Map<String, String> LEVEL_NAMES = new HashMap<>();

    static {
        LEVEL_NAMES.put("TD", "热带低压");
        LEVEL_NAMES.put("TS", "热带风暴");
        LEVEL_NAMES.put("STS", "强热带风暴");
        LEVEL_NAMES.put("TY", "台风");
        LEVEL_NAMES.put("STY", "强台风");
        LEVEL_NAMES.put("SuperTY", "超强台风");
    }

    /** 地球平均半径（km），Haversine 用 */
    private static final double EARTH_RADIUS_KM = 6371.0088D;

    private final Random random = new Random();

    @Autowired
    private TyphoonClient client;

    @Autowired
    private StaleCacheSupport cache;

    @Autowired
    private RateLimiter rateLimiter;

    /** 缓存新鲜期（分钟） */
    @Value("${typhoon.cache-ttl-minutes:30}")
    private int cacheTtlMinutes;

    /** TTL 抖动上限（分钟） */
    @Value("${typhoon.cache-jitter-minutes:10}")
    private int cacheJitterMinutes;

    /** 旧值上限（小时） */
    @Value("${typhoon.stale-max-hours:6}")
    private int staleMaxHours;

    /** 单飞等待超时（秒） */
    @Value("${weather.single-flight.timeout-seconds:3}")
    private int singleFlightWaitSeconds;

    /** 灌区参考中心经度（距离计算基准，唯一来源见方案 §5.3） */
    @Value("${weather.site-lon:116.359678}")
    private double siteLon;

    /** 灌区参考中心纬度 */
    @Value("${weather.site-lat:30.378607}")
    private double siteLat;

    /**
     * 活跃台风列表（含最新位置与强度）。
     * <p>非台风季正常返回空列表；上游失败返回 stale 缓存，无缓存返回空列表。
     */
    public TyphoonActiveVO active() {
        rateLimiter.acquire();
        return loadActive();
    }

    /**
     * 单台风路径详情（实况 + 预报）。
     *
     * @param typhoonId 上游台风 ID（纯数字；非法入参抛 {@link IllegalArgumentException} → 400）
     */
    public TyphoonDetailVO detail(String typhoonId) {
        if (!validTyphoonId(typhoonId)) {
            throw new IllegalArgumentException("typhoonId 必须为数字");
        }
        rateLimiter.acquire();
        return loadDetail(typhoonId.trim());
    }

    /**
     * 台风 ID 合法性（纯数字，≤ 20 位）。
     * <p>对外入参校验与内部列表项合法性<b>同一判据</b>：列表项 id 将被拼入详情 URL
     * （`view_{id}`），上游数据异常时若不校验会构造出非法路径（注入/扫描风险，方案 §4.2 同口径）。
     */
    private boolean validTyphoonId(String typhoonId) {
        return typhoonId != null && typhoonId.trim().matches("\\d{1,20}");
    }

    /** 预热（P3a）：走一次活跃列表（内部顺带填充详情缓存），失败仅记日志 */
    public void warmUp() {
        try {
            TyphoonActiveVO active = loadActive();
            log.info("台风预热完成: 活跃数={}", active.getCount());
            for (TyphoonItemVO item : active.getList()) {
                loadDetail(item.getTyphoonId());
            }
        } catch (Exception e) {
            log.warn("台风预热失败: {}", e.getMessage());
        }
    }

    // ==================== 活跃列表 ====================

    private TyphoonActiveVO loadActive() {
        long now = StaleCacheSupport.nowMs();
        StaleCacheSupport.Envelope<TyphoonActiveVO> envelope = cache.read(CACHE_KEY_ACTIVE, TyphoonActiveVO.class);
        if (envelope != null && envelope.getData() != null) {
            if (now < envelope.getExpireAt()) {
                return envelope.getData();
            }
            if (!beyondStaleLimit(envelope, now)) {
                cache.refreshAsync(CACHE_KEY_ACTIVE, "台风列表", this::pullActive);
                return envelope.getData();
            }
        }
        TyphoonActiveVO fallback = usable(envelope, now) ? envelope.getData() : emptyActive();
        return cache.singleFlight(CACHE_KEY_ACTIVE, "台风列表", this::pullActive, fallback, singleFlightWaitSeconds);
    }

    /** 拉取活跃列表：列表 + 逐条详情补全最新位置强度，并写回两个缓存 */
    private TyphoonActiveVO pullActive() {
        JsonNode root = client.listDefault();
        List<TyphoonItemVO> items = parseList(root);
        for (TyphoonItemVO item : items) {
            if (!validTyphoonId(item.getTyphoonId())) {
                // 上游列表 id 异常：跳过详情拉取（避免把非法串拼进 view_{id}），列表项仍保留基础字段
                log.warn("台风列表项 id 非法，跳过详情拉取: id={}", item.getTyphoonId());
                continue;
            }
            try {
                TyphoonDetailVO detail = pullDetail(item.getTyphoonId());
                fillItem(item, detail);
            } catch (Exception e) {
                // 单条详情失败：保留列表项的基础字段（名称/编号/状态），位置强度留空
                log.warn("台风详情拉取失败 id={}: {}", item.getTyphoonId(), e.getMessage());
            }
        }
        TyphoonActiveVO vo = new TyphoonActiveVO();
        vo.setCount(items.size());
        vo.setList(items);
        cache.write(CACHE_KEY_ACTIVE, vo, ttlSeconds(), staleMaxSeconds());
        log.info("台风活跃列表已刷新: 活跃数={}", items.size());
        return vo;
    }

    /** 用详情的最新实况点补全列表项（含到灌区参考中心点的距离） */
    private void fillItem(TyphoonItemVO item, TyphoonDetailVO detail) {
        if (detail == null || detail.getCurrent() == null) {
            return;
        }
        TyphoonDetailVO.TrackPoint current = detail.getCurrent();
        item.setLevel(current.getLevel());
        item.setLevelName(current.getLevelName());
        item.setLon(current.getLon());
        item.setLat(current.getLat());
        item.setPressure(current.getPressure());
        item.setWindSpeed(current.getWindSpeed());
        item.setMoveDirection(current.getMoveDirection());
        item.setMoveSpeed(current.getMoveSpeed());
        item.setLatestTime(current.getTime());
        if (detail.getStatus() != null) {
            item.setStatus(detail.getStatus());
        }
        if (item.getCode() == null) {
            item.setCode(detail.getCode());
        }
        if (item.getLon() != null && item.getLat() != null) {
            double km = haversine(siteLon, siteLat, item.getLon(), item.getLat());
            item.setDistanceKm(Math.round(km * 10D) / 10D);
        }
    }

    // ==================== 路径详情 ====================

    private TyphoonDetailVO loadDetail(String typhoonId) {
        String key = CACHE_KEY_DETAIL_PREFIX + typhoonId;
        long now = StaleCacheSupport.nowMs();
        StaleCacheSupport.Envelope<TyphoonDetailVO> envelope = cache.read(key, TyphoonDetailVO.class);
        if (envelope != null && envelope.getData() != null) {
            if (now < envelope.getExpireAt()) {
                return envelope.getData();
            }
            if (!beyondStaleLimit(envelope, now)) {
                cache.refreshAsync(key, "台风详情", () -> pullDetail(typhoonId));
                return envelope.getData();
            }
        }
        TyphoonDetailVO fallback = usable(envelope, now) ? envelope.getData() : emptyDetail(typhoonId);
        return cache.singleFlight(key, "台风详情", () -> pullDetail(typhoonId), fallback, singleFlightWaitSeconds);
    }

    /** 拉取并解析单台风详情（含写缓存） */
    private TyphoonDetailVO pullDetail(String typhoonId) {
        JsonNode root = client.view(typhoonId);
        TyphoonDetailVO vo = parseDetail(typhoonId, root);
        cache.write(CACHE_KEY_DETAIL_PREFIX + typhoonId, vo, ttlSeconds(), staleMaxSeconds());
        return vo;
    }

    // ==================== 解析（下标 + 容错） ====================

    /**
     * 台风列表解析：仅保留活跃项（下标 7 = `start`），结构异常整体返回空列表（不抛 5xx）。
     * <p>下标含义（方案 §2.6）：0 ID / 1 英文名 / 2 中文名 / 3 编号 / 7 状态。
     */
    private List<TyphoonItemVO> parseList(JsonNode root) {
        JsonNode typhoonList = root == null ? null : root.get("typhoonList");
        if (typhoonList == null || !typhoonList.isArray()) {
            log.warn("台风列表结构异常（缺少 typhoonList 数组）");
            return new ArrayList<>();
        }
        List<TyphoonItemVO> list = new ArrayList<>();
        for (JsonNode item : typhoonList) {
            if (item == null || !item.isArray() || item.size() < 8) {
                continue;
            }
            String status = cleanStr(item, 7);
            if (!STATUS_ACTIVE.equals(status)) {
                continue;
            }
            TyphoonItemVO vo = new TyphoonItemVO();
            vo.setTyphoonId(cleanStr(item, 0));
            vo.setNameEn(normalizeName(cleanStr(item, 1)));
            vo.setNameCn(cleanStr(item, 2));
            vo.setCode(cleanStr(item, 3));
            vo.setStatus(status);
            list.add(vo);
        }
        log.info("台风列表解析完成: 总条数={} 活跃条数={}", typhoonList.size(), list.size());
        return list;
    }

    /**
     * 台风详情解析：`typhoon` 数组内含「元信息头 + 实况轨迹点」，逐元素按判据分流（方案 §5.4）。
     * <p>轨迹点下标：0 点ID / 1 时间 / 2 epoch 毫秒（权威）/ 3 强度 / 4 经度 / 5 纬度 /
     * 6 气压 / 7 风速 / 8 移向 / 9 移速 / 11 预报路径字典。
     */
    private TyphoonDetailVO parseDetail(String typhoonId, JsonNode root) {
        JsonNode typhoon = root == null ? null : root.get("typhoon");
        if (typhoon == null || !typhoon.isArray()) {
            throw new WeatherCallException("响应缺少 typhoon 数组");
        }

        TyphoonDetailVO vo = new TyphoonDetailVO();
        vo.setTyphoonId(typhoonId);
        List<TyphoonDetailVO.TrackPoint> track = new ArrayList<>();
        JsonNode metaNode = null;
        JsonNode latestPoint = null;
        Long latestEpoch = null;
        for (JsonNode element : typhoon) {
            if (element == null || !element.isArray()) {
                continue;
            }
            if (isTrackPoint(element)) {
                TyphoonDetailVO.TrackPoint point = parseTrackPoint(element);
                if (point != null) {
                    track.add(point);
                    // 取 epoch 最大者作为「最新点」（不依赖上游顺序）
                    Long epoch = longAt(element, 2);
                    if (epoch != null && (latestEpoch == null || epoch > latestEpoch)) {
                        latestEpoch = epoch;
                        latestPoint = element;
                    }
                }
            } else if (metaNode == null) {
                metaNode = element;
            }
        }

        if (metaNode != null) {
            vo.setNameEn(normalizeName(cleanStr(metaNode, 1)));
            vo.setNameCn(cleanStr(metaNode, 2));
            vo.setCode(cleanStr(metaNode, 3));
            vo.setStatus(cleanStr(metaNode, 7));
        }

        track.sort(Comparator.comparing(TyphoonDetailVO.TrackPoint::getTime,
                Comparator.nullsLast(Comparator.<String>naturalOrder())));
        if (!track.isEmpty()) {
            TyphoonDetailVO.TrackPoint current = track.get(track.size() - 1);
            // 移向/移速仅最新点有效（上游实况路径点不带这两个字段）
            if (latestPoint != null) {
                current.setMoveDirection(moveDirection(cleanStr(latestPoint, 8)));
                current.setMoveSpeed(intAt(latestPoint, 9));
            }
            vo.setCurrent(current);
        }

        List<TyphoonDetailVO.ForecastPoint> forecast = latestPoint == null
                ? new ArrayList<>() : parseForecast(latestPoint);
        vo.setForecast(forecast);
        if (!forecast.isEmpty()) {
            vo.setAgency(forecast.get(0).getAgency());
        }
        vo.setTrack(track);
        log.info("台风详情解析完成: id={} 实况点数={} 预报点数={} 最新点={}",
                typhoonId, track.size(), forecast.size(),
                vo.getCurrent() == null ? "-" : (vo.getCurrent().getTime() + " " + vo.getCurrent().getLon()
                        + "," + vo.getCurrent().getLat() + " " + vo.getCurrent().getLevel()));
        return vo;
    }

    /** 轨迹点判据：下标 2（epoch 毫秒）、4（经度）、5（纬度）均为数字（元信息头的下标 2 是中文名） */
    private boolean isTrackPoint(JsonNode element) {
        return element.size() >= 12
                && numeric(element.get(2)) && numeric(element.get(4)) && numeric(element.get(5));
    }

    private TyphoonDetailVO.TrackPoint parseTrackPoint(JsonNode point) {
        Double lon = doubleAt(point, 4);
        Double lat = doubleAt(point, 5);
        Long epoch = longAt(point, 2);
        if (lon == null || lat == null || epoch == null) {
            return null;
        }
        String level = cleanStr(point, 3);
        TyphoonDetailVO.TrackPoint vo = new TyphoonDetailVO.TrackPoint();
        vo.setTime(epochToTime(epoch));
        vo.setLevel(level);
        vo.setLevelName(levelName(level));
        vo.setLon(lon);
        vo.setLat(lat);
        vo.setPressure(intAt(point, 6));
        vo.setWindSpeed(intAt(point, 7));
        return vo;
    }

    /**
     * 预报路径解析（下标 11 的机构字典，默认取 BABJ；BABJ 缺失时退化为首个机构）。
     * <p>每个预报点：[时效h, 起始时间(epoch 毫秒), lon, lat, 气压, 风速, 机构, 强度]。
     */
    private List<TyphoonDetailVO.ForecastPoint> parseForecast(JsonNode point) {
        List<TyphoonDetailVO.ForecastPoint> list = new ArrayList<>();
        JsonNode forecastNode = point.get(11);
        if (forecastNode == null || !forecastNode.isObject()) {
            return list;
        }
        String agency = AGENCY_BABJ;
        JsonNode entries = forecastNode.get(AGENCY_BABJ);
        if (entries == null || !entries.isArray()) {
            Iterator<String> names = forecastNode.fieldNames();
            if (names.hasNext()) {
                agency = names.next();
                entries = forecastNode.get(agency);
            }
        }
        if (entries == null || !entries.isArray()) {
            return list;
        }
        for (JsonNode entry : entries) {
            if (entry == null || !entry.isArray() || entry.size() < 5) {
                continue;
            }
            Double lon = doubleAt(entry, 2);
            Double lat = doubleAt(entry, 3);
            if (lon == null || lat == null) {
                continue;
            }
            Integer hourOffset = intAt(entry, 0);
            String level = cleanStr(entry, 7);
            TyphoonDetailVO.ForecastPoint vo = new TyphoonDetailVO.ForecastPoint();
            vo.setHourOffset(hourOffset);
            vo.setTime(forecastTime(longAt(entry, 1), hourOffset));
            vo.setLevel(level);
            vo.setLevelName(levelName(level));
            vo.setLon(lon);
            vo.setLat(lat);
            vo.setPressure(intAt(entry, 4));
            vo.setWindSpeed(intAt(entry, 5));
            vo.setAgency(cleanStr(entry, 6) == null ? agency : cleanStr(entry, 6));
            list.add(vo);
        }
        list.sort(Comparator.comparing(TyphoonDetailVO.ForecastPoint::getHourOffset,
                Comparator.nullsLast(Comparator.<Integer>naturalOrder())));
        return list;
    }

    // ==================== 降级兜底 ====================

    private TyphoonActiveVO emptyActive() {
        return new TyphoonActiveVO();
    }

    private TyphoonDetailVO emptyDetail(String typhoonId) {
        TyphoonDetailVO vo = new TyphoonDetailVO();
        vo.setTyphoonId(typhoonId);
        vo.setTrack(new ArrayList<>());
        vo.setForecast(new ArrayList<>());
        return vo;
    }

    /** 信封是否仍可用于兜底展示（未超旧值上限） */
    private boolean usable(StaleCacheSupport.Envelope<?> envelope, long now) {
        return envelope != null && envelope.getData() != null && !beyondStaleLimit(envelope, now);
    }

    /**
     * 是否已超旧值上限：以拉取时刻为准；若信封缺失该字段（异常/历史数据），
     * 退化为「过期时长超过上限」判定，宁可少用旧值。
     */
    private boolean beyondStaleLimit(StaleCacheSupport.Envelope<?> envelope, long now) {
        long staleMaxMs = staleMaxSeconds() * 1000L;
        if (envelope.getPulledAtMs() > 0) {
            return now - envelope.getPulledAtMs() > staleMaxMs;
        }
        return now - envelope.getExpireAt() > staleMaxMs;
    }

    private long ttlSeconds() {
        return (cacheTtlMinutes + random.nextInt(Math.max(0, cacheJitterMinutes) + 1)) * 60L;
    }

    private long staleMaxSeconds() {
        return staleMaxHours * 3600L;
    }

    // ==================== 通用工具 ====================

    /** 强度缩写 → 中文（未知缩写透传，不伪造） */
    private String levelName(String level) {
        if (level == null) {
            return null;
        }
        String name = LEVEL_NAMES.get(level);
        return name == null ? level : name;
    }

    private String normalizeName(String nameEn) {
        if (nameEn == null || "nameless".equalsIgnoreCase(nameEn)) {
            return NAME_UNNAMED;
        }
        return nameEn;
    }

    /**
     * 移向归一：上游可能给角度（数字）或方位文字，数字走 16 方位映射，文字原样返回。
     */
    private String moveDirection(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return WeatherConvertUtils.translateWindDirection((int) Math.round(Double.parseDouble(raw)));
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    /** 球面直线距离（Haversine，km） */
    private double haversine(double lon1, double lat1, double lon2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1D, Math.sqrt(a)));
    }

    /** epoch 毫秒（兼容 10 位秒级时间戳）→ 北京时间字符串 */
    private String epochToTime(Long epoch) {
        if (epoch == null) {
            return null;
        }
        long millis = epoch < 100000000000L ? epoch * 1000L : epoch;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(TIME_FORMAT);
    }

    /** 预报时间 = 起始时间 + 时效小时（起始时间缺失则返回 null，不伪造） */
    private String forecastTime(Long baseEpoch, Integer hourOffset) {
        if (baseEpoch == null) {
            return null;
        }
        long millis = baseEpoch < 100000000000L ? baseEpoch * 1000L : baseEpoch;
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        return time.plusHours(hourOffset == null ? 0L : hourOffset).format(TIME_FORMAT);
    }

    /** 下标取值 + 空值归一：`""`、`"pass"`、`"null"` 统一为 null（方案 §5.4） */
    private String cleanStr(JsonNode array, int index) {
        return clean(textAt(array, index));
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty() || "pass".equalsIgnoreCase(text) || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return text;
    }

    private boolean numeric(JsonNode node) {
        return node != null && node.isNumber();
    }

    private String textAt(JsonNode array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonNode node = array.get(index);
        if (node == null || node.isNull() || node.isContainerNode()) {
            return null;
        }
        return node.asText();
    }

    private Double doubleAt(JsonNode array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonNode node = array.get(index);
        return node != null && node.isNumber() ? node.asDouble() : null;
    }

    private Integer intAt(JsonNode array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonNode node = array.get(index);
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private Long longAt(JsonNode array, int index) {
        if (array == null || index < 0 || index >= array.size()) {
            return null;
        }
        JsonNode node = array.get(index);
        return node != null && node.isNumber() ? node.asLong() : null;
    }
}
