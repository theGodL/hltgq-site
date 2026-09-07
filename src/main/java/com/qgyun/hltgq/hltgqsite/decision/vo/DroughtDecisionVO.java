package com.qgyun.hltgq.hltgqsite.decision.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 智能抗旱决策响应（GET /drought-decision/query）。
 * <p>契约与前端页面 drought-trend-analysis.html（原 Mock）完全一致：
 * 一次查询同时返回实测墒情（observed）与预测墒情（forecast）。
 * <p>数值口径：含水率 2 位小数截断补零（-999/-9991 已由数据源排除）；
 * 降雨量 2 位小数截断；干旱等级为平台编码（#1#无旱 / #2#轻旱 / #3#中旱 / #4#重旱 / #5#特旱）。
 */
@Data
public class DroughtDecisionVO {

    /** 站点编号 */
    private String stcd;

    /** 站点名称 */
    private String stnm;

    /** 查询起始日期 yyyy-MM-dd */
    private String startDate;

    /** 查询截止日期 yyyy-MM-dd */
    private String endDate;

    /** 实测墒情（小时级完整序列，无数据小时各字段为 null） */
    private Observed observed;

    /** 预测墒情（最新一次墒情预测方案中该站点、落在查询区间内的逐小时明细） */
    private Forecast forecast;

    /** 站点下拉项（GET /drought-decision/sites 响应元素） */
    @Data
    public static class Site {
        /** 站点编号 */
        private String stcd;
        /** 站点名称 */
        private String stnm;
    }

    @Data
    public static class Observed {
        private List<ObsPoint> points;
    }

    /** 实测点：hour=yyyy-MM-dd HH:00 */
    @Data
    public static class ObsPoint {
        private String hour;
        private BigDecimal mten;
        private BigDecimal mtwenty;
        private BigDecimal mthirty;
    }

    @Data
    public static class Forecast {
        private List<PredPoint> points;
    }

    /** 预测点：tm=yyyy-MM-dd HH:mm:ss */
    @Data
    public static class PredPoint {
        private String tm;
        private BigDecimal rainfall;
        private BigDecimal mten;
        private BigDecimal mtwenty;
        private BigDecimal mthirty;
        private String droughtLevel;
    }
}
