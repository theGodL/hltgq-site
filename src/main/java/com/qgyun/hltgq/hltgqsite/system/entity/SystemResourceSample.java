package com.qgyun.hltgq.hltgqsite.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qgyun.hltgq.hltgqsite.entity.BaseWaterEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 资源采样实体（t_auto_hltgq_sys_resource_sample，2026-09 新增，页面 system-monitor.html）。
 * <p>定时任务按固定间隔（默认 5 分钟）采集一行快照落库——“监控信息保存”要求的落实，
 * 供历史回溯、报警子系统取用；磁盘/表空间明细存 JSON 文本列。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_sys_resource_sample\"")
public class SystemResourceSample extends BaseWaterEntity {

    /** 采样时间 */
    @TableField("\"sample_time\"")
    private LocalDateTime sampleTime;

    /** 采集视角：host 宿主机（挂载 /host 时）/ container 容器（未挂载时） */
    @TableField("\"scope\"")
    private String scope;

    /** 主机名 */
    @TableField("\"hostname\"")
    private String hostname;

    /** CPU 使用率(%) */
    @TableField("\"cpu_percent\"")
    private Double cpuPercent;

    /** 系统负载（1/5/15 分钟平均） */
    @TableField("\"load1\"")
    private Double load1;

    @TableField("\"load5\"")
    private Double load5;

    @TableField("\"load15\"")
    private Double load15;

    /** 内存（字节）与使用率(%) */
    @TableField("\"mem_total\"")
    private Long memTotal;

    @TableField("\"mem_used\"")
    private Long memUsed;

    @TableField("\"mem_percent\"")
    private Double memPercent;

    /** 交换区（字节）与使用率(%) */
    @TableField("\"swap_total\"")
    private Long swapTotal;

    @TableField("\"swap_used\"")
    private Long swapUsed;

    @TableField("\"swap_percent\"")
    private Double swapPercent;

    /** 磁盘分区快照（JSON 数组：mount/total/used/avail/percent） */
    @TableField("\"disk_json\"")
    private String diskJson;

    /** 数据库表空间快照（JSON 数组：name/sizeBytes/capacityBytes/percent） */
    @TableField("\"db_json\"")
    private String dbJson;
}
