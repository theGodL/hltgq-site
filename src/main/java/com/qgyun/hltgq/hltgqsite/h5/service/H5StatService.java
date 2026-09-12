package com.qgyun.hltgq.hltgqsite.h5.service;

import com.qgyun.hltgq.hltgqsite.h5.mapper.H5StatMapper;
import com.qgyun.hltgq.hltgqsite.h5.vo.InspectionResultStatsVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.PatrolIssueMonthlyVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H5 统计服务：巡查及问题统计（柱状图，近 12 个月）、维修养护统计（饼状图，巡检结果 6 档）。
 * <p>柱状图巡查数按巡检计划统计（排除草稿 #1#、含已取消 #4#，按 start_time 归月）；
 * 问题全状态按发现时间；缺月份/缺档补 0，返回固定长度序列供前端图表直接消费。
 */
@Service
public class H5StatService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 巡检结果编码 → 权威名称（固定 6 档） */
    private static final String[][] RESULT_LABELS = {
            {"#1#", "待填写"}, {"#2#", "正常"}, {"#3#", "异常"},
            {"#4#", "隐患"}, {"#5#", "缺陷"}, {"#6#", "故障"}
    };

    private final H5StatMapper statMapper;

    public H5StatService(H5StatMapper statMapper) {
        this.statMapper = statMapper;
    }

    /**
     * 巡查及问题月度统计：基准月 endMonth（yyyy-MM，默认当前月）及往前 11 个月，共 12 个月升序。
     * <p>巡查数 = 区间内巡检计划数（含已取消计划，排除草稿）；问题数 = 全状态问题数。
     */
    public PatrolIssueMonthlyVO patrolIssueMonthly(String endMonth) {
        YearMonth base = parseEndMonth(endMonth);
        YearMonth start = base.minusMonths(11);

        // 窗口：[start 首日 00:00, base 次月首日 00:00)，右开区间避免月末 23:59:59 精度问题
        LocalDateTime windowStart = start.atDay(1).atStartOfDay();
        LocalDateTime windowEnd = base.plusMonths(1).atDay(1).atStartOfDay();

        // 聚合查询：month 键（yyyy-MM）→ 计数
        Map<String, long[]> patrolByMonth = new LinkedHashMap<>();
        for (Map<String, Object> row : statMapper.selectPatrolMonthly(windowStart, windowEnd)) {
            patrolByMonth.put(str(row.get("month")), new long[]{num(row.get("cnt"))});
        }
        Map<String, long[]> issueByMonth = new LinkedHashMap<>();
        for (Map<String, Object> row : statMapper.selectIssueMonthly(windowStart, windowEnd)) {
            issueByMonth.put(str(row.get("month")), new long[]{
                    num(row.get("cnt")), num(row.get("low")), num(row.get("mid")), num(row.get("high"))});
        }

        // 补零生成 12 个月完整序列
        List<PatrolIssueMonthlyVO.MonthStat> months = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            YearMonth ym = start.plusMonths(i);
            String key = ym.format(MONTH_FMT);
            PatrolIssueMonthlyVO.MonthStat stat = new PatrolIssueMonthlyVO.MonthStat();
            stat.setMonth(key);
            long[] patrol = patrolByMonth.get(key);
            stat.setPatrolCount(patrol == null ? 0L : patrol[0]);
            long[] issue = issueByMonth.get(key);
            stat.setIssueCount(issue == null ? 0L : issue[0]);
            stat.setIssueLow(issue == null ? 0L : issue[1]);
            stat.setIssueMid(issue == null ? 0L : issue[2]);
            stat.setIssueHigh(issue == null ? 0L : issue[3]);
            months.add(stat);
        }

        PatrolIssueMonthlyVO vo = new PatrolIssueMonthlyVO();
        vo.setMonths(months);
        return vo;
    }

    /**
     * 维修养护统计：已提交巡检记录按 result 分组，固定 6 档补 0（时间区间可选）。
     */
    public InspectionResultStatsVO inspectionResultStats(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("startTime 不能晚于 endTime");
        }

        Map<String, Long> countByResult = new LinkedHashMap<>();
        for (Map<String, Object> row : statMapper.selectInspectionResultStats(startTime, endTime)) {
            countByResult.put(str(row.get("name")), num(row.get("value")));
        }

        InspectionResultStatsVO vo = new InspectionResultStatsVO();
        long total = 0L;
        List<InspectionResultStatsVO.ResultStat> items = new ArrayList<>(RESULT_LABELS.length);
        for (String[] label : RESULT_LABELS) {
            Long value = countByResult.getOrDefault(label[0], 0L);
            total += value;
            InspectionResultStatsVO.ResultStat stat = new InspectionResultStatsVO.ResultStat();
            stat.setName(label[0]);
            stat.setLabel(label[1]);
            stat.setValue(value);
            items.add(stat);
        }
        vo.setTotal(total);
        vo.setItems(items);
        return vo;
    }

    /** 基准月解析：不传默认当前月，格式错误抛 400 */
    private YearMonth parseEndMonth(String endMonth) {
        if (endMonth == null || endMonth.trim().isEmpty()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(endMonth.trim(), MONTH_FMT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("endMonth 格式错误，应为 yyyy-MM");
        }
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static long num(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }
}
