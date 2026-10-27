package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

/**
 * H5 开始巡检结果（POST /h5/patrol-schedule/start）。
 * <p>服务端校验计划归属与状态后返回一条可编辑草稿（站点/设备/结果/内容待后续补齐）；
 * 当前登录人在该计划下已有草稿时幂等返回最早一条，没有则新建一条；
 * 与列表行 draftRecordId 同一口径（当前登录人 + status='#1#' + 最早一条）。
 */
@Data
public class H5PatrolStartVO {

    /** 巡检记录 id（当前登录人在该计划下最早一条草稿；无草稿时新建后返回） */
    private String recordId;

    /** 巡检计划 id */
    private String scheduleId;

    /** 是否已开始巡检（恒为 true） */
    private Boolean started;

    /** 计划展示态编码（固定 #2# 进行中） */
    private String statusCode;

    /** 计划展示态名称（权威映射） */
    private String statusLabel;
}
