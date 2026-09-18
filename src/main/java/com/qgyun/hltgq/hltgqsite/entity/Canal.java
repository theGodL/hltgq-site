package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 渠系管理表 t_auto_hltgq_knc3g_egvnhw（层级自关联：cyjihq → 本表 id）
 */
@Data
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_knc3g_egvnhw\"")
public class Canal {

    @TableField("\"id\"")
    private String id;

    /** 渠系名称 */
    @TableField("\"gfaegg\"")
    private String name;

    /** 上级渠系 id（NULL = 顶级渠系） */
    @TableField("\"cyjihq\"")
    private String parentId;
}
