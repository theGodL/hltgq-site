package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * H5 水资源概览聚合 VO（/h5/water-resource/overview），一次返回页面三块组件：
 * <ul>
 *   <li>列表 allocationList：水资源分配方案及执行（取用水户 / 分配水量 / 执行进度）</li>
 *   <li>卡片 irrigationStats：灌溉总面积（固定值 105.83 万亩）+ 已完成供水（实际供水量合计）</li>
 *   <li>卡片 countyDetails：四县灌溉面积 + 供水 + 总需水（固定顺序：宿松/怀宁/望江/太湖）</li>
 * </ul>
 * <p>口径（业主 2026-10-27 变更）：灌区内一县即一个用水户，行政区 = 用水户名称；
 * 分配水量 = 该户当年有效年度计划（已生效/已归档）明细的计划水量；
 * 执行进度 = 该户闸口配置当年实测放水量 ÷ 分配水量。
 * 灌溉配水侧「供水」= 该县用水户关联闸口当年实际放水量合计（与执行进度同源），
 * 「总需水」= 该户当年用水申请（待审 + 已通过）合计。
 * <p>数值精度：供水/已完成供水（闸口区间累计口径）万m³ 3 位截断；
 * 分配水量、总需水、灌溉面积 万m³/万亩 2 位截断；执行进度 % 1 位截断。
 */
@Data
public class WaterResourceOverviewVO {

    /** 列表：水资源分配方案及执行（按用水户，一县一户） */
    private List<AllocationRow> allocationList;

    /** 两卡片：灌溉统计 */
    private IrrigationStats irrigationStats;

    /** 四卡片：各区域灌溉详情（固定四县顺序） */
    private List<CountyDetail> countyDetails;

    /** 分配方案及执行列表行（行 = 用水户） */
    @Data
    public static class AllocationRow {

        /** 用水户 ID（稳定唯一键） */
        private String code;

        /** 用水户编号（YH + 3 位序号） */
        private String userNo;

        /** 行政区 = 用水户名称（如宿松县） */
        private String district;

        /** 分配水量(万m³，2 位截断) = 该户当年有效年度计划明细的计划水量；无计划为 null */
        private BigDecimal allocatedVolume;

        /** 执行进度(%)（1 位截断）= 该户闸口当年实测放水量 ÷ 分配水量 × 100；无计划/无读数/计划为 0 为 null */
        private BigDecimal progress;
    }

    /** 灌溉统计卡片 */
    @Data
    public static class IrrigationStats {

        /** 灌溉总面积(万亩，产品定稿固定值 105.83) */
        private BigDecimal totalArea;

        /** 已完成供水(万m³，四县供水合计 = countyDetails 各行 finishedVolume 之和，3 位截断；全缺失为 null) */
        private BigDecimal finishedVolume;
    }

    /** 四县灌溉详情卡片行 */
    @Data
    public static class CountyDetail {

        /** 行政区编码（susong/huaining/wangjiang/taihu），稳定唯一键 */
        private String code;

        /** 行政区 */
        private String district;

        /** 该县面积(万亩，用水户档案「灌溉面积」÷ 10000，2 位截断；可在用水户页面手填，未维护为 null) */
        private BigDecimal irrigationArea;

        /** 供水(万m³) = 该县用水户关联闸口当年实际放水量合计（3 位截断）；无匹配户/闸口无读数为 null */
        private BigDecimal finishedVolume;

        /** 总需水(万m³，2 位截断) = 该县用水户当年用水申请合计（待审 + 已通过）；无申请为 null */
        private BigDecimal demandVolume;
    }
}
