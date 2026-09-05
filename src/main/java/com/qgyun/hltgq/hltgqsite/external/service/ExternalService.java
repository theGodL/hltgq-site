package com.qgyun.hltgq.hltgqsite.external.service;

import com.qgyun.hltgq.hltgqsite.external.mapper.ExternalMapper;
import com.qgyun.hltgq.hltgqsite.external.vo.ExternalVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 三维系统对接（/external）聚合逻辑。
 * <p>契约见 src/main/resources/三维系统对接接口.md。全部为实时查询（无异步），
 * 单次聚合 SQL + 内存整理，数据量小无需缓存。
 * <p>监测值清洗：-999（设备不存在）、-9991（设备异常）视为无数据 → null（契约：null=无数据）。
 */
@Service
public class ExternalService {

    /** 数据时间格式 */
    private static final DateTimeFormatter TM_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 无效监测值：-999 设备不存在、-9991 设备异常 */
    private static final double INVALID_LEVEL = -999.0;
    private static final double INVALID_LEVEL_1 = -9991.0;

    /** 逐日趋势区间上限（天） */
    private static final long MAX_TREND_DAYS = 366;

    /** 默认趋势天数（不传区间时取近 30 天） */
    private static final long DEFAULT_TREND_DAYS = 30;

    @Autowired
    private ExternalMapper mapper;

    /** 渠首进水闸站点 UUID（配置默认值，与页面闸门监测口径一致） */
    @Value("${external.intake-gate-site:CAYQ739MiBWMg9gQvyi}")
    private String intakeGateSite;

    /** 管理单位（固定） */
    @Value("${external.intake-gate-unit:花凉亭灌区}")
    private String intakeGateUnit;

    /** 闸门类型数量：固定 4 类，count 暂恒 0（分类数据待三方收集补录后改真实统计） */
    public ExternalVO.GateTypeCount gateTypeCount() {
        ExternalVO.GateTypeCount vo = new ExternalVO.GateTypeCount();
        List<ExternalVO.GateTypeItem> items = new ArrayList<>(4);
        items.add(item("节制闸"));
        items.add(item("分水口"));
        items.add(item("退水闸"));
        items.add(item("倒虹吸"));
        vo.setItems(items);
        return vo;
    }

    private ExternalVO.GateTypeItem item(String type) {
        ExternalVO.GateTypeItem item = new ExternalVO.GateTypeItem();
        item.setType(type);
        item.setCount(0);
        return item;
    }

    /** 渠首进水闸实时数据：闸前/闸后水位（gate 表）+ 流量（wt_nfo 表），时间取两者较新 */
    public ExternalVO.IntakeGate intakeGate() {
        ExternalVO.IntakeGate vo = new ExternalVO.IntakeGate();
        vo.setStcd(intakeGateSite);
        vo.setStnm("渠首进水闸");
        vo.setManagementUnit(intakeGateUnit);

        Map<String, Object> gate = mapper.selectLatestGateLevel(intakeGateSite);
        LocalDateTime gateTm = null;
        if (gate != null) {
            gateTm = timeOf(gate.get("tm"));
            vo.setUpZ(cleanLevel(gate.get("up_z")));
            vo.setDownZ(cleanLevel(gate.get("down_z")));
        }

        Map<String, Object> flow = mapper.selectLatestFlow(intakeGateSite);
        LocalDateTime flowTm = null;
        if (flow != null) {
            flowTm = timeOf(flow.get("tm"));
            vo.setQ(round3(flow.get("q")));
        }

        LocalDateTime latest = laterOf(gateTm, flowTm);
        vo.setTm(latest == null ? null : TM_FORMAT.format(latest));
        return vo;
    }

    /** 巡检汇总：三项计数 + 维养预算/成本暂恒 0（数据源待确认） */
    public ExternalVO.PatrolSummary patrolSummary() {
        Map<String, Object> row = mapper.selectPatrolSummary();
        ExternalVO.PatrolSummary vo = new ExternalVO.PatrolSummary();
        vo.setPatrolCount(longOf(row.get("patrol_count")));
        vo.setScheduleCount(longOf(row.get("schedule_count")));
        vo.setFinishedCount(longOf(row.get("finished_count")));
        vo.setMaintenanceBudget(0);
        vo.setMaintenanceCost(0);
        return vo;
    }

    /** 巡检/问题逐日趋势：区间默认近 30 天，上限 366 天，逐日补 0 */
    public ExternalVO.DailyTrend dailyTrend(LocalDate startDate, LocalDate endDate) {
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDate start = startDate == null ? end.minusDays(DEFAULT_TREND_DAYS - 1) : startDate;
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("startDate 晚于 endDate: " + start + " > " + end);
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_TREND_DAYS) {
            throw new IllegalArgumentException("区间超过 " + MAX_TREND_DAYS + " 天上限: " + days);
        }

        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.atTime(LocalTime.MAX);
        Map<String, Long> patrolMap = toDayCountMap(mapper.selectDailyPatrol(startTime, endTime));
        Map<String, Long> issueMap = toDayCountMap(mapper.selectDailyIssue(startTime, endTime));

        ExternalVO.DailyTrend vo = new ExternalVO.DailyTrend();
        List<String> dates = new ArrayList<>();
        List<Long> patrol = new ArrayList<>();
        List<Long> issue = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.toString();
            dates.add(key);
            patrol.add(patrolMap.getOrDefault(key, 0L));
            issue.add(issueMap.getOrDefault(key, 0L));
        }
        vo.setDates(dates);
        vo.setPatrol(patrol);
        vo.setIssue(issue);
        return vo;
    }

    /** 问题状态统计：已处理=已关闭、未整改=处理中+已转工单、突发事件=总数 */
    public ExternalVO.IssueStats issueStats() {
        long handled = 0;
        long unrectified = 0;
        long total = 0;
        for (Map<String, Object> row : mapper.selectIssueStatus()) {
            String status = (String) row.get("name");
            long cnt = longOf(row.get("value"));
            total += cnt;
            if ("#4#".equals(status)) {
                handled += cnt;
            } else if ("#2#".equals(status) || "#3#".equals(status)) {
                unrectified += cnt;
            }
        }
        ExternalVO.IssueStats vo = new ExternalVO.IssueStats();
        vo.setHandled(handled);
        vo.setUnrectified(unrectified);
        vo.setEmergencyTotal(total);
        vo.setResolved(handled);
        vo.setUnresolved(unrectified);
        return vo;
    }

    /** 视频按管理所聚合：总数/在线/离线 */
    public List<ExternalVO.VideoItem> videoSummary() {
        List<ExternalVO.VideoItem> list = new ArrayList<>();
        for (Map<String, Object> row : mapper.selectVideoSummary()) {
            ExternalVO.VideoItem item = new ExternalVO.VideoItem();
            String org = (String) row.get("org");
            item.setOrg(org == null || org.trim().isEmpty() ? "未知" : org);
            long total = longOf(row.get("total"));
            long online = longOf(row.get("online"));
            item.setTotal(total);
            item.setOnline(online);
            item.setOffline(total - online);
            list.add(item);
        }
        return list;
    }

    /** 聚合行 → {日期: 计数} */
    private Map<String, Long> toDayCountMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String day = (String) row.get("day");
            if (day != null) {
                map.put(day, longOf(row.get("cnt")));
            }
        }
        return map;
    }

    /** 水位清洗：-999/-9991 → null；截断到 3 位小数（契约精度） */
    private Double cleanLevel(Object value) {
        if (value == null) {
            return null;
        }
        double d = ((Number) value).doubleValue();
        if (d == INVALID_LEVEL || d == INVALID_LEVEL_1) {
            return null;
        }
        return Math.round(d * 1000.0) / 1000.0;
    }

    /** 数值截断到 3 位小数（契约精度，SQL 层已过滤无效值） */
    private Double round3(Object value) {
        if (value == null) {
            return null;
        }
        double d = ((Number) value).doubleValue();
        return Math.round(d * 1000.0) / 1000.0;
    }

    private Long longOf(Object value) {
        if (value == null) {
            return 0L;
        }
        return value instanceof Long ? (Long) value : ((Number) value).longValue();
    }

    private LocalDateTime timeOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        String text = String.valueOf(value);
        if (text.length() >= 19) {
            text = text.substring(0, 19);
        }
        return LocalDateTime.parse(text, TM_FORMAT);
    }

    private LocalDateTime laterOf(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }
}
