package com.qgyun.hltgq.hltgqsite.watersaving.vo;

import java.math.BigDecimal;

/**
 * 上一年度用水计划带出（年度水量设置弹窗：取依据年度年度计划，按创建时间倒序第一条）。
 * <p>口径：plan_type = '#1#'（年度）且 status ≠ '#4#'（已作废）；total_water 库内 m³，输出转万m³。
 */
public class SavingPrevPlanVO {

    /** 是否带出（false 时仅 basisYear 有效，前端提示手工录入） */
    private Boolean found;

    /** 计划 id（落库 plan_id 留痕） */
    private String planId;

    /** 计划编号 */
    private String planNo;

    /** 计划名称 */
    private String planName;

    /** 计划放水量（万m³，total_water ÷ 10000） */
    private BigDecimal planVolume;

    /** 计划创建日期文本（yyyy-MM-dd HH:mm） */
    private String createdText;

    /** 依据年度（= 请求年度 − 1） */
    private Integer basisYear;

    public Boolean getFound() {
        return found;
    }

    public void setFound(Boolean found) {
        this.found = found;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPlanNo() {
        return planNo;
    }

    public void setPlanNo(String planNo) {
        this.planNo = planNo;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public BigDecimal getPlanVolume() {
        return planVolume;
    }

    public void setPlanVolume(BigDecimal planVolume) {
        this.planVolume = planVolume;
    }

    public String getCreatedText() {
        return createdText;
    }

    public void setCreatedText(String createdText) {
        this.createdText = createdText;
    }

    public Integer getBasisYear() {
        return basisYear;
    }

    public void setBasisYear(Integer basisYear) {
        this.basisYear = basisYear;
    }
}
