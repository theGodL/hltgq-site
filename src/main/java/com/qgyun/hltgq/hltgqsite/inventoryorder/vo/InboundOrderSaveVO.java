package com.qgyun.hltgq.hltgqsite.inventoryorder.vo;

import lombok.Data;

import java.util.List;

/**
 * 新增入库单提交体（POST /inventory-order/inbound）。
 * <p>单据编号由后端生成（RK+yyyyMMdd+4位序号）；明细提交 materialId、quantity 与可选 spec
 * （规格型号允许手工填写，为空回退目录快照）；其余物资快照（编号/名称/品牌/单位）由后端按
 * materialId 回查 slaifi 固化，不接受前端传入。
 */
@Data
public class InboundOrderSaveVO {

    /** 入库类型：#1# 采购入库 / #2# 调拨入库 / #3# 退料入库 / #4# 其他入库 */
    private String inboundType;

    /** 单据日期 yyyy-MM-dd（默认当天） */
    private String orderDate;

    /** 仓库 ID（twufyx.id） */
    private String warehouseId;

    /** 供应商/来源单位（调拨入库填来源仓库名称） */
    private String sourceUnit;

    /** 归口科室 ID（平台部门 org.id，默认取仓库 dosozz，可改） */
    private String departmentId;

    /** 经办人 ID（平台用户 user.id，默认取仓库 ivplza，可改） */
    private String operatorId;

    /** 备注（可空） */
    private String remark;

    /** 物资明细（至少 1 行） */
    private List<Item> items;

    /** 明细行 */
    @Data
    public static class Item {

        /** 物资 ID（slaifi.id） */
        private String materialId;

        /** 入库数量（正整数） */
        private Integer quantity;

        /** 规格型号（选填，≤255 字；不为空时覆盖目录快照写入明细，为空回退目录值） */
        private String spec;
    }
}
