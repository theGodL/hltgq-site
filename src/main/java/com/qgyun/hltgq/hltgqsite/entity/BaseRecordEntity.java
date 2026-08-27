package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

/**
 * 模型方案主表实体基类：计算状态 + 逻辑删除 + 错误信息。
 * <p>status：calculating / completed / failed；del_flag：#1#=已删除，#2#=正常。
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
