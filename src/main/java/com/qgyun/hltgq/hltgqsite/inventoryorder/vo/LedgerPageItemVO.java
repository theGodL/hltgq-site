package com.qgyun.hltgq.hltgqsite.inventoryorder.vo;

import lombok.Data;

/**
 * 物资台账行（P4 列表 / 台账导出）。
 * <p>列口径：物资编号=slaifi.higsdd（JOIN jzikio）、品牌=qhtxml、名称=wpxwwi、规格=hughww、
 * 单位=mddokm、所属仓库=oywydb→twufyx.xoysox、库存数量=ejgxlq、安全库存=vhvhja、
 * 状态=mojktw（#1# 正常 / #2# 偏低，Service 翻译）。
 */
@Data
public class LedgerPageItemVO {

    /** 台账行 ID（tzkioe.id） */
    private String id;

    private String materialId;

    /** 物资编号（slaifi.higsdd） */
    private String materialCode;

    /** 品牌（tzkioe.qhtxml） */
    private String brand;

    /** 物资名称（tzkioe.wpxwwi） */
    private String name;

    /** 规格型号（tzkioe.hughww） */
    private String spec;

    /** 单位（tzkioe.mddokm） */
    private String unit;

    private String warehouseId;

    private String warehouseName;

    /** 库存数量（tzkioe.ejgxlq） */
    private Integer quantity;

    /** 安全库存（tzkioe.vhvhja，可空） */
    private Integer safetyStock;

    /** 库存状态编码（mojktw：#1# 正常 / #2# 偏低） */
    private String statusCode;

    /** 库存状态中文（Service 翻译） */
    private String statusName;

    /** 所属部门 ID（czdwpj） */
    private String departmentId;
}
