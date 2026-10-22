package com.qgyun.hltgq.hltgqsite.h5.vo;

import lombok.Data;

/**
 * H5 开始巡检结果（POST /h5/patrol-schedule/start）。
 * <p>服务端校验计划归属与状态后落巡检记录（草稿占位，站点/设备/结果/内容待后续补齐）；
 * 计划下已有记录时幂等返回原记录，不重复写入；返回后计划在列表展示为「进行中」。
 */
@Data
public class H5PatrolStartVO {

    /** 巡检记录 id（本次写入的草稿记录；已开始时为计划下最早一条记录） */
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
