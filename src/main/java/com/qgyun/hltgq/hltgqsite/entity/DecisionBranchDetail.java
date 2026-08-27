package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配水调度支渠逐旬明细（t_auto_hltgq_water_decision_branch_detail）。
 * <p>接口返回 {旬标签: [支渠行...]}，纵向展开：每旬每条支渠一条记录（18旬 × 支渠数）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_decision_branch_detail\"")
public class DecisionBranchDetail extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_decision_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 旬标签（支渠明细对象的键名，如 "5月上旬"） */
    @TableField("\"tenday_label\"")
    private String tendayLabel;

    /** 片区（支渠明细.<旬>[].片区） */
    @TableField("\"district\"")
    private String district;

    /** 分灌区（支渠明细.<旬>[].分灌区） */
    @TableField("\"sub_district\"")
    private String subDistrict;

    /** 支渠名称（支渠明细.<旬>[].支渠） */
    @TableField("\"branch_name\"")
    private String branchName;

    /** 灌区面积(亩)（支渠明细.<旬>[].灌区面积） */
    @TableField("\"area\"")
    private Double area;

    /** 种植作物种类（支渠明细.<旬>[].种植作物种类） */
    @TableField("\"crop_types\"")
    private String cropTypes;

    /** 需水量(万方)（支渠明细.<旬>[].需水量） */
    @TableField("\"demand_volume\"")
    private Double demandVolume;

    /** 净需水量(万方)（支渠明细.<旬>[].净需水量） */
    @TableField("\"net_demand\"")
    private Double netDemand;

    /** 建议供水量(万方)（支渠明细.<旬>[].建议供水量） */
    @TableField("\"suggested_supply\"")
    private Double suggestedSupply;

    /** 是否满足：#1#=是，#2#=否 */
    @TableField("\"is_satisfied\"")
    private String isSatisfied;

    /** 旬排序：TEN_DAY_MAP[tenday_label]（1~18） */
    @TableField("\"sort_order\"")
    private Double sortOrder;
}
