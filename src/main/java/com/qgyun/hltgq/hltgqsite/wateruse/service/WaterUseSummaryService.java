package com.qgyun.hltgq.hltgqsite.wateruse.service;

import com.qgyun.hltgq.hltgqsite.irrigation.mapper.IrrigationWaterMapper;
import com.qgyun.hltgq.hltgqsite.irrigation.vo.IrrigationIntervalVO;
import com.qgyun.hltgq.hltgqsite.model.util.WaterVolumeUtils;
import com.qgyun.hltgq.hltgqsite.wateruse.config.WaterUseCoeffStations;
import com.qgyun.hltgq.hltgqsite.wateruse.config.WaterUseSeasonConfig;
import com.qgyun.hltgq.hltgqsite.wateruse.mapper.WaterUseSummaryMapper;
import com.qgyun.hltgq.hltgqsite.wateruse.vo.WaterUseCollectionRecordVO;
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

/**
 * 用水总结服务：水费表单按统计桶聚合（用水量/应收水费）+ 灌溉水利用系数实测近似值。
 *
 * <p>口径（业主 2026-09-11 确认）：
 * <ul>
 *   <li>归桶锚点 = 统计周期区间终点 xxmefs_max（跨桶按终点，终点缺失回退起点）</li>
 *   <li>用水量 = 计划供水量 mlljya 桶内求和（直取表单值、不做推算；按 m³ 换算 → 万m³，3 位截断，
 *       与累计流量展示同口径）</li>
 *   <li>应收水费 = 水费表单 hsfvdh 桶内求和（按 元 换算 → 万元，2 位截断，联调按日志核对单位）</li>
 *   <li>灌溉水利用系数（近似值）= Σ(北干/南干/太宿/太怀 进水闸区间累计) ÷ 渠首进水闸区间累计
 *       （同桶窗口 ttf 区间累计，3 位小数；渠首缺失/为 0 或四干渠全部无数据时为 null）</li>
 * </ul>
 *
 * <p>征收/收缴统计（另一入口 {@link #feeCollection}）：同一张水费表单，区域经用水户外键关联用水户表取名称，
 * 应收 hsfvdh / 已收 vdhlhm 按 元 → 万元 2 位截断，收缴率 1 位截断。
 *
 * <p>统计桶：月 / 灌季（{@link WaterUseSeasonConfig}）/ 年；桶内无记录为 null（不补 0）。
 * 单个区间取数逐站日志化（{@code [用水总结]} 前缀），联调可核对。
 */
@Service
public class WaterUseSummaryService {

    private static final Logger log = LoggerFactory.getLogger(WaterUseSummaryService.class);

    /** 设备哨兵值：-999 设备不存在、-9991 设备异常；区间累计场景下均视为缺失 */
    private static final BigDecimal DEVICE_MISSING = new BigDecimal("-999");
    private static final BigDecimal DEVICE_ERROR = new BigDecimal("-9991");

    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

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
     * 征收/收缴统计：一次返回「各区域征收情况」（x 轴 = 区域）与「月度收缴趋势」（x 轴 = 当年 12 个月）两段数据，
     * 供两张柱状折线组合图共用（柱 = 应收/已收水费(万元)，折线 = 收缴率(%)）。
     *
     * <p>口径：
     * <ul>
     *   <li>区间 = 统计年整年（按锚点落点判定），归桶锚点同 {@link #report}：统计周期区间终点（缺失回退起点）</li>
     *   <li>区域 = 用水户名称（水费表 xqaoxx → 用水户表 iiatzj）；无区域的记录不计入区域图、仍计入月度趋势</li>
     *   <li>应收/已收 = hsfvdh / vdhlhm 桶内求和（元 → 万元 2 位截断）；桶内该指标全部未填 → null（不补 0）</li>
     *   <li>收缴率 = 已收合计 ÷ 应收合计 × 100（1 位截断）；任一侧缺失或应收 ≤ 0 为 null</li>
     *   <li>月度 1~12 月固定出桶（无数据月份字段为 null）；区域按应收水费降序出参</li>
     * </ul>
     *
     * @param year 统计年份（缺省 = 当年；超出 2000 ~ 2100 返回 400）
     * @return 区域段 + 月度段（同一次取数聚合而来，口径一致）
     */
    public WaterUseFeeCollectionVO feeCollection(Integer year) {
        int targetYear = (year == null) ? LocalDate.now().getYear() : year;
        if (targetYear < MIN_YEAR || targetYear > MAX_YEAR) {
            throw new IllegalArgumentException("year 取值范围 " + MIN_YEAR + " ~ " + MAX_YEAR + "（缺省=当年）");
        }
        LocalDateTime startTime = LocalDate.of(targetYear, 1, 1).atStartOfDay();
        LocalDateTime endTime = LocalDate.of(targetYear, 12, 31).atTime(23, 59, 59);

        List<WaterUseCollectionRecordVO> records =
                waterUseSummaryMapper.selectCollectionRecords(startTime, endTime);

        Map<String, FeeSum> regionSums = new LinkedHashMap<>();
        FeeSum[] monthSums = new FeeSum[MONTHS_OF_YEAR];
        FeeSum yearSum = new FeeSum();
        int noRegion = 0;
        int sampleLogged = 0;
        for (WaterUseCollectionRecordVO record : records) {
            if (sampleLogged < FEE_SAMPLE_LOGS) {
                log.info("[用水总结] 征收记录样例 编号={} 区域={} 归桶锚点={} 应收水费(元)={} 已收水费(元)={}",
                        record.getFeeNo(), record.getRegionName(), record.getAnchorTime(),
                        record.getReceivableRaw(), record.getReceivedRaw());
                sampleLogged++;
            }
            yearSum.accumulate(record);
            String region = record.getRegionName();
            if (region == null || region.trim().isEmpty()) {
                // 无区域记录：区域图无法归属（用水户未填/已删/名称为空），月度趋势不受影响（仍累计）
                noRegion++;
            } else {
                regionSums.computeIfAbsent(region.trim(), key -> new FeeSum()).accumulate(record);
            }
            if (record.getAnchorTime() != null) {
                int index = record.getAnchorTime().getMonthValue() - 1;
                if (monthSums[index] == null) {
                    monthSums[index] = new FeeSum();
                }
                monthSums[index].accumulate(record);
            }
        }

        WaterUseFeeCollectionVO result = new WaterUseFeeCollectionVO();
        result.setYear(targetYear);
        result.setRegions(buildRegionItems(regionSums));
        result.setMonths(buildMonthItems(targetYear, monthSums));

        log.info("[用水总结] 征收统计 year={} 记录={} 条 区域={} 个 无区域记录={} 条 年度合计 应收(万元)={} 已收(万元)={} 收缴率(%)={}",
                targetYear, records.size(), regionSums.size(), noRegion,
                receivableOf(yearSum), receivedOf(yearSum), collectionRateOf(yearSum));
        if (noRegion > 0) {
            log.warn("[用水总结] 征收统计 year={} 有 {} 条记录无区域（用水户未填/已删/名称为空），未计入区域图",
                    targetYear, noRegion);
        }
        for (WaterUseFeeCollectionVO.RegionItem item : result.getRegions()) {
            log.info("[用水总结] 征收统计 year={} 区域={} 应收(万元)={} 已收(万元)={} 收缴率(%)={}",
                    targetYear, item.getRegion(), item.getReceivable(), item.getReceived(),
                    item.getCollectionRate());
        }
        log.info("[用水总结] 征收统计 year={} 月度出桶={} 个月：{}",
                targetYear, result.getMonths().size(), joinMonths(result.getMonths()));
        return result;
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

    /** 应收水费(万元)：元 → 万元 2 位截断；无记录或该桶应收全部未填为 null（不补 0） */
    private BigDecimal receivableOf(FeeSum sum) {
        return (sum == null || sum.receivableRaw == null) ? null
                : sum.receivableRaw.divide(TEN_THOUSAND, WAN_SCALE, RoundingMode.DOWN);
    }

    /** 已收水费(万元)：元 → 万元 2 位截断；无记录或该桶已收全部未填为 null（不补 0） */
    private BigDecimal receivedOf(FeeSum sum) {
        return (sum == null || sum.receivedRaw == null) ? null
                : sum.receivedRaw.divide(TEN_THOUSAND, WAN_SCALE, RoundingMode.DOWN);
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
                log.info("[用水总结] 水费记录样例 编号={} 单位={} 统计周期={} ~ {} 计划供水量(m³)={} 执行水价={} 应收水费={}",
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

    /** 单桶出数：标签 + 用水量(=计划供水量，m³→万m³ 3 位截断)/应收水费（元→万元 2 位截断）+ 系数（流量区间累计近似） */
    private WaterUseReportRowVO buildRow(Bucket bucket) {
        WaterUseReportRowVO row = new WaterUseReportRowVO();
        row.setPeriod(bucket.period);
        row.setLabel(bucket.label);
        row.setPeriodStart(bucket.start.toString());
        row.setPeriodEnd(bucket.end.toString());
        row.setUsage(WaterVolumeUtils.m3ToWan(bucket.plannedSupplyRaw));
        row.setReceivable(bucket.feeRaw != null
                ? bucket.feeRaw.divide(TEN_THOUSAND, 2, RoundingMode.DOWN) : null);
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
            BigDecimal ttf = usable(row.getTtf());
            if (ttf == null) {
                log.info("[用水总结] 区间取数 站点={}({}) 窗口={} ~ {} 末行无累计流量（ttf 为空或哨兵值）",
                        row.getStnm(), row.getSite(), start, end);
                continue;
            }
            BigDecimal prevTtf = usable(row.getPrevTtf());
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

    /** 哨兵/空值过滤：返回可用的累计流量值或无 */
    private BigDecimal usable(BigDecimal value) {
        if (value == null || DEVICE_MISSING.compareTo(value) == 0 || DEVICE_ERROR.compareTo(value) == 0) {
            return null;
        }
        return value;
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

        /** 桶内计划供水量求和（mlljya，单位 m³） */
        private BigDecimal plannedSupplyRaw;

        /** 桶内水费记录原始值求和（hsfvdh，单位 元） */
        private BigDecimal feeRaw;

        private Bucket(String period, String label, LocalDate start, LocalDate end) {
            this.period = period;
            this.label = label;
            this.start = start;
            this.end = end;
        }
    }

    /** 金额累加器（内部使用：始终保留 元 原值求和，仅在出参时换算为万元，避免缩放后累加放大误差） */
    private static class FeeSum {

        /** 应收水费求和 hsfvdh（单位 元；全部未填时保持 null） */
        private BigDecimal receivableRaw;

        /** 已收水费求和 vdhlhm（单位 元；全部未填时保持 null） */
        private BigDecimal receivedRaw;

        /** 累加一条记录：字段为空时不参与求和（不把未填当 0，保证「未填」与「已收 0」可区分） */
        private void accumulate(WaterUseCollectionRecordVO record) {
            if (record.getReceivableRaw() != null) {
                receivableRaw = (receivableRaw == null ? BigDecimal.ZERO : receivableRaw)
                        .add(record.getReceivableRaw());
            }
            if (record.getReceivedRaw() != null) {
                receivedRaw = (receivedRaw == null ? BigDecimal.ZERO : receivedRaw)
                        .add(record.getReceivedRaw());
            }
        }
    }
}
