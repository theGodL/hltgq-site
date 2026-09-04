package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 短期来水逐小时明细（t_auto_hltgq_water_short_forecast_daily）。
 * <p>2026-09 小时尺度重构：forecast_date 为小时粒度（模型 data[].时间），
 * 字段口径对齐 /forecast V3 逐小时演算表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_short_forecast_daily\"")
public class ShortForecastDaily extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_short_forecast_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 时间（data[].时间，小时粒度） */
    @TableField("\"forecast_date\"")
    private LocalDateTime forecastDate;

    /** 降雨量(mm)：入参逐小时序列对应小时值 */
    @TableField("\"rainfall\"")
    private Double rainfall;

    /** 入库水量(万方)：入库流量_m3s × 0.36（小时量换算） */
    @TableField("\"inflow_volume\"")
    private Double inflowVolume;

    /** 出库水量(万方)：合计下泄流量_m3s × 0.36（小时量换算） */
    @TableField("\"outflow_volume\"")
    private Double outflowVolume;

    /** 水位(m)（data[].水位_m） */
    @TableField("\"water_level\"")
    private Double waterLevel;

    /** 库容(万方)（data[].库容_万方，缺失降级查库容曲线表） */
    @TableField("\"storage\"")
    private Double storage;

    /** 蒸发量(万方)：/loss short 模式已废弃，恒 null（历史字段） */
    @TableField("\"evaporation\"")
    private Double evaporation;

    /** 入库流量(m3/s)（data[].入库流量_m3s） */
    @TableField("\"inflow_rate\"")
    private Double inflowRate;

    /** 出库流量(m3/s)（data[].合计下泄流量_m3s） */
    @TableField("\"outflow_rate\"")
    private Double outflowRate;
}
