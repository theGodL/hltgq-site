package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 水库实时雨情 VO — 双视角设计
 * - stations[]: 实时雨情视角，各站点最新观测快照（latestTm = 实际观测时间）
 * - days[]:    日雨情视角，逐日雨量透视表（水文日 8:00 切分）
 */
@Data
public class ReservoirRainfallVO {

    /** 12 个站点信息（含最新观测） */
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
        /** 该站点最新观测时间（实际记录时间，未经水文日调整） */
        private LocalDateTime latestTm;
        /** 该站点最新当前降雨量 DRP（mm）— 水文日累计 */
        private BigDecimal latestDrp;
    }

    @Data
    public static class DayRainfall {
        /** 水文日标签 "yyyy-MM-dd 08:00:00"（代表该日 08:00 切分的水文日） */
        private String day;
        /** stcd → 日降雨量(mm)，无数据的站点值为 0，12 个 key 完整不缺 */
        private Map<String, BigDecimal> values;
        /** 该水文日 12 站平均值 (mm)，保留 2 位小数 */
        private BigDecimal avg;
    }
}
