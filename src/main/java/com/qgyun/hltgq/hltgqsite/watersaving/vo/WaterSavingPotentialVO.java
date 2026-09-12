package com.qgyun.hltgq.hltgqsite.watersaving.vo;

import lombok.Data;

/**
 * 节水潜力测算记录（读接口响应）。
 * <p>year 为年份数字（如 2026）；scope/status 返回平台编码，同时附 scopeName/statusName 中文名；
 * basisFile 为平台附件引用原文，basisFileUrl 为文件服务解析出的预览地址（解析失败/未配置为 null）。
 * <p>结果指标（节水总量/率/供需缺口/占比）由前端按本记录数值实时计算，不在响应内。
 */
@Data
public class WaterSavingPotentialVO {

    /** 年度（如 2026） */
    private Integer year;

    /** 分析范围编码：#1#~#5# */
    private String scope;

    /** 分析范围名称：全灌区/太湖县/望江县/宿松县/怀宁县 */
    private String scopeName;

    /** 水资源总量(万m³) */
    private Double totalResources;

    /** 工程年供水能力(万m³) */
    private Double supplyCapacity;

    /** 年可供水量(万m³) */
    private Double availableSupply;

    /** 基础数据依据（平台附件引用原文） */
    private String basisFile;

    /** 基础数据依据（文件服务预览地址，解析失败为 null） */
    private String basisFileUrl;

    /** 农业基准需水量(万m³) */
    private Double agriBaseline;

    /** 农业节水后需水量(万m³) */
    private Double agriTarget;

    /** 工业基准需水量(万m³) */
    private Double indBaseline;

    /** 工业节水后需水量(万m³) */
    private Double indTarget;

    /** 生活基准需水量(万m³) */
    private Double lifeBaseline;

    /** 生活节水后需水量(万m³) */
    private Double lifeTarget;

    /** 节水测算依据 */
    private String calcBasis;

    /** 拟采取措施 */
    private String measures;

    /** 状态编码：#1# 草稿 / #2# 已测算 */
    private String status;

    /** 状态名称：草稿/已测算 */
    private String statusName;
}
