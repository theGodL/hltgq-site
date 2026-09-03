package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.util.List;

/**
 * 运行管理决策总览聚合结果：总览三项计数 + 问题状态分布 + 工单状态分布。
 */
@Data
public class OperationDecisionVO {

    /** 总览计数 */
    private Summary summary;

    /** 问题状态分布（固定 5 项：待处理/处理中/已转工单/已关闭/已作废，无记录补 0） */
    private List<StatusItem> issueStatus;

    /** 工单状态分布（固定 4 项：待处理/处理中/已关闭/已取消，无记录补 0） */
    private List<StatusItem> orderStatus;

    /** 总览计数 */
    @Data
    public static class Summary {

        /** 巡查总次数（仅已提交巡检记录，排除草稿） */
        private long patrolCount;

        /** 巡查符合度：0~100 百分数（如 85.5 = 85.5%），分母为 0 时返回 0 */
        private double patrolCompliance;

        /** 问题上报数 */
        private long issueCount;

        /** 问题符合度：0~100 百分数，分母为 0 时返回 0 */
        private double issueCompliance;

        /** 工单数 */
        private long orderCount;
    }

    /** 状态分布项 */
    @Data
    public static class StatusItem {

        /** 状态名称（中文） */
        private String name;

        /** 数量 */
        private Long value;
    }
}
