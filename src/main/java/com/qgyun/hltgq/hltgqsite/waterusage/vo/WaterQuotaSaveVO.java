package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用水定额标准新增/编辑提交体（POST/PUT /water-quota）。
 * <p>灌溉方式为必填字典编码（#1# 淹灌 ~ #10# 计划直供），选项由 /water-quota/meta 下发，前端下拉不写死。
 */
@Data
public class WaterQuotaSaveVO {

    /** 作物种类（必填，≤100 字） */
    private String cropType;

    /** 灌溉方式（必填，字典型编码 #1#~#10#；须为字典有效值，前端下拉选择） */
    private String irrigationMethod;

    /** 定额标准（m³/亩，>0，2 位小数） */
    private BigDecimal quotaValue;

    /** 备注（可空，≤200 字） */
    private String remark;
}
