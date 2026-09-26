package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 水质阈值行 VO（t_auto_hltgq_water_threshold 中类型含 #8# 的记录，供前端画预警线）。
 * <p>字段语义与阈值表一致：threshold=警戒值、guarantee=保证值、num=阈值（预警比对值）。
 * 类型读自 zvieyb（历史列 type 兜底，见 WaterQualityMapper#selectThresholdsBySites）。
 * <p>指标：{@code zb} 存指标编码（= 监测数据表字段名，如 nh3n / dox / wt），与响应中的指标字段同名，
 * 前端按 {@code zb} 精确取行画趋势参考线；存量行未落 {@code zb} 时可回退按 {@code remark} 关键字匹配（兼容口径）。
 * 页面不做数值超限标红（2026-09-26 定：标红口径与其他监测页一致，只标采集时间中断）。
 * 设备级配置可能存在多行（不同 device/指标），前端按 zb/device 自行区分。
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

    /** 监测指标编码（= 监测数据表字段名，取自阈值字典：nh3n/codmn/bod5/tp/tn/dox/wt；未落指标的历史行为 null） */
    private String zb;

    /** 告警方向（#1# 高于警戒值触发 / #2# 低于警戒值触发；用于趋势参考线的上限/下限文案，页面不做超限标红） */
    private String alarmDir;

    /** 描述（指标说明，历史行的指标线索） */
    private String remark;

    /** 警戒值 */
    private BigDecimal threshold;

    /** 保证值 */
    private BigDecimal guarantee;

    /** 阈值（预警值，前端画营养盐占比/DO 预警线用） */
    private BigDecimal num;
}
