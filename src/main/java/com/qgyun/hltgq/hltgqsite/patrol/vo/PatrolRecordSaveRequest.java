package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

import java.util.List;

/**
 * 巡检记录新增/编辑入参（POST /patrol-record/create、/update）。
 * <p>字段口径：time/patrol_schedule/site/device/result/site_image 为必填、content/巡检人员非必填；
 * 平台多值字段（imjjpx 可选站点、tzxlun 问题子表）不在此入参内（imjjpx 由计划范围推导，问题走新增问题页）。
 */
@Data
public class PatrolRecordSaveRequest {

    /** 记录 id（编辑必填） */
    private String id;

    /** 巡检时间（yyyy-MM-dd HH:mm 或 yyyy-MM-dd HH:mm:ss / 带 T） */
    private String time;

    /** 所属计划 id（仅已发布 #2# 计划可选；编辑时允许保持原计划） */
    private String scheduleId;

    /** 巡检站点 id（须在计划巡查范围内；计划未配范围时不限） */
    private String siteId;

    /** 巡检对象/设备 id（须属于所选站点） */
    private String deviceId;

    /** 巡检人员用户 id（可空，默认当前登录人） */
    private String userId;

    /** 巡检结果：正常/异常/隐患/缺陷/故障 或编码 */
    private String result;

    /** 巡检说明（非必填） */
    private String content;

    /** 成本金额（非必填，2 位小数） */
    private Double cost;

    /** 现场照片 id 列表（1~3 张，顺序即展示顺序） */
    private List<String> photoIds;
}
