package com.qgyun.hltgq.hltgqsite.watersaving.vo;

import java.math.BigDecimal;

/**
 * 节水指标新增/编辑请求。
 * <p>编辑时：已关联方案的指标计算方式锁定（不可变化）；名称唯一预检排除自身。
 */
public class SavingIndicatorSaveVO {

    /** 指标名称（必填；全局唯一含停用） */
    private String name;

    /** 计算方式（必填三选一：#1# 节水量 / #2# 节水率 / #3# 目标完成率） */
    private String method;

    /** 目标值（选填；填写则 >0、≤6 位小数） */
    private BigDecimal targetValue;

    /** 指标用途（选填） */
    private String purpose;

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
}
