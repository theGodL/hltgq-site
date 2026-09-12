package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

/**
 * 巡查计划导入逐行结果（POST /patrol-schedule/import 响应 rows 元素）。
 * <p>row = Excel 实际行号（表头为第 1 行）；成功时 id/code 有值；
 * 失败时 message 为失败原因，成功但存在忽略项时 message 为提示文案（无则 null）。
 */
@Data
public class PatrolImportRowResult {

    /** Excel 行号（表头为第 1 行，首条数据为第 2 行） */
    private int row;

    /** 该行是否成功入库 */
    private boolean ok;

    /** 成功时的记录主键 id（失败为 null） */
    private String id;

    /** 成功时的最终计划编号（失败为 null） */
    private String code;

    /** 失败原因；成功但存在忽略项时为提示文案（无则 null） */
    private String message;

    public PatrolImportRowResult(int row) {
        this.row = row;
    }
}
