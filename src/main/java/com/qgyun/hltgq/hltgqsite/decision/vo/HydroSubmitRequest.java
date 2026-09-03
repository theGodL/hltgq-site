package com.qgyun.hltgq.hltgqsite.decision.vo;

import lombok.Data;

/**
 * 防洪抗旱决策提交请求（POST /flood-drought/hydro）。
 */
@Data
public class HydroSubmitRequest {

    /** 起始日期 yyyy-MM-dd（含），必填 */
    private String startDate;

    /** 截止日期 yyyy-MM-dd（含），必填；不得早于 startDate */
    private String endDate;
}
