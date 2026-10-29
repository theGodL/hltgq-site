package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.util.List;

/**
 * 渠系树节点（渠系排序页面的展示结构；children 与平台层级树接口一致，按展示顺序排列）
 */
@Data
public class CanalTreeNodeVO {

    /** 渠系 id（站点档案 ywvyds 关联该值） */
    private String id;

    /** 渠系名称（平台字段 gfaegg） */
    private String name;

    /** 上级渠系 id（null = 顶级渠系） */
    private String parentId;

    /** 下属渠系（按平台展示顺序：同级 created_at 倒序） */
    private List<CanalTreeNodeVO> children;
}
