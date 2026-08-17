package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 墒情趋势图表 VO（小时级含水量曲线，每个深度一条线）
 */
@Data
public class SoilMoistureTrendVO {

    /** 站点编号 */
    private String stcd;

    /** 站点名称 */
    private String stnm;

    /** 查询起始时间 */
    private LocalDateTime startTime;

    /** 查询截止时间 */
    private LocalDateTime endTime;

    /** 小时级数据点（严格 1h 步长，无缺失） */
    private List<HourPoint> points;

    @Data
    public static class HourPoint {
        /** 小时标签 yyyy-MM-dd HH:00 */
        private String hour;
        /** 该小时 10 厘米平均含水量。无数据时为 null */
        private BigDecimal mten;
        /** 该小时 20 厘米平均含水量。无数据时为 null */
        private BigDecimal mtwenty;
        /** 该小时 30 厘米平均含水量。无数据时为 null */
        private BigDecimal mthirty;
        /** 该小时 40 厘米平均含水量。无数据时为 null */
        private BigDecimal mforty;
        /** 该小时 50 厘米平均含水量。无数据时为 null */
        private BigDecimal mfifty;
        /** 该小时 60 厘米平均含水量。无数据时为 null */
        private BigDecimal msixty;
        /** 该小时 80 厘米平均含水量。无数据时为 null */
        private BigDecimal meighty;
        /** 该小时 100 厘米平均含水量。无数据时为 null */
        private BigDecimal mhundred;
    }
}
