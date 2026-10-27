package com.qgyun.hltgq.hltgqsite.inventoryorder.vo;

import lombok.Data;

/**
 * 出库台账候选（tzkioe 行，oywydb = 所选仓库 且 库存 > 0）：出库单明细选择器数据源。
 * <p>ledgerId 为台账行主键（保存时出库明细按台账行提交，乐观锁扣减依据）；
 * quantity 为当前可用库存（前端行内只读提示）；safetyStock 为安全库存（实时取物资目录，低于提示用）。
 */
@Data
public class StockLedgerOptionVO {

    /** 台账行 ID（tzkioe.id） */
    private String ledgerId;

    private String materialId;

    /** 物资编号（slaifi.higsdd） */
    private String code;

    /** 物资名称（tzkioe.wpxwwi 快照） */
    private String name;

    /** 品牌（tzkioe.qhtxml 快照） */
    private String brand;

    /** 规格型号（tzkioe.hughww 快照） */
    private String spec;

    /** 单位（tzkioe.mddokm 快照） */
    private String unit;

    /** 当前可用库存（tzkioe.ejgxlq） */
    private Integer quantity;

    /** 安全库存（实时取物资目录 slaifi.unqvqe，可空） */
    private Integer safetyStock;

    private String warehouseId;

    /** 物资类别（slaifi.type 编码归一，空值视作常用物资 #1#） */
    private String category;
}
