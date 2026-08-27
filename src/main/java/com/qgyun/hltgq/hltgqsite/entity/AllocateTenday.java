package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 水资源配置逐旬明细（t_auto_hltgq_water_allocate_tenday）。
 * <p>接口返回旬标签字符串（无 DATE），必须用 sort_order（FULL_TEN_DAY_MAP 1~36）排序。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_allocate_tenday\"")
public class AllocateTenday extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_allocate_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 旬标签（旬尺度[].日期，如 "5月上旬"） */
    @TableField("\"tenday_label\"")
    private String tendayLabel;

    /** BP预测来水量(万方)（旬尺度[].BP预测来水量（万方）） */
    @TableField("\"bp_inflow\"")
    private Double bpInflow;

    /** 库面蒸发数据(万方)（旬尺度[].库面蒸发数据（万方）） */
    @TableField("\"evaporation\"")
    private Double evaporation;

    /** 灌溉需水(万方) */
    @TableField("\"irrigation_demand\"")
    private Double irrigationDemand;

    /** 城镇需水(万方) */
    @TableField("\"urban_demand\"")
    private Double urbanDemand;

    /** 农村生活需水量(万方) */
    @TableField("\"rural_demand\"")
    private Double ruralDemand;

    /** 河道生态需水总量(万方) */
    @TableField("\"eco_demand\"")
    private Double ecoDemand;

    /** 总需水量(万方)（旬尺度[].总需水量（万方）） */
    @TableField("\"total_demand\"")
    private Double totalDemand;

    /** 需大于供(万方)（旬尺度[].需>供） */
    @TableField("\"demand_gt_supply\"")
    private Double demandGtSupply;

    /** 供大于需(万方)（旬尺度[].供>需） */
    @TableField("\"supply_gt_demand\"")
    private Double supplyGtDemand;

    /** 差值（来水-需水）(万方) */
    @TableField("\"diff_inflow_demand\"")
    private Double diffInflowDemand;

    /** 弃水(万方) */
    @TableField("\"spill\"")
    private Double spill;

    /** 下泄+弃水(万方) */
    @TableField("\"discharge_plus_spill\"")
    private Double dischargePlusSpill;

    /** 月末库容(万方) */
    @TableField("\"end_storage\"")
    private Double endStorage;

    /** 月末水位(m) */
    @TableField("\"end_water_level\"")
    private Double endWaterLevel;

    /** 塘坝可供水量(万方)（旬尺度[].塘坝可供水量（万方）） */
    @TableField("\"pond_supply\"")
    private Double pondSupply;

    /** 水厂供水(万方) */
    @TableField("\"waterworks_supply\"")
    private Double waterworksSupply;

    /** 花凉亭水库灌溉供水(万方) */
    @TableField("\"reservoir_irrigation\"")
    private Double reservoirIrrigation;

    /** 总供水(万方) */
    @TableField("\"total_supply\"")
    private Double totalSupply;

    /** 是否满足需水：#1#=是，#2#=否 */
    @TableField("\"is_satisfied\"")
    private String isSatisfied;

    /** 旬排序：FULL_TEN_DAY_MAP[tenday_label]（1~36） */
    @TableField("\"sort_order\"")
    private Double sortOrder;
}
