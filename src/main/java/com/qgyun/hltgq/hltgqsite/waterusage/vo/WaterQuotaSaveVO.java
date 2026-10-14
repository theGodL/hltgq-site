package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用水定额标准新增/编辑提交体（POST/PUT /water-quota）。
 */
@Data
public class WaterQuotaSaveVO {

    /** 作物种类（必填，≤100 字） */
    private String cropType;

    /** 灌溉方式（可空，≤100 字） */
    private String irrigationMethod;

    /** 定额标准（m³/亩，>0，2 位小数） */
    private BigDecimal quotaValue;

    /** 备注（可空，≤200 字） */
    private String remark;
}
