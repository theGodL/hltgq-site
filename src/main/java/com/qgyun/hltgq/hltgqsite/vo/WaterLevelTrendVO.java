package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 水位趋势图表 VO（小时级水位曲线，用于水位监测-详情趋势图）
 */
@Data
public class WaterLevelTrendVO {

    /** 站点编号（站点主键） */
    private String stcd;

    /** 站点名称 */
    private String stnm;

    /** 查询起始时间 */
    private LocalDateTime startTime;

    /** 查询截止时间 */
    private LocalDateTime endTime;

    /** 小时级数据点（严格 1h 步长，无缺失） */
    private List<HourPoint> hours;

    @Data
    public static class HourPoint {
        /** 小时标签 yyyy-MM-dd HH:00 */
        private String hour;

        /** 该小时水位值 (m)。无数据时为 null */
        private BigDecimal waterLevel;
    }
}
