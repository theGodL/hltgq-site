package com.qgyun.hltgq.hltgqsite.duty.vo;

import lombok.Data;

/**
 * 值班记录内部行（igahxz；今日判定 / 签到幂等 / 报告与交班校验用，不直接出 JSON）。
 * <p>记录归属（2026-10 二次改造）：值班记录 = 一个人的值班记录——memberId（ahygpx）为记录归属人，
 * 签到/报告/交班/收口均按归属人判定；时间列均为 SQL to_char 定长文本（yyyy-MM-dd HH:mm:ss）；
 * membersRaw 为历史记录（ahygpx 为空）的人员展示兜底（rel 聚合，顿号姓名）。
 */
@Data
public class AttendanceRecordRowVO {

    /** 记录 id */
    private String id;

    /** 记录编号（pttmxx） */
    private String recordNo;

    /** 值班日期原始值（yaaebo 定长文本） */
    private String dutyDateRaw;

    /** 班次开始时间原始值（egkivk_min 定长文本） */
    private String shiftMinRaw;

    /** 班次结束时间原始值（egkivk_max 定长文本） */
    private String shiftMaxRaw;

    /** 旧班次时间文本（lzcjgq，新列为空时展示兜底） */
    private String legacyShift;

    /** 单位 id（cfszln，新列） */
    private String orgId;

    /** 单位名称（JOIN 单位表） */
    private String orgName;

    /** 旧单位文本（sdcgli，orgName 兜底） */
    private String legacyUnit;

    /** 带班领导 id（atfzxl） */
    private String leaderId;

    /** 带班领导姓名（JOIN 用户表） */
    private String leaderName;

    /** 记录归属人 id（ahygpx，单关联用户；改造前历史记录为空） */
    private String memberId;

    /** 记录归属人姓名（JOIN 用户表；改造前历史记录为空） */
    private String memberName;

    /** 实际开始时间原始值（jlailj 定长文本） */
    private String actualStartRaw;

    /** 实际交班时间原始值（yuyitx 定长文本） */
    private String actualEndRaw;

    /** 重大事件（xggayo；报告字段，值班中回显） */
    private String events;

    /** 值班情况综述（rdammr） */
    private String summary;

    /** 状态编码原文（ylncfw：#2# 值班中 / #3# 已完成 / #4# 异常结束） */
    private String statusCode;

    /** 值班人员姓名聚合文本（顿号连接，rel 表；历史记录展示兜底，新记录人员走 memberName） */
    private String membersRaw;
}
