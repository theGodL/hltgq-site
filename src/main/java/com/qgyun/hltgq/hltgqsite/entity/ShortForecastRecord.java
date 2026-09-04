package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 短期来水预测方案主表（t_auto_hltgq_water_short_forecast_record）。
 * <p>2026-09 小时尺度重构：/forecast V3 契约 start/end/start_level/三开关，旧字段（use_typical 等）废弃不再写入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_short_forecast_record\"")
public class ShortForecastRecord extends BaseRecordEntity {

    /** 开始时间（/forecast start，含小时） */
    @TableField("\"start_date\"")
    private LocalDateTime startDate;

    /** 结束时间（/forecast end，含端点） */
    @TableField("\"end_date\"")
    private LocalDateTime endDate;

    /** 预报小时步数（start→end 逐小时步数，16 天=385） */
    @TableField("\"days\"")
    private Double days;

    /** 起调水位(m)（自动取坝上最新水位，模型 start_level） */
    @TableField("\"start_level\"")
    private Double startLevel;

    /** 是否启用发电下泄：#1#=是，#2#=否 */
    @TableField("\"enable_power\"")
    private String enablePower;

    /** 是否启用泄洪隧洞：#1#=是，#2#=否 */
    @TableField("\"enable_tunnel\"")
    private String enableTunnel;

    /** 是否启用溢洪道：#1#=是，#2#=否 */
    @TableField("\"enable_spillway\"")
    private String enableSpillway;

    /** 是否使用典型洪水：#1#=是，#2#=否（旧字段，小时尺度废弃） */
    @TableField("\"use_typical\"")
    private String useTypical;

    /** 典型洪水样本编号（0~5）（旧字段，小时尺度废弃） */
    @TableField("\"flood_idx\"")
    private Double floodIdx;

    /** 是否调整降雨：#1#=是，#2#=否（旧字段，小时尺度废弃） */
    @TableField("\"adjust_rainfall\"")
    private String adjustRainfall;

    /** 目标总降雨量(mm)（旧字段，小时尺度废弃） */
    @TableField("\"target_total\"")
    private Double targetTotal;

    /** 初始库水位(m)（旧字段，小时尺度废弃） */
    @TableField("\"initial_water_level\"")
    private Double initialWaterLevel;

    /** 下泄模式：max / none / custom（旧字段，小时尺度废弃） */
    @TableField("\"discharge_mode\"")
    private String dischargeMode;

    /** 降雨数组归档 JSON（逐小时序列） */
    @TableField("\"rainfall_json\"")
    private String rainfallJson;

    /** 自定义下泄数组归档 JSON（旧字段，小时尺度废弃） */
    @TableField("\"custom_discharge_json\"")
    private String customDischargeJson;

    /** 请求体归档 JSON */
    @TableField("\"request_json\"")
    private String requestJson;

    /** 总降雨量(mm)（meta.total_rainfall_mm） */
    @TableField("\"total_rainfall\"")
    private Double totalRainfall;

    /** 总入库水量(万方)（summary.总来水量_万方） */
    @TableField("\"total_inflow\"")
    private Double totalInflow;

    /** 总出库水量(万方)（Σ 明细出库流量换算） */
    @TableField("\"total_outflow\"")
    private Double totalOutflow;

    /** 期末水位(m)（data 末条 水位_m） */
    @TableField("\"final_water_level\"")
    private Double finalWaterLevel;

    /** 最高水位(m)（summary.最高水位_m） */
    @TableField("\"max_water_level\"")
    private Double maxWaterLevel;

    /** 峰值入库流量(m3/s)：summary.入库洪峰流量_m3s，缺失回退 MAX(明细.inflow_rate) */
    @TableField("\"peak_inflow_rate\"")
    private Double peakInflowRate;
}
