package com.qgyun.hltgq.hltgqsite.inventorymaterial.vo;

import lombok.Data;

/**
 * 物资目录行（库存管理 / 应急物资管理共用）。
 * <p>safetyStockRaw 为目录 unqvqe 原文（varchar），前端直接展示与编辑；
 * category 由 {@code COALESCE(NULLIF(TRIM("type"),''),'#1#')} 归一，历史空值即常用物资（#1#/#2#）。
 */
@Data
public class MaterialPageItemVO {

    private String id;

    /** 物资编号（higsdd） */
    private String code;

    /** 物资名称（avlsdv） */
    private String name;

    /** 品牌（fqztqm） */
    private String brand;

    /** 规格型号（iwnyuk） */
    private String spec;

    /** 计量单位（boipik） */
    private String unit;

    /** 安全库存原文（unqvqe） */
    private String safetyStockRaw;

    /** 默认仓库 ID（fmewia） */
    private String defaultWarehouseId;

    /** 默认仓库名称（twufyx.xoysox） */
    private String defaultWarehouseName;

    /** 备注（epvuta） */
    private String remark;

    /** 物资类别：常用物资 / 应急物资 */
    private String category;

    /** 创建时间（yyyy-MM-dd HH:mm） */
    private String createdAt;
}
