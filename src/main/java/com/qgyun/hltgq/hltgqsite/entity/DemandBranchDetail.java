package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 需水支渠逐旬明细（t_auto_hltgq_water_demand_branch_detail）。
 * <p>接口返回的每条支渠记录纵向展开为 18 条（5~10月每旬一条）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_demand_branch_detail\"")
public class DemandBranchDetail extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_demand_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 片区（支渠明细[].片区） */
    @TableField("\"district\"")
    private String district;

    /** 分灌区（支渠明细[].分灌区） */
    @TableField("\"sub_district\"")
    private String subDistrict;

    /** 支渠名称（支渠明细[].支渠） */
    @TableField("\"branch_name\"")
    private String branchName;

    /** 灌区面积(亩)（支渠明细[].灌区面积） */
    @TableField("\"area\"")
    private Double area;

    /** 种植作物种类（支渠明细[].种植作物种类） */
    @TableField("\"crop_types\"")
    private String cropTypes;

    /** 旬标签（如 "5月上旬"） */
    @TableField("\"tenday_label\"")
    private String tendayLabel;

    /** 需水量(万方)（支渠明细[].<旬标签>） */
    @TableField("\"demand_volume\"")
    private Double demandVolume;

    /** 旬排序：TEN_DAY_MAP[tenday_label]（1~18） */
    @TableField("\"sort_order\"")
    private Double sortOrder;
}
