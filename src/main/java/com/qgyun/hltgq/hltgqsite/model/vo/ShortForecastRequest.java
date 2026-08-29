package com.qgyun.hltgq.hltgqsite.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 短期来水预测提交入参（/water-forecast/short）。
 */
@Data
public class ShortForecastRequest {

    /** 方案名称（可选，默认自动生成 "短期预报_<起始日期>_<天数>天"） */
    private String schemeName;

    /** 起始日期 YYYY-MM-DD（必填） */
    private String startDate;

    /** 预报天数 1~30（默认30；模型契约上限 30） */
    private Integer days;

    /** 是否使用典型洪水（默认 false） */
    private Boolean useTypical;

    /** 典型洪水样本编号 0~5（useTypical=true 时生效） */
    private Integer floodIdx;

    /** 逐日降雨量(mm)（useTypical=false 时必填，长度==days） */
    private List<Double> rainfall;

    /** 是否调整降雨（默认 false） */
    private Boolean adjustRainfall;

    /** 目标总降雨量(mm)（adjustRainfall=true 时必须 >0） */
    private Double targetTotal;

    /** 初始库水位(m)（默认 79.89） */
    private Double initialWaterLevel;

    /** 下泄模式：max / none / custom（默认 max） */
    private String dischargeMode;

    /** 自定义逐日下泄量（dischargeMode=custom 时必填，长度==days） */
    private List<Double> customDischarge;
}
