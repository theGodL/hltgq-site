package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 灌区水位变化图表 VO
 * 以小时为维度返回水位值和水位变化（用于水位统计曲线图）
 */
@Data
public class IrrigationWaterLevelChartVO {

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

        /** 该小时水位值 (m) */
        private BigDecimal waterLevel;

        /** 该小时水位变化 (cm)，正值=上涨，负值=下降。首小时为 null */
        private BigDecimal change;
    }
}
