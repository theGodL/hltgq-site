package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 阈值设置表 t_auto_hltgq_water_threshold（表由平台模型创建，代码不建表）。
 * <p>列分工（2026-09-24 只读核对）：{@code zvieyb} = 阈值类型编码（告警引擎与下游查询均读本列，
 * 存量 3 行均为 #1# 水位）；{@code alarmdir} = 告警方向编码；{@code threshold} = 警戒值；
 * {@code remark} = 描述；{@code site} = 站点档案主键（t_auto_hltgq_5nw74_vnqqef.id）。
 * <p>{@code guarantee}（保证值）与 {@code num}（设计值）本期仅预留：阈值设置界面不渲染、
 * 保存时也不改写既有值（水位监测页仍读 guarantee 展示保证水位）。
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

    /** 阈值类型编码：#1# 水位 / #2# 雨量 / #3# 流量 / #4# 开度 / #7# 墒情 */
    @TableField("\"zvieyb\"")
    private String zvieyb;

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
