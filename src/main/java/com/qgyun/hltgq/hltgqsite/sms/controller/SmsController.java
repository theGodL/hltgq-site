package com.qgyun.hltgq.hltgqsite.sms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.auth.RequireAdmin;
import com.qgyun.hltgq.hltgqsite.sms.SmsProperties;
import com.qgyun.hltgq.hltgqsite.sms.mapper.SmsRecordMapper;
import com.qgyun.hltgq.hltgqsite.sms.service.SmsAlarmService;
import com.qgyun.hltgq.hltgqsite.sms.service.SmsDailySummaryService;
import com.qgyun.hltgq.hltgqsite.sms.service.SmsSendService;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsAlarmEventRequest;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsAlarmResult;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsBatchStat;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsRecordPageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信接口：mq 告警回调（白名单 + X-Sms-Token）、测试发送（管理员）、发送记录分页。
 * <p>回调接口不走登录会话（mq 无 session），鉴权靠 {@code sms.callback.token}
 * （环境变量 SMS_CALLBACK_TOKEN）；token 未配置时拒绝所有回调（防裸奔）。
 * <p>回调响应固定 {code:0, data:{sent, reason}}；mq 只按 HTTP 200 且 code=0 视为调用成功。
 */
@RestController
@RequestMapping("/sms")
public class SmsController {

    private static final Logger log = LoggerFactory.getLogger(SmsController.class);

    @Autowired
    private SmsProperties properties;

    @Autowired
    private SmsAlarmService smsAlarmService;

    @Autowired
    private SmsSendService smsSendService;

    @Autowired
    private SmsRecordMapper recordMapper;

    @Autowired
    private SmsDailySummaryService smsDailySummaryService;

    /**
     * 手动触发日汇总（仅系统管理员）：与 08:01 定时任务同一链路、同一接收人清单。
     * <p>用途：部署后立即验证取数是否正确（不必等次日 8 点）、数据修正后补发。
     * <p>⚠ 会真实向全部日汇总接收人发送短信，操作前确认取数日志无误。
     */
    @PostMapping("/daily/send")
    @RequireAdmin
    public Map<String, Object> triggerDailySummary() {
        log.info("手动触发日汇总短信（管理员）");
        smsDailySummaryService.runDailySummary();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("triggered", true);
        body.put("note", "已按日汇总链路执行，取数与发送结果见服务日志");
        return body;
    }

    /**
     * mq 告警回调：告警落库后调用，本系统负责组装变量、幂等去重、发送、记录。
     * <p>需在 auth.white-list 放行（application.properties），鉴权走 X-Sms-Token。
     */
    @PostMapping("/alarm-event")
    public ResponseEntity<Map<String, Object>> alarmEvent(
            @RequestHeader(value = "X-Sms-Token", required = false) String token,
            @RequestBody SmsAlarmEventRequest request) {
        if (!StringUtils.hasText(properties.getCallbackToken()) || !constantTimeEquals(token, properties.getCallbackToken())) {
            log.warn("短信告警回调鉴权失败（token 缺失或不匹配）alertId={}",
                    request == null ? null : request.getAlertId());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", 403);
            body.put("message", "鉴权失败");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }
        SmsAlarmResult result = smsAlarmService.handleCallback(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sent", result.isSent());
        data.put("reason", result.getReason());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    /**
     * 测试发送（部署后联调验证，仅系统管理员）：给指定号码发一条样例短信。
     *
     * @param phone 接收号码（必填）
     * @param type  daily=日汇总样例 / rain=雨量告警样例 / level=水位涨幅样例
     */
    @PostMapping("/test")
    @RequireAdmin
    public Map<String, Object> test(@RequestParam String phone,
                                    @RequestParam(defaultValue = "daily") String type) {
        String bizType;
        String templateCode;
        Map<String, String> params = new HashMap<>();
        if ("rain".equals(type)) {
            bizType = "test";
            templateCode = properties.getTemplateRain();
            params.put("hour", "1");
            params.put("threshold", "30");
            params.put("stcd", "周家河12.5");
        } else if ("level".equals(type)) {
            bizType = "test";
            templateCode = properties.getTemplateLevel();
            params.put("station_code", "周家河");
            params.put("increaseRate", "0.12");
            params.put("waterLevel", "88.50");
        } else {
            bizType = "test";
            templateCode = properties.getTemplateDaily();
            params.put("time", LocalDate.now().minusDays(1).toString());
            params.put("hour", "8");
            params.put("rain", "12.34");
            params.put("bashang", "95.13");
            params.put("baxia", "88.20");
            params.put("zhoujiahe", "76.50");
        }
        SmsBatchStat stat = smsSendService.sendToPhones(Collections.singletonList(phone),
                bizType, templateCode, params, null, null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sent", stat.getSuccessCount() > 0);
        body.put("params", params);
        body.put("stat", stat);
        return body;
    }

    /**
     * 发送记录分页（运维排查：看某个号/某类短信是否发出、失败原因）。
     * <p>仅系统管理员（记录含接收人手机号明文，与 /sms/test 同口径保护）。
     */
    @GetMapping("/record/page")
    @RequireAdmin
    public Page<SmsRecordPageVO> recordPage(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        // 钳制分页参数：下界防负 OFFSET，上界防 (page-1)*size 溢出为负
        long safePage = Math.min(Math.max(page, 1), 100_000L);
        long safeSize = Math.min(Math.max(size, 1), 200);
        long total = recordMapper.countRecordPage(bizType, phone, startTime, endTime);
        List<SmsRecordPageVO> records = total == 0
                ? Collections.emptyList()
                : recordMapper.selectRecordPage(bizType, phone, startTime, endTime, (int) safeSize, (safePage - 1) * safeSize);
        Page<SmsRecordPageVO> result = new Page<>(safePage, safeSize);
        result.setTotal(total);
        result.setRecords(records);
        return result;
    }

    /** 常量时间比较（防时序攻击；内部接口也按安全惯例实现） */
    private static boolean constantTimeEquals(String provided, String expected) {
        if (provided == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
