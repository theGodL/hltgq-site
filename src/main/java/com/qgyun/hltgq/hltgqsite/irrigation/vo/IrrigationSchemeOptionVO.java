package com.qgyun.hltgq.hltgqsite.irrigation.vo;

import lombok.Data;

/**
 * 灌溉用水 · 需水方案下拉选项（仅已完成方案）。
 * <p>periodStart / periodEnd 为方案需水旬范围的起止日期（yyyy-MM-dd，年份缺省取方案创建年，可用 year 参数覆盖）。
 */
@Data
public class IrrigationSchemeOptionVO {

    /** 方案ID */
    private String schemeId;

    /** 方案名称 */
    private String schemeName;

    /** 方案保证率（50 / 75 / 90 / 多年平均） */
    private String guaranteeRate;

    /** 计算年（缺省=方案创建年） */
    private Integer year;

    /** 需水起始旬（如 "5月上旬"） */
    private String startTenday;

    /** 需水结束旬（如 "10月下旬"） */
    private String endTenday;

    /** 需水起始日期（yyyy-MM-dd） */
    private String periodStart;

    /** 需水结束日期（yyyy-MM-dd） */
    private String periodEnd;

    /** 方案总需水量(万m³) */
    private Double totalDemand;

    /** 灌区（县）数量（固定 4：宿松/怀宁/望江/太湖） */
    private Integer districtCount;
}
