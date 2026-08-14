package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 水库水情-多年同期水情 VO
 * <p>按年份区间 + 月份查询，每个年份一行，各站月平均水位与 stations 顺序对齐。
 */
@Data
public class YearsRegimeVO {

    /** 站点名称列表（表头列） */
    private List<String> stations;

    /** 数据行：每年一行 */
    private List<YearRow> rows;

    @Data
    public static class YearRow {

        /** 监测日期（yyyy-MM，如 2011-07） */
        private String tm;

        /** 各站月平均水位，与 stations 一一对应；无数据为 null */
        private List<BigDecimal> values;
    }
}
