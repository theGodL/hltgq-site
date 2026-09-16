package com.qgyun.hltgq.hltgqsite.h5.service;

import com.qgyun.hltgq.hltgqsite.h5.mapper.H5WaterResourceMapper;
import com.qgyun.hltgq.hltgqsite.h5.vo.WaterResourceOverviewVO;
import com.qgyun.hltgq.hltgqsite.irrigation.config.IrrigationStationConfig;
import com.qgyun.hltgq.hltgqsite.irrigation.service.IrrigationWaterService;
import com.qgyun.hltgq.hltgqsite.irrigation.vo.IrrigationSchemeOptionVO;
import com.qgyun.hltgq.hltgqsite.irrigation.vo.IrrigationSummaryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * H5 水资源概览服务：一个接口聚合页面四块组件（避免 H5 多请求），按年度区间查询。
 * <ul>
 *   <li>饼状图：水资源量分布 = 年度区间内最新已完成配水方案供水来源求和（塘坝/水厂/花凉亭水库灌溉）</li>
 *   <li>列表：水资源分配方案及执行 = 年度已确认用水计划 × 四县，分配水量=计划量、执行进度=实际供水量÷计划量</li>
 *   <li>两卡片：灌溉总面积固定 105.83 万亩；已完成灌溉 = 四县实际供水量合计（万m³，3 位截断，亩为前端展示标签）</li>
 *   <li>四卡片：四县灌溉面积（需水方案支渠面积归县，万亩）+ 实际供水量（万m³，3 位截断；固定顺序：宿松/怀宁/望江/太湖）</li>
 * </ul>
 * <p>数值精度：实际供水量及其合计（已完成灌溉）3 位截断（累计流量口径，与灌溉用水接口同口径）；
 * 分配水量（年度用水计划表单值）/面积 亩→万亩 2 位截断；进度 1 位截断。
 * <p>年度区间：startDate/endDate 格式 yyyy-MM-dd HH:mm:ss（前端 date.min/date.max 直传），
 * 缺省=本年度 [01-01, 次年 01-01)；年份取 startDate 年份，作用于用水计划/需水方案选取。
 */
@Service
public class H5WaterResourceService {

    private static final Logger log = LoggerFactory.getLogger(H5WaterResourceService.class);

    /** 灌溉总面积（万亩）：产品定稿固定值 */
    private static final BigDecimal TOTAL_AREA = new BigDecimal("105.83");

    /** 供水来源固定顺序（与配水方案旬明细列对应） */
    private static final String[] SOURCE_NAMES = {"塘坝供水", "水厂供水", "花凉亭水库灌溉供水"};

    /** 供水来源稳定编码（与 SOURCE_NAMES 一一对应，前端按 code 精确映射文案与颜色） */
    private static final String[] SOURCE_CODES = {"pond", "waterworks", "reservoir"};

    /** 日期入参格式（与前端 date.min/date.max 一致） */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private H5WaterResourceMapper mapper;

    @Autowired
    private IrrigationWaterService irrigationWaterService;

    /**
     * 水资源概览（聚合一次返回）。
     *
     * @param startDate 年度区间起点（yyyy-MM-dd HH:mm:ss），缺省=本年度 01-01 00:00:00
     * @param endDate   年度区间终点（不含），缺省=startDate 次年 01-01 00:00:00
     */
    public WaterResourceOverviewVO overview(String startDate, String endDate) {
        LocalDateTime start = parseOrDefault(startDate, null);
        if (start == null) {
            start = LocalDateTime.now().withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
        LocalDateTime end = parseOrDefault(endDate, start.plusYears(1));
        if (!end.isAfter(start)) {
            log.warn("[水资源概览] endDate={} 不晚于 startDate={}，已回退为 start+1 年", endDate, startDate);
            end = start.plusYears(1);
        }
        int year = start.getYear();
        log.info("[水资源概览] 年度区间 {} ~ {}（year={}）", DATE_FORMAT.format(start), DATE_FORMAT.format(end), year);

        WaterResourceOverviewVO vo = new WaterResourceOverviewVO();
        vo.setDistribution(buildDistribution(start, end));
        IrrigationSummaryVO summary = latestSummary(year);
        List<IrrigationSummaryVO.CountyRow> rows = summary == null ? null : summary.getRows();
        vo.setAllocationList(buildAllocationList(rows, year));
        vo.setCountyDetails(buildCountyDetails(rows, summary));
        vo.setIrrigationStats(buildStats(rows));
        return vo;
    }

    /** 解析日期入参，非法/缺省返回 fallback */
    private LocalDateTime parseOrDefault(String value, LocalDateTime fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_FORMAT);
        } catch (Exception e) {
            log.warn("[水资源概览] 非法日期入参 {}={}，使用缺省值", value == null ? "" : value.trim());
            return fallback;
        }
    }

    /** 饼状图：年度区间内最新已完成配水方案 → 三来源求和（2 位截断）；无方案空列表 */
    private List<WaterResourceOverviewVO.SourceItem> buildDistribution(LocalDateTime start, LocalDateTime end) {
        String recordId = mapper.selectLatestAllocateId(DATE_FORMAT.format(start), DATE_FORMAT.format(end));
        if (recordId == null) {
            log.info("[水资源概览] 饼状图 年度区间内无已完成配水方案");
            return Collections.emptyList();
        }
        Map<String, Object> sums = mapper.selectSupplySourceSums(recordId);
        List<WaterResourceOverviewVO.SourceItem> items = new ArrayList<>();
        items.add(sourceItem(SOURCE_CODES[0], SOURCE_NAMES[0], sums == null ? null : asBigDecimal(sums.get("pond"))));
        items.add(sourceItem(SOURCE_CODES[1], SOURCE_NAMES[1], sums == null ? null : asBigDecimal(sums.get("waterworks"))));
        items.add(sourceItem(SOURCE_CODES[2], SOURCE_NAMES[2], sums == null ? null : asBigDecimal(sums.get("reservoir"))));
        log.info("[水资源概览] 饼状图 方案={} 塘坝={} 水厂={} 水库灌溉={}",
                recordId, items.get(0).getValue(), items.get(1).getValue(), items.get(2).getValue());
        return items;
    }

    private WaterResourceOverviewVO.SourceItem sourceItem(String code, String name, BigDecimal value) {
        WaterResourceOverviewVO.SourceItem item = new WaterResourceOverviewVO.SourceItem();
        item.setCode(code);
        item.setName(name);
        item.setValue(value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.DOWN) : scale2(value));
        return item;
    }

    /** 年度内最新已完成需水方案汇总（无方案返回 null） */
    private IrrigationSummaryVO latestSummary(int year) {
        List<IrrigationSchemeOptionVO> schemes = irrigationWaterService.schemes(year);
        if (schemes.isEmpty()) {
            log.info("[水资源概览] year={} 无已完成需水方案", year);
            return null;
        }
        String latestId = schemes.get(0).getSchemeId();
        List<IrrigationSummaryVO> summaries = irrigationWaterService.summary(Collections.singletonList(latestId), year);
        if (summaries.isEmpty()) {
            return null;
        }
        log.info("[水资源概览] 列表/卡片 采用最新已完成需水方案={}({})", summaries.get(0).getSchemeName(), latestId);
        return summaries.get(0);
    }

    /** 列表：分配方案及执行（年度已确认用水计划 × 四县；无计划空列表） */
    private List<WaterResourceOverviewVO.AllocationRow> buildAllocationList(
            List<IrrigationSummaryVO.CountyRow> rows, int year) {
        Map<String, Object> plan = mapper.selectAnnualWaterPlan(year);
        if (plan == null) {
            log.info("[水资源概览] 列表 year={} 无已确认年度用水计划，返回空列表", year);
            return Collections.emptyList();
        }
        log.info("[水资源概览] 列表 年度用水计划 宿松={} 怀宁={} 望江={} 太湖={} 合计={}",
                asBigDecimal(plan.get("susong")), asBigDecimal(plan.get("huaining")), asBigDecimal(plan.get("wangjiang")),
                asBigDecimal(plan.get("taihu")), asBigDecimal(plan.get("total")));
        List<WaterResourceOverviewVO.AllocationRow> result = new ArrayList<>();
        for (IrrigationStationConfig.CountySupply county : IrrigationStationConfig.COUNTIES) {
            BigDecimal allocated = asBigDecimal(plan.get(planKey(county.district)));
            BigDecimal supply = rows == null ? null : supplyOf(rows, county.district);
            WaterResourceOverviewVO.AllocationRow item = new WaterResourceOverviewVO.AllocationRow();
            item.setCode(planKey(county.district));
            item.setDistrict(county.district);
            item.setAllocatedVolume(allocated == null ? null : scale2(allocated));
            item.setProgress(supply != null && allocated != null && allocated.compareTo(BigDecimal.ZERO) > 0
                    ? supply.multiply(new BigDecimal("100")).divide(allocated, 1, RoundingMode.DOWN) : null);
            result.add(item);
            log.info("[水资源概览] 列表行 行政区={} 分配水量={} 实际供水量={} 执行进度={}",
                    item.getDistrict(), item.getAllocatedVolume(), supply, item.getProgress());
        }
        return result;
    }

    /** 县名 → 用水计划 Map key */
    private String planKey(String district) {
        switch (district) {
            case "宿松县":
                return "susong";
            case "怀宁县":
                return "huaining";
            case "望江县":
                return "wangjiang";
            case "太湖县":
                return "taihu";
            default:
                return null;
        }
    }

    /** 四卡片：固定四县顺序（灌溉面积 + 已完成灌溉；无方案时均为 null，卡片仍可渲染） */
    private List<WaterResourceOverviewVO.CountyDetail> buildCountyDetails(
            List<IrrigationSummaryVO.CountyRow> rows, IrrigationSummaryVO summary) {
        Map<String, BigDecimal> areaByCounty = areaByCounty(summary);
        List<WaterResourceOverviewVO.CountyDetail> result = new ArrayList<>();
        for (IrrigationStationConfig.CountySupply county : IrrigationStationConfig.COUNTIES) {
            WaterResourceOverviewVO.CountyDetail item = new WaterResourceOverviewVO.CountyDetail();
            item.setCode(planKey(county.district));
            item.setDistrict(county.district);
            item.setIrrigationArea(areaByCounty == null ? null : areaByCounty.get(county.district));
            item.setFinishedVolume(rows == null ? null : supplyOf(rows, county.district));
            result.add(item);
            log.info("[水资源概览] 四卡片 行政区={} 灌溉面积={}万亩 已完成灌溉={}万m³",
                    item.getDistrict(), item.getIrrigationArea(), item.getFinishedVolume());
        }
        return result;
    }

    /** 需水方案支渠面积按片区归县（亩 → 万亩，2 位截断）；无方案/无明细返回 null */
    private Map<String, BigDecimal> areaByCounty(IrrigationSummaryVO summary) {
        if (summary == null) {
            log.info("[水资源概览] 四卡片 无已完成需水方案，灌溉面积均为 null");
            return null;
        }
        List<Map<String, Object>> districtAreas = mapper.selectBranchAreaByDistrict(summary.getSchemeId());
        Map<String, BigDecimal> muByDistrict = new HashMap<>();
        if (districtAreas != null) {
            for (Map<String, Object> row : districtAreas) {
                String district = row.get("district") == null ? null : String.valueOf(row.get("district"));
                BigDecimal area = asBigDecimal(row.get("area"));
                if (district == null || area == null) {
                    continue;
                }
                muByDistrict.put(district, area);
                log.info("[水资源概览] 四卡片 片区={} 面积={}亩", district, area);
            }
        }
        Map<String, BigDecimal> result = new HashMap<>();
        for (IrrigationStationConfig.CountySupply county : IrrigationStationConfig.COUNTIES) {
            BigDecimal mu = null;
            for (String demandArea : county.demandAreas) {
                BigDecimal area = muByDistrict.get(demandArea);
                if (area != null) {
                    mu = mu == null ? area : mu.add(area);
                }
            }
            if (mu != null) {
                result.put(county.district, scale2(mu.divide(new BigDecimal("10000"), 6, RoundingMode.DOWN)));
            }
        }
        return result;
    }

    /** 两卡片：总面积固定值 + 已完成灌溉（四县供水量合计，全缺失为 null） */
    private WaterResourceOverviewVO.IrrigationStats buildStats(List<IrrigationSummaryVO.CountyRow> rows) {
        WaterResourceOverviewVO.IrrigationStats stats = new WaterResourceOverviewVO.IrrigationStats();
        stats.setTotalArea(TOTAL_AREA);
        BigDecimal sum = null;
        if (rows != null) {
            for (IrrigationSummaryVO.CountyRow row : rows) {
                if (row.getSupplyVolume() != null) {
                    sum = sum == null ? row.getSupplyVolume() : sum.add(row.getSupplyVolume());
                }
            }
        }
        stats.setFinishedVolume(sum == null ? null : scale3(sum));
        log.info("[水资源概览] 灌溉统计 总面积={}万亩 已完成灌溉合计={}万m³", TOTAL_AREA, stats.getFinishedVolume());
        return stats;
    }

    /** 按县名取供水量（四县行固定存在，未知县返回 null） */
    private BigDecimal supplyOf(List<IrrigationSummaryVO.CountyRow> rows, String district) {
        for (IrrigationSummaryVO.CountyRow row : rows) {
            if (district.equals(row.getDistrict())) {
                return row.getSupplyVolume();
            }
        }
        return null;
    }

    /** Object → BigDecimal（PG numeric/Integer/Double 通用） */
    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 2 位截断（模型直出水量/面积等非累计流量口径） */
    private BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.DOWN);
    }

    /** 3 位截断（累计流量口径水量：实际供水量及其合计） */
    private BigDecimal scale3(BigDecimal value) {
        return value.setScale(3, RoundingMode.DOWN);
    }
}
