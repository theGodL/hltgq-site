package com.qgyun.hltgq.hltgqsite.weather.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 雨情趋势响应 VO（方案 §6.2）
 * <p>由 `/weather/rain-trend` 实时读取日级缓存计算，<b>不落独立缓存</b>（评审 A1）——
 * 因此与 `/weather/daily` 永远一致，降级状态也完全继承。
 */
@Data
public class RainTrendVO {

    /** 站点名称 */
    private String location;

    /** 数据生成时间（继承日级缓存的生成时间） */
    private String updatedAt;

    /** 数据来源：openmeteo=正常；default=降级占位 */
    private String source;

    /** 累计雨量（mm，2 位截断） */
    private Double totalRainfall;

    /** 最大单日雨量（全为 0 时返回 null） */
    private Double maxDailyRainfall;

    /** 最大单日雨量对应日期（全为 0 时返回 null） */
    private String maxDailyDate;

    /** 有雨天数（日雨量 ≥ 0.1 mm） */
    private Integer rainyDays;

    /** 最长连续无雨天数（雨量 < 0.1 mm 的连续段） */
    private Integer dryDays;

    /** 最长连续无雨段起始日期；dryDays = 0 时为 null（评审 C3） */
    private String dryDaysStart;

    /** 本次返回 list 中 accuracy=exact 的实际天数 */
    private Integer exactDays;

    /** 缺失段标记（继承日级缓存，如 ["extended"]） */
    private List<String> degradedSegments = new ArrayList<>();

    /** 逐日趋势（date 升序） */
    private List<RainTrendDayVO> list = new ArrayList<>();
}
