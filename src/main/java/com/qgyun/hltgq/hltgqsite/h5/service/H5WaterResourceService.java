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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * H5 水资源概览服务：一个接口聚合页面四块组件（避免 H5 多请求）。
 * <ul>
 *   <li>饼状图：水资源量分布 = 最新已完成配水方案（water-allocation）供水来源求和（塘坝/水厂/花凉亭水库灌溉）</li>
 *   <li>列表：水资源分配方案及执行 = 最新已完成需水方案 × 四县，分配水量=需水量、执行进度=实际供水量÷需水量</li>
 *   <li>两卡片：灌溉总面积固定 105.83 万亩；已完成灌溉 = 四县实际供水量合计（万m³，亩为前端展示标签）</li>
 *   <li>四卡片：四县实际供水量（固定顺序：宿松/怀宁/望江/太湖）</li>
 * </ul>
 * <p>数值精度：供水量/分配水量 2 位截断、进度 1 位截断，与灌溉用水接口同口径。
 */
@Service
public class H5WaterResourceService {

    private static final Logger log = LoggerFactory.getLogger(H5WaterResourceService.class);

    /** 灌溉总面积（万亩）：产品定稿固定值 */
    private static final BigDecimal TOTAL_AREA = new BigDecimal("105.83");

    /** 供水来源固定顺序（与配水方案旬明细列对应） */
    private static final String[] SOURCE_NAMES = {"塘坝供水", "水厂供水", "花凉亭水库灌溉供水"};

    @Autowired
    private H5WaterResourceMapper mapper;

    @Autowired
    private IrrigationWaterService irrigationWaterService;

    /**
     * 水资源概览（聚合一次返回，无参数）。
     */
    public WaterResourceOverviewVO overview() {
        WaterResourceOverviewVO vo = new WaterResourceOverviewVO();
        vo.setDistribution(buildDistribution());
        List<IrrigationSummaryVO.CountyRow> rows = latestCountyRows();
        vo.setAllocationList(buildAllocationList(rows));
        vo.setCountyDetails(buildCountyDetails(rows));
        vo.setIrrigationStats(buildStats(rows));
        return vo;
    }

    /** 饼状图：最新已完成配水方案 → 三来源求和（2 位截断）；无方案空列表 */
    private List<WaterResourceOverviewVO.SourceItem> buildDistribution() {
        String recordId = mapper.selectLatestAllocateId();
        if (recordId == null) {
            log.info("[水资源概览] 饼状图 无已完成配水方案");
            return Collections.emptyList();
        }
        Map<String, BigDecimal> sums = mapper.selectSupplySourceSums(recordId);
        List<WaterResourceOverviewVO.SourceItem> items = new ArrayList<>();
        items.add(sourceItem(SOURCE_NAMES[0], sums == null ? null : sums.get("pond")));
        items.add(sourceItem(SOURCE_NAMES[1], sums == null ? null : sums.get("waterworks")));
        items.add(sourceItem(SOURCE_NAMES[2], sums == null ? null : sums.get("reservoir")));
        log.info("[水资源概览] 饼状图 方案={} 塘坝={} 水厂={} 水库灌溉={}",
                recordId, items.get(0).getValue(), items.get(1).getValue(), items.get(2).getValue());
        return items;
    }

    private WaterResourceOverviewVO.SourceItem sourceItem(String name, BigDecimal value) {
        WaterResourceOverviewVO.SourceItem item = new WaterResourceOverviewVO.SourceItem();
        item.setName(name);
        item.setValue(value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.DOWN) : scale2(value));
        return item;
    }

    /** 最新已完成需水方案的四县行（无方案返回 null） */
    private List<IrrigationSummaryVO.CountyRow> latestCountyRows() {
        List<IrrigationSchemeOptionVO> schemes = irrigationWaterService.schemes(null);
        if (schemes.isEmpty()) {
            log.info("[水资源概览] 无已完成需水方案");
            return null;
        }
        String latestId = schemes.get(0).getSchemeId();
        List<IrrigationSummaryVO> summaries = irrigationWaterService.summary(Collections.singletonList(latestId), null);
        if (summaries.isEmpty()) {
            return null;
        }
        log.info("[水资源概览] 列表/卡片 采用最新已完成需水方案={}({})", summaries.get(0).getSchemeName(), latestId);
        return summaries.get(0).getRows();
    }

    /** 列表：分配方案及执行（无方案空列表） */
    private List<WaterResourceOverviewVO.AllocationRow> buildAllocationList(
            List<IrrigationSummaryVO.CountyRow> rows) {
        if (rows == null) {
            return Collections.emptyList();
        }
        List<WaterResourceOverviewVO.AllocationRow> result = new ArrayList<>();
        for (IrrigationSummaryVO.CountyRow row : rows) {
            WaterResourceOverviewVO.AllocationRow item = new WaterResourceOverviewVO.AllocationRow();
            item.setDistrict(row.getDistrict());
            item.setAllocatedVolume(row.getDemandVolume());
            item.setProgress(row.getIrrigationProgress());
            result.add(item);
        }
        return result;
    }

    /** 四卡片：固定四县顺序（无方案时供水量为 null，卡片仍可渲染） */
    private List<WaterResourceOverviewVO.CountyDetail> buildCountyDetails(
            List<IrrigationSummaryVO.CountyRow> rows) {
        List<WaterResourceOverviewVO.CountyDetail> result = new ArrayList<>();
        for (IrrigationStationConfig.CountySupply county : IrrigationStationConfig.COUNTIES) {
            WaterResourceOverviewVO.CountyDetail item = new WaterResourceOverviewVO.CountyDetail();
            item.setDistrict(county.district);
            item.setFinishedVolume(rows == null ? null : supplyOf(rows, county.district));
            result.add(item);
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
        stats.setFinishedVolume(sum == null ? null : scale2(sum));
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

    /** 2 位截断 */
    private BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.DOWN);
    }
}
