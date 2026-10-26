package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 水量损失明细（t_auto_hltgq_water_loss_detail）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_loss_detail\"")
public class LossDetail extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_loss_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 日期：短期=具体日期；中长期=旬首日期（旬标签+年份推断） */
    @TableField("\"data_date\"")
    private LocalDateTime dataDate;

    /** 预测水量(万方)：中长期模式恒 null（蒸发/渗漏分离后无此列） */
    @TableField("\"predict_volume\"")
    private Double predictVolume;

    /** 损失水量(万方)：蒸发量 + 渗漏量（两列均无时留空） */
    @TableField("\"loss_volume\"")
    private Double lossVolume;

    /** 蒸发量(万方)（data[].蒸发损失_万方） */
    @TableField("\"evaporation_volume\"")
    private Double evaporationVolume;

    /** 渗漏量(万方)（data[].渗漏损失_万方） */
    @TableField("\"leakage_volume\"")
    private Double leakageVolume;
}
