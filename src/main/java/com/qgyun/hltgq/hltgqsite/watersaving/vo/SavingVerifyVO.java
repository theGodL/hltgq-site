package com.qgyun.hltgq.hltgqsite.watersaving.vo;

import java.math.BigDecimal;

/**
 * 方案核验请求（核验动作写入 verify_* 并驱动状态流转）。
 * <p>comparable = #1#（对比条件确认）→ 状态已完成、按公式计算全部关联指标；
 * comparable = #2#（未确认）→ 状态待复核、不计算指标（结果仍显示待核验）。
 */
public class SavingVerifyVO {

    /** 方案 id（必填） */
    private String id;

    /** 实施后实际用水量（万m³，必填非负） */
    private BigDecimal afterVolume;

    /** 核验资料来源（必填，如台账/报表编号） */
    private String source;

    /** 核验说明（选填） */
    private String note;

    /** 对比条件确认（必填）：#1# 已确认 / #2# 未确认 */
    private String comparable;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BigDecimal getAfterVolume() {
        return afterVolume;
    }

    public void setAfterVolume(BigDecimal afterVolume) {
        this.afterVolume = afterVolume;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getComparable() {
        return comparable;
    }

    public void setComparable(String comparable) {
        this.comparable = comparable;
    }
}
