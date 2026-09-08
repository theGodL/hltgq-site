package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

import java.util.List;

/**
 * 巡查及问题统计（柱状图）：近 12 个月巡查次数与问题数（问题按风险等级拆三档）。
 */
@Data
public class PatrolIssueMonthlyVO {

    /** 严格 12 个元素，按月份升序（endMonth 往前 11 个月起），无数据月份补 0 */
    private List<MonthStat> months;

    /** 单月统计项 */
    @Data
    public static class MonthStat {

        /** 月份标签 yyyy-MM */
        private String month;

        /** 该月已提交巡检记录数（status=#2#，排除草稿） */
        private long patrolCount;

        /** 该月问题总数（全状态） */
        private long issueCount;

        /** 低风险（risk_level=#1#）问题数 */
        private long issueLow;

        /** 中风险（risk_level=#2#）问题数 */
        private long issueMid;

        /** 高风险（risk_level=#3#）问题数 */
        private long issueHigh;
    }
}
