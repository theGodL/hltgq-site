package com.qgyun.hltgq.hltgqsite.inventorystocktake.vo;

import lombok.Data;

import java.util.List;

/**
 * 新增盘点单提交体（POST /inventory-stocktake/save）。
 * <p>明细行 bookQty 为页面带出/加行时的账面快照（保存时与台账实时值一致性校验，不一致整单拒绝）；
 * checkQty 为实盘数量（≥0）；attachmentIds 为本次提交的附件 ID 列表（上传时未关联，保存时回填）。
 */
@Data
public class StocktakeSaveVO {

    /** 盘点日期 yyyy-MM-dd（默认当天，可改） */
    private String checkDate;

    /** 盘点仓库 ID（必填） */
    private String warehouseId;

    /** 盘点人 ID（默认当前登录人，可改） */
    private String operatorId;

    /** 备注（≤50 字） */
    private String remark;

    private List<Item> items;

    /** 附件 ID 列表（附件上传接口返回的 id，可空） */
    private List<String> attachmentIds;

    /** 明细行：物资 + 账面快照 + 实盘数量（快照与实盘均由前端提交，账面以台账实时值校验） */
    @Data
    public static class Item {

        private String materialId;

        /** 账面库存快照（页面展示值） */
        private Integer bookQty;

        /** 实盘数量（≥0） */
        private Integer checkQty;
    }
}
