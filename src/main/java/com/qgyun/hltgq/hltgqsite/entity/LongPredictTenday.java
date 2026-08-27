package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 中长期来水逐旬明细（t_auto_hltgq_water_long_predict_tenday）。
 * <p>predict_date 存储旬首日期（如 "5月上旬" → 2026-05-01），直接按时间排序。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_long_predict_tenday\"")
public class LongPredictTenday extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_long_predict_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 旬标签（data[].日期 提取，如 "5月上旬"） */
    @TableField("\"tenday_label\"")
    private String tendayLabel;

    /** 预测日期：该旬第一天 */
    @TableField("\"predict_date\"")
    private LocalDateTime predictDate;

    /** 预测来水量(万方)（data[].预测来水量_万方） */
    @TableField("\"predict_volume\"")
    private Double predictVolume;

    /** 实测来水量(万方)（data[].真实来水量_万方） */
    @TableField("\"actual_volume\"")
    private Double actualVolume;
}
