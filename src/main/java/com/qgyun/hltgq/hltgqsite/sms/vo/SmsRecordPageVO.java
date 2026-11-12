package com.qgyun.hltgq.hltgqsite.sms.vo;

import lombok.Data;

/**
 * 短信发送记录行（运维排查页）。
 * <p>success/failed 全量留痕；templateParams 为模板变量 JSON（核对文案用）。
 */
@Data
public class SmsRecordPageVO {

    private String id;

    /** daily / alarm_rain / alarm_level / test */
    private String bizType;

    private String alertId;

    private String phone;

    private String templateCode;

    private String templateParams;

    /** success / failed / skipped（静默跳过） */
    private String sendStatus;

    private String responseCode;

    private String responseMessage;

    private String sentAt;

    private String createdAt;
}
