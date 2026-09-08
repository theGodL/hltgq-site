package com.qgyun.hltgq.hltgqsite.h5.controller;

import com.qgyun.hltgq.hltgqsite.auth.UnauthorizedException;
import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsite.h5.service.H5StatService;
import com.qgyun.hltgq.hltgqsite.h5.service.MessageService;
import com.qgyun.hltgq.hltgqsite.h5.vo.InspectionResultStatsVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessagePageVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessageReadRequest;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessageSummaryVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.PatrolIssueMonthlyVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * H5 移动端接口：巡查及问题统计 / 维修养护统计 / 消息中心。
 * <p>全部接口需登录态（拦截器校验），当前登录人取自 {@link UserContextHolder}，
 * 消息中心未读与已读均为当前登录人视角。
 */
@RestController
@RequestMapping("/h5")
public class H5Controller {

    private final H5StatService statService;
    private final MessageService messageService;

    public H5Controller(H5StatService statService, MessageService messageService) {
        this.statService = statService;
        this.messageService = messageService;
    }

    /**
     * 巡查及问题统计（柱状图）：近 12 个月每月巡查次数与问题数（问题按风险等级拆三档）。
     *
     * @param endMonth 统计基准月 yyyy-MM（默认当前月），返回该月及往前 11 个月
     */
    @GetMapping("/patrol-issue-monthly")
    public PatrolIssueMonthlyVO patrolIssueMonthly(
            @RequestParam(required = false) String endMonth) {
        return statService.patrolIssueMonthly(endMonth);
    }

    /**
     * 维修养护统计（饼状图）：已提交巡检记录按巡检结果 result 分组，固定 6 档。
     *
     * @param startTime 巡检时间起（含）yyyy-MM-dd HH:mm:ss，可选
     * @param endTime   巡检时间止（含）yyyy-MM-dd HH:mm:ss，可选
     */
    @GetMapping("/inspection-result-stats")
    public InspectionResultStatsVO inspectionResultStats(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return statService.inspectionResultStats(startTime, endTime);
    }

    /**
     * 未读消息数汇总（角标）：三类未读数基于当前登录人接收记录。
     */
    @GetMapping("/message/summary")
    public MessageSummaryVO messageSummary() {
        return messageService.summary(currentUserId());
    }

    /**
     * 消息分页列表：按类型返回对应结构行。
     *
     * @param messageType 消息类型：#1# 告警 / #2# 举报投诉 / #3# 意见征集（URL 中 # 转义 %23）
     * @param page        页码，默认 1
     * @param size        每页条数，默认 10
     */
    @GetMapping("/message/page")
    public MessagePageVO messagePage(
            @RequestParam String messageType,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return messageService.page(currentUserId(), messageType, page, size);
    }

    /**
     * 标记已读（支持批量，幂等）。
     */
    @PostMapping("/message/read")
    public Map<String, Object> messageRead(@RequestBody MessageReadRequest request) {
        messageService.read(currentUserId(), request.getMessageType(), request.getMessageIds());
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    /**
     * 该类型全部标记已读。
     */
    @PostMapping("/message/read-all")
    public Map<String, Object> messageReadAll(@RequestBody MessageReadRequest request) {
        messageService.readAll(currentUserId(), request.getMessageType());
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    /** 当前登录人主键（auth 开关关闭或未经过拦截器时为 null → 401） */
    private String currentUserId() {
        UserContext user = UserContextHolder.currentUser();
        if (user == null || user.getUserId() == null) {
            throw new UnauthorizedException("未登录");
        }
        return user.getUserId();
    }
}
