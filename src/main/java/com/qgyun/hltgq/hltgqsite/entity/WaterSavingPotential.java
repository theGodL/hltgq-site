package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 节水潜力测算记录实体（t_auto_hltgq_water_saving_potential，业主 2026-09-12 平台建表）。
 * <p>一行 = 一个「年度 + 分析范围」的测算；页面 static/water-saving-potential.html。
 * <p>数值口径：基础数据三件套与分行业（农业/工业/生活）基准/节水后需水量，单位均为万m³，
 * 由业主在页面人工录入；结果指标（节水总量/率/供需缺口/占比）由前端实时计算、不入库。
 * <p>时间口径：year 列存该年 1 月 1 日 00:00:00（年度锚点），查询按年份区间匹配，
 * 兼容平台日期控件写入的任意日期值。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_saving_potential\"")
public class WaterSavingPotential extends BaseWaterEntity {

    /** 年度（存该年 1 月 1 日 00:00:00） */
    @TableField("\"year\"")
    private LocalDateTime year;

    /** 分析范围：#1# 全灌区 / #2# 太湖县 / #3# 望江县 / #4# 宿松县 / #5# 怀宁县 */
    @TableField("\"scope\"")
    private String scope;

    /** 水资源总量(万m³) */
    @TableField("\"total_resources\"")
    private Double totalResources;

    /** 工程年供水能力(万m³) */
    @TableField("\"supply_capacity\"")
    private Double supplyCapacity;

    /** 年可供水量(万m³) */
    @TableField("\"available_supply\"")
    private Double availableSupply;

    /** 基础数据依据（平台附件引用原文，回显地址经文件服务解析） */
    @TableField("\"basis_file\"")
    private String basisFile;

    /** 农业基准需水量(万m³) */
    @TableField("\"agri_baseline\"")
    private Double agriBaseline;

    /** 农业节水后需水量(万m³) */
    @TableField("\"agri_target\"")
    private Double agriTarget;

    /** 工业基准需水量(万m³) */
    @TableField("\"ind_baseline\"")
    private Double indBaseline;

    /** 工业节水后需水量(万m³) */
    @TableField("\"ind_target\"")
    private Double indTarget;

    /** 生活基准需水量(万m³) */
    @TableField("\"life_baseline\"")
    private Double lifeBaseline;

    /** 生活节水后需水量(万m³) */
    @TableField("\"life_target\"")
    private Double lifeTarget;

    /** 节水测算依据（定额依据、测算口径、数据来源） */
    @TableField("\"calc_basis\"")
    private String calcBasis;

    /** 拟采取措施（渠道防渗、精准灌溉等） */
    @TableField("\"measures\"")
    private String measures;

    /** 状态：#1# 草稿 / #2# 已测算 */
    @TableField("\"status\"")
    private String status;
}
