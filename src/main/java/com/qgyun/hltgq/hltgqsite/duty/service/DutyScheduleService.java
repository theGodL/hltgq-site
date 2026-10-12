package com.qgyun.hltgq.hltgqsite.duty.service;

import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.duty.DutyConflictException;
import com.qgyun.hltgq.hltgqsite.duty.DutySupport;
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
 * 值班排班服务（值班管理页：#1 今日判定、#2 列表、#3 详情、#4 选项、#5 保存、#6 删除）。
 * <p>读：新列优先 + 旧列兜底，「待值班」读时纠偏不落库（§3.1）；
 * 写：主写当前模型字段并兼容双写旧列（保 H5 值班提醒），编辑/删除仅限未开始（存储 #1#）排班。
 */
@Service
public class DutyScheduleService {

    @Autowired
    private DutyScheduleMapper mapper;

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
     * 保存排班（新增/编辑）：校验必填与合法性 → 写主表 + 重写值班人员关系（编辑先删后插）。
     * <p>编辑仅限未开始（存储 #1#），否则 409；新增默认 peuzwi=#1#。
     */
    @Transactional(rollbackFor = Exception.class)
    public ScheduleDetailVO save(ScheduleSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String operator = DutySupport.requireOperator();

        String dutyDate = DutySupport.normalizeDay(request.getDutyDate(), "值班日期");
        if (dutyDate == null) {
            throw new IllegalArgumentException("值班日期不能为空");
        }
        LocalDateTime timeStart = DutySupport.parseMinute(request.getTimeStart(), "值班开始时间");
        LocalDateTime timeEnd = DutySupport.parseMinute(request.getTimeEnd(), "值班结束时间");
        if (timeStart == null || timeEnd == null) {
            throw new IllegalArgumentException("值班时间格式应为 yyyy-MM-dd HH:mm");
        }
        if (!timeEnd.isAfter(timeStart)) {
            throw new IllegalArgumentException("值班结束时间必须晚于开始时间");
        }
        String shiftCode = DutySupport.shiftCode(request.getShiftCode());
        if (shiftCode == null) {
            throw new IllegalArgumentException("班次编码不合法");
        }
        String orgId = DutySupport.requireText(request.getOrgId(), "所在单位不能为空");
        String orgName = mapper.selectOrgNameById(orgId);
        if (orgName == null) {
            throw new IllegalArgumentException("所在单位不存在或已删除");
        }
        String leaderId = DutySupport.requireText(request.getLeaderId(), "带班领导不能为空");
        if (mapper.selectUserNameById(leaderId) == null) {
            throw new IllegalArgumentException("带班领导不存在");
        }
        List<String> memberIds = DutySupport.distinctIds(request.getMemberIds());
        if (!memberIds.isEmpty() && mapper.selectExistingUserIds(memberIds).size() < memberIds.size()) {
            throw new IllegalArgumentException("值班人员不存在或已删除");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dutyDateAt = LocalDate.parse(dutyDate).atStartOfDay();
        String shiftTimeText = DutySupport.fmtShiftTime(timeStart, timeEnd);
        String scheduleId = DutySupport.trim(request.getId());
        if (scheduleId == null) {
            scheduleId = shortIdGenerator.nextUUID(null);
            mapper.insertSchedule(scheduleId, corpCode, operator, now, dutyDateAt, timeStart, timeEnd,
                    shiftCode, orgId, leaderId, shiftTimeText, orgName);
        } else {
            ScheduleRowVO row = mapper.selectScheduleRow(scheduleId);
            if (row == null) {
                throw new IllegalArgumentException("排班不存在或已删除");
            }
            if (!DutySupport.SCHEDULE_STATUS_NOT_STARTED.equals(row.getStatusCode())) {
                throw new DutyConflictException("仅未开始的排班可编辑");
            }
            mapper.updateSchedule(scheduleId, operator, now, dutyDateAt, timeStart, timeEnd,
                    shiftCode, orgId, leaderId, shiftTimeText, orgName);
            mapper.deleteMembers(scheduleId);
        }
        writeMembers(scheduleId, memberIds, operator, now);
        return fillDetail(mapper.selectScheduleDetail(scheduleId));
    }

    /** 删除排班（仅未开始；同步清理值班人员关系）。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> delete(String id) {
        DutySupport.requireOperator();
        String scheduleId = DutySupport.requireText(id, "排班不存在或已删除");
        ScheduleRowVO row = mapper.selectScheduleRow(scheduleId);
        if (row == null) {
            throw new IllegalArgumentException("排班不存在或已删除");
        }
        if (!DutySupport.SCHEDULE_STATUS_NOT_STARTED.equals(row.getStatusCode())) {
            throw new DutyConflictException("仅未开始的排班可删除");
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
        out.put("status", DutySupport.displayScheduleStatus(row.getStatusCode(), row.getDutyDateRaw()));
        out.put("name", mapper.selectUserNameById(userId));
        out.put("shift", DutySupport.shiftLabel(row.getShiftCode()));
        out.put("shiftTime", DutySupport.fmtShiftTime(row.getTneakpMinRaw(), row.getTneakpMaxRaw()));
        out.put("leader", row.getLeaderName());
        return out;
    }

    // ==================== 私有工具 ====================

    /** 列表行转换：原始列 → 对外字段（日期/班次/时间文本/单位兜底/状态纠偏/成员数组）。 */
    private void fillListRow(ScheduleListRowVO row) {
        row.setDate(DutySupport.fmtDate(row.getDutyDateRaw()));
        row.setShift(DutySupport.shiftLabel(row.getShiftCode()));
        row.setShiftTime(DutySupport.fmtShiftTime(row.getTneakpMinRaw(), row.getTneakpMaxRaw()));
        row.setTimeStart(DutySupport.fmtMinute(row.getTneakpMinRaw()));
        row.setTimeEnd(DutySupport.fmtMinute(row.getTneakpMaxRaw()));
        row.setOrg(row.getOrgName() != null ? row.getOrgName() : row.getLegacyUnit());
        row.setStatus(DutySupport.displayScheduleStatus(row.getStatusCode(), row.getDutyDateRaw()));
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
        vo.setStatus(DutySupport.displayScheduleStatus(vo.getStatusCode(), vo.getDutyDateRaw()));
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

    private Map<String, Object> shiftOption(String code, String defaultStart, String defaultEnd) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("label", DutySupport.shiftLabel(code));
        item.put("defaultStart", defaultStart);
        item.put("defaultEnd", defaultEnd);
        return item;
    }
}
