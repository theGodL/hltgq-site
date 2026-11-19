package com.qgyun.hltgq.hltgqsite.sms.service;

import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.sms.SmsProperties;
import com.qgyun.hltgq.hltgqsite.sms.mapper.SmsRecordMapper;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsAlarmEventRequest;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsAlarmResult;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsBatchStat;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsCompensationAlertVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 告警短信服务：mq 回调（POST /sms/alarm-event）与 5 分钟补偿扫描共用处理链路。
 * <p>两类模板（业主 2026-11 定稿）：
 * <ul>
 *   <li>雨量：{@code ${hour}小时累积雨量大于${threshold}mm的雨量站:${stcd}}，
 *       hour/threshold 由 mq 回调传入（与告警引擎判定口径一致），
 *       ${stcd} 值为"站名+实测雨量"（模板无独立雨量变量，沿用原运维拼法）；</li>
 *   <li>水位：{@code 1小时内${station_code}水位涨幅${increaseRate}当前水位${waterLevel}}，
 *       触发=水位阈值告警，涨幅由本系统归纳（当前水位 − 1 小时前水位）。</li>
 * </ul>
 * <p>幂等与静默：同 alertId 只处理一次（success=已发 / skipped=静默均算已处理，
 * 重复回调/补偿直接跳过）；同站点同指标在 {@code sms.alarm.silence-minutes}（默认 30）
 * 分钟内不重发（防轰炸与频控），静默跳过时落 skipped 记录，防止补偿在静默过期后延迟补发。
 * <p>并发防重：处理前用 Redis 抢占锁（qx.hltgq.sms.alert.{alertId}，TTL 10 分钟），
 * 回调与补偿同时到达时只允许一个线程发送；Redis 异常降级为无锁处理（可用性优先）。
 * <p>补偿判定（宁可少发不误发）：设备名含"雨量"→雨量、含"水位"→水位；
 * 设备名不可判定时按阈值表推定——仅当该站全部阈值行均为单一指标
 * （#2#=雨量 / #1#=水位）才可推定，含流量/开度等其他指标或一行多值一律跳过并记日志。
 * 补偿时雨量 hour 按水文日口径推定（报文时间距 08:00 起点整小时数，1~24，与 mq 回调一致）、
 * threshold 取阈值表（无配置跳过）、实测雨量值不拼（DRP 字段实测恒 0，拼进模板就是假 0 错值）。
 * <p>水位取数基准：回调用当前时刻；补偿用<b>告警时刻</b>（窗口内当前值可能已变化，
 * 用告警时刻取值才能与告警内容（如"闸前水位高于40.1m"）严格对应）。
 */
@Service
public class SmsAlarmService {

    private static final Logger log = LoggerFactory.getLogger(SmsAlarmService.class);

    private static final String METRIC_RAIN = "rain";
    private static final String METRIC_WATER_LEVEL = "water_level";

    /** 告警并发锁 key 前缀（回调与补偿同时处理同一告警时抢占） */
    private static final String ALERT_LOCK_PREFIX = "qx.hltgq.sms.alert.";

    /** 锁 TTL（分钟）：覆盖最长发送耗时（含重试），异常崩溃后自动过期由补偿重试 */
    private static final long ALERT_LOCK_TTL_MINUTES = 10;

    /** 锁降级标记：Redis 不可用时放行（未真实持锁，释放时不得删除他人锁） */
    private static final String LOCK_DEGRADED = "DEGRADED";

    @Autowired
    private SmsProperties properties;

    @Autowired
    private SmsRecordMapper recordMapper;

    @Autowired
    private SmsSendService smsSendService;

    @Autowired
    private SmsReceiverService smsReceiverService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    /**
     * 处理 mq 告警回调（参数校验 + 组装 + 发送）。
     */
    public SmsAlarmResult handleCallback(SmsAlarmEventRequest request) {
        if (!properties.isEnabled()) {
            return SmsAlarmResult.skipped("短信功能未启用");
        }
        if (!StringUtils.hasText(request.getAlertId())) {
            return SmsAlarmResult.skipped("alertId 不能为空");
        }
        if (!StringUtils.hasText(request.getStcd())) {
            return SmsAlarmResult.skipped("stcd 不能为空");
        }
        String metric = normalizeMetric(request.getMetric());
        if (metric == null) {
            return SmsAlarmResult.skipped("metric 仅支持 rain / water_level");
        }
        if (METRIC_RAIN.equals(metric) && (request.getThreshold() == null || request.getHour() == null)) {
            return SmsAlarmResult.skipped("雨量告警 threshold/hour 不能为空");
        }
        return processAlarm(request.getAlertId(), metric, request.getStcd(),
                request.getThreshold(), request.getHour(), request.getValue(), null, false);
    }

    /**
     * 告警短信处理公共链路（回调 + 补偿共用）。
     *
     * @param alertTime    告警发生时刻（补偿扫描传入；回调传 null=当前时刻）
     * @param compensation true=补偿扫描（日志标注；雨量 hour 按水文日口径推定，实测值不拼）
     */
    private SmsAlarmResult processAlarm(String alertId, String metric, String stcd,
                                        BigDecimal threshold, Integer hour, BigDecimal value,
                                        LocalDateTime alertTime, boolean compensation) {
        String source = compensation ? "[补偿]" : "[回调]";
        // 幂等：同一告警已处理过（success=已发 / skipped=静默）直接跳过
        if (recordMapper.countHandledByAlertId(alertId) > 0) {
            log.info("{}告警短信已处理过，跳过 alertId={} stcd={}", source, alertId, stcd);
            return SmsAlarmResult.skipped("该告警短信已处理（幂等跳过）");
        }
        // 并发锁：回调与补偿可能同时处理同一告警，Redis 抢占锁防双发（Redis 异常降级无锁）
        String lockToken = tryAcquireAlertLock(alertId);
        if (lockToken == null) {
            log.info("{}该告警正在并发处理中，跳过 alertId={} stcd={}", source, alertId, stcd);
            return SmsAlarmResult.skipped("该告警正在处理中（并发锁）");
        }
        try {
            // 静默：同站点同指标窗口内不重发；落 skipped 记录，防止静默过期后被补偿延迟补发
            String silenceKey = (METRIC_RAIN.equals(metric) ? "rain:" : "level:") + stcd;
            LocalDateTime silenceSince = LocalDateTime.now().minusMinutes(properties.getAlarmSilenceMinutes());
            if (recordMapper.countRecentSilence(silenceKey, silenceSince) > 0) {
                log.info("{}同站点同指标 {} 分钟内已发送，静默 alertId={} silenceKey={}",
                        source, properties.getAlarmSilenceMinutes(), alertId, silenceKey);
                saveSilenceSkipRecord(alertId, metric, silenceKey);
                return SmsAlarmResult.skipped("同站点同指标 " + properties.getAlarmSilenceMinutes() + " 分钟内已发送（静默）");
            }

            String stationName = resolveStationName(stcd);
            Map<String, String> params = new HashMap<>();
            String templateCode;
            String bizType;
            if (METRIC_RAIN.equals(metric)) {
                templateCode = properties.getTemplateRain();
                bizType = "alarm_rain";
                if (threshold == null) {
                    // 防御：回调校验已保证非空、补偿无阈值直接跳过；走到这里说明上游传参异常
                    log.error("{}雨量告警 threshold 为空，未发送 alertId={} stcd={}", source, alertId, stcd);
                    return SmsAlarmResult.skipped("雨量告警缺少 threshold，未发送");
                }
                // ${hour} 小时累积雨量大于 ${threshold} mm 的雨量站:${stcd}
                // 模板无独立雨量值变量：实测值拼在站名后（如"周家河12.5"，沿用原运维拼法）；
                // value 缺省或 <=0 时只拼站名（0 多半是缺数假值，绝不能显示成"站名0"）
                int windowHour = hour == null || hour <= 0 ? 1 : hour;
                String valueText = value != null && value.compareTo(BigDecimal.ZERO) > 0
                        ? trimZeros(value) : "";
                params.put("hour", String.valueOf(windowHour));
                params.put("threshold", trimZeros(threshold));
                params.put("stcd", stationName + valueText);
            } else {
                templateCode = properties.getTemplateLevel();
                bizType = "alarm_level";
                // 取数不完整（当前水位/1 小时前任一缺失）→ 本条不发：宁可少发不误发，
                // 不落处理记录，30 分钟补偿窗口内每 5 分钟重试，数据补齐后自动补发
                if (!fillLevelParams(params, stcd, stationName, alertTime)) {
                    return SmsAlarmResult.skipped("水位取数不完整，未发送（补偿窗口内将重试）");
                }
            }

            log.info("{}告警短信组装 alertId={} metric={} stcd={} 站名={} 参数={}",
                    source, alertId, metric, stcd, stationName, params);

            // 接收人：完全以短信管理页（接收人表）为准（未建表/该类型 0 人 = 不发）
            SmsBatchStat stat = smsSendService.sendToPhones(
                    smsReceiverService.resolvePhones(SmsReceiverService.BIZ_ALARM), bizType,
                    templateCode, params, alertId, silenceKey);
            if (stat.getSuccessCount() > 0) {
                return SmsAlarmResult.sent();
            }
            return SmsAlarmResult.skipped("短信发送失败（详见发送记录）");
        } finally {
            releaseAlertLock(alertId, lockToken);
        }
    }

    /**
     * 抢占告警处理锁。
     *
     * @return 持有锁的 token；null=已被他人持有；{@link #LOCK_DEGRADED}=Redis 异常降级放行（未持锁，不释放）
     */
    private String tryAcquireAlertLock(String alertId) {
        try {
            String token = UUID.randomUUID().toString();
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                    ALERT_LOCK_PREFIX + alertId, token, ALERT_LOCK_TTL_MINUTES, TimeUnit.MINUTES);
            return Boolean.TRUE.equals(locked) ? token : null;
        } catch (Exception e) {
            log.warn("告警短信并发锁不可用（Redis 异常，降级无锁处理）alertId={} err={}", alertId, e.toString());
            return LOCK_DEGRADED;
        }
    }

    /** 释放告警处理锁：降级未持锁不删；仅删自己 token 的锁（防 TTL 过期后误删他人锁） */
    private void releaseAlertLock(String alertId, String lockToken) {
        if (lockToken == null || LOCK_DEGRADED.equals(lockToken)) {
            return;
        }
        try {
            String key = ALERT_LOCK_PREFIX + alertId;
            if (lockToken.equals(stringRedisTemplate.opsForValue().get(key))) {
                stringRedisTemplate.delete(key);
            }
        } catch (Exception e) {
            log.debug("释放告警短信并发锁失败 alertId={} err={}", alertId, e.toString());
        }
    }

    /** 静默跳过落痕：send_status='skipped'（phone 占位 '-'），使补偿扫描按"已处理"跳过 */
    private void saveSilenceSkipRecord(String alertId, String metric, String silenceKey) {
        try {
            LocalDateTime now = LocalDateTime.now();
            boolean rain = METRIC_RAIN.equals(metric);
            Map<String, Object> row = new HashMap<>();
            row.put("id", shortIdGenerator.nextUUID(null));
            row.put("biz_type", rain ? "alarm_rain" : "alarm_level");
            row.put("alert_id", alertId);
            row.put("silence_key", silenceKey);
            row.put("phone", "-");
            row.put("template_code", rain ? properties.getTemplateRain() : properties.getTemplateLevel());
            row.put("template_params", "{\"skipped\":\"silenced\"}");
            row.put("send_status", "skipped");
            row.put("request_id", null);
            row.put("biz_id", null);
            row.put("response_code", "SILENCED");
            row.put("response_message", "同站点同指标静默跳过");
            row.put("sent_at", null);
            row.put("now", now);
            recordMapper.insertRecord(row);
        } catch (Exception e) {
            log.error("静默跳过记录落库失败 alertId={} err={}", alertId, e.toString());
        }
    }

    /**
     * 水位模板参数：station_code / increaseRate（1 小时涨幅）/ waterLevel（当前水位）。
     *
     * @param alertTime 告警时刻（补偿传入；null=取当前时刻）
     * @return true=参数齐全可发送；false=取数不完整（<b>调用方不得发送</b>，宁可少发不误发）
     */
    private boolean fillLevelParams(Map<String, String> params, String stcd, String stationName,
                                    LocalDateTime alertTime) {
        params.put("station_code", stationName);
        LocalDateTime at = alertTime == null ? LocalDateTime.now() : alertTime;
        LevelSnapshot current = readLevelSnapshot(stcd, at);
        if (current == null) {
            log.error("水位告警取当前水位失败：stcd={}（河道表/闸站表均无数据，本条不发送）", stcd);
            return false;
        }
        // 业主水位口径：2 位截断不四舍五入（与 ExternalService.cleanLevel / 日汇总 readLevel 一致）
        BigDecimal zNow = current.z.setScale(2, RoundingMode.DOWN);

        // 1 小时前 = 当前值时刻回退 1 小时（灌区水位 rise1h 同口径；不用 now，避免当前值滞后时基准漂移）
        LevelSnapshot oneHourAgo = readLevelSnapshot(stcd, current.time.minusHours(1));
        if (oneHourAgo == null) {
            log.error("水位告警取 1 小时前水位失败：stcd={} 当前值时刻={}（本条不发送）", stcd, current.time);
            return false;
        }
        BigDecimal zOneHourAgo = oneHourAgo.z.setScale(2, RoundingMode.DOWN);
        BigDecimal increase = zNow.subtract(zOneHourAgo).setScale(2, RoundingMode.DOWN);
        params.put("waterLevel", zNow.toPlainString());
        params.put("increaseRate", increase.toPlainString());
        log.info("水位告警取数：stcd={} 当前[{}] TM={} Z={} 1小时前[{}] TM={} Z={} 涨幅={}",
                stcd, current.source, current.time, zNow.toPlainString(),
                oneHourAgo.source, oneHourAgo.time, zOneHourAgo.toPlainString(), increase.toPlainString());
        return true;
    }

    /**
     * 读水位快照：<b>按该站水位阈值行的 zb 字段取数——告警引擎按哪个字段判定，短信就显示哪个字段</b>。
     * <ul>
     *   <li>zb=up_z → t_auto_hltgq_water_gate 闸前水位（站点须按 site 关联，gate 表 39% 行 STCD 为 NULL）；</li>
     *   <li>zb=down_z → 同表闸后水位（如渠首进水闸 QSJSZ 配的是闸后阈值）；</li>
     *   <li>zb 空 / Z → river_info 最新有效值（COALESCE(Z, Z1)，排除哨兵值）；</li>
     *   <li>河道取不到时兜底闸前 up_z（防御：无阈值配置的闸站告警）。</li>
     * </ul>
     * <p>2026-11-13 太宿（9000000003）实证：阈值表 zb=up_z/40.1，告警引擎读 gate.up_z=42.71 触发；
     * 此前短信只查 river_info（该站无行）导致"暂无"。取不到返回 null。
     */
    private LevelSnapshot readLevelSnapshot(String stcd, LocalDateTime before) {
        String siteId = recordMapper.selectStationIdByStcd(stcd);
        String field = siteId == null ? null : recordMapper.selectLevelThresholdField(siteId);
        boolean downZ = "down_z".equalsIgnoreCase(field);
        boolean upZ = "up_z".equalsIgnoreCase(field);
        if (siteId != null && (upZ || downZ)) {
            Map<String, Object> gate = downZ
                    ? recordMapper.selectGateDownZLatestBySite(siteId, before)
                    : recordMapper.selectGateUpZLatestBySite(siteId, before);
            if (gate != null && gate.get("z") != null) {
                return snapshotOf(gate, downZ ? "闸站down_z" : "闸站up_z");
            }
            log.warn("水位告警取数：阈值 zb={} 但闸站表取不到值，回退河道口径 stcd={} before={}", field, stcd, before);
        }
        Map<String, Object> river = recordMapper.selectRiverLatestUpTo(stcd, before);
        if (river != null && river.get("z") != null) {
            return snapshotOf(river, "河道Z");
        }
        if (siteId != null) {
            Map<String, Object> gate = recordMapper.selectGateUpZLatestBySite(siteId, before);
            if (gate != null && gate.get("z") != null) {
                return snapshotOf(gate, "闸站up_z(兜底)");
            }
        }
        return null;
    }

    /** 结果行 → 水位快照（z/tm/source） */
    private LevelSnapshot snapshotOf(Map<String, Object> row, String source) {
        LevelSnapshot snapshot = new LevelSnapshot();
        snapshot.z = toDecimal(row.get("z"));
        Object tm = row.get("tm");
        if (tm instanceof java.sql.Timestamp) {
            snapshot.time = ((java.sql.Timestamp) tm).toLocalDateTime();
        } else if (tm instanceof LocalDateTime) {
            snapshot.time = (LocalDateTime) tm;
        } else {
            snapshot.time = LocalDateTime.now();
        }
        snapshot.source = source;
        return snapshot;
    }

    /** 水位快照（当前 / 1 小时前） */
    private static final class LevelSnapshot {
        private BigDecimal z;
        private LocalDateTime time;
        private String source;
    }

    /**
     * 5 分钟补偿扫描：最近窗口内未处理的阈值告警补发（回调丢失兜底）。
     * <p>与回调共用 {@link #processAlarm}（幂等/锁/静默同样生效），不会重复发送。
     */
    public void runCompensation() {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDateTime start = LocalDateTime.now().minusMinutes(properties.getCompensationLookbackMinutes());
        List<SmsCompensationAlertVO> alerts = recordMapper.selectCompensationAlerts(start);
        if (alerts.isEmpty()) {
            return;
        }
        int sentCount = 0;
        for (SmsCompensationAlertVO alert : alerts) {
            try {
                if (alert.getAlertId() == null || recordMapper.countHandledByAlertId(alert.getAlertId()) > 0) {
                    continue;
                }
                String metric = resolveMetric(alert);
                if (metric == null) {
                    log.debug("补偿跳过（无法判定指标）：alertId={} stcd={} device={}",
                            alert.getAlertId(), alert.getStcd(), alert.getDeviceName());
                    continue;
                }
                SmsAlarmResult result;
                if (METRIC_RAIN.equals(metric)) {
                    BigDecimal threshold = recordMapper.selectRainThreshold(alert.getSiteId());
                    if (threshold == null) {
                        log.warn("补偿跳过（站点未配置雨量阈值）：alertId={} stcd={}", alert.getAlertId(), alert.getStcd());
                        continue;
                    }
                    // 与 mq 回调口径对齐：hour=报文时间距水文日 08:00 起点整小时数（1~24）；
                    // 实测雨量值不拼：DRP 字段实测恒为 0（2026-11-13 全站核验），拼进模板就是假 0 错值，
                    // 补偿只拼站名（模板语义完整，宁可少显示也不出错值）
                    LocalDateTime alertTime = alert.getAlertTime() == null ? LocalDateTime.now() : alert.getAlertTime();
                    int hour = calcHydroHour(alertTime);
                    log.info("补偿雨量告警（hour={} 按水文日口径推定，实测值不拼）：alertId={} stcd={} threshold={}",
                            hour, alert.getAlertId(), alert.getStcd(), threshold);
                    result = processAlarm(alert.getAlertId(), metric, alert.getStcd(), threshold, hour, null, alertTime, true);
                } else {
                    result = processAlarm(alert.getAlertId(), metric, alert.getStcd(), null, null, null,
                            alert.getAlertTime(), true);
                }
                if (result.isSent()) {
                    sentCount++;
                }
            } catch (Exception e) {
                log.error("补偿告警短信处理异常 alertId={} err={}", alert.getAlertId(), e.toString(), e);
            }
        }
        log.info("告警短信补偿扫描完成 窗口分钟={} 候选数={} 补发成功数={}",
                properties.getCompensationLookbackMinutes(), alerts.size(), sentCount);
    }

    /** 水文日 08:00 起点整小时数（mq 回调同口径：向下取整、最少 1、上限 24） */
    private static int calcHydroHour(LocalDateTime time) {
        long hours = Duration.between(hydroDayStart(time), time).toMinutes() / 60;
        return (int) Math.max(1, Math.min(24, hours));
    }

    /** 告警时间所属水文日起点（08:00 左闭；与 StPptnRServiceImpl#hydroDayStart 同口径） */
    private static LocalDateTime hydroDayStart(LocalDateTime time) {
        return time.toLocalTime().isBefore(LocalTime.of(8, 0))
                ? time.toLocalDate().minusDays(1).atTime(8, 0)
                : time.toLocalDate().atTime(8, 0);
    }

    /**
     * 补偿指标判定（宁可少发不误发）：设备名含"雨量"/"水位"优先；
     * 设备名不可判定时按阈值表推定——仅当该站全部阈值行均为单一指标
     * （#2# 雨量 / #1# 水位）才可推定；含流量/开度等其他指标或一行多值一律跳过。
     */
    private String resolveMetric(SmsCompensationAlertVO alert) {
        String deviceName = alert.getDeviceName() == null ? "" : alert.getDeviceName();
        if (deviceName.contains("雨量")) {
            return METRIC_RAIN;
        }
        if (deviceName.contains("水位")) {
            return METRIC_WATER_LEVEL;
        }
        Map<String, Object> types = recordMapper.countThresholdTypes(alert.getSiteId());
        long total = ((Number) types.get("total")).longValue();
        long rainCount = ((Number) types.get("rain")).longValue();
        long levelCount = ((Number) types.get("level")).longValue();
        if (total > 0 && rainCount == total && levelCount == 0) {
            return METRIC_RAIN;
        }
        if (total > 0 && levelCount == total && rainCount == 0) {
            return METRIC_WATER_LEVEL;
        }
        return null;
    }

    private String resolveStationName(String stcd) {
        String name = recordMapper.selectStationName(stcd);
        return StringUtils.hasText(name) ? name : stcd;
    }

    private static String normalizeMetric(String metric) {
        if (METRIC_RAIN.equals(metric)) {
            return METRIC_RAIN;
        }
        if (METRIC_WATER_LEVEL.equals(metric)) {
            return METRIC_WATER_LEVEL;
        }
        return null;
    }

    private static BigDecimal toDecimal(Object value) {
        return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(String.valueOf(value));
    }

    /** 数值去尾零（12.50 → 12.5，30.00 → 30） */
    private static String trimZeros(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
