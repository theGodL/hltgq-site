package com.qgyun.hltgq.hltgqsite.weather.vo;

import lombok.Data;

/**
 * 雨情趋势-逐日项 VO（方案 §6.2）
 * <p>`rainLevel` 按国标 GB/T 28592 日雨量分级；`cumulative` 为从今天起的累计雨量。
 */
@Data
public class RainTrendDayVO {

    /** 日期（yyyy-MM-dd） */
    private String date;

    /** 日降水量（mm，2 位截断） */
    private Double rainfall;

    /** 降水概率（%） */
    private Integer rainProbability;

    /** 累计雨量（从今天起累加，2 位截断） */
    private Double cumulative;

    /** 日雨量等级（小雨/中雨/大雨/暴雨/大暴雨/特大暴雨；< 0.1 mm 为 null） */
    private String rainLevel;

    /** 数据段标记：exact / extended */
    private String accuracy;
}
