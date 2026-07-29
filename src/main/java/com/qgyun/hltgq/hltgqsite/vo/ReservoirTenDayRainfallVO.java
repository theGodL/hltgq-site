package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 水库旬月雨情 VO
 * 12 个固定水库站点，按旬（上/中/下旬）聚合雨量 + 平均值
 */
@Data
public class ReservoirTenDayRainfallVO {

    /** 12 个站点信息 */
    private List<ReservoirRainfallVO.StationInfo> stations;

    /** 旬月数据 */
    private List<TenDayRow> periods;

    @Data
    public static class TenDayRow {
        /** 年月 "yyyy-MM" */
        private String yearMonth;
        /** 旬段：上旬 / 中旬 / 下旬 */
        private String tenDay;
        /** stcd → 旬累计降雨量 (mm)，12 个 key 完整不缺 */
        private Map<String, BigDecimal> values;
        /** 该旬 12 站平均值 (mm)，保留 2 位小数 */
        private BigDecimal avg;
    }
}
