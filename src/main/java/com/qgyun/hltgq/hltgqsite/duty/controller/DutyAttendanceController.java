package com.qgyun.hltgq.hltgqsite.duty.controller;

import com.qgyun.hltgq.hltgqsite.duty.service.DutyAttendanceService;
import com.qgyun.hltgq.hltgqsite.duty.vo.AttendanceHandoverRequest;
import com.qgyun.hltgq.hltgqsite.duty.vo.AttendanceReportRequest;
import com.qgyun.hltgq.hltgqsite.duty.vo.AttendanceStartRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 值班交接班页接口（当前值班四态、开始值班、填写报告、交班、今日动态；契约见《值班交接班接口.md》§4）。
 * <p>均以当前登录人视角；写接口仅要求登录态（不加管理员限制）。
 */
@RestController
@RequestMapping("/duty/attendance")
public class DutyAttendanceController {

    @Autowired
    private DutyAttendanceService attendanceService;

    /**
     * 当前值班卡片（四态：ongoing 值班中 / notstarted 未开始 / completed 已完成 / none 无安排）。
     * <p>ongoing 额外返回 receiverCandidates（下一班接班人员候选，供交班弹窗默认选中）。
     */
    @GetMapping("/today")
    public Map<String, Object> today() {
        return attendanceService.today();
    }

    /**
     * 开始值班（签到）：依据排班 id 为登录人生成/复用本人值班记录（值班记录 = 一个人的值班记录；
     * 仅本班带班领导/值班人员可签；幂等，重复点击复用本人已有在途记录）。
     *
     * @return {ok:true, recordId, recordNo}
     */
    @PostMapping("/start")
    public Map<String, Object> start(@RequestBody AttendanceStartRequest body) {
        return attendanceService.start(body);
    }

    /**
     * 填写值班报告（覆盖更新；仅值班中记录可写，否则 409）。
     *
     * @return {success:true}
     */
    @PostMapping("/report")
    public Map<String, Object> report(@RequestBody AttendanceReportRequest body) {
        return attendanceService.report(body);
    }

    /**
     * 交班（单事务：记录置已完成 + 创建交接班记录 + 排班联动；重复交班 409）。
     *
     * @return {success:true, handoverNo}
     */
    @PostMapping("/handover")
    public Map<String, Object> handover(@RequestBody AttendanceHandoverRequest body) {
        return attendanceService.handover(body);
    }

    /**
     * 今日交接班动态时间线（已交班/值守中/待交接合并按时刻升序）。
     *
     * @param date 日期 yyyy-MM-dd（可选，默认今天）
     * @return {date, items}
     */
    @GetMapping("/timeline")
    public Map<String, Object> timeline(@RequestParam(required = false) String date) {
        return attendanceService.timeline(date);
    }
}
