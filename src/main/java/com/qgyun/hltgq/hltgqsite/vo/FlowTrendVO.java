package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 流量趋势图表 VO（小时级流量曲线）
 */
@Data
public class FlowTrendVO {

    /** 站点编号 */
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
        /** 该小时平均流量 (m³/s)。无数据时为 null */
        private BigDecimal flow;
    }
}
