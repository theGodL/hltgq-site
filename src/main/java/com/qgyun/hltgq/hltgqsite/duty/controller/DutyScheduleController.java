package com.qgyun.hltgq.hltgqsite.duty.controller;

import com.qgyun.hltgq.hltgqsite.duty.service.DutyAttendanceService;
import com.qgyun.hltgq.hltgqsite.duty.service.DutyScheduleService;
import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleDetailVO;
import com.qgyun.hltgq.hltgqsite.duty.vo.ScheduleSaveRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 值班管理页接口（今日判定、排班列表/详情/选项/保存/删除；契约见《值班交接班接口.md》§3）。
 * <p>含登录值班提醒 /duty/login-remind（契约 §4.6，实现复用值班交接班服务四态判定）
 * 与取号 /duty/next-no（契约 §3.8，编号生成与签到/交班同源互斥）。
 * <p>权限：仅要求登录态（与出入库同口径）；/duty/list 值班记录列表为兼容保留接口，不受影响。
 */
@RestController
@RequestMapping("/duty")
public class DutyScheduleController {

    @Autowired
    private DutyScheduleService scheduleService;

    @Autowired
    private DutyAttendanceService attendanceService;

    /**
     * 今日值班判定（签到提醒弹窗；以当前登录人视角，未命中返回 {onDuty:false}）。
     */
    @GetMapping("/today-status")
    public Map<String, Object> todayStatus() {
        return scheduleService.todayStatus();
    }

    /**
     * 登录值班提醒：供人员登录后调用，判断今天是否有我的值班；有则返回班次/时间段/带班领导等
     * 卡片数据（四态），前端据 status 弹窗并引导至值班交接班页完成签到、填写报告、交班。
     *
     * @return {hasDuty:false} 或 {hasDuty:true, ...今日卡片（结构同 /duty/attendance/today）}
     */
    @GetMapping("/login-remind")
    public Map<String, Object> loginRemind() {
        return attendanceService.loginRemind();
    }

    /**
     * 取号：查询下一个可用编号（record=值班记录 ZBJL-，默认；handover=交接班 JJB-）。
     * <p>与签到/交班内部编号生成同源互斥（单实例不重号），供外部项目/页面在新增记录前取号；
     * 本系统签到/交班响应已含真实编号，页面无需调用。
     *
     * @param type 编号类型 record/handover（可选，默认 record）
     * @param date 日期 yyyy-MM-dd（可选，默认今天）
     * @return {type, date, no}
     */
    @GetMapping("/next-no")
    public Map<String, Object> nextNo(@RequestParam(required = false) String type,
                                      @RequestParam(required = false) String date) {
        return attendanceService.nextNo(type, date);
    }

    /**
     * 排班列表分页（查询条件：值班日期/班次/状态/单位/人员关键词，均为可选）。
     *
     * @param page    页码（默认 1）
     * @param size    每页条数（默认 10，上限 100）
     * @param date    值班日期 yyyy-MM-dd（精确到当日，可选）
     * @param shift   班次：编码（#1# 等）或中文（白班）均可，可选
     * @param status  排班状态中文（未开始/待值班/值班中/已完成），可选
     * @param org     单位：单位 id 或名称模糊，可选
     * @param keyword 人员关键词：值班人员/带班领导姓名模糊，可选
     * @return total / page / size / pages / records
     */
    @GetMapping("/schedule/list")
    public Map<String, Object> scheduleList(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String date,
                                            @RequestParam(required = false) String shift,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String org,
                                            @RequestParam(required = false) String keyword) {
        return scheduleService.list(page, size, date, shift, status, org, keyword);
    }

    /**
     * 排班详情（编辑/查看抽屉回显）。
     *
     * @param id 排班 id（必填）
     */
    @GetMapping("/schedule/detail")
    public ScheduleDetailVO scheduleDetail(@RequestParam String id) {
        return scheduleService.detail(id);
    }

    /**
     * 下拉选项（班次含表单默认时段 + 单位 + 人员，新增/编辑排班抽屉数据源）。
     */
    @GetMapping("/options")
    public Map<String, Object> options() {
        return scheduleService.options();
    }

    /**
     * 保存排班（有 id=编辑，空=新增；仅未开始/待值班可编辑，否则 409）。
     *
     * @return 保存后的完整详情（结构同详情接口）
     */
    @PostMapping("/schedule/save")
    public ScheduleDetailVO scheduleSave(@RequestBody ScheduleSaveRequest body) {
        return scheduleService.save(body);
    }

    /**
     * 保存前数据校验（不落库）：与保存同规则同文案，一次性返回全部问题，供前端标记到对应输入项。
     *
     * @return {valid:true} 或 {valid:false, errors:[{field, message}]}
     */
    @PostMapping("/schedule/verify")
    public Map<String, Object> scheduleVerify(@RequestBody(required = false) ScheduleSaveRequest body) {
        return scheduleService.verify(body);
    }

    /**
     * 删除排班（仅未开始/待值班可删除；同步清理值班人员关系数据）。
     *
     * @param id 排班 id（必填）
     */
    @DeleteMapping("/schedule/{id}")
    public Map<String, Object> scheduleDelete(@PathVariable String id) {
        return scheduleService.delete(id);
    }
}
