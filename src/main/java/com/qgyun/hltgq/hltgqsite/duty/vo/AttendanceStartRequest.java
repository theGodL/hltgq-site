package com.qgyun.hltgq.hltgqsite.duty.vo;

import lombok.Data;

/**
 * 开始值班（签到）请求（POST /duty/attendance/start；契约见《值班交接班接口.md》§4.2）。
 * <p>scheduleId 与 recordId 二选一（同时存在时 recordId 优先）：排班维度传 scheduleId（首签创建记录）；
 * 记录维度传 recordId（待签到补签直达既有记录，不依赖排班反查）。
 */
@Data
public class AttendanceStartRequest {

    /** 排班 id（与 recordId 二选一；首次签到创建记录用） */
    private String scheduleId;

    /** 值班记录 id（可选；待签到补签直达既有记录） */
    private String recordId;
}
