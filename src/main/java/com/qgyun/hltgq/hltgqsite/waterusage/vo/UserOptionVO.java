package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用水户下拉选项（用水计划明细「用水户」选择器数据源；选用后带出灌溉面积快照）。
 */
@Data
public class UserOptionVO {

    private String id;

    private String name;

    private String userNo;

    /** 灌溉面积（亩，快照默认值；可空） */
    private BigDecimal irrigationArea;
}
