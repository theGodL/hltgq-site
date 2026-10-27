package com.qgyun.hltgq.hltgqsite.wateruse.service;

import com.qgyun.hltgq.hltgqsite.irrigation.mapper.IrrigationWaterMapper;
import com.qgyun.hltgq.hltgqsite.irrigation.vo.IrrigationIntervalVO;
import com.qgyun.hltgq.hltgqsite.model.util.WaterVolumeUtils;
import com.qgyun.hltgq.hltgqsite.wateruse.config.WaterUseCoeffStations;
import com.qgyun.hltgq.hltgqsite.wateruse.config.WaterUseSeasonConfig;
import com.qgyun.hltgq.hltgqsite.wateruse.mapper.WaterUseSummaryMapper;
import com.qgyun.hltgq.hltgqsite.wateruse.vo.WaterBillFeeRecordVO;
import com.qgyun.hltgq.hltgqsite.wateruse.vo.WaterUseFeeCollectionVO;
import com.qgyun.hltgq.hltgqsite.wateruse.vo.WaterUseFeeRecordVO;
import com.qgyun.hltgq.hltgqsite.wateruse.vo.WaterUseReportRowVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.qgyun.hltgq.hltgqsite.model.util.DeviceValueUtils;

/**
 * 用水总结服务：水费表单按统计桶聚合（用水量/应收水费）+ 灌溉水利用系数实测近似值。
 *
 * <p>表单字段录入口径（2026-09-17 线上数据复核）：计划供水量 万m³、执行水价 元/m³、**应收/已收水费 元**
 * ——水费金额按「元」入库（表单标签即「应收水费（元）」，自动算式 = 用水量 × 执行水价），
 * 接口对外口径为「万元」，故出参统一除以 10^4 后按展示精度截断（{@link #FEE_YUAN_TO_WAN_SHIFT}）。
 *
 * <p>口径（业主 2026-09-11 确认）：
 * <ul>
 *   <li>归桶锚点 = 统计周期区间终点 xxmefs_max（跨桶按终点，终点缺失回退起点）</li>
 *   <li>用水量 = 计划供水量 mlljya 桶内求和（直取表单值、不做推算；表单单位即万m³，按展示精度 3 位截断，
 *       与累计流量展示同口径）</li>
 *   <li>应收水费 = 水费表单 hsfvdh 桶内求和（表单按元录入 → 出参万元，按展示精度 2 位截断）</li>
 *   <li>灌溉水利用系数（近似值）= Σ(北干/南干/太宿/太怀 进水闸区间累计) ÷ 渠首进水闸区间累计
 *       （同桶窗口 ttf 区间累计，3 位小数；渠首缺失/为 0 或四干渠全部无数据时为 null）</li>
 * </ul>
 *
 * <p>征收/收缴统计（另一入口 {@link #feeCollection}）：自 2026-10-27 起改取<b>新版水费账单</b>
 * t_auto_hltgq_water_bill（调度方案出账与手工台账出账统一沉淀），不再读水费表单；
 * 区域取用水户名称（业务口径：一县即一个用水户，户名即行政区名），应收 amount / 已收 paid 按「元」入库
 * → 出参「万元」（按展示精度 2 位截断），收费率 1 位截断，另出年度合计四卡片（应缴/已收/收费率/欠收）。
 *
 * <p>统计桶：月 / 灌季（{@link WaterUseSeasonConfig}）/ 年；桶内无记录为 null（不补 0）。
 * 单个区间取数逐站日志化（{@code [用水总结]} 前缀），联调可核对。
 */
@Service
public class WaterUseSummaryService {

    private static final Logger log = LoggerFactory.getLogger(WaterUseSummaryService.class);

    /** 查询区间上限（月）：防御大跨度聚合，10 年 */
    private static final long MAX_RANGE_MONTHS = 120L;

    /** 逐次请求输出的水费记录样例条数（联调核对原始值用） */
    private static final int FEE_SAMPLE_LOGS = 5;

    /** 征收/收缴统计年份合法范围（防御越界年份构造无意义区间） */
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    /** 月度收缴趋势固定出桶数（全年 12 个月） */
    private static final int MONTHS_OF_YEAR = 12;

    /** 金额展示小数位（万元，截断） */
    private static final int WAN_SCALE = 2;

    /**
     * 水费金额录入口径 → 出参口径的小数点左移位数（元 → 万元，即 ÷10^4）。
     * <p>依据（2026-09-17 线上数据复核）：水费表单金额字段标签为「应收水费（元）」、自动算式 = 用水量 × 执行水价(元/m³)，
     * 即按「元」录入；线上实录 5130000（潜山县）若按万元解读相当于 513 亿元，与业务量级不符，
     * 换算后 513.00 万元与 1500 万m³ × 0.342 元/m³ 自洽。
     * <p>换算点：应收/已收（{@link #receivableOf} / {@link #receivedOf}）与报表应收水费（{@link #buildRow}）；
     * 收缴率为比值，不受单位影响。
     */
    private static final int FEE_YUAN_TO_WAN_SHIFT = 4;

    /** 收缴率展示小数位（%，截断） */
    private static final int RATE_SCALE = 1;

    /** 收缴率换算基数（%） */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Autowired
    private WaterUseSummaryMapper waterUseSummaryMapper;

    @Autowired
    private IrrigationWaterMapper irrigationWaterMapper;

    @Autowired
    private WaterUseSeasonConfig seasonConfig;

    /**
     * 用水总结报表：按 dimension 归桶。
     *
     * @param dimension 归桶粒度：month（月）/ season（灌季）/ year（年）
     * @param startTime 查询起点（yyyy-MM-dd）
     * @param endTime   查询终点（yyyy-MM-dd）
     * @return 按时间升序的报表行（桶全覆盖查询区间，无数据桶为 null）
     */
    public List<WaterUseReportRowVO> report(String dimension, LocalDate startTime, LocalDate endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("startTime/endTime 必填（yyyy-MM-dd）");
        }
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime 不能早于 startTime");
        }
        if (ChronoUnit.MONTHS.between(startTime.withDayOfMonth(1), endTime.withDayOfMonth(1)) > MAX_RANGE_MONTHS) {
            throw new IllegalArgumentException("查询区间不能超过 10 年");
        }

        List<Bucket> buckets = buildBuckets(dimension, startTime, endTime);
        if (buckets.isEmpty()) {
            log.info("[用水总结] 查询 {} ~ {} dimension={} 未产生统计桶（区间内无完整统计周期）",
                    startTime, endTime, dimension);
            return Collections.emptyList();
        }

        // 水费表单：一次取数覆盖全部桶（按首末桶整段边界外扩）
        List<WaterUseFeeRecordVO> fees = waterUseSummaryMapper.selectFeeRecords(
                buckets.get(0).start.atStartOfDay(), buckets.get(buckets.size() - 1).end.atTime(23, 59, 59));
        fillFeeAmounts(buckets, fees);

        List<WaterUseReportRowVO> rows = new ArrayList<>();
        for (Bucket bucket : buckets) {
            rows.add(buildRow(bucket));
        }
        log.info("[用水总结] 查询完成 dimension={} 窗口={} ~ {} 桶数={} 水费记录={} 条",
                dimension, startTime, endTime, rows.size(), fees.size());
        return rows;
    }

    /**
     * 征收/收缴统计：一次返回大屏「用水缴费」Tab 所需的三段数据——
     * 顶部四卡片（应缴 / 已收 / 收费率 / 欠收）+「各区域征收情况」+「月度收缴趋势」。
     *
     * <p>数据源（2026-10-27 变更）：新版水费账单 t_auto_hltgq_water_bill
     * （调度方案出账与手工台账出账统一沉淀），不再读旧水费计算表单。
     * <p>口径：
     * <ul>
     *   <li>区间 = 统计年整年，按账期终点 period_end 落点判定（月/季/年账单统一按账期止落月，
     *       与旧表单「统计周期区间终点归桶」一致）</li>
     *   <li>区域 = 用水户名称（业务口径：一县即一个用水户，户名即行政区名）；
     *       无区域的账单不计入区域图、仍计入月度趋势</li>
     *   <li>应缴/已收 = amount / paid 桶内求和（库内元 → 出参万元，按展示精度 2 位截断）；桶内无账单 → null（不补 0）</li>
     *   <li>收费率 = 已收合计 ÷ 应缴合计 × 100（1 位截断）；应缴缺失或 ≤ 0 为 null</li>
     *   <li>欠收 = 应缴 − 已收（万元，2 位截断）；任一侧缺失为 null</li>
     *   <li>月度 1~12 月固定出桶（无账单月份字段为 null）；区域按应缴水费降序出参</li>
     * </ul>
     *
     * @param year 统计年份（缺省 = 当年；超出 2000 ~ 2100 返回 400）
     * @return 四卡片合计 + 区域段 + 月度段（同一次取数聚合而来，口径一致）
     */
    public WaterUseFeeCollectionVO feeCollection(Integer year) {
        int targetYear = (year == null) ? LocalDate.now().getYear() : year;
        if (targetYear < MIN_YEAR || targetYear > MAX_YEAR) {
            throw new IllegalArgumentException("year 取值范围 " + MIN_YEAR + " ~ " + MAX_YEAR + "（缺省=当年）");
        }
        LocalDate startDate = LocalDate.of(targetYear, 1, 1);
        LocalDate endDate = LocalDate.of(targetYear, 12, 31);

        List<WaterBillFeeRecordVO> records =
                waterUseSummaryMapper.selectBillCollectionRecords(startDate, endDate);

        Map<String, FeeSum> regionSums = new LinkedHashMap<>();
        FeeSum[] monthSums = new FeeSum[MONTHS_OF_YEAR];
        FeeSum yearSum = new FeeSum();
        int noRegion = 0;
        int sampleLogged = 0;
        for (WaterBillFeeRecordVO record : records) {
            if (sampleLogged < FEE_SAMPLE_LOGS) {
                log.info("[用水总结] 征收账单样例 编号={} 区域={} 账期终点={} 应缴(原值·元)={} 已收(原值·元)={}",
                        record.getBillNo(), record.getUserName(), record.getAnchorTime(),
                        record.getReceivableRaw(), record.getReceivedRaw());
                sampleLogged++;
            }
            yearSum.accumulate(record.getReceivableRaw(), record.getReceivedRaw());
            String region = record.getUserName();
            if (region == null || region.trim().isEmpty()) {
                // 无区域账单：区域图无法归属（用水户未挂接/已删/名称为空），月度趋势不受影响（仍累计）
                noRegion++;
            } else {
                regionSums.computeIfAbsent(region.trim(), key -> new FeeSum())
                        .accumulate(record.getReceivableRaw(), record.getReceivedRaw());
            }
            if (record.getAnchorTime() != null) {
                int index = record.getAnchorTime().getMonthValue() - 1;
                if (monthSums[index] == null) {
                    monthSums[index] = new FeeSum();
                }
                monthSums[index].accumulate(record.getReceivableRaw(), record.getReceivedRaw());
            }
        }

        WaterUseFeeCollectionVO result = new WaterUseFeeCollectionVO();
        result.setYear(targetYear);
        result.setTotal(buildTotalItem(yearSum));
        result.setRegions(buildRegionItems(regionSums));
        result.setMonths(buildMonthItems(targetYear, monthSums));

        log.info("[用水总结] 征收统计 year={} 账单={} 条 区域={} 个 无区域账单={} 条 年度合计 应缴(万元)={} 已收(万元)={} "
                        + "收费率(%)={} 欠收(万元)={}（账单原值合计 应缴={} 已收={} 元，未截断，便于与库中 SUM(amount) / SUM(paid) 对账）",
                targetYear, records.size(), regionSums.size(), noRegion,
                result.getTotal().getReceivable(), result.getTotal().getReceived(),
                result.getTotal().getCollectionRate(), result.getTotal().getArrears(),
                yearSum.receivableRaw, yearSum.receivedRaw);
        if (noRegion > 0) {
            log.warn("[用水总结] 征收统计 year={} 有 {} 条账单无区域（用水户未挂接/已删/名称为空），未计入区域图",
                    targetYear, noRegion);
        }
        for (WaterUseFeeCollectionVO.RegionItem item : result.getRegions()) {
            log.info("[用水总结] 征收统计 year={} 区域={} 应缴(万元)={} 已收(万元)={} 收费率(%)={}",
                    targetYear, item.getRegion(), item.getReceivable(), item.getReceived(),
                    item.getCollectionRate());
        }
        log.info("[用水总结] 征收统计 year={} 月度出桶={} 个月：{}",
                targetYear, result.getMonths().size(), joinMonths(result.getMonths()));
        return result;
    }

    /** 四卡片合计出数：应缴 / 已收 / 收费率 / 欠收（当年无账单时四个字段均为 null） */
    private WaterUseFeeCollectionVO.TotalItem buildTotalItem(FeeSum sum) {
        WaterUseFeeCollectionVO.TotalItem item = new WaterUseFeeCollectionVO.TotalItem();
        item.setReceivable(receivableOf(sum));
        item.setReceived(receivedOf(sum));
        item.setCollectionRate(collectionRateOf(sum));
        item.setArrears(arrearsOf(sum));
        return item;
    }

    /** 区域段出数：按应收水费降序（柱图从左到右递减），应收缺失排末位、同值按区域名升序保证顺序稳定 */
    private List<WaterUseFeeCollectionVO.RegionItem> buildRegionItems(Map<String, FeeSum> regionSums) {
        List<WaterUseFeeCollectionVO.RegionItem> items = new ArrayList<>(regionSums.size());
        for (Map.Entry<String, FeeSum> entry : regionSums.entrySet()) {
            WaterUseFeeCollectionVO.RegionItem item = new WaterUseFeeCollectionVO.RegionItem();
            item.setRegion(entry.getKey());
            item.setReceivable(receivableOf(entry.getValue()));
            item.setReceived(receivedOf(entry.getValue()));
            item.setCollectionRate(collectionRateOf(entry.getValue()));
            items.add(item);
        }
        items.sort((left, right) -> {
            BigDecimal a = left.getReceivable();
            BigDecimal b = right.getReceivable();
            if (a == null || b == null) {
                if (a != null) {
                    return -1;
                }
                if (b != null) {
                    return 1;
                }
                return left.getRegion().compareTo(right.getRegion());
            }
            int cmp = b.compareTo(a);
            return cmp != 0 ? cmp : left.getRegion().compareTo(right.getRegion());
        });
        return items;
    }

    /** 月度段出数：1~12 月固定出桶（供前端铺满 x 轴，无需自行补齐）；无数据月份字段为 null */
    private List<WaterUseFeeCollectionVO.MonthItem> buildMonthItems(int year, FeeSum[] monthSums) {
        List<WaterUseFeeCollectionVO.MonthItem> items = new ArrayList<>(MONTHS_OF_YEAR);
        for (int index = 0; index < MONTHS_OF_YEAR; index++) {
            WaterUseFeeCollectionVO.MonthItem item = new WaterUseFeeCollectionVO.MonthItem();
            item.setMonth(String.format("%04d-%02d", year, index + 1));
            item.setReceivable(receivableOf(monthSums[index]));
            item.setReceived(receivedOf(monthSums[index]));
            item.setCollectionRate(collectionRateOf(monthSums[index]));
            items.add(item);
        }
        return items;
    }

    /** 应收水费(万元)：账单 amount（元）→ 换算为万元并按展示精度 2 位截断；该桶无账单为 null（不补 0） */
    private BigDecimal receivableOf(FeeSum sum) {
        return sum == null ? null : yuanToWan(sum.receivableRaw);
    }

    /** 已收水费(万元)：账单 paid（元）→ 换算为万元并按展示精度 2 位截断；该桶无账单为 null（不补 0） */
    private BigDecimal receivedOf(FeeSum sum) {
        return sum == null ? null : yuanToWan(sum.receivedRaw);
    }

    /** 账单金额（元）→ 出参金额（万元，2 位截断）：null 安全，不把「无账单」当 0 */
    private BigDecimal yuanToWan(BigDecimal yuan) {
        return yuan == null ? null
                : yuan.movePointLeft(FEE_YUAN_TO_WAN_SHIFT).setScale(WAN_SCALE, RoundingMode.DOWN);
    }

    /** 水费收缴率(%)：已收合计 ÷ 应收合计 × 100（1 位截断）；任一侧缺失或应收 ≤ 0 为 null */
    private BigDecimal collectionRateOf(FeeSum sum) {
        if (sum == null || sum.receivableRaw == null || sum.receivedRaw == null
                || sum.receivableRaw.signum() <= 0) {
            return null;
        }
        // 先乘后除：避免中间除法的截断误差进入百分比
        return sum.receivedRaw.multiply(HUNDRED).divide(sum.receivableRaw, RATE_SCALE, RoundingMode.DOWN);
    }

    /** 欠收金额(万元) = 应缴 − 已收（2 位截断）；任一侧缺失为 null（不把「无账单」当 0 欠收） */
    private BigDecimal arrearsOf(FeeSum sum) {
        if (sum == null || sum.receivableRaw == null || sum.receivedRaw == null) {
            return null;
        }
        return yuanToWan(sum.receivableRaw.subtract(sum.receivedRaw));
    }

    /** 月度段联调日志串：2026-01=应收/已收/收缴率（无数据用「无」），便于与前端图表逐月核对 */
    private String joinMonths(List<WaterUseFeeCollectionVO.MonthItem> months) {
        List<String> parts = new ArrayList<>(months.size());
        for (WaterUseFeeCollectionVO.MonthItem item : months) {
            parts.add(item.getMonth() + "=" + logText(item.getReceivable()) + "/"
                    + logText(item.getReceived()) + "/" + logText(item.getCollectionRate()));
        }
        return String.join(", ", parts);
    }

    /** 日志数值文本：null 显示为「无」 */
    private String logText(BigDecimal value) {
        return value == null ? "无" : value.toPlainString();
    }

    /** 构建统计桶：月（区间涉及的自然月）/ 灌季（与区间相交的灌季窗口）/ 年（区间涉及的自然年） */
    private List<Bucket> buildBuckets(String dimension, LocalDate startTime, LocalDate endTime) {
        List<Bucket> buckets = new ArrayList<>();
        if ("month".equals(dimension)) {
            YearMonth cursor = YearMonth.from(startTime);
            YearMonth last = YearMonth.from(endTime);
            while (!cursor.isAfter(last)) {
                buckets.add(new Bucket(String.valueOf(cursor), String.valueOf(cursor),
                        cursor.atDay(1), cursor.atEndOfMonth()));
                cursor = cursor.plusMonths(1);
            }
            return buckets;
        }
        if ("season".equals(dimension)) {
            for (int year = startTime.getYear(); year <= endTime.getYear(); year++) {
                for (WaterUseSeasonConfig.SeasonWindow window : seasonConfig.windowsOf(year)) {
                    if (!window.end.isBefore(startTime) && !window.start.isAfter(endTime)) {
                        buckets.add(new Bucket(window.key, window.label, window.start, window.end));
                    }
                }
            }
            return buckets;
        }
        if ("year".equals(dimension)) {
            for (int year = startTime.getYear(); year <= endTime.getYear(); year++) {
                buckets.add(new Bucket(String.valueOf(year), String.valueOf(year),
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)));
            }
            return buckets;
        }
        throw new IllegalArgumentException("dimension 仅支持 month / season / year（月 / 灌季 / 年）");
    }

    /** 水费记录归桶：按统计周期区间终点（xxmefs_max，缺失回退起点）落入桶区间（含边界）；缝隙期记录忽略并日志计数 */
    private void fillFeeAmounts(List<Bucket> buckets, List<WaterUseFeeRecordVO> fees) {
        int sampleLogged = 0;
        int unassigned = 0;
        for (WaterUseFeeRecordVO fee : fees) {
            if (sampleLogged < FEE_SAMPLE_LOGS) {
                log.info("[用水总结] 水费记录样例 编号={} 单位={} 统计周期={} ~ {} 计划供水量(万m³)={} 执行水价(元/m³)={} 应收水费(万元)={}",
                        fee.getFeeNo(), fee.getUnitName(), fee.getPeriodStartTime(), fee.getPeriodEndTime(),
                        fee.getPlannedSupplyRaw(), fee.getPriceRaw(), fee.getFeeRaw());
                sampleLogged++;
            }
            LocalDateTime anchorTime = fee.getPeriodEndTime() != null
                    ? fee.getPeriodEndTime() : fee.getPeriodStartTime();
            if (anchorTime == null) {
                unassigned++;
                log.warn("[用水总结] 水费记录统计周期为空，已跳过 编号={} 单位={}", fee.getFeeNo(), fee.getUnitName());
                continue;
            }
            LocalDate date = anchorTime.toLocalDate();
            Bucket hit = null;
            for (Bucket bucket : buckets) {
                if (!date.isBefore(bucket.start) && !date.isAfter(bucket.end)) {
                    hit = bucket;
                    break;
                }
            }
            if (hit == null) {
                unassigned++;
                continue;
            }
            if (fee.getPlannedSupplyRaw() != null) {
                hit.plannedSupplyRaw = (hit.plannedSupplyRaw == null ? BigDecimal.ZERO : hit.plannedSupplyRaw)
                        .add(fee.getPlannedSupplyRaw());
            }
            if (fee.getFeeRaw() != null) {
                hit.feeRaw = (hit.feeRaw == null ? BigDecimal.ZERO : hit.feeRaw).add(fee.getFeeRaw());
            }
        }
        if (unassigned > 0) {
            log.info("[用水总结] 水费记录 {} 条未落入任何统计桶（区间缝隙/周期为空），已忽略", unassigned);
        }
    }

    /** 单桶出数：标签 + 用水量(=计划供水量，表单万m³按展示精度 3 位截断)/应收水费（表单元换算万元后按展示精度 2 位截断）+ 系数（流量区间累计近似） */
    private WaterUseReportRowVO buildRow(Bucket bucket) {
        WaterUseReportRowVO row = new WaterUseReportRowVO();
        row.setPeriod(bucket.period);
        row.setLabel(bucket.label);
        row.setPeriodStart(bucket.start.toString());
        row.setPeriodEnd(bucket.end.toString());
        row.setUsage(WaterVolumeUtils.wanScale(bucket.plannedSupplyRaw));
        row.setReceivable(yuanToWan(bucket.feeRaw));
        row.setIrrigationCoef(computeCoefficient(bucket));
        return row;
    }

    /** 灌溉水利用系数近似值：Σ四干渠进水闸区间累计 ÷ 渠首进水闸区间累计（3 位小数） */
    private BigDecimal computeCoefficient(Bucket bucket) {
        LocalDateTime start = bucket.start.atStartOfDay();
        LocalDateTime end = bucket.end.atTime(23, 59, 59);
        List<IrrigationIntervalVO> rows = irrigationWaterMapper.selectIntervalPerStation(
                WaterUseCoeffStations.allCodes(), start, end);

        Map<String, BigDecimal> volumes = new HashMap<>();
        for (IrrigationIntervalVO row : rows) {
            BigDecimal ttf = DeviceValueUtils.usable(row.getTtf());
            if (ttf == null) {
                log.info("[用水总结] 区间取数 站点={}({}) 窗口={} ~ {} 末行无累计流量（ttf 为空或哨兵值）",
                        row.getStnm(), row.getSite(), start, end);
                continue;
            }
            BigDecimal prevTtf = DeviceValueUtils.usable(row.getPrevTtf());
            BigDecimal interval = ttf.subtract(prevTtf != null ? prevTtf : BigDecimal.ZERO);
            volumes.put(row.getSite(), interval);
            log.info("[用水总结] 区间取数 站点={}({}) 末行时间={} 区间累计={} m³（起点前基准 ttf={}）",
                    row.getStnm(), row.getSite(), row.getTm(),
                    interval.setScale(2, RoundingMode.DOWN), prevTtf);
        }

        BigDecimal total = stationVolume(WaterUseCoeffStations.QUSHOU_INTAKE, volumes);
        BigDecimal delivered = null;
        List<String> parts = new ArrayList<>();
        for (WaterUseCoeffStations.Station gate : WaterUseCoeffStations.GATES) {
            BigDecimal value = stationVolume(gate, volumes);
            parts.add(gate.label + "=" + (value == null ? "无数据" : value.setScale(2, RoundingMode.DOWN) + "m³"));
            if (value != null) {
                delivered = (delivered == null) ? value : delivered.add(value);
            }
        }

        if (total == null || total.signum() <= 0 || delivered == null) {
            log.info("[用水总结] 系数 {} 不可计算：窗口={} ~ {} 渠首={} 四干渠=[{}]（渠首缺失/为 0 或四干渠全部缺失）",
                    bucket.label, bucket.start, bucket.end,
                    total == null ? "无数据" : total.setScale(2, RoundingMode.DOWN), String.join(", ", parts));
            return null;
        }
        BigDecimal coef = delivered.divide(total, 3, RoundingMode.HALF_UP);
        log.info("[用水总结] 系数 {} 窗口={} ~ {} 渠首={} m³ 四干渠=[{}] 近似系数={}",
                bucket.label, bucket.start, bucket.end,
                total.setScale(2, RoundingMode.DOWN), String.join(", ", parts), coef);
        if (coef.compareTo(BigDecimal.ONE) > 0) {
            log.warn("[用水总结] 系数 {} 大于 1（{}），请核对四干渠与渠首是否同源同窗口", bucket.label, coef);
        }
        return coef;
    }

    /** 站点取值：候选标识任一命中即取数（多候选=同一物理站的 stcd/UUID 双键兜底），全部无数据返回 null */
    private BigDecimal stationVolume(WaterUseCoeffStations.Station station, Map<String, BigDecimal> volumes) {
        BigDecimal sum = null;
        for (String code : station.codes) {
            BigDecimal value = volumes.get(code);
            if (value != null) {
                sum = (sum == null) ? value : sum.add(value);
            }
        }
        return sum;
    }

    /** 统计桶（内部使用） */
    private static class Bucket {

        /** 桶键：2026-08 / 2026-summer / 2026 */
        private final String period;

        /** 展示标签：2026-08 / 2026夏灌 / 2026 */
        private final String label;

        /** 桶起（含） */
        private final LocalDate start;

        /** 桶止（含） */
        private final LocalDate end;

        /** 桶内计划供水量求和（mlljya，表单单位 万m³） */
        private BigDecimal plannedSupplyRaw;

        /** 桶内水费记录原始值求和（hsfvdh，表单原值·元） */
        private BigDecimal feeRaw;

        private Bucket(String period, String label, LocalDate start, LocalDate end) {
            this.period = period;
            this.label = label;
            this.start = start;
            this.end = end;
        }
    }

    /** 金额累加器（内部使用：始终按库内原值求和，仅在出参时换算为万元并按展示精度截断，避免逐条截断的误差累加） */
    private static class FeeSum {

        /** 应缴金额求和 amount（库内原值·元；无账单时保持 null） */
        private BigDecimal receivableRaw;

        /** 已收金额求和 paid（库内原值·元；无账单时保持 null） */
        private BigDecimal receivedRaw;

        /** 累加一条账单：字段为空时不参与求和（不把未填当 0，保证「未填」与「已收 0」可区分） */
        private void accumulate(BigDecimal receivable, BigDecimal received) {
            if (receivable != null) {
                receivableRaw = (receivableRaw == null ? BigDecimal.ZERO : receivableRaw).add(receivable);
            }
            if (received != null) {
                receivedRaw = (receivedRaw == null ? BigDecimal.ZERO : receivedRaw).add(received);
            }
        }
    }
}
