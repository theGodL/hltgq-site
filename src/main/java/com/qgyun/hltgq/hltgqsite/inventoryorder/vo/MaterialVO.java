package com.qgyun.hltgq.hltgqsite.inventoryorder.vo;

import lombok.Data;

/**
 * 物资目录候选（t_auto_hltgq_knc3g_slaifi）：入库单明细「从目录选择」与行内选择器。
 * <p>safetyStockRaw 为 unqvqe 原文（varchar 存储，如 '100'），建账时由后端解析为
 * 台账 vhvhja；defaultWarehouseId（fmewia）仅作入库选物资时的默认仓库提示，不做硬过滤。
 */
@Data
public class MaterialVO {

    private String id;

    /** 物资编号（higsdd） */
    private String code;

    /** 物资名称（avlsdv） */
    private String name;

    /** 品牌/生产厂家（fqztqm） */
    private String brand;

    /** 规格型号（iwnyuk） */
    private String spec;

    /** 计量单位（boipik） */
    private String unit;

    /** 安全库存下限原文（unqvqe） */
    private String safetyStockRaw;

    /** 默认仓库 ID（fmewia，可空） */
    private String defaultWarehouseId;
}
