package com.qgyun.hltgq.hltgqsite.watersaving.vo;

import java.math.BigDecimal;

/**
 * 方案关联指标的计算结果行（列表"指标结果"列多行展示 / 查看弹窗明细）。
 * <p>展示态：达标 / 不达标 / 待核验（未核验、核验作废、对比条件未确认）/ 无法计算（分母为 0）/ 待设置（未维护目标值）。
 */
public class SavingIndicatorResultVO {

    private String id;

    private String name;

    /** 计算方式（#N#） */
    private String method;

    private String methodName;

    /** 单位（万m³ / %） */
    private String unit;

    /** 公式文案 */
    private String formula;

    /** 指标目标值（可空） */
    private BigDecimal targetValue;

    /** 计算结果（未核验 / 无法计算 → null） */
    private BigDecimal result;

    /** 展示态 key：pass / fail / pending / uncomputable / no_target */
    private String state;

    /** 展示态中文：达标 / 不达标 / 待核验 / 无法计算 / 待设置 */
    private String stateName;

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

    public BigDecimal getResult() {
        return result;
    }

    public void setResult(BigDecimal result) {
        this.result = result;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
    }
}
