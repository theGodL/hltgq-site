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

        /**
         * 该小时库容（亿m³，4 位小数）。
         * 仅花凉亭坝上（3206400007）有「水位-库容」曲线，其余站点恒为 null；
         * 该小时无水位数据、或水位超出曲线范围（65.00 ~ 99.49 m）时也为 null
         */
        private BigDecimal capacity;
    }
}
