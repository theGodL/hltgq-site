package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 闸站流量计算参数实体（t_auto_hltgq_water_sluice_discharge）
 * <p>每次修改新增一条数据（version 递增），回显与使用始终取最新一条。
 */
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_sluice_discharge\"")
public class SluiceDischarge {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"corp_code\"")
    private String corpCode;

    /** 站点 UUID，对应 station_info.id */
    @TableField("\"site\"")
    private String site;

    /** 流量系数（全开自由流） */
    @TableField("\"full_open_free_coeff\"")
    private BigDecimal fullOpenFreeCoeff;

    /** 流量系数（全开淹没流） */
    @TableField("\"submerged_flow_coeff\"")
    private BigDecimal submergedFlowCoeff;

    /** 流量系数（有闸控制自由流） */
    @TableField("\"controlled_free_coeff\"")
    private BigDecimal controlledFreeCoeff;

    /** 流量系数（有闸控制淹没流） */
    @TableField("\"orifice_submerged_coeff\"")
    private BigDecimal orificeSubmergedCoeff;

    /** 孔宽 (m) */
    @TableField("\"width\"")
    private BigDecimal width;

    /** 孔高 (m) */
    @TableField("\"height\"")
    private BigDecimal height;

    /** 闸底高程 (m) */
    @TableField("\"bottom_elevation\"")
    private BigDecimal bottomElevation;

    /** 版本号，每次修改递增 */
    @TableField("\"version\"")
    private Integer version;

    @TableField("\"created_at\"")
    private LocalDateTime createdAt;

    @TableField("\"created_by\"")
    private String createdBy;

    @TableField("\"updated_at\"")
    private LocalDateTime updatedAt;

    @TableField("\"updated_by\"")
    private String updatedBy;

    // ===== getters / setters =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCorpCode() { return corpCode; }
    public void setCorpCode(String corpCode) { this.corpCode = corpCode; }

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }

    public BigDecimal getFullOpenFreeCoeff() { return fullOpenFreeCoeff; }
    public void setFullOpenFreeCoeff(BigDecimal fullOpenFreeCoeff) { this.fullOpenFreeCoeff = fullOpenFreeCoeff; }

    public BigDecimal getSubmergedFlowCoeff() { return submergedFlowCoeff; }
    public void setSubmergedFlowCoeff(BigDecimal submergedFlowCoeff) { this.submergedFlowCoeff = submergedFlowCoeff; }

    public BigDecimal getControlledFreeCoeff() { return controlledFreeCoeff; }
    public void setControlledFreeCoeff(BigDecimal controlledFreeCoeff) { this.controlledFreeCoeff = controlledFreeCoeff; }

    public BigDecimal getOrificeSubmergedCoeff() { return orificeSubmergedCoeff; }
    public void setOrificeSubmergedCoeff(BigDecimal orificeSubmergedCoeff) { this.orificeSubmergedCoeff = orificeSubmergedCoeff; }

    public BigDecimal getWidth() { return width; }
    public void setWidth(BigDecimal width) { this.width = width; }

    public BigDecimal getHeight() { return height; }
    public void setHeight(BigDecimal height) { this.height = height; }

    public BigDecimal getBottomElevation() { return bottomElevation; }
    public void setBottomElevation(BigDecimal bottomElevation) { this.bottomElevation = bottomElevation; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
