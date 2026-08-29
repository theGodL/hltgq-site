package com.qgyun.hltgq.hltgqsite.weather.vo;

import lombok.Data;

/**
 * 实时天气卡片 VO（Open-Meteo）
 * <p>字段契约与天气接入方案 v3.1 文档 4.1 节一致，前端按 source 判断异常态（default 即数据异常）。
 */
@Data
public class WeatherCardVO {

    /** 温度（带单位，如 "32°C"；降级时 "--°C"） */
    private String temperature;

    /** 天气描述（中文，如 "晴朗"） */
    private String weatherDesc;

    /** 体感温度（如 "体感温度 36°"） */
    private String feelTemperature;

    /** WMO 天气代码（如 "51"，前端映射图标） */
    private String weatherIcon;

    /** 站点/地点名称（如 "南排"） */
    private String location;

    /** 数据更新时间（Open-Meteo current.time，如 "2026-08-29T15:00"） */
    private String updateTime;

    /** 数据来源：openmeteo=正常，default=降级占位 */
    private String source;
}
