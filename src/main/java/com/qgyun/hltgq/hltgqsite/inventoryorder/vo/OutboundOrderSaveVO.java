package com.qgyun.hltgq.hltgqsite.inventoryorder.vo;

import lombok.Data;

import java.util.List;

/**
 * 新增出库单提交体（POST /inventory-order/outbound）。
 * <p>明细按台账行（ledgerId）提交：只能从所选仓库台账（库存 > 0）中选择；
 * 数量前置校验 ≤ 可用库存，最终以台账 UPDATE 影响行数兜底（并发不超卖）。
 */
@Data
public class OutboundOrderSaveVO {

    /** 出库类型：#1# 工程领用 / #2# 调拨出库 / #3# 退料出库 / #4# 报废出库 / #5# 其他出库 */
    private String outboundType;

    /** 单据日期 yyyy-MM-dd（默认当天） */
    private String orderDate;

    /** 仓库 ID（twufyx.id） */
    private String warehouseId;

    /** 领用单位/工程名称（调拨出库填目标仓库名称） */
    private String receiveUnit;

    /** 归口科室 ID（平台部门 org.id，默认取仓库 dosozz，可改） */
    private String departmentId;

    /** 经办人 ID（平台用户 user.id，默认取仓库 ivplza，可改） */
    private String operatorId;

    /** 备注（可空） */
    private String remark;

    /** 页面视角（可选）：emergency 应急物资；不传/common = 常用物资（保存时按此校验明细物资类别） */
    private String scope;

    /** 物资明细（至少 1 行） */
    private List<Item> items;

    /** 明细行 */
    @Data
    public static class Item {

        /** 台账行 ID（tzkioe.id，出库台账选择器选中行） */
        private String ledgerId;

        /** 出库数量（正整数；前置校验 ≤ 可用库存，最终以 UPDATE 影响行数为准） */
        private Integer quantity;

        /** 规格型号（选填，≤255 字；不为空时覆盖台账快照写入明细，为空回退台账值） */
        private String spec;
    }
}
