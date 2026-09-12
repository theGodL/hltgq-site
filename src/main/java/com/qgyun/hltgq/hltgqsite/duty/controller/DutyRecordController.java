package com.qgyun.hltgq.hltgqsite.duty.controller;

import com.qgyun.hltgq.hltgqsite.duty.service.DutyRecordService;
import com.qgyun.hltgq.hltgqsite.duty.vo.DutyRecordVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 值班记录查询接口（/duty）。
 * <p>筛选：值班日期 + 班次时间 + 所在单位 + 人员（值班人员/带班领导）；
 * 枚举字段返回中文并附编码原文（权威值）；实时查询无缓存；走现有登录会话鉴权。
 */
@RestController
@RequestMapping("/duty")
public class DutyRecordController {

    @Autowired
    private DutyRecordService dutyRecordService;

    /**
     * 值班记录列表（按值班日期倒序，裸 List）。
     *
     * @param date   值班日期 yyyy-MM-dd（含当日），可选
     * @param shift  班次时间模糊，可选
     * @param unit   所在单位模糊，可选
     * @param person 人员：姓名或用户 id（匹配值班人员/带班领导），可选
     */
    @GetMapping("/list")
    public List<DutyRecordVO> list(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(required = false) String shift,
            @RequestParam(required = false) String unit,
            @RequestParam(required = false) String person) {
        return dutyRecordService.list(date, shift, unit, person);
    }
}
