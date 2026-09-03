package com.qgyun.hltgq.hltgqsite.decision.vo;

import lombok.Data;

import java.util.List;

/**
 * 配水决策拓扑按旬支渠数据（/water-decision/{id}/branches 响应）。
 * <p>拓扑每支渠单值展示：需水/配水标签 + 不满足橙色样式；重名支渠靠 key 与拓扑节点精确命中。
 */
@Data
public class WaterBranchVO {

    /** 当前数据对应旬（无年份前缀，如 "5月上旬"） */
    private String tendayLabel;

    /** 支渠列表 */
    private List<Branch> branches;

    /** 支渠行 */
    @Data
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public static class Branch {
        /** 支渠名（与拓扑 BRANCH_LAYOUT 的 name 对应） */
        private String branchName;
        /** 灌区（如「太怀灌区」） */
        private String district;
        /** 分干渠（如「腊皖分干渠」），重名支渠靠它区分 */
        private String subDistrict;
        /** 拓扑节点标识：重名支渠必填，非重名省略（前端 metricMap[key || branchName]） */
        private String key;
        /** 需水量（万m³） */
        private Double demand;
        /** 建议供水量（万m³） */
        private Double supply;
        /** 是否满足 */
        private Boolean isSatisfied;
    }
}
