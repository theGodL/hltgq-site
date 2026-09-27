package com.qgyun.hltgq.hltgqsite.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qgyun.hltgq.hltgqsite.entity.BaseWaterEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 故障记录实体（t_auto_hltgq_sys_fault_record，2026-09 新增，页面 system-monitor.html）。
 * <p>两类共用一张表，category 区分：soft 软件故障（GlobalExceptionHandler 上游依赖异常埋点）/
 * system 系统故障（服务重启、依赖中断、资源越限）。
 * <p>同键（category + fault_type + fault_source）在抑制窗口内只记一条，防止异常风暴写爆表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_sys_fault_record\"")
public class SystemFaultRecord extends BaseWaterEntity {

    /** 类别：soft 软件故障 / system 系统故障 */
    @TableField("\"category\"")
    private String category;

    /** 故障类型：上游服务调用失败 / 服务启动 / 资源越限 */
    @TableField("\"fault_type\"")
    private String faultType;

    /** 来源：服务名（mq/device/archive/model/auth）或资源名（CPU/内存/磁盘挂载点/表空间名） */
    @TableField("\"fault_source\"")
    private String faultSource;

    /** 级别：error / warn */
    @TableField("\"fault_level\"")
    private String faultLevel;

    /** 描述（含阈值与实际值等） */
    @TableField("\"fault_desc\"")
    private String faultDesc;

    /** 发生时间 */
    @TableField("\"occur_time\"")
    private LocalDateTime occurTime;
}
