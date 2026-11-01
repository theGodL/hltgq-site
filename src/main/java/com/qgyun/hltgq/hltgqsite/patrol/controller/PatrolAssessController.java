package com.qgyun.hltgq.hltgqsite.patrol.controller;

import com.qgyun.hltgq.hltgqsite.patrol.service.PatrolAssessService;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolAssessRequest;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolAssessVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 巡查考核接口（patrol-plan-list.html 的「考核 / 查看考核」弹窗）。
 *
 * <p>与计划列表同前缀 /patrol-schedule，方法路径独立：
 * <ul>
 *   <li>GET /assess-preview：考核弹窗预览，返回自动项实时统计</li>
 *   <li>POST /assess：提交考核（仅 3 项手工，自动项后端重算并落快照）</li>
 *   <li>GET /assess：查看考核（读提交时快照）</li>
 * </ul>
 * <p>约束：仅已完成计划可考核；一计划一考核，重复提交返回 400；考核人取当前登录人。
 */
@RestController
@RequestMapping("/patrol-schedule")
public class PatrolAssessController {

    @Autowired
    private PatrolAssessService assessService;

    /** 考核弹窗预览：自动项实时统计（得分/等级/结论为空；已考核返回 assessed=true）。 */
    @GetMapping("/assess-preview")
    public PatrolAssessVO preview(@RequestParam String scheduleId) {
        return assessService.preview(scheduleId);
    }

    /** 提交考核：仅「考核得分 / 考核等级 / 考核结论」采用请求值，其余自动项后端重算。 */
    @PostMapping("/assess")
    public PatrolAssessVO submit(@RequestBody PatrolAssessRequest request) {
        return assessService.submit(request);
    }

    /** 查看考核：读提交时快照，未考核返回 400。 */
    @GetMapping("/assess")
    public PatrolAssessVO detail(@RequestParam String scheduleId) {
        return assessService.detail(scheduleId);
    }
}
