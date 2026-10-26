package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 水量损失预测方案主表（t_auto_hltgq_water_loss_record）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_loss_record\"")
public class LossRecord extends BaseRecordEntity {

    /** 模式：short（短期逐日）/ long（中长期逐旬） */
    @TableField("\"mode\"")
    private String mode;

    /** 起始日期（短期模式） */
    @TableField("\"start_date\"")
    private LocalDateTime startDate;

    /** 天数（短期模式） */
    @TableField("\"days\"")
    private Double days;

    /** 情景（中长期模式） */
    @TableField("\"scenario\"")
    private String scenario;

    /** 是否使用历史数据（中长期模式）：#1#=是，#2#=否 */
    @TableField("\"use_historical\"")
    private String useHistorical;

    /** 历史年份（中长期模式） */
    @TableField("\"historical_year\"")
    private Double historicalYear;

    /** 是否重训练（中长期模式）：#1#=是，#2#=否 */
    @TableField("\"retrain\"")
    private String retrain;

    /** 年渗漏量(万方)：默认 156（/loss mode=long 入参 annual_leakage_wan_m3） */
    @TableField("\"annual_leakage_wan_m3\"")
    private Double annualLeakageWanM3;

    /** 降雨数组归档 JSON（短期模式） */
    @TableField("\"rainfall_json\"")
    private String rainfallJson;

    /** 请求体归档 JSON */
    @TableField("\"request_json\"")
    private String requestJson;

    /** 总损失水量(万方)：总蒸发量 + 总渗漏损失（summary.总蒸发量_万方 + 总渗漏损失_万方） */
    @TableField("\"total_loss\"")
    private Double totalLoss;

    /** 总蒸发量(万方)（summary.总蒸发量_万方） */
    @TableField("\"total_evaporation\"")
    private Double totalEvaporation;

    /** 总渗漏损失(万方)（summary.总渗漏损失_万方） */
    @TableField("\"total_leakage\"")
    private Double totalLeakage;

    /** 总来水量(万方)：summary.总预测来水量_万方 / 总入库水量_万方 */
    @TableField("\"total_inflow\"")
    private Double totalInflow;
}
