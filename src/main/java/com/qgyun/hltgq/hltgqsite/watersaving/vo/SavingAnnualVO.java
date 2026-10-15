package com.qgyun.hltgq.hltgqsite.watersaving.vo;

import java.math.BigDecimal;

/**
 * 年度水量记录（行 = 一个潜力年度）。
 * <p>潜力 = 实际 − 计划（万m³，可为负、不截断；任一为空 → null 不可计算）。
 */
public class SavingAnnualVO {

    private String id;

    /** 潜力年度（如 2026） */
    private Integer potentialYear;

    /** 依据年度（= 潜力年度 − 1） */
    private Integer basisYear;

    /** 上一年计划放水量（万m³） */
    private BigDecimal prevPlanVolume;

    /** 上一年实际放水量（万m³） */
    private BigDecimal prevActualVolume;

    /** 潜力（服务端计算：实际 − 计划；可为负） */
    private BigDecimal potential;

    /** 计划来源：auto_plan 用水计划带出 / manual 手工录入 */
    private String planSource;

    private String planSourceName;

    /** 来源计划 id（auto_plan 时留痕） */
    private String planId;

    /** 实际来源：manual 手工（canal_head_meter 渠首预留） */
    private String actualSource;

    private String actualSourceName;

    /** 演示数据标记：#1# 虚拟演示 / #2# 正式 */
    private String isDemo;

    private String createdText;

    private String updatedText;

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

    public Integer getBasisYear() {
        return basisYear;
    }

    public void setBasisYear(Integer basisYear) {
        this.basisYear = basisYear;
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

    public BigDecimal getPotential() {
        return potential;
    }

    public void setPotential(BigDecimal potential) {
        this.potential = potential;
    }

    public String getPlanSource() {
        return planSource;
    }

    public void setPlanSource(String planSource) {
        this.planSource = planSource;
    }

    public String getPlanSourceName() {
        return planSourceName;
    }

    public void setPlanSourceName(String planSourceName) {
        this.planSourceName = planSourceName;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getActualSource() {
        return actualSource;
    }

    public void setActualSource(String actualSource) {
        this.actualSource = actualSource;
    }

    public String getActualSourceName() {
        return actualSourceName;
    }

    public void setActualSourceName(String actualSourceName) {
        this.actualSourceName = actualSourceName;
    }

    public String getIsDemo() {
        return isDemo;
    }

    public void setIsDemo(String isDemo) {
        this.isDemo = isDemo;
    }

    public String getCreatedText() {
        return createdText;
    }

    public void setCreatedText(String createdText) {
        this.createdText = createdText;
    }

    public String getUpdatedText() {
        return updatedText;
    }

    public void setUpdatedText(String updatedText) {
        this.updatedText = updatedText;
    }
}
