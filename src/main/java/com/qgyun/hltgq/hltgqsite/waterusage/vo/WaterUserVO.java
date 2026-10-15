package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 用水户（列表行 / 详情共用）。
 * <p>gateConfigRaw 为库内 JSON 原文（不序列化给前端）；gateConfig 为 Service 解析后的对象，
 * gateText 为列表展示文案（累加「闸A、闸B」；差额「主闸 − 扣A、扣B」）。
 * <p>annualPlanWater 为档案字段（water_user.annual_plan_water 列，m³；可空=未设定）；
 * annualScheduledQty 为派生值（该户当年年度计划上未取消调度方案的 plan_amount 合计）。
 */
@Data
public class WaterUserVO {

    private String id;

    /** 编号：YH + 3 位序号（后端自动生成，不可修改） */
    private String userNo;

    /** 名称（必填，全局唯一） */
    private String name;

    /** 用水类型：#1# 农业 / #2# 工业 / #3# 生活 / #4# 生态 */
    private String userType;

    private String userTypeName;

    /** 联系人 */
    private String contactName;

    /** 联系电话（11 位数字，可空） */
    private String contactPhone;

    /** 灌溉面积（亩，2 位小数；工业/生活可空） */
    private BigDecimal irrigationArea;

    /** 位置经度（可空，-180 ~ 180） */
    private BigDecimal lng;

    /** 位置纬度（可空，-90 ~ 90） */
    private BigDecimal lat;

    /** 水价（元/m³，3 位小数） */
    private BigDecimal waterPrice;

    /** 计算策略：#1# 指定闸口累加 / #2# 闸口差额计量 */
    private String calcMode;

    private String calcModeName;

    /** 关联闸口 JSON 原文（库内存储，不输出） */
    @JsonIgnore
    private String gateConfigRaw;

    /** 关联闸口（解析后的结构化对象） */
    private Object gateConfig;

    /** 关联闸口展示文案（列表「用水计算策略」列辅助行） */
    private String gateText;

    /** 统计周期：#1# 月度结算 / #2# 季度结算 / #3# 年度结算 */
    private String settleCycle;

    private String settleCycleName;

    /** 状态：#1# 正常 / #2# 停用 */
    private String status;

    private String statusName;

    /** 年度计划水量（m³，档案字段；可空=未设定） */
    private BigDecimal annualPlanWater;

    /** 年度已调度水量（m³，派生：该户当年年度计划上未取消调度方案的 plan_amount 合计） */
    private BigDecimal annualScheduledQty;

    /** 待出账水量（m³，列表装配：未计费未取消方案的实时计量合计，口径同水费台账） */
    private BigDecimal pendingQty;

    /** 待出账方案数（列表装配） */
    private Integer pendingCount;

    /** 备注 */
    private String remark;
}
