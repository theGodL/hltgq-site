package com.qgyun.hltgq.hltgqsite.sms.vo;

import lombok.Data;

/**
 * 告警短信处理结果（回调响应 data 段）。
 * <p>sent=true 表示至少一个接收号码发送成功；false 时 reason 说明原因
 * （已发送/静默/未启用/发送失败），mq 只需记录日志、不需要重试。
 */
@Data
public class SmsAlarmResult {

    private boolean sent;

    private String reason;

    public static SmsAlarmResult sent() {
        SmsAlarmResult result = new SmsAlarmResult();
        result.setSent(true);
        return result;
    }

    public static SmsAlarmResult skipped(String reason) {
        SmsAlarmResult result = new SmsAlarmResult();
        result.setSent(false);
        result.setReason(reason);
        return result;
    }
}
