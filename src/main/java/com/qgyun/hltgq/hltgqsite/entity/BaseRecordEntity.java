package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

/**
 * 模型方案主表实体基类：计算状态 + 逻辑删除 + 错误信息。
 * <p>del_flag：#1#=已删除，#2#=正常。
 * <p>status 双口径：墒情方案主表（平台建表名 t_auto_hltgq_water_moisture_detail，与实体名交叉）
 * 平台配单选字典，存 #1#（计算中）/#2#（已完成）/#3#（失败）（常量见 MoisturePredictService）；
 * 其余 6 张主表及防洪抗旱任务平台未配字典，存纯值
 * （常量见 ModelRecordCommonService，与前端轮询判断口径一致）。
 */
@Getter
@Setter
public class BaseRecordEntity extends BaseWaterEntity {

    /** 方案名称（前端传入或自动生成） */
    @TableField("\"scheme_name\"")
    private String schemeName;

    @TableField("\"status\"")
    private String status;

    @TableField("\"del_flag\"")
    private String delFlag;

    @TableField("\"error_msg\"")
    private String errorMsg;
}
