package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 短期来水逐日明细（t_auto_hltgq_water_short_forecast_daily）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_short_forecast_daily\"")
public class ShortForecastDaily extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_short_forecast_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 日期（data[].日期） */
    @TableField("\"forecast_date\"")
    private LocalDateTime forecastDate;

    /** 降雨量(mm)（data[].降雨量_mm） */
    @TableField("\"rainfall\"")
    private Double rainfall;

    /** 入库水量(万方)（data[].入库水量_万方） */
    @TableField("\"inflow_volume\"")
    private Double inflowVolume;

    /** 出库水量(万方)（data[].出库水量_万方） */
    @TableField("\"outflow_volume\"")
    private Double outflowVolume;

    /** 水位(m)（data[].水位_m） */
    @TableField("\"water_level\"")
    private Double waterLevel;

    /** 库容(万方)：查库容曲线表（精确匹配或线性插值） */
    @TableField("\"storage\"")
    private Double storage;

    /** 蒸发量(万方)：同步调 /loss(mode=short) 按日期匹配，失败留空 */
    @TableField("\"evaporation\"")
    private Double evaporation;

    /** 入库流量(m3/s)：inflow_volume*10000/86400 */
    @TableField("\"inflow_rate\"")
    private Double inflowRate;

    /** 出库流量(m3/s)：outflow_volume*10000/86400 */
    @TableField("\"outflow_rate\"")
    private Double outflowRate;
}
