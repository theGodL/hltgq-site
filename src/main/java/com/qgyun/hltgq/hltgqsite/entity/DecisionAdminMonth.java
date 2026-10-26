package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配水调度行政区月汇总（t_auto_hltgq_water_decision_admin_month）。
 * <p>模型 /decision V3 响应「行政区月汇总」：按 年份×月份×行政区 聚合的自流/提灌建议供水量
 * （按 面积分配.xlsx 县级面积比例估算，非逐支渠精确分类，《接口对接手册》4.7）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_decision_admin_month\"")
public class DecisionAdminMonth extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_decision_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 年份 */
    @TableField("\"year\"")
    private Integer year;

    /** 月份（5~10） */
    @TableField("\"month\"")
    private Integer month;

    /** 行政区 */
    @TableField("\"district\"")
    private String district;

    /** 自流建议供水量(万方) */
    @TableField("\"gravity_supply\"")
    private Double gravitySupply;

    /** 提灌建议供水量(万方) */
    @TableField("\"lift_supply\"")
    private Double liftSupply;
}
