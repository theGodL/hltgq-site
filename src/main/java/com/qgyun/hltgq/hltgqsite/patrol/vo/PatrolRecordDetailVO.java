package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

import java.util.List;

/**
 * 巡检记录详情（GET /patrol-record/detail；查看弹窗 + 编辑页回填共用）。
 * <p>字段口径同列表行，另含照片列表（自建表，按 nature_order）与关联问题（问题表 abqezf）。
 */
@Data
public class PatrolRecordDetailVO {

    /** 巡检记录主键 */
    private String id;

    /** 巡检时间（yyyy-MM-dd HH:mm；编辑页转 datetime-local 用） */
    private String time;

    /** 所属计划 id */
    private String scheduleId;

    /** 所属计划名称 */
    private String scheduleTitle;

    /** 巡检站点 id */
    private String siteId;

    /** 巡检站点名称 */
    private String siteName;

    /** 巡检对象/设备 id */
    private String deviceId;

    /** 巡检对象名称 */
    private String deviceName;

    /** 巡检人员用户 id */
    private String userId;

    /** 巡检人员姓名 */
    private String userName;

    /** 巡检结果编码 */
    private String resultCode;

    /** 巡检结果名称（Service 填充） */
    private String resultLabel;

    /** 巡检说明 */
    private String content;

    /** 成本金额（szeedc，2 位小数） */
    private Double cost;

    /** 状态编码（#1# 草稿 / #2# 已提交） */
    private String statusCode;

    /** 状态名称（Service 填充） */
    private String statusLabel;

    /** 现场照片（自建表，按提交时顺序；url 为站点内相对路径） */
    private List<PatrolRecordPhotoItemVO> photos;

    /** 关联问题（问题表 abqezf = 本记录，按发现时间倒序） */
    private List<PatrolRecordIssueItemVO> issues;
}
