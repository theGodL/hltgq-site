package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 水库时段雨情 VO
 * 12 个固定水库雨量站点（不含水情站花凉亭坝下），按可配时间间隔聚合时段雨量
 */
@Data
public class ReservoirPeriodRainfallVO {

    /** 12 个站点信息（复用 StationInfo） */
    private List<ReservoirRainfallVO.StationInfo> stations;

    /** 时段桶数据 */
    private List<BucketRow> buckets;

    @Data
    public static class BucketRow {
        /** 时段标签 "yyyy-MM-dd HH:mm"（时段终点标注，如 "12:00" 表示区间 (11:00, 12:00] 的雨量） */
        private String time;
        /** stcd → 时段降雨量(mm)，无数据站点 = 0 */
        private Map<String, BigDecimal> values;
        /** 该时段 12 站平均值 (mm) */
        private BigDecimal avg;
    }
}
