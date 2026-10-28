package com.qgyun.hltgq.hltgqsite.wateruse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 征收/收缴统计 VO：一次返回大屏「用水缴费」Tab 所需的全部数据
 * （顶部四张卡片 + 「各区域征收情况」+「月度收缴趋势」+「年度收缴趋势」）。
 *
 * <p>数据源自 2026-10-27 起切换为<b>新版水费账单</b> t_auto_hltgq_water_bill
 * （调度方案出账与手工台账出账统一沉淀），不再读旧水费计算表单；四张卡片由后端聚合，
 * 前端无需再拉平台低代码明细自行求和。
 *
 * <p>指标与单位（库内金额为元，出参统一万元，2 位截断）：
 * <ul>
 *   <li>应缴/已收 = amount / paid 求和（万元）</li>
 *   <li>收费率 = 已收 ÷ 应缴 × 100（%，1 位截断，应缴 ≤ 0 为 null）</li>
 *   <li>欠收 = 应缴 − 已收（万元，2 位截断）</li>
 * </ul>
 *
 * <p>口径与缺失：
 * <ul>
 *   <li>归桶锚点 = 账期终点 period_end（月/季/年账单统一按账期止落月），只统计锚点落在统计年内的账单</li>
 *   <li>区域 = 用水户名称（业务口径：一县即一个用水户，户名即行政区名）；无区域的账单不计入区域图、仍计入月度与年度趋势</li>
 *   <li>年度趋势固定近 3 年（含统计年、升序），每个年份独立成桶，四卡片 / 区域 / 月度仍只算统计年</li>
 *   <li>桶内无账单 → 对应字段为 null，不补 0</li>
 * </ul>
 */
@Data
public class WaterUseFeeCollectionVO {

    /** 统计年份 */
    private Integer year;

    /** 年度合计（顶部四张卡片：应缴 / 已收 / 收费率 / 欠收）；当年无账单时各字段为 null */
    private TotalItem total;

    /** 各区域征收情况（x 轴 = 区域；按应缴水费降序，应缴为 null 的区域排在末位） */
    private List<RegionItem> regions;

    /** 月度收缴趋势（x 轴 = 统计年 12 个月，1~12 月固定出桶、无账单月份字段为 null） */
    private List<MonthItem> months;

    /** 年度收缴趋势（2026-10-28 新增：x 轴 = 近 3 年，含统计年、升序；无账单年份字段为 null） */
    private List<YearItem> years;

    /** 年度合计项（四卡片） */
    @Data
    public static class TotalItem {

        /** 应缴水费(万元)：当年账单金额合计（2 位截断）；无账单为 null */
        private BigDecimal receivable;

        /** 已收水费(万元)：当年账单已收金额合计（2 位截断）；无账单为 null */
        private BigDecimal received;

        /** 收费率(%)：已收合计 ÷ 应缴合计 × 100（1 位截断）；应缴缺失或 ≤0 为 null */
        private BigDecimal collectionRate;

        /** 欠收金额(万元) = 应缴 − 已收（2 位截断）；任一侧缺失为 null */
        private BigDecimal arrears;
    }

    /** 区域行 */
    @Data
    public static class RegionItem {

        /** 区域（用水户名称，如望江县） */
        private String region;

        /** 应缴水费(万元)：该区域账单金额求和（2 位截断）；无数据为 null */
        private BigDecimal receivable;

        /** 已收水费(万元)：该区域账单已收求和（2 位截断）；无数据为 null */
        private BigDecimal received;

        /** 水费收缴率(%)：已收合计 ÷ 应缴合计 × 100（1 位截断）；应缴缺失或 ≤0 为 null */
        private BigDecimal collectionRate;
    }

    /** 年度行（近三年趋势） */
    @Data
    public static class YearItem {

        /** 年份（如 2026） */
        private Integer year;

        /** 应缴水费(万元)：该年账期止的账单金额求和（2 位截断）；该年无数据为 null */
        private BigDecimal receivable;

        /** 已收水费(万元)：该年账单已收求和（2 位截断）；该年无数据为 null */
        private BigDecimal received;

        /** 水费收缴率(%)：该年已收 ÷ 该年应缴 × 100（1 位截断）；应缴缺失或 ≤0 为 null */
        private BigDecimal collectionRate;

        /** 欠收金额(万元) = 应缴 − 已收（2 位截断）；任一侧缺失为 null */
        private BigDecimal arrears;
    }

    /** 月度行 */
    @Data
    public static class MonthItem {

        /** 月份（yyyy-MM） */
        private String month;

        /** 应缴水费(万元)：当月账期止的账单金额求和（2 位截断）；当月无数据为 null */
        private BigDecimal receivable;

        /** 已收水费(万元)：当月账期止的账单已收求和（2 位截断）；当月无数据为 null */
        private BigDecimal received;

        /** 水费收缴率(%)：当月已收 ÷ 当月应缴 × 100（1 位截断）；应缴缺失或 ≤0 为 null */
        private BigDecimal collectionRate;
    }
}
