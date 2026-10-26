package com.qgyun.hltgq.hltgqsite.duty.vo;

import lombok.Data;

/**
 * 值班排班导入逐行结果（POST /duty/schedule/import 响应 rows 元素）。
 * <p>row = Excel 实际行号（表头为第 1 行）；成功时 id/dutyDate/shift 有值；
 * 失败时 message 为失败原因（成功为 null）。
 */
@Data
public class ScheduleImportRowResult {

    /** Excel 行号（表头为第 1 行，首条数据为第 2 行） */
    private int row;

    /** 该行是否成功入库 */
    private boolean ok;

    /** 成功时的排班 id（失败为 null） */
    private String id;

    /** 成功时的值班日期 yyyy-MM-dd（失败为 null） */
    private String dutyDate;

    /** 成功时的班次中文（失败为 null） */
    private String shift;

    /** 失败原因（成功为 null） */
    private String message;

    public ScheduleImportRowResult(int row) {
        this.row = row;
    }
}
