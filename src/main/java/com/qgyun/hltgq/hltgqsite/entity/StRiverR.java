package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 复合主键：STCD + TM
 */
@Data
@TableName("\"qixiao-apaas\".t_auto_hltgq_water_river_info")
public class StRiverR {

    @TableField("\"STCD\"")
    private String stcd;

    @TableField("\"TM\"")
    private LocalDateTime tm;

    @TableField("\"Z\"")
    private BigDecimal z;

    @TableField("\"Z2\"")
    private BigDecimal z2;

    @TableField("\"Q\"")
    private BigDecimal q;

    @TableField("\"XSA\"")
    private BigDecimal xsa;

    @TableField("\"XSAVV\"")
    private BigDecimal xsavv;

    @TableField("\"XSMXV\"")
    private BigDecimal xsmxv;

    @TableField("\"FLWCHRCD\"")
    private String flwchrcd;

    @TableField("\"WPTN\"")
    private String wptn;
}
