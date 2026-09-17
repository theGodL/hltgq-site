package com.qgyun.hltgq.hltgqsite.wateruse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 征收/收缴统计 VO：一次返回「各区域征收情况」与「月度收缴趋势」两段数据（供两张柱状折线组合图使用）。
 *
 * <p>两段数据共用同一套指标与单位：
 * <ul>
 *   <li>柱 = 应收水费 / 已收水费（万元，2 位截断）</li>
 *   <li>折线 = 水费收缴率（%，1 位截断，为 null 时折线断点）</li>
 * </ul>
 *
 * <p>口径与缺失（同用水总结）：
 * <ul>
 *   <li>归桶锚点 = 统计周期区间终点 xxmefs_max（缺失回退起点），只统计锚点落在统计年内的记录</li>
 *   <li>区域 = 用水户名称（用水户表 iiatzj）；无区域的记录不计入区域图、仍计入月度趋势</li>
 *   <li>桶内无数据（或该指标全部未填）→ 对应字段为 null，不补 0</li>
 *   <li>应收合计缺失或 ≤0 → 收缴率为 null</li>
 * </ul>
 */
@Data
public class WaterUseFeeCollectionVO {

    /** 统计年份 */
    private Integer year;

    /** 各区域征收情况（x 轴 = 区域；按应收水费降序，应收为 null 的区域排在末位） */
    private List<RegionItem> regions;

    /** 月度收缴趋势（x 轴 = 统计年 12 个月，1~12 月固定出桶、无数据月份字段为 null） */
    private List<MonthItem> months;

    /** 区域行 */
    @Data
    public static class RegionItem {

        /** 区域（用水户名称，如望江县） */
        private String region;

        /** 应收水费(万元)：该区域 hsfvdh 求和（元 → 万元，2 位截断）；无应收数据为 null */
        private BigDecimal receivable;

        /** 已收水费(万元)：该区域 vdhlhm 求和（元 → 万元，2 位截断）；无已收数据为 null */
        private BigDecimal received;

        /** 水费收缴率(%)：已收合计 ÷ 应收合计 × 100（1 位截断）；应收缺失或 ≤0 为 null */
        private BigDecimal collectionRate;
    }

    /** 月度行 */
    @Data
    public static class MonthItem {

        /** 月份（yyyy-MM） */
        private String month;

        /** 应收水费(万元)：当月 hsfvdh 求和（元 → 万元，2 位截断）；当月无应收数据为 null */
        private BigDecimal receivable;

        /** 已收水费(万元)：当月 vdhlhm 求和（元 → 万元，2 位截断）；当月无已收数据为 null */
        private BigDecimal received;

        /** 水费收缴率(%)：当月已收 ÷ 当月应收 × 100（1 位截断）；应收缺失或 ≤0 为 null */
        private BigDecimal collectionRate;
    }
}
