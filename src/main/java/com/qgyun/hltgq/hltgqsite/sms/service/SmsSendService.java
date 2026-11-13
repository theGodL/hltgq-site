package com.qgyun.hltgq.hltgqsite.sms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.sms.SmsProperties;
import com.qgyun.hltgq.hltgqsite.sms.client.AliyunSmsClient;
import com.qgyun.hltgq.hltgqsite.sms.mapper.SmsRecordMapper;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsBatchStat;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsSendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 短信发送服务：逐号码调用阿里云 + 失败重试 + 发送记录落库。
 * <p>重试策略：仅网络异常（超时/连接失败）重试，最多 2 次（间隔 1s、2s）；
 * 业务错误（isv.BUSINESS_LIMIT_CONTROL 频控、参数非法等）重试无意义，直接落失败记录。
 * <p>幂等/静默判断在调用方（SmsAlarmService）完成，本服务只负责"发"与"记账"。
 * <p>发送记录逐号码落库（成功/失败都落），供回调幂等、静默窗口、运维排查三处复用。
 */
@Service
public class SmsSendService {

    private static final Logger log = LoggerFactory.getLogger(SmsSendService.class);

    /** 网络异常最大重试次数（总尝试 = 1 + 2 次） */
    private static final int MAX_RETRY = 2;

    /**
     * 历史缺数占位（已停用）：现行策略为"取数不全不发送"（宁可少发不误发），
     * 短信变量不允许再出现"暂无"；保留常量仅供兼容引用，业务代码不得再使用。
     */
    @Deprecated
    public static final String EMPTY_TEXT = "暂无";

    @Autowired
    private SmsProperties properties;

    @Autowired
    private AliyunSmsClient smsClient;

    @Autowired
    private SmsRecordMapper recordMapper;

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 批量发送：循环号码逐个发送并落记录。
     *
     * @param phones         接收号码清单
     * @param bizType        业务类型（daily / alarm_rain / alarm_level / test）
     * @param templateCode   模板编码
     * @param templateParams 模板变量（调用方保证取数齐全，缺数不发送，不允许"暂无"）
     * @param alertId        告警 id（日汇总/测试为 null）
     * @param silenceKey     静默键（告警为 rain:{stcd} / level:{stcd}，日汇总为 null）
     */
    public SmsBatchStat sendToPhones(List<String> phones, String bizType, String templateCode,
                                     Map<String, String> templateParams, String alertId, String silenceKey) {
        if (phones == null || phones.isEmpty()) {
            log.warn("短信接收人为空，跳过发送 bizType={}", bizType);
            return SmsBatchStat.of(0, 0, 0);
        }
        if (!properties.isEnabled()) {
            log.info("短信总开关关闭（sms.enabled=false），跳过发送 bizType={} 号码数={}", bizType, phones.size());
            return SmsBatchStat.of(0, 0, 0);
        }

        String paramsJson;
        try {
            paramsJson = objectMapper.writeValueAsString(templateParams);
        } catch (Exception e) {
            paramsJson = String.valueOf(templateParams);
        }

        int successCount = 0;
        int failCount = 0;
        int retryCount = 0;
        for (String phone : phones) {
            SmsSendResult result = null;
            for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
                // paramsJson 由本方法统一序列化一次，发送与落库共用（client 不再重复序列化）
                result = smsClient.send(phone, templateCode, paramsJson);
                if (result.isSuccess() || !result.isRetryable()) {
                    break;
                }
                if (attempt < MAX_RETRY) {
                    retryCount++;
                    sleepQuietly(1000L * (attempt + 1));
                }
            }
            boolean success = result != null && result.isSuccess();
            saveRecord(phone, bizType, templateCode, paramsJson, alertId, silenceKey, result);
            if (success) {
                successCount++;
            } else {
                failCount++;
            }
        }
        log.info("短信发送完成 bizType={} template={} 成功={} 失败={} 网络重试={}",
                bizType, templateCode, successCount, failCount, retryCount);
        return SmsBatchStat.of(successCount, failCount, retryCount);
    }

    /** 落一条发送记录（成功/失败都落；异常不影响发送结果返回） */
    private void saveRecord(String phone, String bizType, String templateCode, String paramsJson,
                            String alertId, String silenceKey, SmsSendResult result) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Map<String, Object> row = new HashMap<>();
            row.put("id", shortIdGenerator.nextUUID(null));
            row.put("biz_type", bizType);
            row.put("alert_id", alertId);
            row.put("silence_key", silenceKey);
            row.put("phone", phone);
            row.put("template_code", templateCode);
            row.put("template_params", paramsJson);
            row.put("send_status", result != null && result.isSuccess() ? "success" : "failed");
            row.put("request_id", result == null ? null : result.getRequestId());
            row.put("biz_id", result == null ? null : result.getBizId());
            row.put("response_code", result == null ? null : result.getCode());
            row.put("response_message", result == null ? null : truncate(result.getMessage(), 500));
            row.put("sent_at", now);
            row.put("now", now);
            recordMapper.insertRecord(row);
        } catch (Exception e) {
            // 记录失败不阻断发送链路，仅告警日志
            log.error("短信发送记录落库失败 phone={} bizType={} err={}",
                    AliyunSmsClient.maskPhone(phone), bizType, e.toString());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
