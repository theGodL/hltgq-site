package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 阈值设置表 t_auto_hltgq_water_threshold
 */
@Data
@TableName("\"qixiao-apaas\".t_auto_hltgq_water_threshold")
public class WaterThreshold {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    /** 站点 UUID，对应 station_info.id */
    @TableField("\"site\"")
    private String site;

    /** 设备 */
    @TableField("\"device\"")
    private String device;

    /**
     * 类型，多选用 | 分割，如 #1#|#3#
     * #1# 水位  #2# 雨量  #3# 流量  #4# 闸门  #5# 视频  #7# 墒情  #8# 水质
     */
    @TableField("\"type\"")
    private String type;

    /** 描述 */
    @TableField("\"remark\"")
    private String remark;

    /** 警戒值 */
    @TableField("\"threshold\"")
    private BigDecimal threshold;

    /** 保证值 */
    @TableField("\"guarantee\"")
    private BigDecimal guarantee;

    /** 阈值编号（扩展预留） */
    @TableField("\"num\"")
    private Integer num;
}
