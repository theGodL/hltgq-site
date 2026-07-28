package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_gate\"")
public class GateMonitor {

    @TableId(value = "\"id\"", type = IdType.INPUT)
    private String id;

    @TableField("\"stcd\"")
    private String stcd;

    @TableField("\"gate_no\"")
    private String gateNo;

    @TableField("\"site\"")
    private String site;

    @TableField("\"device\"")
    private String device;

    @TableField("\"site_name\"")
    private String siteName;

    @TableField("\"tm\"")
    private LocalDateTime tm;

    @TableField("\"open_degree\"")
    private BigDecimal openDegree;

    @TableField("\"up_z\"")
    private BigDecimal upZ;

    @TableField("\"down_z\"")
    private BigDecimal downZ;

    @TableField("\"gate_discharge\"")
    private BigDecimal gateDischarge;

    @TableField("\"status\"")
    private String status;

    @TableField("\"local\"")
    private String localMode;

    @TableField("\"remote\"")
    private String remoteMode;

    @TableField("\"open_run\"")
    private String openRun;

    @TableField("\"close_run\"")
    private String closeRun;

    @TableField("\"full_open\"")
    private String fullOpen;

    @TableField("\"full_close\"")
    private String fullClose;

    @TableField("\"overload\"")
    private String overload;

    @TableField("\"power_ok\"")
    private String powerOk;

    // ===== getters / setters =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStcd() { return stcd; }
    public void setStcd(String stcd) { this.stcd = stcd; }

    public String getGateNo() { return gateNo; }
    public void setGateNo(String gateNo) { this.gateNo = gateNo; }

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }

    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }

    public LocalDateTime getTm() { return tm; }
    public void setTm(LocalDateTime tm) { this.tm = tm; }

    public BigDecimal getOpenDegree() { return openDegree; }
    public void setOpenDegree(BigDecimal openDegree) { this.openDegree = openDegree; }

    public BigDecimal getUpZ() { return upZ; }
    public void setUpZ(BigDecimal upZ) { this.upZ = upZ; }

    public BigDecimal getDownZ() { return downZ; }
    public void setDownZ(BigDecimal downZ) { this.downZ = downZ; }

    public BigDecimal getGateDischarge() { return gateDischarge; }
    public void setGateDischarge(BigDecimal gateDischarge) { this.gateDischarge = gateDischarge; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLocalMode() { return localMode; }
    public void setLocalMode(String localMode) { this.localMode = localMode; }

    public String getRemoteMode() { return remoteMode; }
    public void setRemoteMode(String remoteMode) { this.remoteMode = remoteMode; }

    public String getOpenRun() { return openRun; }
    public void setOpenRun(String openRun) { this.openRun = openRun; }

    public String getCloseRun() { return closeRun; }
    public void setCloseRun(String closeRun) { this.closeRun = closeRun; }

    public String getFullOpen() { return fullOpen; }
    public void setFullOpen(String fullOpen) { this.fullOpen = fullOpen; }

    public String getFullClose() { return fullClose; }
    public void setFullClose(String fullClose) { this.fullClose = fullClose; }

    public String getOverload() { return overload; }
    public void setOverload(String overload) { this.overload = overload; }

    public String getPowerOk() { return powerOk; }
    public void setPowerOk(String powerOk) { this.powerOk = powerOk; }
}
