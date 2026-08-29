package com.qgyun.hltgq.hltgqsite.weather.vo;

import lombok.Data;

/**
 * 逐小时天气列表项 VO（Open-Meteo）
 * <p>字段契约与天气接入方案 v3.1 文档 4.2 节一致；列表时间倒序（最新在前）、id 重新编号。
 */
@Data
public class WeatherListItemVO {

    /** 序号（列表内重新编号，从 1 开始） */
    private Long id;

    /** 日期（yyyy-MM-dd） */
    private String date;

    /** 小时（HH:mm） */
    private String hour;

    /** 站点/地点名称 */
    private String location;

    /** 天气描述（中文） */
    private String weather;

    /** 温度（°C，整数，四舍五入） */
    private Integer temperature;

    /** 降雨量（mm） */
    private Double rainfall;

    /** 风向（中文，如 "东北风"） */
    private String windDirection;

    /** 风力等级（蒲福风级 0~12） */
    private Integer windLevel;

    /** 风速（km/h，四舍五入） */
    private Integer windSpeed;

    /** 湿度（%RH，四舍五入） */
    private Integer humidity;

    /** WMO 天气代码（前端映射图标） */
    private String weatherIcon;
}
