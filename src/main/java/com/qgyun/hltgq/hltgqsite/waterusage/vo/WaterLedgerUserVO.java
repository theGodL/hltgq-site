package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 手工台账「用水户」下拉选项（仅正常状态用水户）。
 * <p>选用后前端带出单价与默认结算周期；cycleKey 为该户当前统计周期（新增弹窗默认期间）。
 */
@Data
public class WaterLedgerUserVO {

    private String id;

    private String name;

    private String userNo;

    /** 单价（元/m³；档案未配置为 null） */
    private BigDecimal price;

    /** 统计周期（库内 #1# 月度 / #2# 季度 / #3# 年度） */
    private String settleCycle;

    /** 统计周期（前端英文码：month/quarter/year） */
    private String cycle;

    private String cycleName;

    /** 当前统计周期 key（2026-10 / 2026-Q3 / 2026） */
    private String cycleKey;
}
