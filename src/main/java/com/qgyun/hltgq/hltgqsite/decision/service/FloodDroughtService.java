package com.qgyun.hltgq.hltgqsite.decision.service;

import com.qgyun.hltgq.hltgqsite.decision.mapper.FloodDroughtMapper;
import com.qgyun.hltgq.hltgqsite.decision.vo.HydroHistoryVO;
import com.qgyun.hltgq.hltgqsite.decision.vo.ObsDailyVO;
import com.qgyun.hltgq.hltgqsite.decision.vo.ObsPointVO;
import com.qgyun.hltgq.hltgqsite.decision.vo.StationOptionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 防洪抗旱决策服务（会议 2 定稿版）。
 * <p>防洪页拆为两块：<b>历史实测</b>（本服务：区间逐日 + 降雨/水位/流量三序列，站点可切换，
 * 同步查询）与<b>预测块</b>（前端下拉短期预报方案、从 /water-forecast/short/{id} 详情取数，
 * 本服务不涉及）。原「一张图混拼实测/预测 + 后台自动调模型」的 hydro 编排已退役。
 * <p>实测口径沿用：雨量水文日聚合（(D-1 08:00, D 08:00] 正向增量）；水位/流量每日 8 时
 * 整点值（07:00~08:00 最后一条，无则当日最后一条）；缺数据 null 不补 0。
 */
@Service
public class FloodDroughtService {

    private static final Logger log = LoggerFactory.getLogger(FloodDroughtService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 站点类型编码：水位 / 雨量 / 流量 */
    private static final String TYPE_WATER_LEVEL = "#1#";
    private static final String TYPE_RAIN = "#2#";
    private static final String TYPE_FLOW = "#3#";

    private final FloodDroughtMapper mapper;
    private final String obsRainStcd;
    private final String obsLevelStcd;
    private final String obsFlowStcd;
    private final int maxRangeDays;

    public FloodDroughtService(FloodDroughtMapper mapper,
                               @Value("${flood-drought.obs-rain-stcd:}") String obsRainStcd,
                               @Value("${flood-drought.obs-level-stcd:}") String obsLevelStcd,
                               @Value("${flood-drought.obs-flow-stcd:}") String obsFlowStcd,
                               @Value("${flood-drought.max-range-days:90}") int maxRangeDays) {
        this.mapper = mapper;
        this.obsRainStcd = trimToNull(obsRainStcd);
        this.obsLevelStcd = trimToNull(obsLevelStcd);
        this.obsFlowStcd = trimToNull(obsFlowStcd);
        this.maxRangeDays = maxRangeDays;
    }

    // ==================== 可切换候选站 ====================

    /**
     * 候选站列表（前端下拉）：按站点类型档案 epjutj 过滤，分水位/雨量/流量三组。
     * 前端默认选中项 = 本服务 resolveStcd 结果（配置优先、未配置按类型自动选站）。
     */
    public Map<String, Object> stations() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("waterLevel", mapper.selectStationsByType(TYPE_WATER_LEVEL));
        result.put("rain", mapper.selectStationsByType(TYPE_RAIN));
        result.put("flow", mapper.selectStationsByType(TYPE_FLOW));
        return result;
    }

    // ==================== 历史实测（同步） ====================

    /**
     * 历史实测块：区间逐日 + 降雨/水位/流量三序列（各自站点可切换）。
     *
     * @param startDate 起始日期（含，必填）
     * @param endDate   截止日期（含，必填）；endDate &lt; startDate → 400；区间 &gt; maxRangeDays → 400
     * @param levelStcd 水位站（可选；不传按配置，未配置按类型自动选站）
     * @param flowStcd  流量站（可选；同上）
     * @param rainStcd  雨量站（可选；同上）
     */
    public HydroHistoryVO history(LocalDate startDate, LocalDate endDate,
                                  String levelStcd, String flowStcd, String rainStcd) {
        validateRange(startDate, endDate);

        int n = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<String> dates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            dates.add(startDate.plusDays(i).format(DATE_FMT));
        }

        // 站点解析：入参优先 → 配置其次 → 按类型自动选站；均无则该序列全 null
        String rainStcdResolved = resolveStcd(trimToNull(rainStcd), obsRainStcd, TYPE_RAIN);
        String levelStcdResolved = resolveStcd(trimToNull(levelStcd), obsLevelStcd, TYPE_WATER_LEVEL);
        String flowStcdResolved = resolveStcd(trimToNull(flowStcd), obsFlowStcd, TYPE_FLOW);

        HydroHistoryVO vo = new HydroHistoryVO();
        vo.setDates(dates);
        vo.setRain(buildRainSeries(rainStcdResolved, startDate, endDate, dates));
        vo.setLevel(buildPointSeries(levelStcdResolved, startDate, endDate, dates, true));
        vo.setFlow(buildPointSeries(flowStcdResolved, startDate, endDate, dates, false));
        return vo;
    }

    /** 雨量序列（水文日聚合，1 位小数）。 */
    private HydroHistoryVO.SeriesVO buildRainSeries(String stcd, LocalDate start, LocalDate end,
                                                    List<String> dates) {
        List<Double> values = new ArrayList<>(Collections.nCopies(dates.size(), null));
        if (stcd != null) {
            // 标签 D = (D-1 08:00, D 08:00]：窗口前后各扩 1 天覆盖全部目标标签样本
            LocalDateTime winStart = start.minusDays(1).atTime(8, 0);
            LocalDateTime winEnd = end.plusDays(1).atTime(8, 0);
            Map<LocalDate, Double> daily = new HashMap<>();
            for (ObsDailyVO row : mapper.selectRainDaily(stcd, winStart, winEnd)) {
                if (row.getD() != null && row.getValue() != null) {
                    daily.put(row.getD().toLocalDate(), row.getValue());
                }
            }
            for (int i = 0; i < dates.size(); i++) {
                Double v = daily.get(LocalDate.parse(dates.get(i), DATE_FMT));
                values.set(i, v == null ? null : round1(v));
            }
        }
        return newSeries(stcd, values);
    }

    /** 水位/流量序列（每日 8 时整点值，3 位小数）。 */
    private HydroHistoryVO.SeriesVO buildPointSeries(String stcd, LocalDate start, LocalDate end,
                                                     List<String> dates, boolean isLevel) {
        List<Double> values = new ArrayList<>(Collections.nCopies(dates.size(), null));
        if (stcd != null) {
            List<ObsPointVO> points = isLevel
                    ? mapper.selectLevelPoints(stcd, start.atStartOfDay(), end.atTime(LocalTime.MAX))
                    : mapper.selectFlowPoints(stcd, start.atStartOfDay(), end.atTime(LocalTime.MAX));
            Map<LocalDate, List<ObsPointVO>> byDay = new HashMap<>();
            for (ObsPointVO p : points) {
                if (p.getTm() == null || p.getValue() == null) {
                    continue;
                }
                byDay.computeIfAbsent(p.getTm().toLocalDate(), k -> new ArrayList<>()).add(p);
            }
            for (int i = 0; i < dates.size(); i++) {
                List<ObsPointVO> dayPoints = byDay.get(LocalDate.parse(dates.get(i), DATE_FMT));
                if (dayPoints == null || dayPoints.isEmpty()) {
                    continue;
                }
                values.set(i, round3(pickAtEight(dayPoints).getValue()));
            }
        }
        return newSeries(stcd, values);
    }

    /** 组装单站点序列；站点无数据源（null stcd）时 stnm 同 stcd 为空。 */
    private HydroHistoryVO.SeriesVO newSeries(String stcd, List<Double> values) {
        HydroHistoryVO.SeriesVO series = new HydroHistoryVO.SeriesVO();
        series.setStcd(stcd);
        String stnm = stcd == null ? null : mapper.selectStationName(stcd);
        series.setStnm(stnm != null ? stnm : stcd);
        series.setValues(values);
        return series;
    }

    /** 按 tm 升序输入：窗口 (07:00, 08:00] 内最后一条；无则当日最后一条。 */
    private ObsPointVO pickAtEight(List<ObsPointVO> dayPoints) {
        ObsPointVO fallback = null;
        ObsPointVO atEight = null;
        for (ObsPointVO p : dayPoints) {
            fallback = p;
            LocalTime t = p.getTm().toLocalTime();
            if (t.isAfter(LocalTime.of(7, 0)) && !t.isAfter(LocalTime.of(8, 0))) {
                atEight = p;
            }
        }
        return atEight != null ? atEight : fallback;
    }

    // ==================== 站点解析与参数校验 ====================

    /** 入参优先 → 配置其次 → 按站点类型自动选站；均无返回 null（对应序列全 null）。 */
    private String resolveStcd(String requested, String configured, String typeCode) {
        if (requested != null) {
            return requested;
        }
        if (configured != null) {
            return configured;
        }
        String auto = mapper.selectStationByType(typeCode);
        if (auto == null) {
            log.warn("防洪抗旱：类型 {} 无候选站，对应序列为空", typeCode);
        }
        return auto;
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("起止日期不能为空（格式 YYYY-MM-DD）");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("截止日期不得早于起始日期");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > maxRangeDays) {
            throw new IllegalArgumentException("统计区间不得超过 " + maxRangeDays + " 天");
        }
    }

    // ==================== 工具 ====================

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
