package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警记录
 */
@Data
public class WaterAlertVO {

    /** 主键 */
    private String id;

    /** 告警编号 */
    private String code;

    /** 告警站点（站点主键 ID） */
    private String site;

    /** 告警设备（设备主键 ID） */
    private String device;

    /** 告警内容 */
    private String content;

    /** 告警级别：#1# 一般、#2# 较重、#3# 严重、#4# 特别严重 */
    private String level;

    /** 处理状态：#1# 未确认、#2# 已确认、#3# 处理中、#4# 已关闭 */
    private String status;

    /** 发生时间 */
    private LocalDateTime time;
}
