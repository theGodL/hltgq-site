package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

import java.util.List;

/**
 * 维修养护统计（饼状图）：已提交巡检记录按巡检结果 result 分组计数，固定 6 档。
 */
@Data
public class InspectionResultStatsVO {

    /** 已提交巡检记录总数 */
    private long total;

    /** 固定 6 档（#1#~#6#），无数据的档 value=0 */
    private List<ResultStat> items;

    /** 巡检结果统计项 */
    @Data
    public static class ResultStat {

        /** 巡检结果编码 */
        private String name;

        /** 巡检结果名称（权威映射） */
        private String label;

        /** 该结果的巡检记录数 */
        private long value;
    }
}
