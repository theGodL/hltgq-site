package com.qgyun.hltgq.hltgqsite.weather.vo;

import lombok.Data;

/**
 * 日级预报项 VO（方案 §3.2）
 * <p>1~16 天来自 Forecast API（`accuracy=exact`，概率为上游预计算值）；
 * 17~40 天来自 EC46 50 成员聚合（`accuracy=extended`，概率由成员降水比例推导）。
 */
@Data
public class WeatherDailyVO {

    /** 日期（yyyy-MM-dd，Asia/Shanghai） */
    private String date;

    /** 星期（中文，如 "周二"） */
    private String weekday;

    /** 天气描述（中文，复用 28 项 WMO 映射） */
    private String weather;

    /** WMO 天气代码（前端映射图标） */
    private String weatherIcon;

    /** 最高温（°C，四舍五入） */
    private Integer tempMax;

    /** 最低温（°C，四舍五入） */
    private Integer tempMin;

    /** 日降水量（mm，2 位截断） */
    private Double rainfall;

    /** 降水概率（%）：A 段=上游 precipitation_probability_max；B 段=成员降水比例 */
    private Integer rainProbability;

    /** 平均相对湿度（%） */
    private Integer humidity;

    /** 风向（中文，如 "东北风"） */
    private String windDirection;

    /** 风力等级（蒲福风级 0~12） */
    private Integer windLevel;

    /** 风速（km/h，四舍五入） */
    private Integer windSpeed;

    /** 数据段标记：exact（1~16 天）/ extended（17~40 天） */
    private String accuracy;
}
