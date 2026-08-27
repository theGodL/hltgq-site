package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 短期来水预测方案主表（t_auto_hltgq_water_short_forecast_record）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_short_forecast_record\"")
public class ShortForecastRecord extends BaseRecordEntity {

    /** 起始日期（/forecast start_date） */
    @TableField("\"start_date\"")
    private LocalDateTime startDate;

    /** 预报天数（1~30） */
    @TableField("\"days\"")
    private Double days;

    /** 是否使用典型洪水：#1#=是，#2#=否 */
    @TableField("\"use_typical\"")
    private String useTypical;

    /** 典型洪水样本编号（0~5） */
    @TableField("\"flood_idx\"")
    private Double floodIdx;

    /** 是否调整降雨：#1#=是，#2#=否 */
    @TableField("\"adjust_rainfall\"")
    private String adjustRainfall;

    /** 目标总降雨量(mm) */
    @TableField("\"target_total\"")
    private Double targetTotal;

    /** 初始库水位(m) */
    @TableField("\"initial_water_level\"")
    private Double initialWaterLevel;

    /** 下泄模式：max / none / custom */
    @TableField("\"discharge_mode\"")
    private String dischargeMode;

    /** 降雨数组归档 JSON */
    @TableField("\"rainfall_json\"")
    private String rainfallJson;

    /** 自定义下泄数组归档 JSON */
    @TableField("\"custom_discharge_json\"")
    private String customDischargeJson;

    /** 请求体归档 JSON */
    @TableField("\"request_json\"")
    private String requestJson;

    /** 总降雨量(mm)（summary.总降雨量_mm） */
    @TableField("\"total_rainfall\"")
    private Double totalRainfall;

    /** 累计入库水量(万方)（summary.总入库水量_万方） */
    @TableField("\"total_inflow\"")
    private Double totalInflow;

    /** 累计出库水量(万方)（summary.总出库水量_万方） */
    @TableField("\"total_outflow\"")
    private Double totalOutflow;

    /** 期末水位(m)（summary.期末水位_m） */
    @TableField("\"final_water_level\"")
    private Double finalWaterLevel;

    /** 最高水位(m)（summary.最高水位_m） */
    @TableField("\"max_water_level\"")
    private Double maxWaterLevel;

    /** 峰值入库流量(m3/s)：MAX(明细.inflow_rate) */
    @TableField("\"peak_inflow_rate\"")
    private Double peakInflowRate;
}
