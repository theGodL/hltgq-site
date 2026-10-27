package com.qgyun.hltgq.hltgqsite.inventorymaterial.vo;

import lombok.Data;

/**
 * 物资目录新增/编辑请求。
 * <p>scope=emergency 时类别由后端强制「应急物资」，前端传值被忽略（保证应急侧不可改）；
 * 其余情况按 category 落库，缺省为「常用物资」。
 */
@Data
public class MaterialSaveVO {

    /** 编辑时必填；新增留空 */
    private String id;

    /** 页面视角：emergency / common / 空 */
    private String scope;

    /** 物资编号（留空自动生成 WZ+时间戳） */
    private String code;

    /** 物资名称（必填） */
    private String name;

    /** 品牌 */
    private String brand;

    /** 规格型号 */
    private String spec;

    /** 计量单位（必填） */
    private String unit;

    /** 安全库存（数字文本，可空） */
    private String safetyStock;

    /** 默认仓库 ID（可空） */
    private String defaultWarehouseId;

    /** 备注 */
    private String remark;

    /** 物资类别（仅库存管理视角生效） */
    private String category;
}
