package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配水调度旬缩放系数（t_auto_hltgq_water_decision_scale_factor）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_decision_scale_factor\"")
public class DecisionScaleFactor extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_decision_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 旬标签（缩放系数对象的键名，如 "5月上旬"） */
    @TableField("\"tenday_label\"")
    private String tendayLabel;

    /** 缩放系数（缩放系数.<旬标签>） */
    @TableField("\"scale_factor\"")
    private Double scaleFactor;

    /** 旬排序：TEN_DAY_MAP[tenday_label]（1~18） */
    @TableField("\"sort_order\"")
    private Double sortOrder;
}
