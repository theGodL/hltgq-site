package com.qgyun.hltgq.hltgqsite.duty.service;

import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.duty.DutyConflictException;
import com.qgyun.hltgq.hltgqsite.duty.DutySupport;
import com.qgyun.hltgq.hltgqsite.duty.mapper.DutyAttendanceMapper;
import com.qgyun.hltgq.hltgqsite.duty.mapper.DutyScheduleMapper;
import com.qgyun.hltgq.hltgqsite.duty.vo.AttendanceHandoverRequest;
import com.qgyun.hltgq.hltgqsite.duty.vo.AttendanceRecordRowVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.AttendanceReportRequest;
import com.qgyun.hltgq.hltgqsite.duty.vo.AttendanceStartRequest;
import com.qgyun.hltgq.hltgqsite.duty.vo.HandoverTimelineRowVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.OngoingTimelineRowVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.PendingTimelineRowVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleRowVO;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 值班交接班服务（值班交接班页：#7 当前值班四态、#8 开始值班、#9 填写报告、#10 交班、#11 今日动态）。
 * <p>四态判定顺序：我的值班中记录 → 我的今日排班（未开始）→ 今日交班的已完成记录 → 无安排；
 * 记录与排班无外键，靠「单位 + 班次起止时间」精确匹配回查（失败降级同单位同日，再失败跳过联动 + warn）；
 * 编号生成：ZBJL-{值班日期}-{NN} / JJB-{交班当天}-{NN}（查当日前缀 + Java 解析最大序号）。
 */
@Service
public class DutyAttendanceService {

    private static final Logger log = LoggerFactory.getLogger(DutyAttendanceService.class);

    /** 编号日期段格式（yyyyMMdd） */
    private static final DateTimeFormatter DAY_COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private DutyAttendanceMapper mapper;

    @Autowired
    private DutyScheduleMapper scheduleMapper;

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    @Value("${hltgq.corp-code}")
    private String corpCode;

    // ==================== #7 当前值班（四态） ====================

    /**
     * 当前值班卡片（四态判定）：
     * ① 我的 #2# 记录 → ongoing；② 我的今日排班（#cmiu#/#zzkl#/当日 #1#）→ notstarted；
     * ③ 今日交班的我的 #3# 记录 → completed；④ none。
     * <p>ongoing 额外返回 receiverCandidates（下一班接班人员候选，供交班弹窗默认选中）。
     */
    public Map<String, Object> today() {
        UserContext user = UserContextHolder.currentUser();
        String userId = user == null ? null : DutySupport.trim(user.getUserId());
        if (userId == null) {
            return noneResult();
        }
        AttendanceRecordRowVO ongoing = mapper.selectOngoingRecord(userId);
        if (ongoing != null) {
            String scheduleId = matchScheduleId(ongoing);
            Map<String, Object> out = buildRecordCard(ongoing, "ongoing", "值班中", scheduleId);
            out.put("receiverCandidates", receiverCandidates(ongoing, scheduleId));
            return out;
        }
        LocalDate today = LocalDate.now();
        String dayStart = today.toString();
        String dayEnd = today.plusDays(1).toString();
        ScheduleRowVO sched = scheduleMapper.selectMyTodaySchedule(userId, dayStart, dayEnd);
        if (sched != null) {
            return buildNotstarted(sched);
        }
        AttendanceRecordRowVO completed = mapper.selectCompletedTodayRecord(userId, dayStart, dayEnd);
        if (completed != null) {
            return buildRecordCard(completed, "completed", "已完成", matchScheduleId(completed));
        }
        return noneResult();
    }

    /**
     * 登录值班提醒：判断今天是否有我的值班（四态判定与组装复用 {@link #today()}，不重复查询/组装）。
     * <p>无值班返回 {@code {"hasDuty": false}}；有（未开始/值班中/已完成）返回
     * {@code {"hasDuty": true}} + 今日卡片同构数据（班次/时间段/带班领导/单位/值班人员等），
     * 前端据 status 提醒并引导至值班交接班页完成签到、填写报告、交班。
     */
    public Map<String, Object> loginRemind() {
        Map<String, Object> card = today();
        Map<String, Object> out = new LinkedHashMap<>();
        if ("none".equals(card.get("status"))) {
            out.put("hasDuty", false);
            return out;
        }
        out.put("hasDuty", true);
        out.putAll(card);
        return out;
    }

    // ==================== #8 开始值班（签到） ====================

    /**
     * 开始值班：校验排班存在且未完成 → 幂等（已有值班中记录直接复用）→
     * 生成 ZBJL 编号并创建记录（字段从排班复制、人员走 rel 表）→ 排班置值班中 + H5 提醒标记。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> start(AttendanceStartRequest request) {
        String operator = DutySupport.requireOperator();
        String scheduleId = DutySupport.requireText(request == null ? null : request.getScheduleId(),
                "排班 id 不能为空");
        ScheduleRowVO row = scheduleMapper.selectScheduleRow(scheduleId);
        if (row == null) {
            throw new IllegalArgumentException("排班不存在或已删除");
        }
        if (DutySupport.SCHEDULE_STATUS_COMPLETED.equals(row.getStatusCode())) {
            throw new DutyConflictException("该排班已完成，无法开始值班");
        }
        String dutyDate = DutySupport.fmtDate(row.getDutyDateRaw());
        String existingId = mapper.selectOngoingIdBySchedule(row.getOrgId(),
                DutySupport.trim(row.getTneakpMinRaw()), DutySupport.trim(row.getTneakpMaxRaw()), dutyDate);
        if (existingId != null) {
            return startResult(mapper.selectRecordRow(existingId));
        }

        LocalDateTime now = LocalDateTime.now();
        String recordNo = nextRecordNo(dutyDate);
        String recordId = shortIdGenerator.nextUUID(null);
        String shiftTimeText = DutySupport.fmtShiftTime(row.getTneakpMinRaw(), row.getTneakpMaxRaw());
        String orgName = row.getOrgName() != null ? row.getOrgName() : row.getLegacyUnit();
        mapper.insertRecord(recordId, corpCode, operator, now, recordNo,
                DutySupport.parseTs(row.getDutyDateRaw()),
                DutySupport.parseTs(row.getTneakpMinRaw()),
                DutySupport.parseTs(row.getTneakpMaxRaw()),
                row.getOrgId(), row.getLeaderId(), now, shiftTimeText, orgName);
        int order = 1;
        for (String memberId : scheduleMapper.selectMemberIds(scheduleId)) {
            mapper.insertRecordMember(shortIdGenerator.nextUUID(null), recordId, memberId,
                    String.valueOf(order++), corpCode, now, operator);
        }
        scheduleMapper.markScheduleOnDuty(scheduleId, operator, now);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("recordId", recordId);
        out.put("recordNo", recordNo);
        return out;
    }

    // ==================== #9 填写值班报告 ====================

    /** 填写报告（提交字段更新：未提交或为空的字段保持原值；记录非值班中 409；events/summary 至少一项非空、综述 ≤800 字）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> report(AttendanceReportRequest request) {
        String operator = DutySupport.requireOperator();
        String recordId = DutySupport.requireText(request == null ? null : request.getRecordId(),
                "值班记录 id 不能为空");
        AttendanceRecordRowVO row = mapper.selectRecordRow(recordId);
        if (row == null) {
            throw new IllegalArgumentException("值班记录不存在");
        }
        if (!DutySupport.RECORD_STATUS_ON_DUTY.equals(row.getStatusCode())) {
            throw new DutyConflictException("当前记录不在值班中，无法填写报告");
        }
        String events = DutySupport.trim(request.getEvents());
        String summary = DutySupport.trim(request.getSummary());
        if (events == null && summary == null) {
            throw new IllegalArgumentException("请填写值班报告内容");
        }
        if (summary != null && summary.length() > 800) {
            throw new IllegalArgumentException("值班情况综述不能超过 800 字");
        }
        int updated = mapper.updateRecordReport(recordId, events, summary, operator, LocalDateTime.now());
        if (updated == 0) {
            throw new DutyConflictException("当前记录不在值班中，无法填写报告");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    // ==================== #10 交班（核心事务） ====================

    /**
     * 交班（单事务）：① 校验接班人员存在性（400）；② 记录写实际交班时间 + #3#；
     * ③ 生成 JJB 编号并创建交接班记录（原班次时间/单位取自记录，交班人员=本班值班人员，
     * 接班人员=receiverIds，待办=matters）；④ 本班排班置已完成；
     * ⑤ 同单位下一班未开始排班置待值班（无下一班跳过）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handover(AttendanceHandoverRequest request) {
        String operator = DutySupport.requireOperator();
        String recordId = DutySupport.requireText(request == null ? null : request.getRecordId(),
                "值班记录 id 不能为空");
        AttendanceRecordRowVO rec = mapper.selectRecordRow(recordId);
        if (rec == null) {
            throw new IllegalArgumentException("值班记录不存在");
        }
        if (DutySupport.RECORD_STATUS_COMPLETED.equals(rec.getStatusCode())) {
            throw new DutyConflictException("该记录已完成交班");
        }
        if (!DutySupport.RECORD_STATUS_ON_DUTY.equals(rec.getStatusCode())) {
            throw new DutyConflictException("当前记录不在值班中，无法交班");
        }

        List<String> receiverIds = DutySupport.distinctIds(request.getReceiverIds());
        if (!receiverIds.isEmpty()
                && scheduleMapper.selectExistingUserIds(receiverIds).size() < receiverIds.size()) {
            throw new IllegalArgumentException("接班人员不存在或已删除");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = mapper.updateRecordHandover(recordId, operator, now);
        if (updated == 0) {
            throw new DutyConflictException("该记录已完成交班");
        }
        String handoverNo = nextHandoverNo(now.toLocalDate());
        String handoverId = shortIdGenerator.nextUUID(null);
        String matters = DutySupport.trim(request.getMatters());
        mapper.insertHandover(handoverId, corpCode, operator, now, handoverNo,
                DutySupport.parseTs(rec.getShiftMinRaw()), DutySupport.parseTs(rec.getShiftMaxRaw()),
                DutySupport.trim(rec.getOrgId()), now, matters);
        int fromOrder = 1;
        for (String fromId : mapper.selectRecordMemberIds(recordId)) {
            mapper.insertHandoverFromMember(shortIdGenerator.nextUUID(null), handoverId, fromId,
                    String.valueOf(fromOrder++), corpCode, now, operator);
        }
        int toOrder = 1;
        for (String toId : receiverIds) {
            mapper.insertHandoverToMember(shortIdGenerator.nextUUID(null), handoverId, toId,
                    String.valueOf(toOrder++), corpCode, now, operator);
        }
        linkSchedule(rec, operator, now);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("handoverNo", handoverNo);
        return out;
    }

    /** 交班排班联动：本班置已完成、同单位下一班置待值班；匹配失败降级跳过 + warn（不阻断）。 */
    private void linkSchedule(AttendanceRecordRowVO rec, String operator, LocalDateTime now) {
        String orgId = DutySupport.trim(rec.getOrgId());
        if (orgId == null) {
            log.warn("交班排班联动跳过：记录无单位 recordId={}", rec.getId());
            return;
        }
        String scheduleId = matchScheduleId(rec);
        if (scheduleId == null) {
            log.warn("交班排班联动跳过：未匹配到排班 recordId={}", rec.getId());
            return;
        }
        scheduleMapper.updateScheduleStatus(scheduleId, DutySupport.SCHEDULE_STATUS_COMPLETED, operator, now);
        String endRaw = DutySupport.trim(rec.getShiftMaxRaw());
        if (endRaw == null) {
            return;
        }
        String nextId = scheduleMapper.selectNextScheduleId(orgId, endRaw, scheduleId);
        if (nextId != null) {
            scheduleMapper.updateScheduleStatus(nextId, DutySupport.SCHEDULE_STATUS_WAITING, operator, now);
        }
    }

    // ==================== #11 今日交接班动态（时间线） ====================

    /**
     * 今日动态时间线：已交班（当日 kwzwga）+ 值守中（当日 #2# 记录）+ 待交接（当日 #zzkl#
     * 或已到点未开始 #1# 排班），合并后按时刻升序（同刻保持类型收集序）。
     */
    public Map<String, Object> timeline(String date) {
        String day = DutySupport.normalizeDay(date, "日期");
        if (day == null) {
            day = LocalDate.now().toString();
        }
        String dayEnd = LocalDate.parse(day).plusDays(1).toString();
        LocalDateTime now = LocalDateTime.now();

        List<Map<String, Object>> items = new ArrayList<>();
        for (HandoverTimelineRowVO row : mapper.selectTimelineHandovers(day, dayEnd)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "handover");
            item.put("status", "done");
            item.put("time", DutySupport.fmtHm(row.getHandoverTimeRaw()));
            item.put("title", handoverTitle(row.getShiftMinRaw(), row.getShiftMaxRaw()));
            item.put("handoverNo", row.getHandoverNo());
            item.put("fromUsers", DutySupport.splitNames(row.getFromNamesRaw()));
            item.put("toUsers", DutySupport.splitNames(row.getToNamesRaw()));
            items.add(item);
        }
        for (OngoingTimelineRowVO row : mapper.selectTimelineOngoing(day, dayEnd)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "ongoing");
            item.put("status", "active");
            item.put("time", DutySupport.fmtHm(row.getActualStartRaw()));
            item.put("title", titleOrFallback(DutySupport.shiftNameOfTime(row.getShiftMinRaw())));
            item.put("shiftTime", shiftTimeText(row.getShiftMinRaw(), row.getShiftMaxRaw(), row.getLegacyShift()));
            item.put("dutyUsers", DutySupport.splitNames(row.getMembersRaw()));
            items.add(item);
        }
        for (PendingTimelineRowVO row : mapper.selectTimelinePending(day, dayEnd, now)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "pending");
            item.put("status", "pending");
            item.put("time", DutySupport.fmtHm(row.getShiftMinRaw()));
            item.put("title", pendingTitle(row.getShiftMinRaw(), row.getShiftCode()));
            item.put("fromUsers", null);
            item.put("toUsers", DutySupport.splitNames(row.getMembersRaw()));
            items.add(item);
        }
        items.sort(Comparator.comparing((Map<String, Object> it) -> (String) it.get("time"),
                Comparator.nullsLast(Comparator.naturalOrder())));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", day);
        out.put("items", items);
        return out;
    }

    // ==================== 卡片组装 ====================

    /** 无安排响应 */
    private Map<String, Object> noneResult() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "none");
        out.put("badge", "无安排");
        return out;
    }

    /** 记录卡片（ongoing/completed 共用）：展示字段 + 反查排班 id。 */
    private Map<String, Object> buildRecordCard(AttendanceRecordRowVO rec, String status, String badge,
                                                String scheduleId) {
        String shiftCode = null;
        if (scheduleId != null) {
            ScheduleRowVO row = scheduleMapper.selectScheduleRow(scheduleId);
            shiftCode = row == null ? null : row.getShiftCode();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status);
        out.put("badge", badge);
        out.put("scheduleId", scheduleId);
        out.put("recordId", rec.getId());
        out.put("recordNo", rec.getRecordNo());
        out.put("date", DutySupport.fmtDate(rec.getDutyDateRaw()));
        out.put("shift", shiftCode != null ? DutySupport.shiftLabel(shiftCode)
                : DutySupport.shiftNameOfTime(rec.getShiftMinRaw()));
        out.put("shiftTime", shiftTimeText(rec.getShiftMinRaw(), rec.getShiftMaxRaw(), rec.getLegacyShift()));
        out.put("org", rec.getOrgName() != null ? rec.getOrgName() : rec.getLegacyUnit());
        out.put("leader", rec.getLeaderName());
        out.put("members", rec.getMembersRaw());
        out.put("summary", rec.getSummary());
        out.put("actualStart", DutySupport.fmtMinute(rec.getActualStartRaw()));
        out.put("actualEnd", DutySupport.fmtMinute(rec.getActualEndRaw()));
        return out;
    }

    /** 未开始卡片（我的排班；scheduleId 供「开始值班」使用）。 */
    private Map<String, Object> buildNotstarted(ScheduleRowVO sched) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "notstarted");
        out.put("badge", "未开始");
        out.put("scheduleId", sched.getId());
        out.put("date", DutySupport.fmtDate(sched.getDutyDateRaw()));
        out.put("shift", DutySupport.shiftLabel(sched.getShiftCode()));
        out.put("shiftTime", DutySupport.fmtShiftTime(sched.getTneakpMinRaw(), sched.getTneakpMaxRaw()));
        out.put("org", sched.getOrgName() != null ? sched.getOrgName() : sched.getLegacyUnit());
        out.put("leader", sched.getLeaderName());
        out.put("members", sched.getMemberNamesRaw());
        return out;
    }

    /** 接班人员候选：同单位下一班未开始排班的值班人员（无则空数组）。 */
    private List<Map<String, Object>> receiverCandidates(AttendanceRecordRowVO rec, String scheduleId) {
        List<Map<String, Object>> out = new ArrayList<>();
        String orgId = DutySupport.trim(rec.getOrgId());
        String endRaw = DutySupport.trim(rec.getShiftMaxRaw());
        if (orgId == null || endRaw == null) {
            return out;
        }
        String nextId = scheduleMapper.selectNextScheduleId(orgId, endRaw, scheduleId);
        if (nextId == null) {
            return out;
        }
        List<String> memberIds = scheduleMapper.selectMemberIds(nextId);
        if (memberIds.isEmpty()) {
            return out;
        }
        Map<String, String> nameById = userNames(memberIds);
        for (String id : memberIds) {
            String name = nameById.get(id);
            if (name != null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", id);
                item.put("name", name);
                out.add(item);
            }
        }
        return out;
    }

    // ==================== 匹配 / 编号 / 工具 ====================

    /**
     * 记录反查排班 id：单位 + 班次起止时间精确匹配（to_char 到秒）；
     * 未命中降级为「同单位 + 进行态 + 值班日期相同」；均未命中返回 null。
     */
    private String matchScheduleId(AttendanceRecordRowVO rec) {
        String orgId = DutySupport.trim(rec.getOrgId());
        if (orgId == null) {
            return null;
        }
        String startRaw = DutySupport.trim(rec.getShiftMinRaw());
        String endRaw = DutySupport.trim(rec.getShiftMaxRaw());
        if (startRaw != null && endRaw != null) {
            String id = scheduleMapper.selectScheduleIdByMatch(orgId, startRaw, endRaw);
            if (id != null) {
                return id;
            }
        }
        String day = DutySupport.fmtDate(rec.getDutyDateRaw());
        if (day == null) {
            return null;
        }
        ScheduleRowVO fallback = scheduleMapper.selectScheduleByFallback(orgId, day,
                LocalDate.parse(day).plusDays(1).toString());
        return fallback == null ? null : fallback.getId();
    }

    /** 班次时间文本：新列格式化优先，旧列文本兜底。 */
    private String shiftTimeText(String shiftMinRaw, String shiftMaxRaw, String legacyShift) {
        String fmt = DutySupport.fmtShiftTime(shiftMinRaw, shiftMaxRaw);
        return fmt != null ? fmt : DutySupport.trim(legacyShift);
    }

    /** 批量用户姓名映射（id → name）。 */
    private Map<String, String> userNames(List<String> ids) {
        Map<String, String> nameById = new HashMap<>();
        if (ids.isEmpty()) {
            return nameById;
        }
        for (Map<String, Object> u : scheduleMapper.selectUsersByIds(ids)) {
            Object idVal = u.get("id");
            Object nameVal = u.get("name");
            if (idVal != null && nameVal != null) {
                nameById.put(idVal.toString(), nameVal.toString());
            }
        }
        return nameById;
    }

    /** 生成值班记录编号 ZBJL-{值班日期 yyyyMMdd}-{NN}（当日最大序号 +1、两位补零）。 */
    private String nextRecordNo(String dutyDate) {
        String prefix = "ZBJL-" + compactDay(dutyDate) + "-";
        return prefix + String.format("%02d", maxSeq(mapper.selectRecordNosByPrefix(prefix + "%"), prefix) + 1);
    }

    /** 生成交接班编号 JJB-{交班当天 yyyyMMdd}-{NN}。 */
    private String nextHandoverNo(LocalDate day) {
        String prefix = "JJB-" + day.format(DAY_COMPACT) + "-";
        return prefix + String.format("%02d", maxSeq(mapper.selectHandoverNosByPrefix(prefix + "%"), prefix) + 1);
    }

    /** 当日编号最大序号（非数字后缀跳过；空列表返回 0）。 */
    private int maxSeq(List<String> nos, String prefix) {
        int max = 0;
        if (nos == null) {
            return max;
        }
        for (String no : nos) {
            if (no == null || !no.startsWith(prefix)) {
                continue;
            }
            String suffix = no.substring(prefix.length());
            if (!suffix.matches("\\d+")) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(suffix));
            } catch (NumberFormatException ignore) {
                // 超长数字串按非法值跳过
            }
        }
        return max;
    }

    /** 日期文本 → yyyyMMdd（非法/空取当天，防御兜底）。 */
    private String compactDay(String dutyDate) {
        String day = DutySupport.trim(dutyDate);
        if (day != null && day.length() >= 10) {
            return day.substring(0, 10).replace("-", "");
        }
        return LocalDate.now().format(DAY_COMPACT);
    }

    /** 已交班标题：上班次→下班次（按班次时间点推断，无法推断为「交接班」）。 */
    private String handoverTitle(String shiftMinRaw, String shiftMaxRaw) {
        String from = DutySupport.shiftNameOfTime(shiftMinRaw);
        String to = DutySupport.shiftNameOfTime(shiftMaxRaw);
        if (from != null && to != null) {
            return from + "→" + to;
        }
        return "交接班";
    }

    /** 待交接标题：上一班次→本班次（按开始时间点推断上一班，无法推断为「交接班」）。 */
    private String pendingTitle(String shiftMinRaw, String shiftCode) {
        String from = prevShiftName(shiftMinRaw);
        String to = DutySupport.shiftLabel(shiftCode);
        if (from == null || to == null) {
            return "交接班";
        }
        return from + "→" + to;
    }

    /** 按开始时间点推断上一班次名：08:00→夜班、16:00→白班、00:00→中班。 */
    private String prevShiftName(String shiftMinRaw) {
        LocalDateTime t = DutySupport.parseTs(shiftMinRaw);
        if (t == null) {
            return null;
        }
        LocalTime hm = t.toLocalTime();
        if (hm.equals(LocalTime.of(8, 0))) {
            return DutySupport.shiftLabel("#zsfh#");
        }
        if (hm.equals(LocalTime.of(16, 0))) {
            return DutySupport.shiftLabel("#1#");
        }
        if (hm.equals(LocalTime.MIDNIGHT)) {
            return DutySupport.shiftLabel("#jhyu#");
        }
        return null;
    }

    private String titleOrFallback(String title) {
        return title != null ? title : "交接班";
    }

    /** 幂等命中结果（复用已有值班中记录） */
    private Map<String, Object> startResult(AttendanceRecordRowVO existing) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("recordId", existing == null ? null : existing.getId());
        out.put("recordNo", existing == null ? null : existing.getRecordNo());
        return out;
    }
}
