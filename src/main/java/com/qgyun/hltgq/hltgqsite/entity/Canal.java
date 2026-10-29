package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

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

    /**
     * 创建时间：平台层级树接口对同级节点按该列倒序展示（无独立排序字段），
     * 「渠系排序」页面通过改写该值调整展示顺序
     */
    @TableField("\"created_at\"")
    private LocalDateTime createdAt;
}
