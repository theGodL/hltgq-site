package com.qgyun.hltgq.hltgqsite.irrigation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qgyun.hltgq.hltgqsite.entity.DemandAreaSummary;
import com.qgyun.hltgq.hltgqsite.entity.DemandRecord;
import com.qgyun.hltgq.hltgqsite.irrigation.config.IrrigationStationConfig;
import com.qgyun.hltgq.hltgqsite.irrigation.mapper.IrrigationWaterMapper;
import com.qgyun.hltgq.hltgqsite.irrigation.vo.IrrigationIntervalVO;
import com.qgyun.hltgq.hltgqsite.irrigation.vo.IrrigationSchemeOptionVO;
import com.qgyun.hltgq.hltgqsite.irrigation.vo.IrrigationSummaryVO;
import com.qgyun.hltgq.hltgqsite.mapper.DemandAreaSummaryMapper;
import com.qgyun.hltgq.hltgqsite.mapper.DemandRecordMapper;
import com.qgyun.hltgq.hltgqsite.model.service.ModelRecordCommonService;
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
import com.qgyun.hltgq.hltgqsite.model.util.TenDayDateUtils;
import com.qgyun.hltgq.hltgqsite.model.util.TenDayMapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 灌溉用水服务：需水方案 × 四县（宿松/怀宁/望江/太湖）需水量、实际供水量与保证率计算。
 *
 * <p>计算口径：
 * <ul>
 *   <li>需水量 = 该县对应片区（太宿干渠/太怀灌区/南干渠+北干渠/总干渠）18 旬求和（万m³）</li>
 *   <li>实际供水量 = 该县需水时间窗口内的区间累计流量（= 末行 ttf − 起点前最近 ttf，与流量监测同口径），
 *       站点口径见 {@link IrrigationStationConfig}；太湖为总量扣减口径（含三电站与损耗）</li>
 *   <li>实际保证率 = 实际供水量 ÷ 需水量 × 100（万m³ 口径相除）；灌溉进度暂同实际保证率（产品稿）</li>
 *   <li>部件站点无数据时供水量记 null 并在 missingData 说明（设备未接入/窗口内无流量数据），不做近似估算</li>
 * </ul>
 *
 * <p>计算年：请求参数 year 优先，缺省取方案创建年（方案在灌溉季内生成，与实际监测数据同季）。
 */
@Service
public class IrrigationWaterService {

    private static final Logger log = LoggerFactory.getLogger(IrrigationWaterService.class);

    /** 单次最多查询方案数（区间取数防御限制） */
    private static final int MAX_SCHEME_COUNT = 10;

    /** 设备哨兵值：-999 设备不存在、-9991 设备异常；区间累计场景下均视为缺失 */
    private static final BigDecimal DEVICE_MISSING = new BigDecimal("-999");
    private static final BigDecimal DEVICE_ERROR = new BigDecimal("-9991");

    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /** 旬标签格式："5月上旬" / "10月下旬" */
    private static final Pattern TENDAY_PATTERN = Pattern.compile("^(\\d{1,2})月(上|中|下)旬$");

    @Autowired
    private IrrigationWaterMapper irrigationWaterMapper;

    @Autowired
    private DemandRecordMapper demandRecordMapper;

    @Autowired
    private DemandAreaSummaryMapper demandAreaSummaryMapper;

    @Autowired
    private Environment environment;

    /** 需水方案下拉选项（仅已完成方案，按创建时间倒序） */
    public List<IrrigationSchemeOptionVO> schemes(Integer year) {
        QueryWrapper<DemandRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("\"del_flag\"", BoolTextUtils.FALSE)
                .eq("\"status\"", ModelRecordCommonService.STATUS_COMPLETED)
                .orderByDesc("\"created_at\"");
        List<DemandRecord> records = demandRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = records.stream().map(DemandRecord::getId).collect(Collectors.toList());
        Map<String, List<DemandAreaSummary>> areasByRecord = loadAreas(ids);

        List<IrrigationSchemeOptionVO> result = new ArrayList<>();
        for (DemandRecord record : records) {
            List<DemandAreaSummary> areas = areasByRecord.getOrDefault(record.getId(), Collections.emptyList());
            Integer[] range = tendayRange(areas);
            int resolvedYear = resolveYear(year, record);
            String startLabel = range != null ? TenDayMapUtils.labelOf(range[0]) : null;
            String endLabel = range != null ? TenDayMapUtils.labelOf(range[1]) : null;

            IrrigationSchemeOptionVO vo = new IrrigationSchemeOptionVO();
            vo.setSchemeId(record.getId());
            vo.setSchemeName(record.getSchemeName());
            vo.setGuaranteeRate(record.getGuaranteeRate());
            vo.setYear(resolvedYear);
            vo.setStartTenday(startLabel);
            vo.setEndTenday(endLabel);
            vo.setPeriodStart(formatDate(tendayFirstDate(startLabel, resolvedYear)));
            vo.setPeriodEnd(formatDate(tendayLastDate(endLabel, resolvedYear)));
            vo.setTotalDemand(record.getTotalDemand());
            vo.setDistrictCount(IrrigationStationConfig.COUNTIES.size());
            result.add(vo);
        }
        return result;
    }

    /** 灌溉用水汇总：选中方案 × 四县，含需水量/供水量/设计保证率/实际保证率/灌溉进度 */
    public List<IrrigationSummaryVO> summary(List<String> schemeIds, Integer year) {
        List<String> ids = normalizeSchemeIds(schemeIds);

        List<DemandRecord> records = demandRecordMapper.selectBatchIds(ids);
        Map<String, DemandRecord> byId = new LinkedHashMap<>();
        for (DemandRecord record : records) {
            byId.put(record.getId(), record);
        }
        List<String> notFound = ids.stream().filter(id -> !byId.containsKey(id)).collect(Collectors.toList());
        if (!notFound.isEmpty()) {
            throw new IllegalArgumentException("需水方案不存在: " + String.join(", ", notFound));
        }

        Map<String, List<DemandAreaSummary>> areasByRecord = loadAreas(ids);
        BigDecimal lossM3 = resolveLoss();
        // 窗口 → 站点标识 → 区间累计流量(m³)：多方案同一窗口时复用一次取数
        Map<String, Map<String, BigDecimal>> windowCache = new HashMap<>();

        List<IrrigationSummaryVO> result = new ArrayList<>();
        for (String id : ids) {
            DemandRecord record = byId.get(id);
            if (BoolTextUtils.TRUE.equals(record.getDelFlag())) {
                throw new IllegalArgumentException("需水方案已删除: " + id);
            }
            int resolvedYear = resolveYear(year, record);
            List<DemandAreaSummary> areas = areasByRecord.getOrDefault(id, Collections.emptyList());
            Integer[] schemeRange = tendayRange(areas);
            String schemeStartLabel = schemeRange != null ? TenDayMapUtils.labelOf(schemeRange[0]) : null;
            String schemeEndLabel = schemeRange != null ? TenDayMapUtils.labelOf(schemeRange[1]) : null;

            IrrigationSummaryVO vo = new IrrigationSummaryVO();
            vo.setSchemeId(id);
            vo.setSchemeName(record.getSchemeName());
            vo.setGuaranteeRate(record.getGuaranteeRate());
            vo.setYear(resolvedYear);
            vo.setStartTenday(schemeStartLabel);
            vo.setEndTenday(schemeEndLabel);
            vo.setPeriodStart(formatDate(tendayFirstDate(schemeStartLabel, resolvedYear)));
            vo.setPeriodEnd(formatDate(tendayLastDate(schemeEndLabel, resolvedYear)));

            List<IrrigationSummaryVO.CountyRow> rows = new ArrayList<>();
            for (IrrigationStationConfig.CountySupply county : IrrigationStationConfig.COUNTIES) {
                rows.add(buildCountyRow(county, areas, schemeRange, resolvedYear, lossM3, windowCache));
            }
            vo.setRows(rows);
            result.add(vo);

            for (IrrigationSummaryVO.CountyRow row : rows) {
                log.info("[灌溉用水] 方案={}({}) 年={} 县={} 窗口={} ~ {} 需水={}万m³ 供水={}万m³ 设计保证率={}% 实际保证率={}% 缺失={}",
                        record.getSchemeName(), id, resolvedYear, row.getDistrict(),
                        row.getPeriodStart(), row.getPeriodEnd(), row.getDemandVolume(),
                        row.getSupplyVolume(), row.getDesignGuaranteeRate(),
                        row.getActualGuaranteeRate(), row.getMissingData());
            }
        }
        return result;
    }

    /** 构建单个县行：需水量 + 需水时间 + 实际供水量（口径站点取数）+ 保证率 */
    private IrrigationSummaryVO.CountyRow buildCountyRow(IrrigationStationConfig.CountySupply county,
                                                         List<DemandAreaSummary> schemeAreas,
                                                         Integer[] schemeRange,
                                                         int year,
                                                         BigDecimal lossM3,
                                                         Map<String, Map<String, BigDecimal>> windowCache) {
        List<DemandAreaSummary> areaRows = schemeAreas.stream()
                .filter(area -> county.demandAreas.contains(area.getAreaName()))
                .collect(Collectors.toList());

        IrrigationSummaryVO.CountyRow row = new IrrigationSummaryVO.CountyRow();
        row.setDistrict(county.district);

        // 需水量：该县片区全部旬求和（万m³）
        BigDecimal demand = null;
        for (DemandAreaSummary area : areaRows) {
            if (area.getDemandVolume() == null) {
                continue;
            }
            demand = (demand == null ? BigDecimal.ZERO : demand).add(BigDecimal.valueOf(area.getDemandVolume()));
        }
        if (demand != null) {
            demand = demand.setScale(2, RoundingMode.DOWN);
        }
        row.setDemandVolume(demand);

        // 需水时间：该县片区 volume>0 的首末旬；无则回退方案整体范围
        Integer[] range = tendayRange(areaRows);
        if (range == null) {
            range = schemeRange;
        }
        String startLabel = range != null ? TenDayMapUtils.labelOf(range[0]) : null;
        String endLabel = range != null ? TenDayMapUtils.labelOf(range[1]) : null;
        row.setStartTenday(startLabel);
        row.setEndTenday(endLabel);
        LocalDate firstDate = tendayFirstDate(startLabel, year);
        LocalDate lastDate = tendayLastDate(endLabel, year);
        row.setPeriodStart(formatDate(firstDate));
        row.setPeriodEnd(formatDate(lastDate));

        // 实际供水量（m³ → 万m³，2 位截断）
        List<String> missing = new ArrayList<>();
        BigDecimal supplyM3 = null;
        if (firstDate == null || lastDate == null) {
            missing.add("需水时间无法解析");
        } else {
            SupplyResult supply = computeSupply(county, firstDate.atStartOfDay(),
                    lastDate.atTime(23, 59, 59), windowCache, lossM3);
            supplyM3 = supply.volume;
            missing.addAll(supply.missing);
        }
        BigDecimal supplyWan = supplyM3 != null
                ? supplyM3.divide(TEN_THOUSAND, 2, RoundingMode.DOWN) : null;
        row.setSupplyVolume(supplyWan);

        // 设计保证率（逐县配置，业务定稿前用产品稿默认值）
        row.setDesignGuaranteeRate(new BigDecimal(
                environment.getProperty(county.designRateProperty, county.designRateDefault).trim()));

        // 实际保证率 = 供水量 ÷ 需水量 × 100（1 位截断）；灌溉进度暂同实际保证率（产品稿口径）
        BigDecimal rate = null;
        if (supplyWan != null && demand != null && demand.signum() > 0) {
            rate = supplyWan.divide(demand, 6, RoundingMode.DOWN)
                    .multiply(ONE_HUNDRED)
                    .setScale(1, RoundingMode.DOWN);
        }
        row.setActualGuaranteeRate(rate);
        row.setIrrigationProgress(rate);
        row.setMissingData(missing);
        return row;
    }

    /**
     * 县口径供水量计算：
     * 相加口径（宿松/怀宁/望江）= 站点组区间累计求和；
     * 总量扣减口径（太湖）= 渠首总 − 各县取水 − 三电站 − 损耗。
     * 任一必需部件缺失 → 返回 null（由 missing 说明原因），不做近似。
     */
    private SupplyResult computeSupply(IrrigationStationConfig.CountySupply county,
                                       LocalDateTime start, LocalDateTime end,
                                       Map<String, Map<String, BigDecimal>> windowCache,
                                       BigDecimal lossM3) {
        String windowKey = start + "|" + end;
        Map<String, BigDecimal> volumes = windowCache.computeIfAbsent(windowKey, key -> queryIntervals(start, end));
        List<String> missing = new ArrayList<>();

        if (county.totalStation != null) {
            // 太湖县：渠首总流量 − 扣减站组 − 损耗
            BigDecimal total = stationVolume(county.totalStation, volumes, missing);
            BigDecimal deduct = BigDecimal.ZERO;
            for (IrrigationStationConfig.Station station : county.minusStations) {
                BigDecimal value = stationVolume(station, volumes, missing);
                if (value != null) {
                    deduct = deduct.add(value);
                }
            }
            if (total == null || !missing.isEmpty()) {
                return new SupplyResult(null, missing);
            }
            BigDecimal supply = total.subtract(deduct).subtract(lossM3);
            if (supply.signum() < 0) {
                log.warn("[灌溉用水] 太湖县扣减后供水量为负（{} m³），请核对各站数据与损耗配置", supply);
            }
            return new SupplyResult(supply, missing);
        }

        // 相加口径
        BigDecimal sum = null;
        for (IrrigationStationConfig.Station station : county.plusStations) {
            BigDecimal value = stationVolume(station, volumes, missing);
            if (value != null) {
                sum = (sum == null) ? value : sum.add(value);
            }
        }
        if (sum == null && missing.isEmpty()) {
            missing.add("未配置取数站点");
        }
        return (sum == null || !missing.isEmpty()) ? new SupplyResult(null, missing) : new SupplyResult(sum, missing);
    }

    /** 站点组取值：候选标识任一命中即取数（多候选=同一物理站的 stcd/UUID 双键兜底）；全无数据记缺失 */
    private BigDecimal stationVolume(IrrigationStationConfig.Station station,
                                     Map<String, BigDecimal> volumes, List<String> missing) {
        if (station.codes.isEmpty()) {
            missing.add(station.label + "（设备未接入）");
            return null;
        }
        BigDecimal sum = null;
        for (String code : station.codes) {
            BigDecimal value = volumes.get(code);
            if (value != null) {
                sum = (sum == null) ? value : sum.add(value);
            }
        }
        if (sum == null) {
            missing.add(station.label + "（无流量数据）");
        }
        return sum;
    }

    /** 窗口区间取数：一次查询覆盖全部候选站点，返回 站点标识 → 区间累计流量(m³)（联调核对用日志逐站输出） */
    private Map<String, BigDecimal> queryIntervals(LocalDateTime start, LocalDateTime end) {
        List<String> codes = IrrigationStationConfig.allStationCodes();
        List<IrrigationIntervalVO> rows = irrigationWaterMapper.selectIntervalPerStation(codes, start, end);
        Map<String, BigDecimal> volumes = new HashMap<>();
        for (IrrigationIntervalVO row : rows) {
            BigDecimal ttf = usable(row.getTtf());
            if (ttf == null) {
                log.info("[灌溉用水] 区间取数 站点={}({}) 窗口={} ~ {} 末行无累计流量（ttf 为空或哨兵值）",
                        row.getStnm(), row.getSite(), start, end);
                continue;
            }
            BigDecimal prevTtf = usable(row.getPrevTtf());
            BigDecimal interval = ttf.subtract(prevTtf != null ? prevTtf : BigDecimal.ZERO);
            volumes.put(row.getSite(), interval);
            log.info("[灌溉用水] 区间取数 站点={}({}) 末行时间={} 区间累计={} m³（起点前基准 ttf={}）",
                    row.getStnm(), row.getSite(), row.getTm(),
                    interval.setScale(2, RoundingMode.DOWN), prevTtf);
        }
        log.info("[灌溉用水] 区间取数完成 窗口={} ~ {} 命中站点={}/{}（未命中=窗口内无数据）",
                start, end, volumes.size(), codes.size());
        return volumes;
    }

    /** 按方案批量加载片区汇总（一次查询，避免 N+1） */
    private Map<String, List<DemandAreaSummary>> loadAreas(List<String> recordIds) {
        if (recordIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<DemandAreaSummary> wrapper = new QueryWrapper<>();
        wrapper.in("\"record_id\"", recordIds)
                .eq("\"summary_type\"", "片区")
                .orderByAsc("\"sort_order\"");
        List<DemandAreaSummary> rows = demandAreaSummaryMapper.selectList(wrapper);
        Map<String, List<DemandAreaSummary>> grouped = new LinkedHashMap<>();
        for (DemandAreaSummary row : rows) {
            grouped.computeIfAbsent(row.getRecordId(), key -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    /** 需水旬范围（sort_order 1~18）：优先取 volume>0 的首末旬；全为 0 回退全部行首末旬；无行返回 null */
    private Integer[] tendayRange(List<DemandAreaSummary> rows) {
        Integer[] byVolume = range(rows, true);
        return byVolume != null ? byVolume : range(rows, false);
    }

    private Integer[] range(List<DemandAreaSummary> rows, boolean onlyPositive) {
        Integer min = null;
        Integer max = null;
        for (DemandAreaSummary row : rows) {
            if (onlyPositive && (row.getDemandVolume() == null || row.getDemandVolume() <= 0)) {
                continue;
            }
            Integer order = row.getSortOrder() != null
                    ? row.getSortOrder().intValue() : TenDayMapUtils.sortOrderOf(row.getTendayLabel());
            if (order == null) {
                continue;
            }
            if (min == null || order < min) {
                min = order;
            }
            if (max == null || order > max) {
                max = order;
            }
        }
        return min == null ? null : new Integer[]{min, max};
    }

    /** 参数归一化：去空去重、数量防御 */
    private static List<String> normalizeSchemeIds(List<String> schemeIds) {
        if (schemeIds == null || schemeIds.isEmpty()) {
            throw new IllegalArgumentException("请选择需水方案");
        }
        List<String> ids = schemeIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("请选择需水方案");
        }
        if (ids.size() > MAX_SCHEME_COUNT) {
            throw new IllegalArgumentException("单次最多查询 " + MAX_SCHEME_COUNT + " 个需水方案");
        }
        return ids;
    }

    /** 计算年：请求参数优先；缺省取方案创建年；创建时间为空取当前年 */
    private static int resolveYear(Integer year, DemandRecord record) {
        if (year != null) {
            return year;
        }
        if (record.getCreatedAt() != null) {
            return record.getCreatedAt().getYear();
        }
        return LocalDate.now().getYear();
    }

    /** 损耗配置（m³）：口径未定前缺省 0（不扣减）；配置非法时按 0 处理 */
    private BigDecimal resolveLoss() {
        String configured = environment.getProperty("irrigation.water.loss-volume-m3", "0");
        try {
            BigDecimal loss = new BigDecimal(configured.trim());
            return loss.signum() >= 0 ? loss : BigDecimal.ZERO;
        } catch (NumberFormatException e) {
            log.warn("[灌溉用水] 损耗配置 irrigation.water.loss-volume-m3={} 非法，按 0 处理", configured);
            return BigDecimal.ZERO;
        }
    }

    /** 哨兵值视为缺失 */
    private static BigDecimal usable(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(DEVICE_MISSING) == 0 || value.compareTo(DEVICE_ERROR) == 0) {
            return null;
        }
        return value;
    }

    /** 旬标签 + 年份 → 该旬第一天；无法解析返回 null */
    private static LocalDate tendayFirstDate(String label, int year) {
        return label == null ? null : TenDayDateUtils.toFirstDate(label, year);
    }

    /** 旬标签 + 年份 → 该旬最后一天（上旬=10日、中旬=20日、下旬=当月最后一天）；无法解析返回 null */
    private static LocalDate tendayLastDate(String label, int year) {
        if (label == null) {
            return null;
        }
        Matcher matcher = TENDAY_PATTERN.matcher(label.trim());
        if (!matcher.matches()) {
            return null;
        }
        int month = Integer.parseInt(matcher.group(1));
        int day;
        switch (matcher.group(2)) {
            case "上":
                day = 10;
                break;
            case "中":
                day = 20;
                break;
            default:
                day = YearMonth.of(year, month).lengthOfMonth();
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    /** 供水量计算结果：volume=null 表示必需部件缺失（missing 为说明） */
    private static final class SupplyResult {
        private final BigDecimal volume;
        private final List<String> missing;

        private SupplyResult(BigDecimal volume, List<String> missing) {
            this.volume = volume;
            this.missing = missing;
        }
    }
}

