package com.qgyun.hltgq.hltgqsite.watersaving.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 节水方案新增/编辑请求。
 * <p>校验口径：名称年度内唯一；0 &lt; 目标 ≤ 潜力（潜力 ≤ 0 不可建方案）；关联指标 ≥1 个（新增勾选须为启用）；
 * 新增/未核验方案状态仅可选 #1# 草稿 / #2# 实施中；已核验方案状态由核验动作驱动（提交值忽略）。
 */
public class SavingSchemeSaveVO {

    /** 方案名称（必填；同一年度内唯一） */
    private String name;

    /** 关联年度水量记录 id（必填；实施年度取记录的潜力年度） */
    private String evidenceId;

    /** 节水目标（万m³，必填 >0 且 ≤ 潜力） */
    private BigDecimal targetVolume;

    /** 负责人（必填；平台用户姓名快照） */
    private String owner;

    /** 负责人平台用户 id（选人下拉；非空时后端按 id 回查固化姓名，不信任前端文本） */
    private String ownerUserId;

    /** 具体措施（必填） */
    private String measure;

    /** 状态（新增 / 未核验方案编辑）：#1# 草稿 / #2# 实施中 */
    private String status;

    /** 关联评估指标 id 列表（必填 ≥1） */
    private List<String> indicatorIds;

    /** 演示数据标记：#1# 虚拟演示 / #2# 正式（默认 #2#） */
    private String isDemo;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(String evidenceId) {
        this.evidenceId = evidenceId;
    }

    public BigDecimal getTargetVolume() {
        return targetVolume;
    }

    public void setTargetVolume(BigDecimal targetVolume) {
        this.targetVolume = targetVolume;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getMeasure() {
        return measure;
    }

    public void setMeasure(String measure) {
        this.measure = measure;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getIndicatorIds() {
        return indicatorIds;
    }

    public void setIndicatorIds(List<String> indicatorIds) {
        this.indicatorIds = indicatorIds;
    }

    public String getIsDemo() {
        return isDemo;
    }

    public void setIsDemo(String isDemo) {
        this.isDemo = isDemo;
    }
}
