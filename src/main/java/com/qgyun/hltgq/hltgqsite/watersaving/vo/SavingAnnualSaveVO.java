package com.qgyun.hltgq.hltgqsite.watersaving.vo;

import java.math.BigDecimal;

/**
 * 年度水量记录保存请求（有 id 更新 / 无 id 新增；潜力年度唯一，被方案引用后禁止删除与改年度）。
 */
public class SavingAnnualSaveVO {

    /** 更新时必填；新增留空 */
    private String id;

    /** 潜力年度（必填；如 2026） */
    private Integer potentialYear;

    /** 上一年计划放水量（万m³，选填非负；auto_plan 带出或手工覆盖） */
    private BigDecimal prevPlanVolume;

    /** 上一年实际放水量（万m³，必填非负） */
    private BigDecimal prevActualVolume;

    /** 计划来源：auto_plan / manual */
    private String planSource;

    /** 来源计划 id（auto_plan 时） */
    private String planId;

    /** 演示数据标记：#1# 虚拟演示 / #2# 正式（默认 #2#） */
    private String isDemo;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getPotentialYear() {
        return potentialYear;
    }

    public void setPotentialYear(Integer potentialYear) {
        this.potentialYear = potentialYear;
    }

    public BigDecimal getPrevPlanVolume() {
        return prevPlanVolume;
    }

    public void setPrevPlanVolume(BigDecimal prevPlanVolume) {
        this.prevPlanVolume = prevPlanVolume;
    }

    public BigDecimal getPrevActualVolume() {
        return prevActualVolume;
    }

    public void setPrevActualVolume(BigDecimal prevActualVolume) {
        this.prevActualVolume = prevActualVolume;
    }

    public String getPlanSource() {
        return planSource;
    }

    public void setPlanSource(String planSource) {
        this.planSource = planSource;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getIsDemo() {
        return isDemo;
    }

    public void setIsDemo(String isDemo) {
        this.isDemo = isDemo;
    }
}
