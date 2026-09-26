package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 阈值设置表 t_auto_hltgq_water_threshold（表由平台模型创建，代码不建表）。
 * <p>列分工（2026-09-24 只读核对）：{@code zvieyb} = 阈值类型编码（告警引擎与下游查询均读本列，
 * 存量 3 行均为 #1# 水位）；{@code zb} = 监测指标编码（多指标类型用，见下）；
 * {@code alarmdir} = 告警方向编码；{@code threshold} = 警戒值；
 * {@code remark} = 描述；{@code site} = 站点档案主键（t_auto_hltgq_5nw74_vnqqef.id）。
 * <p>指标口径（{@code zb}）：水质 #8# 与墒情 #7# 一个类型下含多个指标（水质七项、墒情三层，
 * 按监测页实际展示口径开放，见 {@code ThresholdServiceImpl#TYPES}），
 * 故按「站点 + 类型 + 指标」各存一行，{@code zb} 存指标编码（= 监测数据表字段名，如 nh3n / mten），
 * 告警比对接该列到数据表取数；单指标类型（水位/雨量/流量/开度）该列为空。
 * <p>{@code guarantee}（保证值）与 {@code num}（设计值）本期仅预留：阈值设置界面不渲染、
 * 保存时也不改写既有值；站点侧不读本列作展示（水质页仅透传，页面口径为 {@code threshold} 单一取值）。
 * <p>历史列 {@code type} 为旧类型列（存量数据全空），读取侧按
 * {@code COALESCE(NULLIF(zvieyb,''), type)} 兼容，新写入一律落 {@code zvieyb}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".t_auto_hltgq_water_threshold")
public class WaterThreshold extends BaseWaterEntity {

    /** 站点档案主键（t_auto_hltgq_5nw74_vnqqef.id） */
    @TableField("\"site\"")
    private String site;

    /** 设备 ID（站级配置时为空） */
    @TableField("\"device\"")
    private String device;

    /** 旧类型列（多选用 | 分割，如 #1#|#3#）；新配置不写本列，仅在读取时作兜底 */
    @TableField("\"type\"")
    private String type;

    /** 阈值类型编码：#1# 水位 / #2# 雨量 / #3# 流量 / #4# 开度 / #7# 墒情 / #8# 水质 */
    @TableField("\"zvieyb\"")
    private String zvieyb;

    /**
     * 监测指标编码（多指标类型必填，单指标类型为空）：编码即监测数据表字段名——
     * 水质 {@code nh3n/codmn/bod5/tp/tn/dox/wt}（页面展示七项）、墒情 {@code mten/mtwenty/mthirty}（页面展示三层）；
     * 单指标类型（水位 z / 雨量 / 流量 / 开度）无需填写，类型本身已唯一确定指标。
     */
    @TableField("\"zb\"")
    private String zb;

    /** 告警方向编码：#1# 高于警戒值触发 / #2# 低于警戒值触发 */
    @TableField("\"alarmdir\"")
    private String alarmdir;

    /** 描述（阈值设置界面：阈值描述，≤500 字） */
    @TableField("\"remark\"")
    private String remark;

    /** 警戒值（本期唯一可编辑的数值列） */
    @TableField("\"threshold\"")
    private BigDecimal threshold;

    /** 保证值（本期预留，界面不渲染，保存时不动本列） */
    @TableField("\"guarantee\"")
    private BigDecimal guarantee;

    /** 设计值 / 扩展预留（本期预留，界面不渲染，保存时不动本列） */
    @TableField("\"num\"")
    private BigDecimal num;
}
