package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 流量监测数据实体（t_auto_hltgq_water_wt_nfo）
 */
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\"")
public class WaterFlow {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"corp_code\"")
    private String corpCode;

    @TableField("\"site\"")
    private String site;

    @TableField("\"device\"")
    private String device;

    @TableField("\"stcd\"")
    private String stcd;

    @TableField("\"tm\"")
    private LocalDateTime tm;

    /** 流量 (m³/s) */
    @TableField("\"q\"")
    private BigDecimal q;

    // ===== getters / setters =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCorpCode() { return corpCode; }
    public void setCorpCode(String corpCode) { this.corpCode = corpCode; }

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }

    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }

    public String getStcd() { return stcd; }
    public void setStcd(String stcd) { this.stcd = stcd; }

    public LocalDateTime getTm() { return tm; }
    public void setTm(LocalDateTime tm) { this.tm = tm; }

    public BigDecimal getQ() { return q; }
    public void setQ(BigDecimal q) { this.q = q; }
}
