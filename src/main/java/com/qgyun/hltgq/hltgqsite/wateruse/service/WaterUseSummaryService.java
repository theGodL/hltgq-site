package com.qgyun.hltgq.hltgqsite.wateruse.service;

import com.qgyun.hltgq.hltgqsite.irrigation.mapper.IrrigationWaterMapper;
import com.qgyun.hltgq.hltgqsite.irrigation.vo.IrrigationIntervalVO;
import com.qgyun.hltgq.hltgqsite.wateruse.config.WaterUseCoeffStations;
import com.qgyun.hltgq.hltgqsite.wateruse.config.WaterUseSeasonConfig;
import com.qgyun.hltgq.hltgqsite.wateruse.mapper.WaterUseSummaryMapper;
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
import java.util.List;
import java.util.Map;

/**
 * 用水总结服务：水费表单按统计桶聚合（用水量/应收水费）+ 灌溉水利用系数实测近似值。
 *
 * <p>口径（业主 2026-09-11 确认）：
 * <ul>
 *   <li>用水量 = 水费表单 hqwvsf 桶内求和（按 m³ 换算 → 万m³，2 位截断）</li>
 *   <li>应收水费 = 水费表单 hsfvdh 桶内求和（按 元 换算 → 万元，2 位截断，联调按日志核对单位）</li>
 *   <li>灌溉水利用系数（近似值）= Σ(北干/南干/太宿/太怀 进水闸区间累计) ÷ 渠首进水闸区间累计
 *       （同桶窗口 ttf 区间累计，3 位小数；渠首缺失/为 0 或四干渠全部无数据时为 null）</li>
 * </ul>
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

    /** 水费记录归桶：按统计周期日期落入桶区间（含边界）；缝隙期记录忽略并日志计数 */
    private void fillFeeAmounts(List<Bucket> buckets, List<WaterUseFeeRecordVO> fees) {
        int sampleLogged = 0;
        int unassigned = 0;
        for (WaterUseFeeRecordVO fee : fees) {
            if (sampleLogged < FEE_SAMPLE_LOGS) {
                log.info("[用水总结] 水费记录样例 编号={} 单位={} 统计周期={} 计算用水量={} 执行水价={} 应用水费={}",
                        fee.getFeeNo(), fee.getUnitName(), fee.getPeriodTime(),
                        fee.getUsageRaw(), fee.getPriceRaw(), fee.getFeeRaw());
                sampleLogged++;
            }
            if (fee.getPeriodTime() == null) {
                unassigned++;
                log.warn("[用水总结] 水费记录统计周期为空，已跳过 编号={} 单位={}", fee.getFeeNo(), fee.getUnitName());
                continue;
            }
            LocalDate date = fee.getPeriodTime().toLocalDate();
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
            if (fee.getUsageRaw() != null) {
                hit.usageRaw = (hit.usageRaw == null ? BigDecimal.ZERO : hit.usageRaw).add(fee.getUsageRaw());
            }
            if (fee.getFeeRaw() != null) {
                hit.feeRaw = (hit.feeRaw == null ? BigDecimal.ZERO : hit.feeRaw).add(fee.getFeeRaw());
            }
        }
        if (unassigned > 0) {
            log.info("[用水总结] 水费记录 {} 条未落入任何统计桶（区间缝隙/周期为空），已忽略", unassigned);
        }
    }

    /** 单桶出数：标签 + 用水量/应收水费（表单求和）+ 系数（流量区间累计近似） */
    private WaterUseReportRowVO buildRow(Bucket bucket) {
        WaterUseReportRowVO row = new WaterUseReportRowVO();
        row.setPeriod(bucket.period);
        row.setLabel(bucket.label);
        row.setPeriodStart(bucket.start.toString());
        row.setPeriodEnd(bucket.end.toString());
        row.setUsage(bucket.usageRaw != null
                ? bucket.usageRaw.divide(TEN_THOUSAND, 2, RoundingMode.DOWN) : null);
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

        /** 桶内水费记录原始值求和（hqwvsf，单位 m³） */
        private BigDecimal usageRaw;

        /** 桶内水费记录原始值求和（hsfvdh，单位 元） */
        private BigDecimal feeRaw;

        private Bucket(String period, String label, LocalDate start, LocalDate end) {
            this.period = period;
            this.label = label;
            this.start = start;
            this.end = end;
        }
    }
}
