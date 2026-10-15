package com.qgyun.hltgq.hltgqsite.watersaving.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.util.List;

/**
 * 节水方案（列表行 / 详情 / 查看弹窗）。
 * <p>服务端派生字段：indicators（关联指标计算结果行）、attainLevel/attainLabel/attainReason（达标优先级链）、
 * statusReason（待复核原因）、evidence*（依据记录摘要）。
 */
public class SavingSchemeVO {

    private String id;

    /** 方案名称（同一年度内唯一） */
    private String name;

    /** 实施年度（= 依据记录的潜力年度） */
    private Integer year;

    /** 关联年度水量记录 id */
    private String evidenceId;

    /** 实施前实际用水量（快照；仅建方案 / 换依据 / 刷新对比依据时更新） */
    private BigDecimal baselineVolume;

    /** 节水目标（万m³；0 < 目标 ≤ 潜力） */
    private BigDecimal targetVolume;

    /** 负责人 */
    private String owner;

    /** 具体措施 */
    private String measure;

    /** 状态：#1# 草稿 / #2# 实施中 / #3# 已完成 / #4# 待复核（核验动作驱动） */
    private String status;

    private String statusName;

    /** 待复核原因（status = #4# 时：对比条件未确认 / 核验依据已变更） */
    private String statusReason;

    /** 关联评估指标 id 列表 */
    private List<String> indicatorIds;

    /** 库列 indicator_ids JSON 原文（Service 解析为 indicatorIds 后置空，不出 JSON） */
    @JsonIgnore
    private String indicatorIdsJson;

    /** 关联指标计算结果行（列表多行展示 / 详情明细） */
    private List<SavingIndicatorResultVO> indicators;

    /** 达标等级 key（pass/partial/fail/pending_verify/pending_target/pending_indicator/uncomputable） */
    private String attainLevel;

    /** 达标状态中文（达标/部分达标/不达标/待核验/待设置目标/待关联指标/待复核） */
    private String attainLabel;

    /** 达标说明（无法计算 → "指标无法计算"；其余常见为空） */
    private String attainReason;

    /** 演示数据标记：#1# 虚拟演示 / #2# 正式 */
    private String isDemo;

    /** 附件原始文件名（无附件 → null） */
    private String attachmentName;

    /** 核验：实施后实际用水量（万m³；未核验 → null） */
    private BigDecimal verifyAfterVolume;

    /** 核验资料来源 */
    private String verifySource;

    /** 核验说明 */
    private String verifyNote;

    /** 核验日期（yyyy-MM-dd） */
    private String verifyDate;

    /** 对比条件确认：#1# 已确认 / #2# 未确认；null = 未核验 */
    private String verifyComparable;

    /** 核验作废标记：#1# 已作废（数据保留、达标强制待核验）/ #2# 有效 */
    private String verifyInvalid;

    /** 最近重算/核验作废时间（文本） */
    private String lastRecalcText;

    /** 重算/作废原因 */
    private String lastRecalcReason;

    /** 依据记录摘要：依据年度 */
    private Integer evidenceBasisYear;

    /** 依据记录摘要：上一年计划放水量（万m³） */
    private BigDecimal evidencePlanVolume;

    /** 依据记录摘要：上一年实际放水量（万m³） */
    private BigDecimal evidenceActualVolume;

    /** 依据记录摘要：潜力（万m³，可为负） */
    private BigDecimal evidencePotential;

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

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(String evidenceId) {
        this.evidenceId = evidenceId;
    }

    public BigDecimal getBaselineVolume() {
        return baselineVolume;
    }

    public void setBaselineVolume(BigDecimal baselineVolume) {
        this.baselineVolume = baselineVolume;
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

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public List<String> getIndicatorIds() {
        return indicatorIds;
    }

    public void setIndicatorIds(List<String> indicatorIds) {
        this.indicatorIds = indicatorIds;
    }

    public String getIndicatorIdsJson() {
        return indicatorIdsJson;
    }

    public void setIndicatorIdsJson(String indicatorIdsJson) {
        this.indicatorIdsJson = indicatorIdsJson;
    }

    public List<SavingIndicatorResultVO> getIndicators() {
        return indicators;
    }

    public void setIndicators(List<SavingIndicatorResultVO> indicators) {
        this.indicators = indicators;
    }

    public String getAttainLevel() {
        return attainLevel;
    }

    public void setAttainLevel(String attainLevel) {
        this.attainLevel = attainLevel;
    }

    public String getAttainLabel() {
        return attainLabel;
    }

    public void setAttainLabel(String attainLabel) {
        this.attainLabel = attainLabel;
    }

    public String getAttainReason() {
        return attainReason;
    }

    public void setAttainReason(String attainReason) {
        this.attainReason = attainReason;
    }

    public String getIsDemo() {
        return isDemo;
    }

    public void setIsDemo(String isDemo) {
        this.isDemo = isDemo;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public void setAttachmentName(String attachmentName) {
        this.attachmentName = attachmentName;
    }

    public BigDecimal getVerifyAfterVolume() {
        return verifyAfterVolume;
    }

    public void setVerifyAfterVolume(BigDecimal verifyAfterVolume) {
        this.verifyAfterVolume = verifyAfterVolume;
    }

    public String getVerifySource() {
        return verifySource;
    }

    public void setVerifySource(String verifySource) {
        this.verifySource = verifySource;
    }

    public String getVerifyNote() {
        return verifyNote;
    }

    public void setVerifyNote(String verifyNote) {
        this.verifyNote = verifyNote;
    }

    public String getVerifyDate() {
        return verifyDate;
    }

    public void setVerifyDate(String verifyDate) {
        this.verifyDate = verifyDate;
    }

    public String getVerifyComparable() {
        return verifyComparable;
    }

    public void setVerifyComparable(String verifyComparable) {
        this.verifyComparable = verifyComparable;
    }

    public String getVerifyInvalid() {
        return verifyInvalid;
    }

    public void setVerifyInvalid(String verifyInvalid) {
        this.verifyInvalid = verifyInvalid;
    }

    public String getLastRecalcText() {
        return lastRecalcText;
    }

    public void setLastRecalcText(String lastRecalcText) {
        this.lastRecalcText = lastRecalcText;
    }

    public String getLastRecalcReason() {
        return lastRecalcReason;
    }

    public void setLastRecalcReason(String lastRecalcReason) {
        this.lastRecalcReason = lastRecalcReason;
    }

    public Integer getEvidenceBasisYear() {
        return evidenceBasisYear;
    }

    public void setEvidenceBasisYear(Integer evidenceBasisYear) {
        this.evidenceBasisYear = evidenceBasisYear;
    }

    public BigDecimal getEvidencePlanVolume() {
        return evidencePlanVolume;
    }

    public void setEvidencePlanVolume(BigDecimal evidencePlanVolume) {
        this.evidencePlanVolume = evidencePlanVolume;
    }

    public BigDecimal getEvidenceActualVolume() {
        return evidenceActualVolume;
    }

    public void setEvidenceActualVolume(BigDecimal evidenceActualVolume) {
        this.evidenceActualVolume = evidenceActualVolume;
    }

    public BigDecimal getEvidencePotential() {
        return evidencePotential;
    }

    public void setEvidencePotential(BigDecimal evidencePotential) {
        this.evidencePotential = evidencePotential;
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
