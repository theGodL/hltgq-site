package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用水计划（列表行 / 详情共用）。
 * <p>userCount（用水户数，明细 DISTINCT 用水户）由列表查询计算；
 * 执行进度 = scheduledQty ÷ totalWater，由前端动态计算（本期 scheduledQty 恒 0，显示 0%）。
 */
@Data
public class WaterPlanVO {

    private String id;

    /** 编号：YJ + 3 位序号 */
    private String planNo;

    /** 计划名称（必填，全局唯一） */
    private String planName;

    /** 类型：#1# 年度 / #2# 月度 / #3# 旬度 */
    private String planType;

    private String planTypeName;

    /** 计划周期起（yyyy-MM-dd，按类型自动推导不可改） */
    private String periodStart;

    /** 计划周期止 */
    private String periodEnd;

    /** 制定时间（yyyy-MM-dd HH:mm，列表名称辅助行） */
    private String createdAtText;

    /** 计划总水量（m³，明细汇总，只读） */
    private BigDecimal totalWater;

    /** 已调度水量（m³，默认 0；调度模块后续回写） */
    private BigDecimal scheduledQty;

    /** 用水户数（明细 DISTINCT 用水户） */
    private Integer userCount;

    /** 状态：#1# 编制中 / #2# 已生效 / #3# 已归档 / #4# 已作废 */
    private String status;

    private String statusName;

    /** 作废人（作废时记录） */
    private String voidBy;

    /** 作废时间（yyyy-MM-dd HH:mm） */
    private String voidAt;

    /** 作废原因 */
    private String voidReason;

    /** 明细（详情返回；列表为空） */
    private List<WaterPlanItemVO> items;
}
