package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配水调度明细扩展表行（t_auto_hltgq_water_decision_extra_table）。
 * <p>模型响应 4 张表的原始快照逐行落库（键名以模型实测为准，行内容原样归档）：
 * 总计_全部旬（对象→单行）、月汇总_5至10月、分灌区汇总、片区汇总（数组→每元素一行）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_decision_extra_table\"")
public class DecisionExtraTable extends BaseWaterEntity {

    /** 关联方案ID（t_auto_hltgq_water_decision_record.id） */
    @TableField("\"record_id\"")
    private String recordId;

    /** 表标识：总计_全部旬 / 月汇总_5至10月 / 分灌区汇总 / 片区汇总 */
    @TableField("\"table_key\"")
    private String tableKey;

    /** 行标识：数组行名称（分灌区/片区名）或序号；对象表固定「总计」 */
    @TableField("\"row_key\"")
    private String rowKey;

    /** 行内容 JSON（原始键值快照，键名以模型实测为准） */
    @TableField("\"row_json\"")
    private String rowJson;

    /** 行排序（数组行 1~n；对象表恒 1） */
    @TableField("\"sort_order\"")
    private Double sortOrder;
}
