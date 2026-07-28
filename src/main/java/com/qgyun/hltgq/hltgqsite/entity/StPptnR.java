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
@TableName("\"qixiao-apaas\".t_auto_hltgq_water_rain_info")
public class StPptnR {

    @TableField("\"STCD\"")
    private String stcd;

    @TableField("\"TM\"")
    private LocalDateTime tm;

    @TableField("\"DRP\"")
    private BigDecimal drp;

    @TableField("\"INTV\"")
    private Integer intv;

    @TableField("\"PDR\"")
    private BigDecimal pdr;

    @TableField("\"DYP\"")
    private BigDecimal dyp;

    @TableField("\"WTH\"")
    private String wth;
}
