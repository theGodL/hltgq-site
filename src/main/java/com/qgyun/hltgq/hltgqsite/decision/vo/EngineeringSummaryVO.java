package com.qgyun.hltgq.hltgqsite.decision.vo;

import lombok.Data;

import java.util.List;

/**
 * 工程管理决策总览（/engineering/decision-summary 响应）。
 * <p>站点问题风险等级堆叠柱（low/mid/high）+ 工单类型饼图 + 问题状态饼图，全量统计无日期过滤。
 */
@Data
public class EngineeringSummaryVO {

    /** 站点 × 风险等级计数（堆叠柱，颜色用页面内置 RISK_COLORS） */
    private List<RiskStation> riskStations;

    /** 工单类型分布（固定 10 项顺序：9 类型 + 其他，无记录补 0，color 取自数据） */
    private List<NamedValue> orderTypes;

    /** 问题状态分布（固定 5 项顺序，无记录补 0，color 取自数据） */
    private List<NamedValue> issueStatus;

    /** 站点风险计数 */
    @Data
    public static class RiskStation {
        /** 站点名（问题未关联站点时 = "未关联站点"） */
        private String name;
        /** 低风险问题数 */
        private Long low;
        /** 中风险问题数 */
        private Long mid;
        /** 高风险问题数 */
        private Long high;
    }

    /** 名称 + 数量 + 色值（饼图项） */
    @Data
    public static class NamedValue {
        private String name;
        private Long value;
        private String color;
    }
}
