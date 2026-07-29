package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 灌区雨量变化图表 VO
 * 以小时为维度返回实时雨量和累计雨量
 */
@Data
public class GqRainfallChartVO {

    /** 站点编号 */
    private String stcd;

    /** 站点名称 */
    private String stnm;

    /** 查询起始时间 */
    private LocalDateTime startTime;

    /** 查询截止时间 */
    private LocalDateTime endTime;

    /** 小时级数据点 */
    private List<HourPoint> hours;

    @Data
    public static class HourPoint {
        /** 小时标签 "yyyy-MM-dd HH:00" */
        private String hour;

        /** 该小时增量雨量(mm) */
        private BigDecimal rainfall;

        /** 从起始时间到该小时的累计雨量(mm) */
        private BigDecimal cumulative;
    }
}
