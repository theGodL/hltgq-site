package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 水质历史趋势 VO（三图共用一份 2 小时级数据点）
 * <p>有机污染曲线用 codcr/bod5；营养盐相对限值占比与 DO 24h 趋势用 nh3n/tn/tp/dox
 * 配合预警值（该站阈值行）由前端计算占比/画线。
 * <p>2h 桶聚合对偶数小时（00:00/02:00/...），跨天连续；无数据桶各项为 null（前端断线）。
 */
@Data
public class WaterQualityTrendVO {

    /** 站点编号 */
    private String stcd;

    /** 站点名称 */
    private String stnm;

    /** 查询起始时间 */
    private LocalDateTime startTime;

    /** 查询截止时间 */
    private LocalDateTime endTime;

    /** 2 小时级数据点（严格 2h 步长对齐偶数小时，无缺失） */
    private List<HourPoint> points;

    /** 该站水质阈值行（type 含 #8#），无配置为空列表 */
    private List<WaterThresholdVO> thresholds;

    @Data
    public static class HourPoint {
        /** 桶标签 yyyy-MM-dd HH:00（偶数小时：00:00/02:00/...） */
        private String hour;
        /** 桶内氨氮均值 (mg/L)。无数据时为 null */
        private BigDecimal nh3n;
        /** 桶内 COD 均值 (mg/L)。无数据时为 null */
        private BigDecimal codcr;
        /** 桶内 BOD 均值 (mg/L)。无数据时为 null */
        private BigDecimal bod5;
        /** 桶内 TP 均值 (mg/L)。无数据时为 null */
        private BigDecimal tp;
        /** 桶内 TN 均值 (mg/L)。无数据时为 null */
        private BigDecimal tn;
        /** 桶内 DO 均值 (mg/L)。无数据时为 null */
        private BigDecimal dox;
    }
}
