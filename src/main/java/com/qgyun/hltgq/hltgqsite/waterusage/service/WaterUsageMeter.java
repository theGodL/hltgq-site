package com.qgyun.hltgq.hltgqsite.waterusage.service;

import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageDict;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport.GateConfig;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterBillMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.GateIntervalVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 闸口实测计量组件（灌区用水全模块唯一实现）：水费账单出账/台账、用水计划灌溉进度、
 * 实际保证率统计共用同一口径，避免多处口径漂移。
 * <ul>
 *   <li>单闸口区间累计（{@link #gateInterval}）= ttf(窗口内末条有效行) − prevTtf(起点前最近一条有效行)；
 *       窗口内无有效行 → null（计量不可得）；起点前无基准按 0；哨兵值 -999/-9991 视为缺失；
 *       不可得原因由 {@link #measure} 以对外文案返回（台账 dataNote / 出账 400 直接展示，便于定位是无数据还是站点对不上）；</li>
 *   <li>差额策略（DIFF）扣减闸口取负参与合计（{@link #isDeduct}）；</li>
 *   <li>户级合计（{@link #userInterval}，对齐产品原型 measuredWater 语义）：逐全部闸口按符号合计，
 *       任一闸口缺读数 / 读数回退（区间为负）→ null；合计为负 → null（扣减超进水，不可认定）。</li>
 * </ul>
 */
@Service
public class WaterUsageMeter {

    private static final Logger log = LoggerFactory.getLogger(WaterUsageMeter.class);

    private final WaterBillMapper billMapper;

    public WaterUsageMeter(WaterBillMapper billMapper) {
        this.billMapper = billMapper;
    }

    /**
     * 单闸口计量（带不可得原因）：窗口内末条有效累计流量行 − 起点前最近一条有效行。
     * <p>value 非 null 时 issue 为 null（读数回退保留原始负值，由调用方判定）；
     * value 为 null 时 issue 为对外原因文案（同时记日志，便于现场定位）。
     */
    public Result measure(String gateId, String startText, String endText) {
        LocalDateTime start;
        LocalDateTime end;
        try {
            start = WaterUsageSupport.parseDateTime(startText, "执行时段起");
            end = WaterUsageSupport.parseDateTime(endText, "执行时段止");
        } catch (IllegalArgumentException e) {
            log.warn("[灌区用水] 计量不可得（时段非法）：gate={}，{} ~ {}", gateId, startText, endText);
            return Result.issue("执行时段不正确，无法计量");
        }
        List<GateIntervalVO> rows = billMapper.selectGateInterval(gateId, start, end);
        if (rows.isEmpty()) {
            log.warn("[灌区用水] 计量不可得（闸口在执行时段内无流量记录）：gate={}，{} ~ {}",
                    gateId, startText, endText);
            return Result.issue("执行时段内该闸口无流量记录");
        }
        GateIntervalVO row = rows.get(0);
        BigDecimal ttf = WaterUsageSupport.usableFlow(row.getTtf());
        if (ttf == null) {
            log.warn("[灌区用水] 计量不可得（末行累计流量 ttf 缺失）：gate={}，{} ~ {}，末行时间={}",
                    gateId, startText, endText, row.getTm());
            return Result.issue("执行时段内该闸口无有效累计流量");
        }
        BigDecimal prev = WaterUsageSupport.usableFlow(row.getPrevTtf());
        BigDecimal interval = ttf.subtract(prev == null ? BigDecimal.ZERO : prev);
        log.info("[灌区用水] 计量 gate={} {} ~ {} 末行时间={} 区间累计={} m\u00b3（起点前基准 ttf={}）",
                gateId, startText, endText, row.getTm(), interval.toPlainString(), prev);
        return Result.of(interval);
    }

    /**
     * 单闸口区间累计流量（m³）：ttf(窗口内末条有效行) − prevTtf(起点前最近一条有效行)；
     * 窗口内无有效行 / 末行 ttf 缺失（null 或哨兵）→ null（计量不可得）；起点前无基准按 0。
     */
    public BigDecimal gateInterval(String gateId, String startText, String endText) {
        return measure(gateId, startText, endText).getValue();
    }

    /** 差额策略扣减闸口判定（记账符号取负） */
    public static boolean isDeduct(GateConfig config, String gateId) {
        return config != null && WaterUsageDict.GATE_MODE_DIFF.equals(config.mode) && config.isDeduct(gateId);
    }

    /**
     * 户级全部闸口区间累计（产品原型 measuredWater 语义，实际保证率/户级供水认定用）：
     * 逐配置闸口按符号（进 + / 扣 −）合计；任一闸口缺读数或读数回退（区间为负）→ null；
     * 合计为负（扣减超过进水）→ null。配置缺失（null / 无闸口）→ null。
     */
    public BigDecimal userInterval(GateConfig config, String startText, String endText) {
        if (config == null || config.allIds == null || config.allIds.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (String gateId : config.allIds) {
            BigDecimal raw = gateInterval(gateId, startText, endText);
            if (raw == null || raw.signum() < 0) {
                return null;
            }
            total = total.add(isDeduct(config, gateId) ? raw.negate() : raw);
        }
        if (total.signum() < 0) {
            return null;
        }
        return total;
    }

    /**
     * 计量结果：value = 区间累计（m³，可含读数回退负值）；value 为 null 时 issue 为不可得原因（对外文案）。
     */
    public static final class Result {

        private final BigDecimal value;

        private final String issue;

        private Result(BigDecimal value, String issue) {
            this.value = value;
            this.issue = issue;
        }

        static Result of(BigDecimal value) {
            return new Result(value, null);
        }

        static Result issue(String issue) {
            return new Result(null, issue);
        }

        /** 区间累计水量（m³）；计量不可得为 null */
        public BigDecimal getValue() {
            return value;
        }

        /** 不可得原因文案（可计量为 null） */
        public String getIssue() {
            return issue;
        }
    }
}
