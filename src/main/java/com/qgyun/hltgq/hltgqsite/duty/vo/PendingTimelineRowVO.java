package com.qgyun.hltgq.hltgqsite.duty.vo;

import lombok.Data;

/**
 * 交接班时间线·待交接行（当日 #zzkl# 待值班排班；GET /duty/attendance/timeline 组装用）。
 */
@Data
public class PendingTimelineRowVO {

    /** 排班 id */
    private String id;

    /** 班次编码（fjyyhu） */
    private String shiftCode;

    /** 班次开始时间原始值（tneakp_min 定长文本） */
    private String shiftMinRaw;

    /** 值班人员姓名聚合文本（顿号连接，rel 表） */
    private String membersRaw;
}
