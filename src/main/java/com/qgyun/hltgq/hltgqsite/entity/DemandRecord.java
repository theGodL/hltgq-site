package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 需水预测方案主表（t_auto_hltgq_water_demand_record）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_demand_record\"")
public class DemandRecord extends BaseRecordEntity {

    /** 保证率：50 / 75 / 90 / 多年平均 */
    @TableField("\"guarantee_rate\"")
    private String guaranteeRate;

    /** 渠系水利用系数（0~1） */
    @TableField("\"canal_eff\"")
    private Double canalEff;

    /** 目标总毛需水量(万方)，null 不约束 */
    @TableField("\"target_total\"")
    private Double targetTotal;

    /** 请求体归档 JSON */
    @TableField("\"request_json\"")
    private String requestJson;

    /** 总毛需水量(万方)（summary.总毛需水量_万方） */
    @TableField("\"total_demand\"")
    private Double totalDemand;

    /** 支渠数量（summary.支渠数量） */
    @TableField("\"branch_count\"")
    private Double branchCount;

    /** 灌溉面积(亩)：支渠面积按支渠名去重求和 */
    @TableField("\"irrigated_area\"")
    private Double irrigatedArea;

    /** 需水高峰旬：argmax(SUM(demand_volume) GROUP BY tenday_label) */
    @TableField("\"peak_tenday\"")
    private String peakTenday;
}
