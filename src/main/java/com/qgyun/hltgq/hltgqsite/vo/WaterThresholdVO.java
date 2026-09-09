package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 水质阈值行 VO（t_auto_hltgq_water_threshold 中 type 含 #8# 的记录，供前端画预警线）
 * <p>字段语义与阈值表一致：threshold=警戒值、guarantee=保证值、num=阈值（预警比对值）。
 * 设备级配置可能存在多行（不同 device/指标），前端按 remark/device 自行区分。
 */
@Data
public class WaterThresholdVO {

    /** 主键 */
    private String id;

    /** 站点档案主键 UUID */
    private String site;

    /** 设备 ID（站级配置时为空） */
    private String device;

    /** 类型（#8# 水质，多选用 | 分割） */
    private String type;

    /** 描述（指标说明） */
    private String remark;

    /** 警戒值 */
    private BigDecimal threshold;

    /** 保证值 */
    private BigDecimal guarantee;

    /** 阈值（预警值，前端画营养盐占比/DO 预警线用） */
    private BigDecimal num;
}
