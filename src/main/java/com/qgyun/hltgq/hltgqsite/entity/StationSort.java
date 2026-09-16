package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站点排序配置实体：监测类型 × 站点 → 展示顺序（表由平台模型创建，代码不建表）。
 * <p>metric_type 为平台单选枚举编码：#1# 水位、#2# 雨量、#3# 流量、#4# 闸门、#5# 墒情；
 * site 为站点管理主键（站点档案表 t_auto_hltgq_5nw74_vnqqef.id）；
 * 同一类型下 sort_no 从 1 递增，保存为整表覆盖。
 * <p>排序配置由监测页面「站点排序」抽屉保存，对同一类型的所有接口与页面生效。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_station_sort\"")
public class StationSort extends BaseWaterEntity {

    /** 监测类型编码：#1# 水位 / #2# 雨量 / #3# 流量 / #4# 闸门 / #5# 墒情 */
    @TableField("\"metric_type\"")
    private String metricType;

    /** 站点管理主键（站点档案表 id） */
    @TableField("\"site\"")
    private String site;

    /** 展示顺序，从 1 开始 */
    @TableField("\"sort_no\"")
    private Integer sortNo;
}
