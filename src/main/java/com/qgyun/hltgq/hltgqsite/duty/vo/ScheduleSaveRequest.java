package com.qgyun.hltgq.hltgqsite.duty.vo;

import lombok.Data;

import java.util.List;

/**
 * 保存排班请求（POST /duty/schedule/save；有 id = 编辑，空 = 新增）。
 * <p>契约见《值班交接班接口.md》§3.5：时间为 yyyy-MM-dd HH:mm，结束须晚于开始（跨天允许）。
 */
@Data
public class ScheduleSaveRequest {

    /** 排班 id（空 = 新增） */
    private String id;

    /** 值班日期 yyyy-MM-dd */
    private String dutyDate;

    /** 班次编码（#1# 白班 / #jhyu# 中班 / #zsfh# 夜班） */
    private String shiftCode;

    /** 值班开始时间 yyyy-MM-dd HH:mm */
    private String timeStart;

    /** 值班结束时间 yyyy-MM-dd HH:mm */
    private String timeEnd;

    /** 所在单位 id（t_apaas_uc_org.id） */
    private String orgId;

    /** 带班领导用户 id（t_apaas_uc_user.id） */
    private String leaderId;

    /** 值班人员用户 id 数组（可为空数组 = 不安排人员） */
    private List<String> memberIds;
}
