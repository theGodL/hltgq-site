package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

/**
 * 计划引用行（用水户/定额删除保护查询：引用方计划 + 明细行数）。
 */
@Data
public class PlanRefVO {

    private String planId;

    private String planNo;

    private String planName;

    /** 计划状态（#1# 编制中 等，用于两级删除保护分流） */
    private String status;

    /** 引用明细行数 */
    private Integer itemCount;
}
