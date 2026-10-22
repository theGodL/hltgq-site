package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

import java.util.List;

/**
 * 巡检记录上下文（新增问题页回填用）：站点/设备/时间以巡检记录为准。
 * <p>站点必填：记录已带站点时 siteLocked=true（前端只读、提交强校验）；记录缺站点
 * （开始巡检占位草稿且未补录）属数据异常，页面与 create 均拒绝上报。
 */
@Data
public class PatrolIssueRecordContextVO {

    /** 巡检记录 id */
    private String recordId;

    /** 所属巡检计划 id（用于取巡查范围） */
    private String scheduleId;

    /** 记录站点 id（可能为空：开始巡检占位草稿） */
    private String siteId;

    /** 记录站点名称 */
    private String siteName;

    /** 记录设备 id（可能为空） */
    private String deviceId;

    /** 记录设备名称 */
    private String deviceName;

    /** 巡检时间（yyyy-MM-dd HH:mm:ss） */
    private String recordTime;

    /** 站点是否锁定（记录已带站点 → 问题站点必须一致；false 表示记录缺站点，数据异常） */
    private boolean siteLocked;

    /** 计划巡查范围站点（供 H5 补录巡检记录时参考，不用于问题站点选择） */
    private List<PatrolSiteVO> sites;
}
