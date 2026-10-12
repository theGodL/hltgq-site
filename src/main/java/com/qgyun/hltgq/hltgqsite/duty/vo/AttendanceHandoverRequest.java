package com.qgyun.hltgq.hltgqsite.duty.vo;

import lombok.Data;

import java.util.List;

/**
 * 交班请求（POST /duty/attendance/handover；契约见《值班交接班接口.md》§4.4）。
 */
@Data
public class AttendanceHandoverRequest {

    /** 值班记录 id（必填） */
    private String recordId;

    /** 接班人员用户 id 数组（默认取下一班排班人员，允许空数组） */
    private List<String> receiverIds;

    /** 待办移交事项（gdtjny） */
    private String matters;
}
