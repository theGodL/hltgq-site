package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配水调度方案主表（t_auto_hltgq_water_decision_record）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_decision_record\"")
public class DecisionRecord extends BaseRecordEntity {

    /** 渠系水利用系数 */
    @TableField("\"canal_eff\"")
    private Double canalEff;

    /** 数据来源：auto / manual */
    @TableField("\"source\"")
    private String source;

    /** 指定旬数组归档 JSON */
    @TableField("\"tens\"")
    private String tens;

    /** 请求体归档 JSON */
    @TableField("\"request_json\"")
    private String requestJson;

    /** 关联资源配置方案ID */
    @TableField("\"allocate_record_id\"")
    private String allocateRecordId;

    /** 关联需水方案ID */
    @TableField("\"demand_record_id\"")
    private String demandRecordId;

    /** 总需水量(万方)：SUM(branch_detail.demand_volume) */
    @TableField("\"total_demand\"")
    private Double totalDemand;

    /** 总建议供水(万方)：SUM(branch_detail.suggested_supply) */
    @TableField("\"total_supply\"")
    private Double totalSupply;

    /** 不满足需水支渠数量：COUNT(DISTINCT branch_name WHERE is_satisfied='#2#') */
    @TableField("\"unsatisfied_count\"")
    private Double unsatisfiedCount;
}
