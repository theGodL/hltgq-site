package com.qgyun.hltgq.hltgqsite.irrigation.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 灌溉用水汇总 VO（方案 × 四县）。
 * <p>行内字段口径见 {@link CountyRow}；数值精度：需水量/供水量保留 2 位小数（截断），
 * 保证率/进度保留 1 位小数（截断）。
 */
@Data
public class IrrigationSummaryVO {

    /** 方案ID */
    private String schemeId;

    /** 方案名称 */
    private String schemeName;

    /** 方案保证率（50 / 75 / 90 / 多年平均） */
    private String guaranteeRate;

    /** 计算年（缺省=方案创建年） */
    private Integer year;

    /** 方案需水起始旬 */
    private String startTenday;

    /** 方案需水结束旬 */
    private String endTenday;

    /** 方案需水起始日期（yyyy-MM-dd） */
    private String periodStart;

    /** 方案需水结束日期（yyyy-MM-dd） */
    private String periodEnd;

    /** 四县行（固定顺序：宿松县、怀宁县、望江县、太湖县） */
    private List<CountyRow> rows;

    /** 县（灌区）行 */
    @Data
    public static class CountyRow {

        /** 灌区（县）名称 */
        private String district;

        /** 该县需水起始旬 */
        private String startTenday;

        /** 该县需水结束旬 */
        private String endTenday;

        /** 该县需水起始日期（yyyy-MM-dd） */
        private String periodStart;

        /** 该县需水结束日期（yyyy-MM-dd） */
        private String periodEnd;

        /** 需水量(万m³)：该县对应片区 18 旬求和；无汇总数据时为 null */
        private BigDecimal demandVolume;

        /** 实际供水量(万m³)：按县口径从流量监测区间累计计算；部件数据缺失时为 null */
        private BigDecimal supplyVolume;

        /** 设计保证率(%)：逐县配置（业务定稿前用产品稿默认值） */
        private BigDecimal designGuaranteeRate;

        /** 实际保证率(%)：实际供水量 ÷ 需水量 × 100，需水量 ≤ 0 或供水量缺失时为 null */
        private BigDecimal actualGuaranteeRate;

        /** 灌溉进度(%)：暂同实际保证率口径（产品稿），口径调整后单独修改 */
        private BigDecimal irrigationProgress;

        /** 缺失数据说明（设备未接入/窗口内无流量数据等），全部就绪时为空数组 */
        private List<String> missingData;
    }
}
