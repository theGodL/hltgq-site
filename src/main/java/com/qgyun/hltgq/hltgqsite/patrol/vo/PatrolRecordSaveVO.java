package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

import java.util.List;

/**
 * 添加巡检记录请求（POST /patrol-schedule/record，列表页「添加巡检记录」弹窗提交体）。
 * <p>站点须属于该计划巡查范围；设备须属于所选站点；
 * 照片先调 /patrol-record/photo 上传，状态为已提交时至少 1 张（草稿可空）。
 */
@Data
public class PatrolRecordSaveVO {

    /** 巡检计划 id（必填） */
    private String scheduleId;

    /** 巡检站点 id（必填，须在计划巡查范围内） */
    private String siteId;

    /** 巡检设备 id（必填，须属于所选站点） */
    private String deviceId;

    /** 巡检时间（yyyy-MM-dd HH:mm；留空取当前时间） */
    private String inspTime;

    /** 巡检结果：正常/异常/隐患/缺陷/故障 或编码 #2#~#6#（必填） */
    private String result;

    /** 巡检内容（必填，≤1000 字） */
    private String content;

    /** 巡检人员用户 id（留空 = 当前登录人） */
    private String userId;

    /** 状态：草稿/已提交 或编码 #1#/#2#（留空默认已提交） */
    private String status;

    /** 现场照片 id 列表（先调 /patrol-record/photo 上传；最多 3 张；已提交时至少 1 张） */
    private List<String> photoIds;
}
