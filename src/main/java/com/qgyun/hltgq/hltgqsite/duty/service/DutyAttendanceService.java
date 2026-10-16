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
 * 值班交接班服务（值班交接班页：#7 当前值班四态、#8 开始值班、#9 填写报告、#10 交班、#11 今日动态、#12 取号）。
 * <p>记录按人（2026-10 二次改造）：值班记录 = 一个人的值班记录——每人签到各自生成一条记录（归属人 ahygpx），
 * 报告写各自的记录（互不可见互不覆盖）；一次交班全班记录一并收口（同班组批量置 #3# + 一条交接班记录）。
 * 改造前历史记录（ahygpx 为空）不可报告/交班，其参与人按排班维度回到「待签到」各自签到生成自己的记录；
 * 历史班组随任一收口一并关闭，展示回退 rel 人员。
 * <p>判定顺序：我的在途记录（值班中）→ 我的在途排班（待签到）→ 我的待值班排班（未开始）→ 今日交班的已完成记录 → 无安排；
 * 记录与排班无外键，靠「单位 + 班次起止时间」精确匹配回查（失败降级同单位同日，再失败跳过联动 + warn）；
 * 编号生成：ZBJL-{值班日期}-{NN} / JJB-{交班当天}-{NN}（查当日前缀 + Java 解析最大序号；
 * 取号接口（契约 §3.8）与签到/交班同源，数据库事务级建议锁 + 进程内互斥防并发重号，
 * 签到并发幂等（重复点击/双端并发）由同一事务级建议锁 + 归属人查重串行化）。
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
     * 当前值班卡片（判定顺序，记录按人）：
     * ① 我的在途 #2# 记录（归属人 = 我、班次时间与今日相交）→ ongoing（附 myCheckinAt = 本人签到时间，
     *    即 jlailj）+ receiverCandidates（下一班接班人员候选，供交班弹窗默认选中）；
     * ② 我的在途排班（#cmiu# 且班次时间与今日相交，我尚未生成自己的记录）→ notstarted（badge「待签到」，引导签到）；
     * ③ 我的待值班排班（#zzkl#/#1# 且班次时间与今日相交）→ notstarted（badge「未开始」）；
     * ④ 今日交班的我的 #3# 记录（含历史班记录回退匹配）→ completed；⑤ none。
     * <p>同一天多条并存（如白班在途未签 + 晚班待值班）时优先返回待签到，登录提醒先弹「去签到」；
     * 签到后按判定顺序自然流转为「值班中」。
     */
    public Map<String, Object> today() {
        UserContext user = UserContextHolder.currentUser();
        String userId = user == null ? null : DutySupport.trim(user.getUserId());
        if (userId == null) {
            return noneResult();
        }
        LocalDate today = LocalDate.now();
        String dayStart = today.toString();
        String dayEnd = today.plusDays(1).toString();
        AttendanceRecordRowVO ongoing = mapper.selectOngoingRecord(userId, dayStart, dayEnd);
        if (ongoing != null) {
            String scheduleId = matchScheduleId(ongoing);
            Map<String, Object> out = buildRecordCard(ongoing, "ongoing", "值班中", scheduleId);
            out.put("myCheckinAt", DutySupport.fmtMinute(ongoing.getActualStartRaw()));
            out.put("receiverCandidates", receiverCandidates(ongoing, scheduleId));
            return out;
        }
        ScheduleRowVO onDuty = scheduleMapper.selectMyOngoingSchedule(userId, dayStart, dayEnd);
        if (onDuty != null) {
            return buildNotstarted(onDuty, "待签到");
        }
        ScheduleRowVO pending = scheduleMapper.selectMyPendingTodaySchedule(userId, dayStart, dayEnd);
        if (pending != null) {
            return buildNotstarted(pending, "未开始");
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
     * 开始值班（签到）：校验排班存在、<b>本人为本班带班领导或值班人员</b>（非本班人员 409）→
     * 事务级建议锁内签到幂等检查（本人已有该排班在途记录则直接复用返回，重复点击/并发不重复创建）→
     * 状态闸门（仅已完成 #qavx# 拒绝；值班中 #cmiu# 允许同班后来者签到）→
     * 生成 ZBJL 编号并为本人创建记录（归属人 = 本人；领导本人签到则带班领导 = 本人）→ 排班置值班中 + H5 提醒标记。
     * <p>记录按人：同一排班内每人各签各的记录、各填各的报告，互不影响。
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
        List<String> memberIds = scheduleMapper.selectMemberIds(scheduleId);
        boolean isLeader = operator.equals(DutySupport.trim(row.getLeaderId()));
        if (!isLeader && !memberIds.contains(operator)) {
            throw new DutyConflictException("仅本班带班领导或值班人员可签到");
        }
        String dutyDate = DutySupport.fmtDate(row.getDutyDateRaw());
        String startRaw = DutySupport.trim(row.getTneakpMinRaw());
        String endRaw = DutySupport.trim(row.getTneakpMaxRaw());
        mapper.lockDutyNoSerial();
        String existingId = mapper.selectMyOngoingIdBySchedule(row.getOrgId(), startRaw, endRaw, dutyDate,
                operator);
        if (existingId != null) {
            // 我已签到：幂等复用本人记录（重复点击/并发重复提交不重复创建）
            return startResult(mapper.selectRecordRow(existingId));
        }
        if (DutySupport.SCHEDULE_STATUS_COMPLETED.equals(row.getStatusCode())) {
            throw new DutyConflictException("该排班已完成，无法签到");
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
                row.getOrgId(), row.getLeaderId(), operator, now, shiftTimeText, orgName);
        scheduleMapper.markScheduleOnDuty(scheduleId, operator, now);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("recordId", recordId);
        out.put("recordNo", recordNo);
        return out;
    }

    // ==================== #9 填写值班报告 ====================

    /**
     * 填写报告（提交字段更新：个人记录版——<b>仅记录归属人（ahygpx）可写本人报告</b>，
     * 各人报告互不可见互不覆盖；非归属人 409）；记录非值班中 409；events/summary 至少一项非空、综述 ≤800 字。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> report(AttendanceReportRequest request) {
        String operator = DutySupport.requireOperator();
        String recordId = DutySupport.requireText(request == null ? null : request.getRecordId(),
                "值班记录 id 不能为空");
        AttendanceRecordRowVO row = mapper.selectRecordRow(recordId);
        if (row == null) {
            throw new IllegalArgumentException("值班记录不存在");
        }
        if (!operator.equals(DutySupport.trim(row.getMemberId()))) {
            throw new DutyConflictException("仅记录归属人可填写本人值班报告");
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
     * 交班（单事务）：① 校验本人为记录归属人（ahygpx；非归属人 409）、记录在途（#2#）、接班人员存在性（400）；
     * ② 全班收口：同单位 + 班次起止时间的在途记录组（排班无班次时间降级同单位 + 值班日期）
     * 一并置 #3#、写同一交班时间（含改造前历史班记录；任一人发起即全班收口，其余人再交班 409）；
     * ③ 生成 JJB 编号并创建交接班记录（原班次时间/单位取自记录，交班人员 = 组内各记录归属人
     * （历史记录回退 rel 人员），接班人员 = receiverIds，待办 = matters）；④ 本班排班置已完成；
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
        if (!operator.equals(DutySupport.trim(rec.getMemberId()))) {
            throw new DutyConflictException("仅记录归属人可发起交班");
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
        String orgId = DutySupport.trim(rec.getOrgId());
        String startRaw = DutySupport.trim(rec.getShiftMinRaw());
        String endRaw = DutySupport.trim(rec.getShiftMaxRaw());
        String dutyDate = DutySupport.fmtDate(rec.getDutyDateRaw());
        List<String> fromIds;
        int closed;
        if (orgId == null) {
            // 记录无单位信息无法按班组收口：单条兜底（历史脏数据场景）
            fromIds = new ArrayList<>();
            String owner = DutySupport.trim(rec.getMemberId());
            if (owner != null) {
                fromIds.add(owner);
            }
            closed = mapper.updateRecordHandover(recordId, operator, now);
        } else {
            fromIds = mapper.selectGroupMemberIds(orgId, startRaw, endRaw, dutyDate);
            closed = mapper.updateGroupHandover(orgId, startRaw, endRaw, dutyDate, operator, now);
        }
        if (closed == 0) {
            throw new DutyConflictException("该记录已完成交班");
        }
        String handoverNo = nextHandoverNo(now.toLocalDate());
        String handoverId = shortIdGenerator.nextUUID(null);
        String matters = DutySupport.trim(request.getMatters());
        mapper.insertHandover(handoverId, corpCode, operator, now, handoverNo,
                DutySupport.parseTs(rec.getShiftMinRaw()), DutySupport.parseTs(rec.getShiftMaxRaw()),
                orgId, now, matters);
        int fromOrder = 1;
        for (String fromId : fromIds) {
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
     * 今日动态时间线：已交班（当日 kwzwga）+ 值守中（当日 #2# 记录按班组聚合一行）+ 待交接（当日 #zzkl#
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
            item.put("matters", DutySupport.trim(row.getMatters()));
            items.add(item);
        }
        for (OngoingTimelineRowVO row : mapper.selectTimelineOngoing(day, dayEnd)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "ongoing");
            item.put("status", "active");
            item.put("time", DutySupport.fmtHm(row.getActualStartRaw()));
            item.put("title", titleOrFallback(DutySupport.shiftNameOfTime(row.getShiftMinRaw())));
            item.put("shiftTime", shiftTimeText(row.getShiftMinRaw(), row.getShiftMaxRaw(), row.getLegacyShift()));
            item.put("dutyUsers", ongoingUsers(row));
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

    /**
     * 值守中人员（含带班领导，领导在前并标注 leader=true）：按「单位 + 班次时间」反查排班取本班全员名册，
     * 各人按「是否已有归属自己的在途记录」标注 checkedIn（true=正在值守 / false=待签到）；
     * 反查不到排班（历史数据）时回退「记录人员聚合」并均视为已签到；
     * 名册外已有记录者（排班人员被编辑）追加到尾部，保证值守人员不漏。
     */
    private List<Map<String, Object>> ongoingUsers(OngoingTimelineRowVO row) {
        List<String> checkedIn = DutySupport.splitNames(row.getMembersRaw());
        List<String> roster = new ArrayList<>();
        String leaderName = null;
        String orgKey = DutySupport.trim(row.getOrgKey());
        String startRaw = DutySupport.trim(row.getShiftMinRaw());
        String endRaw = DutySupport.trim(row.getShiftMaxRaw());
        if (orgKey != null && startRaw != null && endRaw != null) {
            String scheduleId = scheduleMapper.selectScheduleIdByMatch(orgKey, startRaw, endRaw);
            if (scheduleId != null) {
                ScheduleRowVO sched = scheduleMapper.selectScheduleRow(scheduleId);
                if (sched != null) {
                    leaderName = DutySupport.trim(sched.getLeaderName());
                    addName(roster, leaderName);
                }
                List<String> memberIds = scheduleMapper.selectMemberIds(scheduleId);
                Map<String, String> nameById = userNames(memberIds);
                for (String id : memberIds) {
                    addName(roster, nameById.get(id));
                }
            }
        }
        if (roster.isEmpty()) {
            roster = checkedIn;
        } else {
            for (String name : checkedIn) {
                addName(roster, name);
            }
        }
        List<Map<String, Object>> users = new ArrayList<>();
        for (String name : roster) {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("name", name);
            u.put("checkedIn", checkedIn.contains(name));
            u.put("leader", name.equals(leaderName));
            users.add(u);
        }
        return users;
    }

    /** 名册去重追加（姓名非空且未存在时追加，保持先领导后成员顺序）。 */
    private void addName(List<String> list, String name) {
        String n = DutySupport.trim(name);
        if (n != null && !list.contains(n)) {
            list.add(n);
        }
    }

    // ==================== #12 取号（下一个编号查询） ====================

    /**
     * 取号：查询下一个可用编号（不落库、不预留）。
     * <p>type=record（值班记录 ZBJL-，默认）/ handover（交接班 JJB-）；date 可选 yyyy-MM-dd（默认今天，
     * record 传值班日期口径、handover 传交班当天口径）。与签到/交班内部编号生成同源：
     * 签到/交班在事务内持有数据库级建议锁至提交，并发不重号；本接口仅查询、不预留占用，
     * 编号以数据表现存记录为准，取号后未落库不视为已占用。
     */
    public Map<String, Object> nextNo(String type, String date) {
        String typeNorm = DutySupport.trim(type);
        if (typeNorm == null) {
            typeNorm = "record";
        }
        String day = DutySupport.normalizeDay(date, "日期");
        if (day == null) {
            day = LocalDate.now().toString();
        }
        String no;
        if ("record".equals(typeNorm)) {
            no = nextRecordNo(day);
        } else if ("handover".equals(typeNorm)) {
            no = nextHandoverNo(LocalDate.parse(day));
        } else {
            throw new IllegalArgumentException("编号类型仅支持 record（值班记录）或 handover（交接班）：" + typeNorm);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", typeNorm);
        out.put("date", day);
        out.put("no", no);
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

    /** 记录卡片（ongoing/completed 共用）：展示字段 + 反查排班 id；成员展示优先归属人（新记录），历史记录回退 rel 聚合。 */
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
        out.put("members", rec.getMemberName() != null ? rec.getMemberName() : rec.getMembersRaw());
        out.put("events", rec.getEvents());
        out.put("summary", rec.getSummary());
        out.put("actualStart", DutySupport.fmtMinute(rec.getActualStartRaw()));
        out.put("actualEnd", DutySupport.fmtMinute(rec.getActualEndRaw()));
        return out;
    }

    /**
     * 未开始卡片（我的排班；scheduleId 供签到使用）：badge「未开始」（待值班→开始值班）/
     * 「待签到」（排班已在途、我尚未签到→去签到）。
     */
    private Map<String, Object> buildNotstarted(ScheduleRowVO sched, String badge) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "notstarted");
        out.put("badge", badge);
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

    /**
     * 生成值班记录编号 ZBJL-{值班日期 yyyyMMdd}-{NN}（当日最大序号 +1、四位补零，容量 9999/天）。
     * <p>事务级建议锁 + 进程内互斥：锁在「查最大序号 → 插入 → 事务提交」全程持有，防并发重号。
     */
    private synchronized String nextRecordNo(String dutyDate) {
        mapper.lockDutyNoSerial();
        String prefix = "ZBJL-" + compactDay(dutyDate) + "-";
        return prefix + String.format("%04d", maxSeq(mapper.selectRecordNosByPrefix(prefix + "%"), prefix) + 1);
    }

    /** 生成交接班编号 JJB-{交班当天 yyyyMMdd}-{NN}（四位补零；事务级建议锁 + 进程内互斥防并发重号）。 */
    private synchronized String nextHandoverNo(LocalDate day) {
        mapper.lockDutyNoSerial();
        String prefix = "JJB-" + day.format(DAY_COMPACT) + "-";
        return prefix + String.format("%04d", maxSeq(mapper.selectHandoverNosByPrefix(prefix + "%"), prefix) + 1);
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
