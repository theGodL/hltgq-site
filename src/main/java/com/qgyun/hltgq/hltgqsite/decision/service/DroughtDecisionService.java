package com.qgyun.hltgq.hltgqsite.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qgyun.hltgq.hltgqsite.decision.vo.DroughtDecisionVO;
import com.qgyun.hltgq.hltgqsite.entity.MoistureDetail;
import com.qgyun.hltgq.hltgqsite.mapper.MoistureDetailMapper;
import com.qgyun.hltgq.hltgqsite.mapper.SoilMoistureMapper;
import com.qgyun.hltgq.hltgqsite.service.SoilMoistureService;
import com.qgyun.hltgq.hltgqsite.vo.SoilMoistureTrendVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 智能抗旱决策服务（/drought-decision，2026-09 新增）。
 * <p>数据源复用：
 * <ul>
 *   <li>实测墒情：{@link SoilMoistureService#trend}（小时级完整序列，SQL 已排除 -999/-9991）；</li>
 *   <li>预测墒情：墒情预测明细表（t_auto_hltgq_water_moisture_record）——
 *   取该站点最近一次落库明细所属方案（明细仅在方案 completed 事务内落库，故必为有效方案），
 *   过滤落在查询区间内的逐小时点（含降雨与干旱等级）。</li>
 * </ul>
 * <p>数值口径：含水率 2 位小数截断补零、降雨量 2 位小数截断（业主截断规范，不四舍五入）。
 */
@Service
public class DroughtDecisionService {

    private static final Logger log = LoggerFactory.getLogger(DroughtDecisionService.class);

    /** 查询区间上限（天），防御大区间聚合 */
    private static final int MAX_RANGE_DAYS = 90;

    /** 默认查询窗口（天，向前取） */
    private static final int DEFAULT_DAYS = 14;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TM_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SoilMoistureService soilMoistureService;
    private final SoilMoistureMapper soilMoistureMapper;
    private final MoistureDetailMapper moistureDetailMapper;

    public DroughtDecisionService(SoilMoistureService soilMoistureService,
                                  SoilMoistureMapper soilMoistureMapper,
                                  MoistureDetailMapper moistureDetailMapper) {
        this.soilMoistureService = soilMoistureService;
        this.soilMoistureMapper = soilMoistureMapper;
        this.moistureDetailMapper = moistureDetailMapper;
    }

    /** 抗旱站点下拉（复用墒情监测站点，字段转契约口径 stcd/stnm） */
    public List<DroughtDecisionVO.Site> sites() {
        List<StationSiteVO> stations = soilMoistureService.sites();
        List<DroughtDecisionVO.Site> result = new ArrayList<>(stations.size());
        for (StationSiteVO s : stations) {
            DroughtDecisionVO.Site site = new DroughtDecisionVO.Site();
            site.setStcd(s.getCode());
            site.setStnm(s.getName());
            result.add(site);
        }
        return result;
    }

    /**
     * 一次查询同时返回实测 + 预测。
     *
     * @param stcd      站点编号（必填）
     * @param startDate 起始日期 yyyy-MM-dd（不传默认 14 天前）
     * @param endDate   截止日期 yyyy-MM-dd（不传默认今天）
     */
    public DroughtDecisionVO query(String stcd, LocalDate startDate, LocalDate endDate) {
        if (stcd == null || stcd.trim().isEmpty()) {
            throw new IllegalArgumentException("请选择站点");
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        if (startDate == null) {
            startDate = endDate.minusDays(DEFAULT_DAYS - 1);
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("起始日期不能晚于截止日期");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("查询区间不能超过 " + MAX_RANGE_DAYS + " 天");
        }

        LocalDateTime from = startDate.atStartOfDay();
        LocalDateTime to = endDate.atTime(LocalTime.MAX);

        DroughtDecisionVO vo = new DroughtDecisionVO();
        vo.setStcd(stcd);
        vo.setStartDate(startDate.format(DAY_FMT));
        vo.setEndDate(endDate.format(DAY_FMT));

        // 1. 实测墒情：完整小时序列（无数据小时为 null，前端断线展示）
        SoilMoistureTrendVO trend = soilMoistureService.trend(stcd, from, to);
        vo.setStnm(trend.getStnm());
        DroughtDecisionVO.Observed observed = new DroughtDecisionVO.Observed();
        List<DroughtDecisionVO.ObsPoint> obsPoints = new ArrayList<>();
        if (trend.getPoints() != null) {
            for (SoilMoistureTrendVO.HourPoint p : trend.getPoints()) {
                DroughtDecisionVO.ObsPoint point = new DroughtDecisionVO.ObsPoint();
                point.setHour(p.getHour());
                point.setMten(scale2(p.getMten()));
                point.setMtwenty(scale2(p.getMtwenty()));
                point.setMthirty(scale2(p.getMthirty()));
                obsPoints.add(point);
            }
        }
        observed.setPoints(obsPoints);
        vo.setObserved(observed);

        // 2. 预测墒情：最近一次方案中该站点的区间内明细
        DroughtDecisionVO.Forecast forecast = new DroughtDecisionVO.Forecast();
        forecast.setPoints(buildForecastPoints(stcd, from, to));
        vo.setForecast(forecast);
        return vo;
    }

    /**
     * 预测点组装：stcd → 档案表站点ID → 该站最近一条明细的 record_id → 该方案区间内明细。
     * <p>任何一环缺失（档案无 ID / 无预测明细 / 区间无点）返回空列表，前端显示「暂无预测墒情」。
     */
    private List<DroughtDecisionVO.PredPoint> buildForecastPoints(String stcd, LocalDateTime from, LocalDateTime to) {
        List<Map<String, String>> siteIds = soilMoistureMapper.selectStationIdsByStcds(
                Collections.singletonList(stcd));
        String siteId = null;
        // 防御：mapper 返回 null（异常情形）时视为无档案ID，返回空列表而非 500
        if (siteIds != null) {
            for (Map<String, String> row : siteIds) {
                if (stcd.equals(row.get("stcd")) && row.get("id") != null) {
                    siteId = row.get("id");
                    break;
                }
            }
        }
        if (siteId == null) {
            log.warn("抗旱决策：站点 {} 无档案站点ID，预测序列为空", stcd);
            return Collections.emptyList();
        }

        // 该站最近一条明细 → 所属方案（明细仅在 completed 事务内落库）
        QueryWrapper<MoistureDetail> latestWrapper = new QueryWrapper<>();
        latestWrapper.eq("\"site\"", siteId).orderByDesc("\"tm\"").last("LIMIT 1");
        List<MoistureDetail> latest = moistureDetailMapper.selectList(latestWrapper);
        if (latest == null || latest.isEmpty()) {
            return Collections.emptyList();
        }
        String recordId = latest.get(0).getRecordId();

        QueryWrapper<MoistureDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("\"record_id\"", recordId)
                .eq("\"site\"", siteId)
                .ge("\"tm\"", from)
                .le("\"tm\"", to)
                .orderByAsc("\"tm\"");
        List<MoistureDetail> details = moistureDetailMapper.selectList(wrapper);

        List<DroughtDecisionVO.PredPoint> points = new ArrayList<>(details.size());
        for (MoistureDetail d : details) {
            DroughtDecisionVO.PredPoint point = new DroughtDecisionVO.PredPoint();
            point.setTm(d.getTm() == null ? null : d.getTm().format(TM_FMT));
            point.setRainfall(scale2(d.getRainfall() == null ? null : BigDecimal.valueOf(d.getRainfall())));
            point.setMten(scale2(d.getMten() == null ? null : BigDecimal.valueOf(d.getMten())));
            point.setMtwenty(scale2(d.getMtwenty() == null ? null : BigDecimal.valueOf(d.getMtwenty())));
            point.setMthirty(scale2(d.getMthirty() == null ? null : BigDecimal.valueOf(d.getMthirty())));
            point.setDroughtLevel(d.getDroughtLevel());
            points.add(point);
        }
        return points;
    }

    /** 2 位小数截断补零（业主截断规范）；null 透传 */
    private static BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.DOWN);
    }
}
