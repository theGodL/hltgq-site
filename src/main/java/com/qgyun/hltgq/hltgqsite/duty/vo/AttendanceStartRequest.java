package com.qgyun.hltgq.hltgqsite.duty.vo;

import lombok.Data;

/**
 * 开始值班（签到）请求（POST /duty/attendance/start；契约见《值班交接班接口.md》§4.2）。
 */
@Data
public class AttendanceStartRequest {

    /** 排班 id（必填） */
    private String scheduleId;
}
