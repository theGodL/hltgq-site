package com.qgyun.hltgq.hltgqsite.duty.vo;

import lombok.Data;

/**
 * 排班内部行（编辑/删除校验、开始值班复制源；不对外序列化）。
 * <p>时间列均为 SQL to_char 定长文本（yyyy-MM-dd HH:mm:ss）；
 * dutyDateRaw 为 COALESCE(idjwlq, owcvsv)，已到点纠偏与日期取值共用。
 */
@Data
public class ScheduleRowVO {

    /** 排班 id */
    private String id;

    /** 排班状态编码（peuzwi） */
    private String statusCode;

    /** 班次编码（fjyyhu） */
    private String shiftCode;

    /** 单位 id（jepbhm） */
    private String orgId;

    /** 单位名称（JOIN 单位表；历史数据为空） */
    private String orgName;

    /** 旧单位文本（ihdflq，显示兜底） */
    private String legacyUnit;

    /** 带班领导 id（alidpq） */
    private String leaderId;

    /** 带班领导姓名（JOIN 用户表；今日判定展示用） */
    private String leaderName;

    /** 值班日期（COALESCE(idjwlq, owcvsv) 定长文本） */
    private String dutyDateRaw;

    /** 值班开始时间（tneakp_min 定长文本） */
    private String tneakpMinRaw;

    /** 值班结束时间（tneakp_max 定长文本） */
    private String tneakpMaxRaw;

    /** 值班人员姓名聚合（rel 表顿号连接；selectMyTodaySchedule 专用，其他查询为 null） */
    private String memberNamesRaw;

    /** 冲突人员姓名聚合（候选人员与冲突班领导/人员名单交集的顿号连接；selectScheduleConflict 专用，其他查询为 null） */
    private String conflictNamesRaw;
}
