package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配水调度供水分配汇总（t_auto_hltgq_water_decision_supply_alloc）。
 * <p>模型 /decision V3 响应「供水分配汇总」实测为 18 行逐旬表，
 * 每行键：旬/需水量/建议供水量/塘坝补水量/补水后总供水量/是否满足需水；
 * 「总计_全部旬」对象为年度合计，走扩展表快照保留原文。逐行落库，sort_order 按旬正序展示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_decision_supply_alloc\"")
public class DecisionSupplyAlloc extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_decision_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 旬标签（如 "5月上旬"） */
    @TableField("\"tenday_label\"")
    private String tendayLabel;

    /** 旬排序：TEN_DAY_MAP[tenday_label]（1~18），用于详情按旬正序展示 */
    @TableField("\"sort_order\"")
    private Double sortOrder;

    /** 需水量(万方) */
    @TableField("\"demand_volume\"")
    private Double demandVolume;

    /** 建议供水量(万方) */
    @TableField("\"suggested_supply\"")
    private Double suggestedSupply;

    /** 塘坝补水量(万方) */
    @TableField("\"pond_supplement\"")
    private Double pondSupplement;

    /** 补水后总供水量(万方) */
    @TableField("\"supply_after_pond\"")
    private Double supplyAfterPond;

    /** 净需水量合计(万方)：数组行无此键（仅「总计_全部旬」对象有），恒 null */
    @TableField("\"net_demand\"")
    private Double netDemand;

    /** 是否满足：#1#=是，#2#=否（模型原始取值 "是/否"，后端归一化） */
    @TableField("\"is_satisfied\"")
    private String isSatisfied;
}
