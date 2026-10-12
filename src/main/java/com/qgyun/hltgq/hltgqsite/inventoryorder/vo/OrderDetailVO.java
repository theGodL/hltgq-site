package com.qgyun.hltgq.hltgqsite.inventoryorder.vo;

import lombok.Data;

import java.util.List;

/**
 * 单据详情（主表 + 明细），入口：GET /inventory-order/detail?source=RK|CK&id=。
 */
@Data
public class OrderDetailVO {

    /** 来源：RK 入库单 / CK 出库单 */
    private String source;

    private String id;

    private String orderNo;

    private String bizType;

    private String bizTypeName;

    /** 单据日期 yyyy-MM-dd */
    private String orderDate;

    private String warehouseId;

    private String warehouseName;

    private String departmentId;

    private String departmentName;

    /** 供应商/来源单位（RK） 或 领用单位/工程名称（CK） */
    private String counterparty;

    private String operatorId;

    private String operatorName;

    private String remark;

    private List<Item> items;

    /** 明细行（快照字段固化于单据，防目录后续修改影响历史） */
    @Data
    public static class Item {

        private String materialId;

        /** 物资编号快照 */
        private String materialCode;

        /** 物资名称快照 */
        private String materialName;

        private String materialBrand;

        private String materialSpec;

        private String materialUnit;

        private Integer quantity;

        /** 变动前库存快照（仅出库行有值：台账 UPDATE 成功后回填） */
        private Integer stockBefore;
    }
}
