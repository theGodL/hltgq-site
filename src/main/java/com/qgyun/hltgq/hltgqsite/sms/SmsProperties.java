package com.qgyun.hltgq.hltgqsite.sms;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 短信配置（AK/签名/模板/接收人全部走环境变量，见 application.properties 的 sms.* 段）。
 * <p>生产密钥通过 deploy.sh 注入：SMS_ALIYUN_AK_ID / SMS_ALIYUN_AK_SECRET / SMS_CALLBACK_TOKEN，
 * 代码与镜像内不落真实密钥。
 * <p>开关 {@code sms.enabled} 默认关闭：关闭时不发任何短信（任务/接口直接返回），
 * 避免开发环境误发；生产部署显式置 true。
 */
@Data
@Component
public class SmsProperties {

    /** 短信总开关（默认关闭，生产置 true） */
    @Value("${sms.enabled:false}")
    private boolean enabled;

    /** 阿里云 AccessKeyId（花凉亭账号，环境变量 SMS_ALIYUN_AK_ID） */
    @Value("${sms.access-key-id:}")
    private String accessKeyId;

    /** 阿里云 AccessKeySecret（环境变量 SMS_ALIYUN_AK_SECRET） */
    @Value("${sms.access-key-secret:}")
    private String accessKeySecret;

    /** 短信服务接入点（固定值，仅测试环境可覆盖） */
    @Value("${sms.endpoint:https://dysmsapi.aliyuncs.com}")
    private String endpoint;

    @Value("${sms.region-id:cn-hangzhou}")
    private String regionId;

    @Value("${sms.connect-timeout-ms:3000}")
    private int connectTimeoutMs;

    @Value("${sms.read-timeout-ms:5000}")
    private int readTimeoutMs;

    /** 短信签名（安徽花凉亭…管理处的官方签名，模板审核时绑定） */
    @Value("${sms.sign-name:}")
    private String signName;

    /** 模板：日汇总（平均雨量 + 8 时水位） */
    @Value("${sms.template.daily:}")
    private String templateDaily;

    /** 模板：雨量报警（${hour}小时累积雨量大于${threshold}mm的雨量站:${stcd}） */
    @Value("${sms.template.rain:}")
    private String templateRain;

    /** 模板：水位涨幅（1小时内${station_code}水位涨幅${increaseRate}当前水位${waterLevel}） */
    @Value("${sms.template.level:}")
    private String templateLevel;

    /** 日汇总接收人（逗号分隔，原运维 msg.number8 的 30 个号码） */
    @Value("${sms.receivers.daily:}")
    private String receiversDailyConfig;

    /** 告警接收人（逗号分隔：18956904399、18909665998） */
    @Value("${sms.receivers.alarm:}")
    private String receiversAlarmConfig;

    /** 告警回调鉴权 token（X-Sms-Token）；为空 = 拒绝所有回调（防裸奔） */
    @Value("${sms.callback.token:}")
    private String callbackToken;

    /** 日汇总任务 cron（每天 8:01，取 TM <= 08:00:00 的数据） */
    @Value("${sms.daily.cron:0 1 8 * * ?}")
    private String dailyCron;

    /** 同站点同指标静默窗口（分钟） */
    @Value("${sms.alarm.silence-minutes:30}")
    private int alarmSilenceMinutes;

    /** 告警补偿扫描 cron（每 5 分钟，补回调丢失） */
    @Value("${sms.alarm.compensation-cron:0 */5 * * * ?}")
    private String compensationCron;

    /** 补偿扫描回看窗口（分钟） */
    @Value("${sms.alarm.compensation-lookback-minutes:30}")
    private int compensationLookbackMinutes;

    /** 坝上站号（水位 + 雨量；新库真实 STCD，老编号 00000007 需映射） */
    @Value("${sms.station.bashang:3206400007}")
    private String stationBashang;

    /** 坝下站号（水位 + 雨量；新库真实 STCD，老编号 00000004 需映射） */
    @Value("${sms.station.baxia:320640000A}")
    private String stationBaxia;

    /** 周家河站号（水位 + 雨量；新库真实 STCD，老编号 00000001 需映射） */
    @Value("${sms.station.zhoujiahe:3206400001}")
    private String stationZhoujiahe;

    public boolean isClientConfigured() {
        return StringUtils.hasText(accessKeyId) && StringUtils.hasText(accessKeySecret)
                && StringUtils.hasText(signName);
    }

    /** 日汇总接收人清单（去空白、去重） */
    public List<String> getReceiversDaily() {
        return parsePhones(receiversDailyConfig);
    }

    /** 告警接收人清单（去空白、去重） */
    public List<String> getReceiversAlarm() {
        return parsePhones(receiversAlarmConfig);
    }

    private List<String> parsePhones(String config) {
        if (!StringUtils.hasText(config)) {
            return Collections.emptyList();
        }
        Set<String> phones = new LinkedHashSet<>();
        for (String item : config.split("[,;]")) {
            String phone = item.trim();
            if (!phone.isEmpty()) {
                phones.add(phone);
            }
        }
        return new ArrayList<>(phones);
    }
}
