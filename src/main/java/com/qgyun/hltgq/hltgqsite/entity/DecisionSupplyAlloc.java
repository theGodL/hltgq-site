package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配水调度供水分配汇总（t_auto_hltgq_water_decision_supply_alloc）。
 * <p>模型 /decision V3 响应「供水分配汇总」：与「总计_全部旬」同口径同值，
 * 仅按 Excel sheet 名别名提供，便于前端按 sheet 名对接（《接口对接手册》4.7）；单行落库。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_decision_supply_alloc\"")
public class DecisionSupplyAlloc extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_decision_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 需水量(万方) */
    @TableField("\"demand_volume\"")
    private Double demandVolume;

    /** 建议供水量(万方) */
    @TableField("\"suggested_supply\"")
    private Double suggestedSupply;

    /** 塘坝补水量(万方) */
    @TableField("\"pond_supplement\"")
    private Double pondSupplement;

    /** 补水后总供水量(万方) */
    @TableField("\"supply_after_pond\"")
    private Double supplyAfterPond;

    /** 净需水量合计(万方) */
    @TableField("\"net_demand\"")
    private Double netDemand;

    /** 是否满足：#1#=是，#2#=否（模型原始取值 "是/否"，后端归一化） */
    @TableField("\"is_satisfied\"")
    private String isSatisfied;
}
