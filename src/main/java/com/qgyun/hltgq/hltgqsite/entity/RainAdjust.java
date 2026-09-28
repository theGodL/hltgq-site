package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 雨量补偿配置表 t_auto_hltgq_water_rain_adjust（监测数据删除方案 §3.2，v1.11 键设计）。
 * <p>维护入口＝站点+设备联动；唯一/匹配键 = {@code device}（mq 入库按 device.id 匹配补偿，
 * 兼容 MQTT 链路无 stcd 报文，见方案附十二）；{@code site} = 站点ID（设备表 site 值，
 * 识别/展示/联查用）；{@code stcd} = RTU 站号识别辅助列（RabbitMQ 链路有值、MQTT 链路为空，
 * 不参与匹配）。
 * <p>生效：表单修改 ≤5 分钟（mq 缓存 TTL）自动生效，无需重启；停用（enabled=false）＝按原值入库；
 * 预置自检（mq 按 stcd 列）对本表"无任何记录"的出厂默认站告警——删除配置行须评估此影响。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".t_auto_hltgq_water_rain_adjust")
public class RainAdjust extends BaseWaterEntity {

    /** 站点ID（设备表 site 值；与 device 同属两条链路共有的归属锚点） */
    @TableField("\"site\"")
    private String site;

    /** 设备ID（device.id 值；唯一/匹配键——入库按此匹配补偿） */
    @TableField("\"device\"")
    private String device;

    /** 站点 RTU 站号（如 3206400007；识别辅助列，不参与匹配，可空） */
    @TableField("\"stcd\"")
    private String stcd;

    /** 入库补偿值（如 -34.00，可正可负） */
    @TableField("\"offset_value\"")
    private BigDecimal offsetValue;

    /** 启用开关（停用=无补偿入库，不得回退出厂兜底） */
    @TableField("\"enabled\"")
    private Boolean enabled;

    /** 备注（调整原因、巡检记录） */
    @TableField("\"remark\"")
    private String remark;
}
