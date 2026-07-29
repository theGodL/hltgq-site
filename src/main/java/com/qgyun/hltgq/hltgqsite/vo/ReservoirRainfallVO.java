package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 水库实时雨情 VO
 * 12 个固定水库站点，按水文日（8:00 切分）聚合日雨量
 */
@Data
public class ReservoirRainfallVO {

    /** 12 个站点信息 */
    private List<StationInfo> stations;

    /** 逐日雨情数据（水文日维度） */
    private List<DayRainfall> days;

    @Data
    public static class StationInfo {
        /** 站点编号 */
        private String stcd;
        /** 站点名称 */
        private String stnm;
        /** 站点 UUID */
        private String id;
        /** 该站点最新观测时间 */
        private LocalDateTime latestTm;
        /** 该站点最新 DRP 值 (mm) */
        private BigDecimal latestDrp;
    }

    @Data
    public static class DayRainfall {
        /** 水文日标签 "yyyy-MM-dd 08:00:00"（代表该日 08:00 切分的水文日） */
        private String day;
        /** stcd → 日降雨量(mm)，无数据的站点 key 缺失或值为 0 */
        private Map<String, BigDecimal> values;
        /** 该水文日 12 站平均值 (mm)，保留 2 位小数 */
        private BigDecimal avg;
    }
}
