package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 水资源配置方案主表（t_auto_hltgq_water_allocate_record）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_allocate_record\"")
public class AllocateRecord extends BaseRecordEntity {

    /** 模式：恒 auto（2026-10-26 定稿，manual 已下线；历史 manual 方案保留原值） */
    @TableField("\"mode\"")
    private String mode;

    /** 来水情景（auto 模式） */
    @TableField("\"scenario\"")
    private String scenario;

    /** 保证率（auto 模式） */
    @TableField("\"guarantee_rate\"")
    private String guaranteeRate;

    /** 渠系水利用系数 */
    @TableField("\"canal_eff\"")
    private Double canalEff;

    /** 塘坝可供水量情景：50% / 75% / 90% / 多年平均（2026-10-26 字段名定稿 pondScenario） */
    @TableField("\"pond_option\"")
    private String pondScenario;

    /** 初始库水位(m)（2026-10-26 字段名定稿 initialLevel） */
    @TableField("\"start_level\"")
    private Double initialLevel;

    /** 汛限水位(m) */
    @TableField("\"flood_limit_level\"")
    private Double floodLimitLevel;

    /** 溢洪道开启水位(m)（2026-10-26 字段名定稿 spillwayOpenLevel） */
    @TableField("\"max_level\"")
    private Double spillwayOpenLevel;

    /** 请求体归档 JSON */
    @TableField("\"request_json\"")
    private String requestJson;

    /** V3 两视图原始响应快照 JSON（灌区水资源配置/水库逐旬清单/水库月汇总/水库年汇总/diagnostics/口径变更），详情按此组装 irrigation/reservoir */
    @TableField("\"views_json\"")
    private String viewsJson;

    /** 关联需水方案ID（auto 模式内部 /demand 产生的方案ID） */
    @TableField("\"demand_record_id\"")
    private String demandRecordId;

    /** 关联水量损失方案ID（可选；年渗漏量 annual_leakage_wan_m3 提取进 /allocate） */
    @TableField("\"loss_record_id\"")
    private String lossRecordId;

    /** 总来水(万方)：SUM(tenday.bp_inflow) */
    @TableField("\"total_inflow\"")
    private Double totalInflow;

    /** 总需水(万方)：SUM(tenday.total_demand) */
    @TableField("\"total_demand\"")
    private Double totalDemand;

    /** 总供水(万方)：SUM(tenday.total_supply) */
    @TableField("\"total_supply\"")
    private Double totalSupply;

    /** 缺水量(万方)：MAX(total_demand-total_supply, 0) */
    @TableField("\"deficit\"")
    private Double deficit;

    /** 弃水量(万方)：SUM(tenday.spill) */
    @TableField("\"spill\"")
    private Double spill;
}
