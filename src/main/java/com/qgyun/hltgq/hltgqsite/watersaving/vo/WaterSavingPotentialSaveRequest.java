package com.qgyun.hltgq.hltgqsite.watersaving.vo;

import lombok.Data;

/**
 * 节水潜力测算保存请求（POST /water-saving-potential/record）。
 * <p>同一「年度 + 分析范围」存在即整行覆盖、不存在即插入；数值留空传 null 即可（也支持把已存值清空）。
 */
@Data
public class WaterSavingPotentialSaveRequest {

    /** 年度（必填，2000~2100） */
    private Integer year;

    /** 分析范围（必填）：#1# 全灌区 / #2# 太湖县 / #3# 望江县 / #4# 宿松县 / #5# 怀宁县 */
    private String scope;

    /** 水资源总量(万m³) */
    private Double totalResources;

    /** 工程年供水能力(万m³) */
    private Double supplyCapacity;

    /** 年可供水量(万m³) */
    private Double availableSupply;

    /** 基础数据依据（平台附件引用原文） */
    private String basisFile;

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

    /** 状态（可空，默认 #2# 已测算）：#1# 草稿 / #2# 已测算 */
    private String status;
}
