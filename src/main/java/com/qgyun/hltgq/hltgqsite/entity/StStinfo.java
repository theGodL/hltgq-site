package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("\"qixiao-apaas\".t_auto_hltgq_5nw74_vnqqef")
public class StStinfo {

    @TableId(value = "\"iofhpi\"", type = IdType.INPUT)
    private String stcd;

    @TableField("\"id\"")
    private String id;

    @TableField("\"zzkaec\"")
    private String stnm;

    @TableField(value = "\"STGROUP\"", exist = false)
    private String stgroup;

    @TableField(value = "\"STTP\"", exist = false)
    private String sttp;

    @TableField(value = "\"ALARMZ\"", exist = false)
    private BigDecimal alarmz;

    @TableField(value = "\"JUMPZ\"", exist = false)
    private BigDecimal jumpz;

    @TableField(value = "\"JUMPP\"", exist = false)
    private BigDecimal jumpp;

    @TableField(value = "\"PARA\"", exist = false)
    private Integer para;

    @TableField(value = "\"zbase\"", exist = false)
    private BigDecimal zbase;
}
