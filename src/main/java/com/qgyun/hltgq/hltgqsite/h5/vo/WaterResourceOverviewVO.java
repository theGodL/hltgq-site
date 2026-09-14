package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * H5 水资源概览聚合 VO（/h5/water-resource/overview），一次返回页面四块组件：
 * <ul>
 *   <li>饼状图 distribution：水资源量分布（供水来源三项，最新已完成配水方案）</li>
 *   <li>列表 allocationList：水资源分配方案及执行（行政区 / 分配水量 / 执行进度）</li>
 *   <li>卡片 irrigationStats：灌溉总面积（固定值）+ 已完成灌溉（实际供水量合计）</li>
 *   <li>卡片 countyDetails：四县已完成灌溉（固定顺序：宿松/怀宁/望江/太湖）</li>
 * </ul>
 * <p>数值口径：供水量/分配水量万m³ 2 位截断；执行进度 % 1 位截断（复用灌溉用水口径）；
 * 「已完成灌溉」直接展示实际供水量数值（亩为前端展示标签，后端不做换算）。
 */
@Data
public class WaterResourceOverviewVO {

    /** 饼状图：水资源量分布（供水来源） */
    private List<SourceItem> distribution;

    /** 列表：水资源分配方案及执行 */
    private List<AllocationRow> allocationList;

    /** 两卡片：灌溉统计 */
    private IrrigationStats irrigationStats;

    /** 四卡片：各区域灌溉详情（固定四县顺序） */
    private List<CountyDetail> countyDetails;

    /** 供水来源饼图项 */
    @Data
    public static class SourceItem {

        /** 来源名称（塘坝供水 / 水厂供水 / 花凉亭水库灌溉供水） */
        private String name;

        /** 供水量(万m³，2 位截断) */
        private BigDecimal value;
    }

    /** 分配方案及执行列表行 */
    @Data
    public static class AllocationRow {

        /** 行政区（宿松县/怀宁县/望江县/太湖县） */
        private String district;

        /** 分配水量(万m³，= 方案需水量) */
        private BigDecimal allocatedVolume;

        /** 执行进度(%)（= 实际供水量 ÷ 需水量 × 100，1 位截断；供水量缺失为 null） */
        private BigDecimal progress;
    }

    /** 灌溉统计卡片 */
    @Data
    public static class IrrigationStats {

        /** 灌溉总面积(万亩，产品定稿固定值 105.83) */
        private BigDecimal totalArea;

        /** 已完成灌溉(万m³，四县实际供水量合计，2 位截断；全部缺失为 null) */
        private BigDecimal finishedVolume;
    }

    /** 四县灌溉详情卡片行 */
    @Data
    public static class CountyDetail {

        /** 行政区 */
        private String district;

        /** 已完成灌溉(万m³，该县实际供水量，2 位截断；缺失为 null) */
        private BigDecimal finishedVolume;
    }
}
