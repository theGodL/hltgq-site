package com.qgyun.hltgq.hltgqsite.duty.service;

import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.duty.DutyConflictException;
import com.qgyun.hltgq.hltgqsite.duty.DutySupport;
import com.qgyun.hltgq.hltgqsite.duty.mapper.DutyAttendanceMapper;
import com.qgyun.hltgq.hltgqsite.duty.mapper.DutyScheduleMapper;
import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleDetailVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleListRowVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleRowVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleSaveRequest;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 值班排班服务（值班管理页：#1 今日判定、#2 列表、#3 详情、#4 选项、#5 保存、#6 删除、#7 保存前校验）。
 * <p>读：新列优先 + 旧列兜底，「待值班」读时纠偏不落库（§3.1）；
 * 写：主写当前模型字段并兼容双写旧列（保 H5 值班提醒），编辑/删除仅限未开始类
 * （未开始 #1# 或待值班 #zzkl#）排班；保存时校验人员时间冲突（同一时间段一个人只能有一条排班），
 * 校验规则与 §3.7 校验接口同源（collectErrors，保存取首条、校验返回全部）。
 */
@Service
public class DutyScheduleService {

    @Autowired
    private DutyScheduleMapper mapper;

    @Autowired
    private DutyAttendanceMapper attendanceMapper;

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    @Value("${hltgq.corp-code}")
    private String corpCode;

    /** 下拉选项：班次（含表单默认时段）+ 单位 + 人员（带班领导与值班人员共用）。 */
    public Map<String, Object> options() {
        List<Map<String, Object>> shifts = new ArrayList<>();
        shifts.add(shiftOption("#1#", "08:00", "16:00"));
        shifts.add(shiftOption("#jhyu#", "16:00", "24:00"));
        shifts.add(shiftOption("#zsfh#", "00:00", "08:00"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("shifts", shifts);
        out.put("orgs", mapper.selectOptionsOrgs());
        out.put("users", mapper.selectOptionsUsers());
        return out;
    }

    /**
     * 排班分页列表。
     *
     * @param date    值班日期 yyyy-MM-dd（精确到当日），可选
     * @param shift   班次：编码或中文均可，可选
     * @param status  排班状态中文（未开始/待值班/值班中/已完成，纠偏口径），可选
     * @param org     单位：单位 id 或名称模糊，可选
     * @param keyword 人员关键词：值班人员/带班领导姓名模糊，可选
     */
    public Map<String, Object> list(int page, int size, String date, String shift,
                                    String status, String org, String keyword) {
        int safePage = DutySupport.clampPage(page);
        int safeSize = DutySupport.clampSize(size);
        String day = DutySupport.normalizeDay(date, "值班日期");
        String dayStart = day;
        String dayEnd = day == null ? null : LocalDate.parse(day).plusDays(1).toString();

        String shiftCode = null;
        if (DutySupport.trim(shift) != null) {
            shiftCode = DutySupport.shiftCode(shift);
            if (shiftCode == null) {
                throw new IllegalArgumentException("班次编码不合法");
            }
        }
        String statusFilter = DutySupport.trim(status);
        String orgFilter = DutySupport.trim(org);
        String keywordFilter = DutySupport.trim(keyword);

        LocalDateTime now = LocalDateTime.now();
        long total = mapper.countSchedulePage(dayStart, dayEnd, shiftCode, statusFilter, orgFilter, keywordFilter, now);
        List<ScheduleListRowVO> records = new ArrayList<>();
        if (total > 0) {
            records = mapper.selectSchedulePage(dayStart, dayEnd, shiftCode, statusFilter, orgFilter, keywordFilter,
                    safeSize, (safePage - 1) * safeSize, now);
            for (ScheduleListRowVO row : records) {
                fillListRow(row);
            }
        }
        return DutySupport.pageResult(total, safePage, safeSize, records);
    }

    /** 排班详情（编辑/查看回显；含成员数组与读时纠偏状态）。 */
    public ScheduleDetailVO detail(String id) {
        String scheduleId = DutySupport.requireText(id, "排班不存在或已删除");
        ScheduleDetailVO vo = mapper.selectScheduleDetail(scheduleId);
        if (vo == null) {
            throw new IllegalArgumentException("排班不存在或已删除");
        }
        return fillDetail(vo);
    }

    /**
     * 保存排班（新增/编辑）：校验（与 §3.7 校验接口同源 collectErrors）→ 写主表 + 重写值班人员关系
     * （编辑先删后插）。
     * <p>编辑仅限未开始类（存储 #1# 或 #zzkl#），否则 409；新增默认 peuzwi=#1#。
     * <p>人员时间唯一：同一时间段一个人（带班领导或值班人员）只能有一条排班，冲突 409。
     * <p>校验失败返回首个问题（格式/存在性 400，状态/冲突 409）；全部问题见 verify（§3.7）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduleDetailVO save(ScheduleSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String operator = DutySupport.requireOperator();
        List<ValidationError> problems = collectErrors(request);
        if (!problems.isEmpty()) {
            ValidationError first = problems.get(0);
            if (first.conflict) {
                throw new DutyConflictException(first.message);
            }
            throw new IllegalArgumentException(first.message);
        }

        // 校验已通过，以下取值必然合法（与 collectErrors 同口径，仅做写库取值）
        String dutyDate = DutySupport.normalizeDay(request.getDutyDate(), "值班日期");
        LocalDateTime timeStart = DutySupport.parseMinute(request.getTimeStart(), "值班开始时间");
        LocalDateTime timeEnd = DutySupport.parseMinute(request.getTimeEnd(), "值班结束时间");
        String shiftCode = DutySupport.shiftCode(request.getShiftCode());
        String orgId = DutySupport.trim(request.getOrgId());
        String orgName = mapper.selectOrgNameById(orgId);
        String leaderId = DutySupport.trim(request.getLeaderId());
        List<String> memberIds = DutySupport.distinctIds(request.getMemberIds());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dutyDateAt = LocalDate.parse(dutyDate).atStartOfDay();
        String shiftTimeText = DutySupport.fmtShiftTime(timeStart, timeEnd);
        String scheduleId = DutySupport.trim(request.getId());
        if (scheduleId == null) {
            scheduleId = shortIdGenerator.nextUUID(null);
            mapper.insertSchedule(scheduleId, corpCode, operator, now, dutyDateAt, timeStart, timeEnd,
                    shiftCode, orgId, leaderId, shiftTimeText, orgName);
        } else {
            mapper.updateSchedule(scheduleId, operator, now, dutyDateAt, timeStart, timeEnd,
                    shiftCode, orgId, leaderId, shiftTimeText, orgName);
            mapper.deleteMembers(scheduleId);
        }
        writeMembers(scheduleId, memberIds, operator, now);
        return fillDetail(mapper.selectScheduleDetail(scheduleId));
    }

    /**
     * 保存前数据校验（`POST /duty/schedule/verify`；不落库）：与 {@link #save} 同一套规则
     * （collectErrors），一次性返回全部问题，供前端标记到对应输入项。
     *
     * @return {@code {"valid": true}} 或 {@code {"valid": false, "errors": [{"field", "message"}]}}
     */
    public Map<String, Object> verify(ScheduleSaveRequest request) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (request == null) {
            out.put("valid", false);
            List<Map<String, String>> errors = new ArrayList<>();
            errors.add(errorItem(null, "请求体不能为空"));
            out.put("errors", errors);
            return out;
        }
        List<ValidationError> problems = collectErrors(request);
        out.put("valid", problems.isEmpty());
        if (!problems.isEmpty()) {
            List<Map<String, String>> errors = new ArrayList<>();
            for (ValidationError problem : problems) {
                errors.add(errorItem(problem.field, problem.message));
            }
            out.put("errors", errors);
        }
        return out;
    }

    /** 删除排班（仅未开始类：未开始/待值班；同步清理值班人员关系）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> delete(String id) {
        DutySupport.requireOperator();
        String scheduleId = DutySupport.requireText(id, "排班不存在或已删除");
        ScheduleRowVO row = mapper.selectScheduleRow(scheduleId);
        if (row == null) {
            throw new IllegalArgumentException("排班不存在或已删除");
        }
        if (!editableStatus(row.getStatusCode())) {
            throw new DutyConflictException("仅未开始或待值班的排班可删除");
        }
        mapper.deleteMembers(scheduleId);
        mapper.deleteSchedule(scheduleId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }

    /**
     * 今日值班判定（签到提醒）：我参与、且「值班中（不限时间）或 未开始/待值班且班次时间与今日相交」的排班；
     * 未命中返回 {@code {"onDuty": false}}。
     * <p>signed：签到按人——每条值班记录归属一个值班人员——仅排班已置值班中（#cmiu#）时
     * 判定我已生成在途记录（按归属人 + 单位 + 班次匹配），其余状态恒 false；
     * 弹签到提醒条件由此修订为 {@code #1#/#zzkl# 或（#cmiu# 且 signed=false）}（契约 §3.1）。
     */
    public Map<String, Object> todayStatus() {
        Map<String, Object> out = new LinkedHashMap<>();
        UserContext user = UserContextHolder.currentUser();
        String userId = user == null ? null : DutySupport.trim(user.getUserId());
        if (userId == null) {
            out.put("onDuty", false);
            return out;
        }
        LocalDate today = LocalDate.now();
        ScheduleRowVO row = mapper.selectMyTodaySchedule(userId, today.toString(), today.plusDays(1).toString());
        if (row == null) {
            out.put("onDuty", false);
            return out;
        }
        out.put("onDuty", true);
        out.put("scheduleId", row.getId());
        out.put("dutyDate", DutySupport.fmtDate(row.getDutyDateRaw()));
        out.put("statusCode", row.getStatusCode());
        out.put("status", DutySupport.displayScheduleStatus(row.getStatusCode(),
                scheduleStartRaw(row.getTneakpMinRaw(), row.getDutyDateRaw())));
        out.put("name", mapper.selectUserNameById(userId));
        out.put("shift", DutySupport.shiftLabel(row.getShiftCode()));
        out.put("shiftTime", DutySupport.fmtShiftTime(row.getTneakpMinRaw(), row.getTneakpMaxRaw()));
        out.put("leader", row.getLeaderName());
        out.put("signed", signedOnDuty(row, userId));
        return out;
    }

    /**
     * 本人是否已在本班签到（值班记录 = 一个人的值班记录：该排班我已生成在途 #2# 记录即已签到）：
     * 仅排班已置值班中（#cmiu#）时判定（按归属人 + 单位 + 班次匹配记录），
     * 其余状态视为未签到（弹窗侧以 statusCode + signed 联合判断）。
     */
    private boolean signedOnDuty(ScheduleRowVO row, String userId) {
        if (!DutySupport.SCHEDULE_STATUS_ON_DUTY.equals(row.getStatusCode())) {
            return false;
        }
        String signedId = attendanceMapper.selectMyOngoingIdBySchedule(row.getOrgId(),
                DutySupport.trim(row.getTneakpMinRaw()), DutySupport.trim(row.getTneakpMaxRaw()),
                DutySupport.fmtDate(row.getDutyDateRaw()), userId);
        return signedId != null;
    }

    // ==================== 私有工具 ====================

    /** 读时纠偏开始时间：班次开始时间兜底值班日期（对齐 SQL 的 COALESCE(tneakp_min, owcvsv)）。 */
    private String scheduleStartRaw(String tneakpMinRaw, String dutyDateRaw) {
        String start = DutySupport.trim(tneakpMinRaw);
        return start != null ? start : dutyDateRaw;
    }

    /** 列表行转换：原始列 → 对外字段（日期/班次/时间文本/单位兜底/状态纠偏/成员数组）。 */
    private void fillListRow(ScheduleListRowVO row) {
        row.setDate(DutySupport.fmtDate(row.getDutyDateRaw()));
        row.setShift(DutySupport.shiftLabel(row.getShiftCode()));
        row.setShiftTime(DutySupport.fmtShiftTime(row.getTneakpMinRaw(), row.getTneakpMaxRaw()));
        row.setTimeStart(DutySupport.fmtMinute(row.getTneakpMinRaw()));
        row.setTimeEnd(DutySupport.fmtMinute(row.getTneakpMaxRaw()));
        row.setOrg(row.getOrgName() != null ? row.getOrgName() : row.getLegacyUnit());
        row.setStatus(DutySupport.displayScheduleStatus(row.getStatusCode(),
                scheduleStartRaw(row.getTneakpMinRaw(), row.getDutyDateRaw())));
        row.setMemberIds(DutySupport.splitIds(row.getMemberIdsRaw()));
    }

    /** 详情转换：原始列 → 对外字段（与列表同口径，成员拆分两数组）。 */
    private ScheduleDetailVO fillDetail(ScheduleDetailVO vo) {
        vo.setDutyDate(DutySupport.fmtDate(vo.getDutyDateRaw()));
        vo.setShift(DutySupport.shiftLabel(vo.getShiftCode()));
        vo.setTimeStart(DutySupport.fmtMinute(vo.getTneakpMinRaw()));
        vo.setTimeEnd(DutySupport.fmtMinute(vo.getTneakpMaxRaw()));
        if (vo.getOrgName() == null) {
            vo.setOrgName(vo.getLegacyUnit());
        }
        vo.setStatus(DutySupport.displayScheduleStatus(vo.getStatusCode(),
                scheduleStartRaw(vo.getTneakpMinRaw(), vo.getDutyDateRaw())));
        vo.setMemberIds(DutySupport.splitIds(vo.getMemberIdsRaw()));
        vo.setMemberNames(DutySupport.splitNames(vo.getMemberNamesRaw()));
        return vo;
    }

    /** 值班人员关系写入（nature_order 从 1 递增）。 */
    private void writeMembers(String scheduleId, List<String> memberIds, String operator, LocalDateTime now) {
        int order = 1;
        for (String memberId : memberIds) {
            mapper.insertMember(shortIdGenerator.nextUUID(null), scheduleId, memberId,
                    String.valueOf(order++), corpCode, now, operator);
        }
    }

    /** 可编辑/删除的排班状态（未开始类：#1# 未开始、#zzkl# 待值班）。 */
    private boolean editableStatus(String statusCode) {
        return DutySupport.SCHEDULE_STATUS_NOT_STARTED.equals(statusCode)
                || DutySupport.SCHEDULE_STATUS_WAITING.equals(statusCode);
    }

    /** 保存排班的参与人集合（带班领导 + 值班人员，保序去重）。 */
    private List<String> buildParticipantIds(String leaderId, List<String> memberIds) {
        List<String> raw = new ArrayList<>();
        raw.add(leaderId);
        raw.addAll(memberIds);
        return DutySupport.distinctIds(raw);
    }

    /**
     * 人员时间冲突提示文案：同一时间段一个人（带班领导或值班人员，不限单位/状态）只能有一条排班；
     * 命中返回提示文案（含重叠人员与冲突班次；编辑时排除自身），无冲突返回 null。
     */
    private String timeConflictMessage(String excludeId, LocalDateTime timeStart, LocalDateTime timeEnd,
                                       List<String> participantIds) {
        ScheduleRowVO conflict = mapper.selectScheduleConflict(excludeId,
                DutySupport.fmtSecond(timeStart), DutySupport.fmtSecond(timeEnd), participantIds);
        if (conflict == null) {
            return null;
        }
        String names = DutySupport.trim(conflict.getConflictNamesRaw());
        String day = DutySupport.fmtDate(conflict.getDutyDateRaw());
        if (day == null) {
            day = DutySupport.fmtDate(conflict.getTneakpMinRaw());
        }
        String org = conflict.getOrgName() != null ? conflict.getOrgName() : conflict.getLegacyUnit();
        String when = DutySupport.fmtShiftTime(conflict.getTneakpMinRaw(), conflict.getTneakpMaxRaw());
        StringBuilder message = new StringBuilder("值班时间冲突：");
        message.append(names == null ? "所选人员" : names);
        message.append(" 在 ");
        if (day != null) {
            message.append(day).append(' ');
        }
        message.append(DutySupport.shiftLabel(conflict.getShiftCode()));
        if (org != null) {
            message.append('（').append(org);
            if (when != null) {
                message.append(' ').append(when);
            }
            message.append('）');
        }
        message.append(" 已有值班安排");
        return message.toString();
    }

    /**
     * 保存/校验共用的规则收集（单一来源，按序收集全部问题；保存只取首条抛错）：
     * 必填与格式 → 班次/单位/领导/人员存在性 → 编辑对象状态 → 人员时间唯一。
     * <p>基础问题存在时跳过冲突查询（时间/人员不可靠，结果无意义）。
     */
    private List<ValidationError> collectErrors(ScheduleSaveRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        if (DutySupport.trim(request.getDutyDate()) == null) {
            errors.add(new ValidationError("dutyDate", "值班日期不能为空", false));
        } else {
            try {
                DutySupport.normalizeDay(request.getDutyDate(), "值班日期");
            } catch (IllegalArgumentException e) {
                errors.add(new ValidationError("dutyDate", e.getMessage(), false));
            }
        }
        LocalDateTime timeStart = null;
        LocalDateTime timeEnd = null;
        if (DutySupport.trim(request.getTimeStart()) == null) {
            errors.add(new ValidationError("timeStart", "值班开始时间不能为空", false));
        } else {
            try {
                timeStart = DutySupport.parseMinute(request.getTimeStart(), "值班开始时间");
            } catch (IllegalArgumentException e) {
                errors.add(new ValidationError("timeStart", e.getMessage(), false));
            }
        }
        if (DutySupport.trim(request.getTimeEnd()) == null) {
            errors.add(new ValidationError("timeEnd", "值班结束时间不能为空", false));
        } else {
            try {
                timeEnd = DutySupport.parseMinute(request.getTimeEnd(), "值班结束时间");
            } catch (IllegalArgumentException e) {
                errors.add(new ValidationError("timeEnd", e.getMessage(), false));
            }
        }
        if (timeStart != null && timeEnd != null && !timeEnd.isAfter(timeStart)) {
            errors.add(new ValidationError("timeEnd", "值班结束时间必须晚于开始时间", false));
        }
        if (DutySupport.shiftCode(request.getShiftCode()) == null) {
            errors.add(new ValidationError("shiftCode", "班次编码不合法", false));
        }
        String orgId = DutySupport.trim(request.getOrgId());
        if (orgId == null) {
            errors.add(new ValidationError("orgId", "所在单位不能为空", false));
        } else if (mapper.selectOrgNameById(orgId) == null) {
            errors.add(new ValidationError("orgId", "所在单位不存在或已删除", false));
        }
        String leaderId = DutySupport.trim(request.getLeaderId());
        if (leaderId == null) {
            errors.add(new ValidationError("leaderId", "带班领导不能为空", false));
        } else if (mapper.selectUserNameById(leaderId) == null) {
            errors.add(new ValidationError("leaderId", "带班领导不存在", false));
        }
        List<String> memberIds = DutySupport.distinctIds(request.getMemberIds());
        if (!memberIds.isEmpty() && mapper.selectExistingUserIds(memberIds).size() < memberIds.size()) {
            errors.add(new ValidationError("memberIds", "值班人员不存在或已删除", false));
        }
        String scheduleId = DutySupport.trim(request.getId());
        if (scheduleId != null) {
            ScheduleRowVO row = mapper.selectScheduleRow(scheduleId);
            if (row == null) {
                errors.add(new ValidationError("id", "排班不存在或已删除", false));
            } else if (!editableStatus(row.getStatusCode())) {
                errors.add(new ValidationError("id", "仅未开始或待值班的排班可编辑", true));
            }
        }
        if (errors.isEmpty()) {
            String conflict = timeConflictMessage(scheduleId, timeStart, timeEnd,
                    buildParticipantIds(leaderId, memberIds));
            if (conflict != null) {
                errors.add(new ValidationError("timeStart", conflict, true));
            }
        }
        return errors;
    }

    /** 校验问题的输出项（field 可空：请求体整体问题）。 */
    private Map<String, String> errorItem(String field, String message) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("field", field);
        item.put("message", message);
        return item;
    }

    /** 校验问题项：field 定位请求字段；conflict=true 为状态/业务冲突类（保存时抛 409，其余 400）。 */
    private static final class ValidationError {

        private final String field;
        private final String message;
        private final boolean conflict;

        private ValidationError(String field, String message, boolean conflict) {
            this.field = field;
            this.message = message;
            this.conflict = conflict;
        }
    }

    private Map<String, Object> shiftOption(String code, String defaultStart, String defaultEnd) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("label", DutySupport.shiftLabel(code));
        item.put("defaultStart", defaultStart);
        item.put("defaultEnd", defaultEnd);
        return item;
    }
}
