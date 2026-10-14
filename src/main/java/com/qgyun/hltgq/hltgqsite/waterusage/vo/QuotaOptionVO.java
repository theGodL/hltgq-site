package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 定额下拉选项（用水计划明细「适用定额」选择器数据源）。
 */
@Data
public class QuotaOptionVO {

    private String id;

    private String cropType;

    private String irrigationMethod;

    private BigDecimal quotaValue;
}
