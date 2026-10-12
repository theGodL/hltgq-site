package com.qgyun.hltgq.hltgqsite.duty.vo;

import lombok.Data;

/**
 * 填写值班报告请求（POST /duty/attendance/report；契约见《值班交接班接口.md》§4.3）。
 * <p>events 与 summary 至少一项非空；重复提交为覆盖更新。
 */
@Data
public class AttendanceReportRequest {

    /** 值班记录 id（必填） */
    private String recordId;

    /** 重大事件记载（xggayo） */
    private String events;

    /** 值班情况综述（rdammr，≤800 字） */
    private String summary;
}
