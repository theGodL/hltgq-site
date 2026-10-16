package com.qgyun.hltgq.hltgqsite.duty.vo;

import lombok.Data;

/**
 * 交接班时间线·值守中行（当日 #2# 值班记录；GET /duty/attendance/timeline 组装用）。
 */
@Data
public class OngoingTimelineRowVO {

    /** 值班记录 id */
    private String id;

    /** 实际开始时间原始值（jlailj 定长文本） */
    private String actualStartRaw;

    /** 班次开始时间原始值（egkivk_min 定长文本） */
    private String shiftMinRaw;

    /** 班次结束时间原始值（egkivk_max 定长文本） */
    private String shiftMaxRaw;

    /** 旧班次时间文本（lzcjgq，新列为空时展示兜底） */
    private String legacyShift;

    /** 单位 id（cfszln；用于反查排班取本班全员名册） */
    private String orgKey;

    /** 组内各记录人员聚合（新记录 = 归属人姓名；历史记录回退 rel 人员），顿号连接 */
    private String membersRaw;
}
