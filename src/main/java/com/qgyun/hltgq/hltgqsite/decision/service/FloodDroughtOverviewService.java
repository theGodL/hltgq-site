package com.qgyun.hltgq.hltgqsite.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qgyun.hltgq.hltgqsite.decision.vo.HydroHistoryVO;
import com.qgyun.hltgq.hltgqsite.entity.ShortForecastDaily;
import com.qgyun.hltgq.hltgqsite.entity.ShortForecastRecord;
import com.qgyun.hltgq.hltgqsite.mapper.ShortForecastDailyMapper;
import com.qgyun.hltgq.hltgqsite.mapper.ShortForecastRecordMapper;
import com.qgyun.hltgq.hltgqsite.model.service.ModelRecordCommonService;
import com.qgyun.hltgq.hltgqsite.model.service.ShortForecastService;
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.ShortForecastRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 防洪决策页「进入即见数据」概览（2026-09-13）。
 * <p>背景：页面进页默认不查询（避免打开即跑模型、每次刷新堆新方案），首屏只有空图需手动点「计算」。
 * 本服务把「默认区间完整图（实测 + 预测）」常备化，思路对齐数据统计缓存预热：
 * <ul>
 *   <li><b>读路径</b> {@link #overview()}（GET /flood-drought/overview）：实时组装实测段（本地快查）
 *       与预置预测段（已落库记录日聚合），毫秒级返回、绝不触发模型；</li>
 *   <li><b>预跑守护</b> {@link #ensurePreset()}：定时检查默认预测窗口是否有可用短期预报记录，
 *       缺失/过旧时按页面同款参数提交一次（方案名前缀「智能防洪预置_」，异步执行、秒回不阻塞）。</li>
 * </ul>
 * <p>窗口口径与页面点「计算」完全一致：[明日 08:00, 今日+13 08:00]（覆盖展示末日全天）；
 * 复用判定为窗口全覆盖（start_date ≤ 窗口起始 且 end_date ≥ 窗口结束），
 * 用户手动提交的同窗口方案同样会被概览复用（取最新一条），避免重复计算、方案不堆积。
 * <p>「先给老数据、后台再刷新」：展示永远取最新可用记录，跨天过渡期旧记录不完整覆盖时
 * 仍按其完整覆盖的日先展示（见 predMeta.partial），首屏不出现空预测；
 * 预跑每 4 小时检查一轮、结果超过 refresh-hours 即重新演算（上午/下午各时段进页均见新一轮预测），
 * 过期预置方案由 {@link #cleanupOldPresets()} 按 cleanup-hours 软删，方案列表不被整日堆积。
 */
@Service
public class FloodDroughtOverviewService {

    private static final Logger log = LoggerFactory.getLogger(FloodDroughtOverviewService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 实测回看天数（页面默认区间今日-2 起） */
    private static final int OBS_LOOKBACK_DAYS = 2;

    /** 预测前展天数（页面默认区间至今日+12） */
    private static final int PRED_LOOKAHEAD_DAYS = 12;

    /** 每日分界时刻（水文日 08:00，预测窗口对齐口径） */
    private static final LocalTime DAILY_SPLIT_TIME = LocalTime.of(8, 0);

    /** 预置方案名前缀（供方案列表辨识；复用判定不依赖名称） */
    private static final String PRESET_SCHEME_PREFIX = "智能防洪预置_";

    @Value("${flood-drought.overview.enabled:true}")
    private boolean enabled;

    @Value("${flood-drought.overview.refresh-hours:3}")
    private long refreshHours;

    /** 预置方案保鲜期（小时）：超过即软删；0 = 不清理 */
    @Value("${flood-drought.overview.cleanup-hours:48}")
    private long cleanupHours;

    private final FloodDroughtService floodDroughtService;
    private final ShortForecastService shortForecastService;
    private final ShortForecastRecordMapper recordMapper;
    private final ShortForecastDailyMapper dailyMapper;

    public FloodDroughtOverviewService(FloodDroughtService floodDroughtService,
                                       ShortForecastService shortForecastService,
                                       ShortForecastRecordMapper recordMapper,
                                       ShortForecastDailyMapper dailyMapper) {
        this.floodDroughtService = floodDroughtService;
        this.shortForecastService = shortForecastService;
        this.recordMapper = recordMapper;
        this.dailyMapper = dailyMapper;
    }

    // ==================== 读路径：概览组装 ====================

    /**
     * 默认区间完整图（实测 + 预测）：
     * <p>实测段 = history(今日-2, 今日)（同步快查，站点按配置解析）；
     * 预测段 = 窗口 [明日 08:00, 今日+13 08:00] 最新全覆盖记录的逐小时明细
     * 按日聚合（口径镜像前端 aggregateForecastDaily：雨量和、水位/流量取 08:00 值否则当日最后值）。
     * <p>predMeta：tag=available（有预测，带 generatedAt）/ generating（计算中且暂无可展示结果）/ none；
     * refreshing=是否有计算中任务（无论是否已可展示）；partial=当前预测是否未完整覆盖展示区间。
     */
    public Map<String, Object> overview() {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(OBS_LOOKBACK_DAYS);
        LocalDate end = today.plusDays(PRED_LOOKAHEAD_DAYS);

        HydroHistoryVO hist = floodDroughtService.history(start, today, null, null, null);
        List<String> histDates = hist.getDates();
        Map<String, Object> rainMap = toDayMap(histDates, hist.getRain() == null ? null : hist.getRain().getValues());
        Map<String, Object> levelMap = toDayMap(histDates, hist.getLevel() == null ? null : hist.getLevel().getValues());
        Map<String, Object> flowMap = toDayMap(histDates, hist.getFlow() == null ? null : hist.getFlow().getValues());

        // x 轴 = 完整展示区间（今日-2 ~ 今日+12）逐日序列：实测段在前、预测段在后，
        // 预测数据未就绪的日子自动留白（前端按 dates 逐日取值渲染，dates 缺日则预测段不可见）
        List<String> dates = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            dates.add(day.format(DATE_FMT));
        }

        String predTag = "none";
        String recordId = null;
        String generatedAt = null;
        boolean partial = false;
        LocalDateTime winStart = winStart(today);
        LocalDateTime winEnd = winEnd(today);
        ShortForecastRecord record = selectCoveringRecord(winStart, winEnd,
                ModelRecordCommonService.STATUS_COMPLETED);
        if (record == null) {
            // 跨天过渡兜底：旧记录（如昨日预置）不覆盖整个窗口时也先展示，防止首屏出现空预测
            record = selectLatestUsableRecord(winStart, end, ModelRecordCommonService.STATUS_COMPLETED);
        }
        if (record != null) {
            recordId = record.getId();
            generatedAt = record.getCreatedAt() == null ? null : record.getCreatedAt().format(HOUR_FMT);
            partial = (record.getStartDate() != null && record.getStartDate().isAfter(winStart))
                    || (record.getEndDate() != null && record.getEndDate().isBefore(winEnd));
            Map<LocalDate, DailyAcc> predDaily = aggregateForecastDaily(loadDailies(record.getId()));
            for (LocalDate day = today.plusDays(1); !day.isAfter(end); day = day.plusDays(1)) {
                // 日中段截断（记录未从当日 08:00 前起或未覆盖到 23:00）：宁留白不展示失真值
                if (record.getStartDate() == null
                        || record.getStartDate().isAfter(day.atTime(DAILY_SPLIT_TIME))
                        || record.getEndDate() == null
                        || record.getEndDate().isBefore(day.atTime(23, 0))) {
                    continue;
                }
                DailyAcc acc = predDaily.get(day);
                if (acc == null) {
                    continue;
                }
                String key = day.format(DATE_FMT);
                if (acc.rainHit) {
                    rainMap.put(key, Math.round(acc.rain * 10.0) / 10.0);
                }
                Double level = acc.level08 != null ? acc.level08 : acc.level;
                if (level != null) {
                    levelMap.put(key, level);
                }
                Double flow = acc.flow08 != null ? acc.flow08 : acc.flow;
                if (flow != null) {
                    flowMap.put(key, flow);
                }
            }
            predTag = "available";
        }
        boolean refreshing = selectCoveringRecord(winStart, winEnd,
                ModelRecordCommonService.STATUS_CALCULATING) != null;
        if (record == null && refreshing) {
            predTag = "generating";
        }

        Map<String, Object> predMeta = new LinkedHashMap<>();
        predMeta.put("tag", predTag);
        predMeta.put("refreshing", refreshing);
        predMeta.put("partial", partial);
        predMeta.put("recordId", recordId);
        predMeta.put("generatedAt", generatedAt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("splitDate", today.format(DATE_FMT));
        result.put("rainMap", rainMap);
        result.put("levelMap", levelMap);
        result.put("flowMap", flowMap);
        result.put("stnm", hist.getLevel() == null ? null : hist.getLevel().getStnm());
        result.put("predMeta", predMeta);
        return result;
    }

    // ==================== 预跑守护 ====================

    /**
     * 预跑守护：默认预测窗口内无「计算中或足够新鲜已完成」记录时，按页面同款参数提交一次预置方案。
     * <p>触发频率：启动 60s 后首查，之后默认每 4 小时一轮（配置项 warm-delay-ms），
     * 结果超过 refresh-hours 即重算，保证上午/下午各时段进页看到的都是新一轮预测；
     * 每轮仅几次索引查询、提交为异步秒回（与手动提交同队列串行，单日模型计算约 6 次、每次约 20s），
     * 新鲜结果轮次直接复用跳过；顺带清理超期预置方案（cleanupOldPresets）。
     * <p>异常仅告警等下一轮（调度任务不中断）；关闭开关只停预跑，overview 读路径不受影响。
     */
    @Scheduled(initialDelayString = "${flood-drought.overview.warm-initial-delay-ms:60000}",
            fixedDelayString = "${flood-drought.overview.warm-delay-ms:14400000}")
    public void ensurePreset() {
        if (!enabled) {
            return;
        }
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime winStart = winStart(today);
            LocalDateTime winEnd = winEnd(today);

            cleanupOldPresets();

            if (selectCoveringRecord(winStart, winEnd, ModelRecordCommonService.STATUS_CALCULATING) != null) {
                log.info("防洪概览预跑：窗口内已有计算中方案，本轮跳过");
                return;
            }
            ShortForecastRecord fresh = selectCoveringRecord(winStart, winEnd,
                    ModelRecordCommonService.STATUS_COMPLETED);
            if (fresh != null && fresh.getCreatedAt() != null
                    && fresh.getCreatedAt().isAfter(LocalDateTime.now().minusHours(refreshHours))) {
                log.info("防洪概览预跑：窗口内已有 {} 小时内结果（recordId={}），复用跳过",
                        refreshHours, fresh.getId());
                return;
            }

            ShortForecastRequest req = new ShortForecastRequest();
            req.setStart(winStart.format(HOUR_FMT));
            req.setEnd(winEnd.format(HOUR_FMT));
            req.setEnablePower(false);
            req.setEnableTunnel(false);
            req.setEnableSpillway(false);
            req.setSchemeName(PRESET_SCHEME_PREFIX + today.format(DATE_FMT));
            String recordId = shortForecastService.submit(req);
            log.info("防洪概览预跑已提交：recordId={}, 窗口 {} ~ {}（模型异步执行，完成后页面即见）",
                    recordId, req.getStart(), req.getEnd());
        } catch (Exception e) {
            log.warn("防洪概览预跑失败（等下一轮）：{}", e.getMessage());
        }
    }

    /**
     * 软删过期预置方案（名称前缀匹配 + 超过 cleanup-hours，cleanup-hours=0 关闭）：
     * 控制方案列表长度；软删后 del_flag=#1#，overview 查询始终按 #2# 过滤、不受影响。
     */
    private void cleanupOldPresets() {
        if (cleanupHours <= 0) {
            return;
        }
        QueryWrapper<ShortForecastRecord> wrapper = new QueryWrapper<>();
        wrapper.likeRight("\"scheme_name\"", PRESET_SCHEME_PREFIX)
                .eq("\"del_flag\"", BoolTextUtils.FALSE)
                .lt("\"created_at\"", LocalDateTime.now().minusHours(cleanupHours));
        List<ShortForecastRecord> olds = recordMapper.selectList(wrapper);
        if (olds.isEmpty()) {
            return;
        }
        for (ShortForecastRecord old : olds) {
            ShortForecastRecord patch = new ShortForecastRecord();
            patch.setId(old.getId());
            patch.setDelFlag(BoolTextUtils.TRUE);
            patch.setUpdatedAt(LocalDateTime.now());
            recordMapper.updateById(patch);
        }
        log.info("防洪概览预跑：软删 {} 条过期预置方案（>{}h）", olds.size(), cleanupHours);
    }

    private static LocalDateTime winStart(LocalDate today) {
        return today.plusDays(1).atTime(DAILY_SPLIT_TIME);
    }

    private static LocalDateTime winEnd(LocalDate today) {
        return today.plusDays(PRED_LOOKAHEAD_DAYS + 1).atTime(DAILY_SPLIT_TIME);
    }

    // ==================== 查询与聚合 ====================

    /** 窗口全覆盖记录（del_flag=#2# + 指定状态，最新在前取第一条）；无则 null。 */
    private ShortForecastRecord selectCoveringRecord(LocalDateTime winStart, LocalDateTime winEnd, String status) {
        QueryWrapper<ShortForecastRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("\"del_flag\"", BoolTextUtils.FALSE)
                .eq("\"status\"", status)
                .le("\"start_date\"", winStart)
                .ge("\"end_date\"", winEnd)
                .orderByDesc("\"created_at\"");
        List<ShortForecastRecord> list = recordMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 可展示记录（宽松条件，跨天过渡兜底）：start_date ≤ 展示末日 08:00 且 end_date ≥ 明日 08:00，
     * 最新在前取第一条；输出层再按「完整覆盖的日」逐日过滤，宁留白不展示失真值。
     */
    private ShortForecastRecord selectLatestUsableRecord(LocalDateTime winStart, LocalDate end, String status) {
        QueryWrapper<ShortForecastRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("\"del_flag\"", BoolTextUtils.FALSE)
                .eq("\"status\"", status)
                .le("\"start_date\"", end.atTime(DAILY_SPLIT_TIME))
                .ge("\"end_date\"", winStart)
                .orderByDesc("\"created_at\"");
        List<ShortForecastRecord> list = recordMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    private List<ShortForecastDaily> loadDailies(String recordId) {
        QueryWrapper<ShortForecastDaily> wrapper = new QueryWrapper<>();
        wrapper.eq("\"record_id\"", recordId).orderByAsc("\"forecast_date\"");
        return dailyMapper.selectList(wrapper);
    }

    /** 逐日聚合暂存（镜像前端 aggregateForecastDaily 的取值口径）。 */
    private static class DailyAcc {
        double rain;
        boolean rainHit;
        Double level;
        Double flow;
        Double level08;
        Double flow08;
    }

    /**
     * 短期预报逐小时明细 → 按日聚合：雨量和（1 位小数）；水位/流量优先 08:00 值、
     * 否则当日最后值；流量取 outflowRate 缺失回退 inflowRate。输入需按 forecast_date 升序。
     */
    private Map<LocalDate, DailyAcc> aggregateForecastDaily(List<ShortForecastDaily> dailies) {
        Map<LocalDate, DailyAcc> map = new HashMap<>();
        for (ShortForecastDaily d : dailies) {
            LocalDateTime time = d.getForecastDate();
            if (time == null) {
                continue;
            }
            LocalDate day = time.toLocalDate();
            DailyAcc acc = map.computeIfAbsent(day, k -> new DailyAcc());
            Double rain = d.getRainfall();
            if (rain != null) {
                acc.rain += rain;
                acc.rainHit = true;
            }
            boolean atEight = time.toLocalTime().equals(DAILY_SPLIT_TIME);
            Double level = d.getWaterLevel();
            if (level != null) {
                acc.level = level;
                if (atEight) {
                    acc.level08 = level;
                }
            }
            Double flow = d.getOutflowRate() != null ? d.getOutflowRate() : d.getInflowRate();
            if (flow != null) {
                acc.flow = flow;
                if (atEight) {
                    acc.flow08 = flow;
                }
            }
        }
        return map;
    }

    /** dates/values 等长序列 → 逐日映射（缺数据不下发，前端按 undefined 断线处理）。 */
    private static Map<String, Object> toDayMap(List<String> dates, List<Double> values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < dates.size(); i++) {
            Double v = values != null && i < values.size() ? values.get(i) : null;
            if (v != null) {
                map.put(dates.get(i), v);
            }
        }
        return map;
    }
}
