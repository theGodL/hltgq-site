package com.qgyun.hltgq.hltgqsite.stationdata.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 站点信息导入结果（POST /station-data/import 响应体）。
 * <p>total/success/failed = 数据行总数 / 成功行数 / 失败行数（空行不计入）；
 * rows 按 Excel 实际行号升序。整体性错误（文件为空/非 xlsx/缺列/超 300 行/无数据行）走 400，不返回本结构。
 */
@Data
public class StationImportResult {

    /** 数据行总数（空行不计入） */
    private int total;

    /** 成功行数 */
    private int success;

    /** 失败行数 */
    private int failed;

    /** 逐行结果（按 Excel 行号升序） */
    private List<StationImportRowResult> rows = new ArrayList<>();
}
