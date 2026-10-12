package com.qgyun.hltgq.hltgqsite.inventorystocktake.vo;

import lombok.Data;

/**
 * 盘点单明细行（详情/查看态）。
 * <p>物资六字段为保存时固化快照；bookQty 账面快照；checkQty 实盘；
 * profitQty/lossQty 为保存时计算的盘盈/盘亏（同步写流水，未差异行为 0）。
 */
@Data
public class StocktakeItemVO {

    private String materialId;

    /** 物资编号快照 */
    private String materialCode;

    /** 物资名称快照 */
    private String materialName;

    /** 品牌快照（可空） */
    private String materialBrand;

    /** 规格型号快照（可空） */
    private String materialSpec;

    /** 计量单位快照（可空） */
    private String materialUnit;

    /** 账面库存快照（保存时与台账实时值一致） */
    private Integer bookQty;

    /** 实盘数量 */
    private Integer checkQty;

    /** 盘盈（check_qty - book_qty，>0 时） */
    private Integer profitQty;

    /** 盘亏（book_qty - check_qty，>0 时） */
    private Integer lossQty;
}
