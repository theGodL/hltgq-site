package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 巡检记录管理页行（GET /patrol-record/page）。
 * <p>记录表 t_auto_hltgq_water_inspection_record；巡检对象存平台动态列 "_d_10497_device"（设备 id 单值）。
 * <p>时间由 SQL to_char 输出 yyyy-MM-dd HH:mm（与低代码列表口径一致）；照片数量取自建照片表。
 */
@Data
public class PatrolRecordRowVO {

    /** 巡检记录主键 */
    private String id;

    /** 巡检时间（yyyy-MM-dd HH:mm） */
    private String time;

    /** 所属计划 id */
    private String scheduleId;

    /** 所属计划名称（计划已删为 null） */
    private String scheduleTitle;

    /** 巡检站点 id（记录表 site） */
    private String siteId;

    /** 巡检站点名称（站点档案关联；站点已删为 null） */
    private String siteName;

    /** 巡检对象/设备 id（动态列 "_d_10497_device"） */
    private String deviceId;

    /** 巡检对象名称（设备表关联；设备已删为 null） */
    private String deviceName;

    /** 巡检人员用户 id（记录表 "user"） */
    private String userId;

    /** 巡检人员姓名（用户表关联） */
    private String userName;

    /** 巡检结果编码（#2# 正常 / #3# 异常 / #4# 隐患 / #5# 缺陷 / #6# 故障） */
    private String resultCode;

    /** 巡检结果名称（权威映射，Service 填充） */
    private String resultLabel;

    /** 巡检说明 */
    private String content;

    /** 状态编码（#1# 草稿 / #2# 已提交） */
    private String statusCode;

    /** 状态名称（权威映射，Service 填充） */
    private String statusLabel;

    /** 现场照片数量（自建照片表；H5 走平台文件的不计数） */
    private Integer photoCount;

    /** 关联问题数量（问题表 abqezf） */
    private Integer issueCount;
}
