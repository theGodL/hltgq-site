package com.qgyun.hltgq.hltgqsite.stationdata.vo;

import lombok.Data;

/**
 * 站点信息导入逐行结果（POST /station-data/import 响应 rows 元素）。
 * <p>row = Excel 实际行号（表头为第 1 行）；成功时 id/code 有值；
 * 失败时 message 为失败原因（成功为 null）。
 */
@Data
public class StationImportRowResult {

    /** Excel 行号（表头为第 1 行，首条数据为第 2 行） */
    private int row;

    /** 该行是否成功入库 */
    private boolean ok;

    /** 成功时的记录主键 id（失败为 null） */
    private String id;

    /** 成功时的站点编号（失败为 null） */
    private String code;

    /** 失败原因（成功为 null） */
    private String message;

    public StationImportRowResult(int row) {
        this.row = row;
    }
}
