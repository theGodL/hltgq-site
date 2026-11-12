package com.qgyun.hltgq.hltgqsite.sms.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 补偿扫描候选告警行（阈值告警 + 站点/设备信息）。
 * <p>用于判定告警指标（雨量/水位）与取阈值；实际发送的参数组装与回调链路共用。
 */
@Data
public class SmsCompensationAlertVO {

    private String alertId;

    private LocalDateTime alertTime;

    /** 站点档案主键（阈值表 site 列） */
    private String siteId;

    /** 站点编号（iofhpi） */
    private String stcd;

    private String siteName;

    /** 告警关联设备名称（雨量计/水位计等；指标判定首选依据） */
    private String deviceName;
}
