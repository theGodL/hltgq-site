package com.qgyun.hltgq.hltgqsite.duty.vo;

import lombok.Data;

/**
 * 交接班时间线·已交班行（kwzwga 当日记录；GET /duty/attendance/timeline 组装用）。
 */
@Data
public class HandoverTimelineRowVO {

    /** 交接班记录 id */
    private String id;

    /** 交接编号（nizxsv） */
    private String handoverNo;

    /** 实际交班时间原始值（qwiioz 定长文本） */
    private String handoverTimeRaw;

    /** 原班次开始时间原始值（tavpmi_min 定长文本） */
    private String shiftMinRaw;

    /** 原班次结束时间原始值（tavpmi_max 定长文本） */
    private String shiftMaxRaw;

    /** 交班人员姓名聚合文本（顿号连接，kwzwga_ezvhop_rel） */
    private String fromNamesRaw;

    /** 接班人员姓名聚合文本（顿号连接，kwzwga_igkyeu_rel） */
    private String toNamesRaw;
}
