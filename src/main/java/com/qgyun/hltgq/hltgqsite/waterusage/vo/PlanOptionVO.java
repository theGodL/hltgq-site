package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用水计划下拉选项（调度方案「关联用水计划」；仅已生效）。
 * <p>totalWater / userPlanWater / userScheduledQty 单位均为 m³（库内口径，前端展示按需 ÷10000 换算万m³）。
 * <p>userPlanWater / userScheduledQty 仅在请求带 userId 时返回：前者 = 该户在该计划中的明细合计
 * （配额分母），后者 = 该户在该计划中已下达合计（未取消方案，配额分子）——即调度方案页
 * 「本计划该户计划用水量 X · 已下达 Y · 剩余可下达 Z」提示条数据（Z = userPlanWater − userScheduledQty）。
 */
@Data
public class PlanOptionVO {

    private String id;

    /** 编号：YJ + 3 位序号 */
    private String planNo;

    /** 计划名称（全局唯一） */
    private String planName;

    /** 类型：#1# 年度 / #2# 月度 / #3# 旬度 */
    private String planType;

    private String planTypeName;

    /** 计划周期起（yyyy-MM-dd） */
    private String periodStart;

    /** 计划周期止（yyyy-MM-dd） */
    private String periodEnd;

    /** 计划总水量（m³，全计划明细汇总） */
    private BigDecimal totalWater;

    /** 该户在该计划中的计划水量合计（m³；带 userId 时返回，无明细为 0） */
    private BigDecimal userPlanWater;

    /** 该户在该计划中已下达合计（m³，未取消方案；带 userId 时返回） */
    private BigDecimal userScheduledQty;
}
