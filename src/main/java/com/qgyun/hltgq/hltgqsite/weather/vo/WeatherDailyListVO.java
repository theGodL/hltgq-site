package com.qgyun.hltgq.hltgqsite.weather.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 40 天日级预报响应 VO（方案 §3.2）
 * <p>`days` 为实际返回天数（缺失段时如实反映）；`degradedSegments` 与 `days`
 * 同为"部分数据"的显式标记（复审 A6：既不静默也不丢弃有效段）。
 */
@Data
public class WeatherDailyListVO {

    /** 站点名称 */
    private String location;

    /** 数据生成时间（yyyy-MM-ddTHH:mm:ss） */
    private String updatedAt;

    /** 数据来源：openmeteo=正常；default=降级占位 */
    private String source;

    /** 实际返回天数 */
    private Integer days;

    /** 本次返回 list 中 accuracy=exact 的实际天数（复审 C6：随响应变化，前端不可写死 16） */
    private Integer exactDays;

    /** 因上游失败/超旧值上限而缺失的段，如 ["extended"]；空数组=完整 */
    private List<String> degradedSegments = new ArrayList<>();

    /** 逐日预报（date 升序，从今天起） */
    private List<WeatherDailyVO> list = new ArrayList<>();
}
