package com.qgyun.hltgq.hltgqsite.sms.task;

import com.qgyun.hltgq.hltgqsite.sms.service.SmsAlarmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 告警短信补偿扫描：每 5 分钟（cron 配置 sms.alarm.compensation-cron）。
 * <p>正常链路是 mq 告警落库后回调 /sms/alarm-event（秒级）；
 * 本任务只兜底回调丢失（网络抖动/本服务重启），扫描最近
 * {@code sms.alarm.compensation-lookback-minutes}（默认 30）分钟内未发送成功的阈值告警。
 * <p>与回调共用处理链路：同 alertId 幂等、同站点同指标静默同样生效，不会重复轰炸。
 */
@Component
public class SmsAlarmCompensationTask {

    private static final Logger log = LoggerFactory.getLogger(SmsAlarmCompensationTask.class);

    @Autowired
    private SmsAlarmService smsAlarmService;

    @Scheduled(cron = "${sms.alarm.compensation-cron:0 */5 * * * ?}")
    public void runCompensation() {
        try {
            smsAlarmService.runCompensation();
        } catch (Exception e) {
            log.error("告警短信补偿扫描异常", e);
        }
    }
}
