package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 需水片区/分灌区汇总（t_auto_hltgq_water_demand_area_summary）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_demand_area_summary\"")
public class DemandAreaSummary extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_demand_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 汇总类型："片区" 或 "分灌区" */
    @TableField("\"summary_type\"")
    private String summaryType;

    /** 区域名称（片区汇总[].片区 或 分灌区汇总[].分灌区） */
    @TableField("\"area_name\"")
    private String areaName;

    /** 旬标签（如 "5月上旬"） */
    @TableField("\"tenday_label\"")
    private String tendayLabel;

    /** 需水量(万方) */
    @TableField("\"demand_volume\"")
    private Double demandVolume;

    /** 旬排序：TEN_DAY_MAP[tenday_label]（1~18） */
    @TableField("\"sort_order\"")
    private Double sortOrder;
}
