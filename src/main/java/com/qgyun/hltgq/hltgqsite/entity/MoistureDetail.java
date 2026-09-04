package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 墒情预测逐小时明细实体。
 * <p>注意：平台建表名为 t_auto_hltgq_water_moisture_record（含 record_id 的明细表，
 * 平台命名与实体名交叉，勿按字面误改），2026-09 定稿。
 * <p>字段口径对齐模型 /moisture results 逐小时条目：
 * 时间/降雨_mm/10cm_%/20cm_%/30cm_%/G值(RSM)/干旱等级。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_moisture_record\"")
public class MoistureDetail extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_moisture_detail.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 站点ID（平台档案表主键 UUID，通过 stcd 映射获取；对应平台表单模型 site 列） */
    @TableField("\"site\"")
    private String site;

    /** 时间（results[].时间） */
    @TableField("\"tm\"")
    private LocalDateTime tm;

    /** 降雨(mm)（results[].降雨_mm） */
    @TableField("\"rainfall\"")
    private Double rainfall;

    /** 10cm 层含水量(%)（results[].10cm_%） */
    @TableField("\"mten\"")
    private Double mten;

    /** 20cm 层含水量(%)（results[].20cm_%） */
    @TableField("\"mtwenty\"")
    private Double mtwenty;

    /** 30cm 层含水量(%)（results[].30cm_%） */
    @TableField("\"mthirty\"")
    private Double mthirty;

    /** G 值(RSM 相对湿度)（results[].G值(RSM)） */
    @TableField("\"g_value\"")
    private Double gValue;

    /** 干旱等级（平台编码：#1#无旱 / #2#轻旱 / #3#中旱 / #4#重旱 / #5#特旱，后端解析时归一化） */
    @TableField("\"drought_level\"")
    private String droughtLevel;
}
