package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警分页查询结果（全局告警列表）
 * <p>SQL 列别名与本类属性同名（map-underscore-to-camel-case=false 下 MyBatis 依赖同名映射）。
 */
@Data
public class AlertPageVO {

    /** 告警主键 */
    private String id;

    /** 告警编号 */
    private String code;

    /** 站点 ID（告警表 site = 站点表主键） */
    private String siteId;

    /** 站点名称（站点表 zzkaec，站点被删时可为 null） */
    private String siteName;

    /** 设备 ID（告警表 device = 设备表主键） */
    private String deviceId;

    /** 设备名称（设备表 name，如"南山寺节制闸1#"，设备被删时可为 null） */
    private String deviceName;

    /** 告警时间 */
    private LocalDateTime time;

    /** 告警级别：#1# 一般、#2# 较重、#3# 严重、#4# 特别严重 */
    private String level;

    /** 告警内容 */
    private String content;

    /** 处理状态：#1# 未确认、#2# 已确认、#3# 处理中、#4# 已关闭 */
    private String status;
}
