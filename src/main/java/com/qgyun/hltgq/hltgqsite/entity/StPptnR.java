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

    /** 当前降雨量 — 水文日累计（8:00 ~ 当前），每日8:00归零 */
    @TableField("\"DRP\"")
    private BigDecimal drp;

    @TableField("\"INTV\"")
    private Integer intv;

    @TableField("\"PDR\"")
    private BigDecimal pdr;

    /** 累计雨量 — RTU安装至今总累计，永不归零 */
    @TableField("\"DYP\"")
    private BigDecimal dyp;

    @TableField("\"WTH\"")
    private String wth;
}
