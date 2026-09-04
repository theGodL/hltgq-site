package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 墒情预测方案主表实体。
 * <p>注意：平台建表名为 t_auto_hltgq_water_moisture_detail（含 scheme_name 的方案表，
 * 平台命名与实体名交叉，勿按字面误改），2026-09 定稿。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_moisture_detail\"")
public class MoistureRecord extends BaseRecordEntity {

    /** 模拟开始时间（各站点初始时刻最早整点） */
    @TableField("\"start_time\"")
    private LocalDateTime startTime;

    /** 模拟结束时间（rain_data 最后时间点） */
    @TableField("\"end_time\"")
    private LocalDateTime endTime;

    /** 参与站点数（有效初始墒情站点） */
    @TableField("\"station_count\"")
    private Double stationCount;

    /** 请求体归档 JSON */
    @TableField("\"request_json\"")
    private String requestJson;
}
