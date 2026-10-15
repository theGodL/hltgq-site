package com.qgyun.hltgq.hltgqsite.watersaving.vo;

import java.math.BigDecimal;

/**
 * 节水指标（计算方式固定三选一；名称全局唯一含停用；已关联方案的指标计算方式锁定、不可删除）。
 */
public class SavingIndicatorVO {

    private String id;

    /** 指标名称（全局唯一，含停用指标） */
    private String name;

    /** 计算方式：#1# 节水量(万m³) / #2# 节水率(%) / #3# 目标完成率(%) */
    private String method;

    private String methodName;

    /** 单位（万m³ / %） */
    private String unit;

    /** 公式文案（展示用） */
    private String formula;

    /** 目标值（可空；未设目标 → 方案"待设置目标"、不出达标结论） */
    private BigDecimal targetValue;

    /** 指标用途（选填） */
    private String purpose;

    /** 状态：#1# 启用 / #2# 停用（停用后新方案不可勾选，既有关联保留） */
    private String enabled;

    private String enabledName;

    /** 已关联方案数（>0：计算方式锁定、不可删除） */
    private Integer schemeCount;

    private String createdText;

    private String updatedText;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getFormula() {
        return formula;
    }

    public void setFormula(String formula) {
        this.formula = formula;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(BigDecimal targetValue) {
        this.targetValue = targetValue;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getEnabled() {
        return enabled;
    }

    public void setEnabled(String enabled) {
        this.enabled = enabled;
    }

    public String getEnabledName() {
        return enabledName;
    }

    public void setEnabledName(String enabledName) {
        this.enabledName = enabledName;
    }

    public Integer getSchemeCount() {
        return schemeCount;
    }

    public void setSchemeCount(Integer schemeCount) {
        this.schemeCount = schemeCount;
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
