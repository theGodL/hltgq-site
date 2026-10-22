package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 巡检记录行（GET /patrol-schedule/{id}/records，列表页「查看巡检记录」弹窗）。
 * <p>记录表为 t_auto_hltgq_water_inspection_record；巡检对象存平台动态列 "_d_10497_device"（设备 id 单值）。
 */
@Data
public class PatrolRecordVO {

    /** 巡检记录主键 */
    private String id;

    /** 巡检时间 */
    private LocalDateTime time;

    /** 巡检站点 id（记录表 site） */
    private String siteId;

    /** 巡检站点名称（站点档案关联；站点已删为 null） */
    private String siteName;

    /** 巡检设备 id（动态列 "_d_10497_device"） */
    private String deviceId;

    /** 巡检设备名称（设备表关联；设备已删为 null） */
    private String deviceName;

    /** 巡检人员用户 id（记录表 "user"） */
    private String userId;

    /** 巡检人员姓名（用户表关联） */
    private String userName;

    /** 巡检结果编码（#2# 正常 / #3# 异常 / #4# 隐患 / #5# 缺陷 / #6# 故障） */
    private String resultCode;

    /** 巡检结果名称（权威映射） */
    private String resultLabel;

    /** 巡检内容 */
    private String content;

    /** 状态编码（#1# 草稿 / #2# 已提交） */
    private String statusCode;

    /** 状态名称（权威映射） */
    private String statusLabel;
}
