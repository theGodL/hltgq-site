package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 复合主键：STCD + TM
 */
@Data
@TableName("\"qixiao-apaas\".t_auto_hltgq_water_rain_info")
public class StPptnR {

    @TableField("\"STCD\"")
    private String stcd;

    @TableField("\"TM\"")
    private LocalDateTime tm;

    /** 当前降雨量 — 水文日累计（8:00 ~ 当前），每日8:00归零 */
    @TableField("\"DRP\"")
    private BigDecimal drp;

    @TableField("\"INTV\"")
    private Integer intv;

    @TableField("\"PDR\"")
    private BigDecimal pdr;

    /** 累计雨量 — RTU安装至今总累计，永不归零 */
    @TableField("\"DYP\"")
    private BigDecimal dyp;

    @TableField("\"WTH\"")
    private String wth;

    /**
     * 软删标记：true=业主巡检删除的问题数据行（监测数据删除方案；不参与入库计算前值/基线，保留占位）。
     * <p>列由平台 DDL 新增（NOT NULL DEFAULT false），列名小写无需引号；mq 入库过滤依赖本列。
     */
    @TableField("deleted")
    private Boolean deleted;

    /**
     * 最近一次软删状态变更时刻（删除/恢复均刷新，恢复不清空；由 DB 时钟 now() 写入）。
     * <p>仅供历史数据管理表单展示与留痕，不参与任何计算（v1.9 无重算机制）。
     */
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    /** 最近一次操作人（平台用户 id，随 deleted_at 一并刷新） */
    @TableField("deleted_by")
    private String deletedBy;
}
