package com.qgyun.hltgq.hltgqsite.inventoryorder.vo;

import lombok.Data;

/**
 * 库存变动流水行（P5 出入库记录 / 流水导出）。
 * <p>按台账行（materialId + warehouseId）过滤，created_at 倒序逐笔追溯；
 * changeType（#1# 入库 / #2# 出库）与 bizType（按 orderKind 解释的业务类型）均附中文翻译。
 */
@Data
public class StockChangeVO {

    private String id;

    /** 变动类型编码（#1# 入库 / #2# 出库 / #3# 盘盈 / #4# 盘亏预留） */
    private String changeType;

    /** 变动类型中文（Service 翻译） */
    private String changeTypeName;

    /** 单据类别编码（#1# 入库单 / #2# 出库单 / #3# 盘点单预留） */
    private String orderKind;

    private String orderId;

    /** 单据编号快照 */
    private String orderNo;

    /** 业务类型编码（按 orderKind 解释） */
    private String bizType;

    /** 业务类型中文（Service 按 orderKind 翻译） */
    private String bizTypeName;

    /** 变动数量（正整数，方向由 changeType 表达） */
    private Integer changeQty;

    /** 变动前库存 */
    private Integer qtyBefore;

    /** 变动后库存 */
    private Integer qtyAfter;

    /** 往来单位/领用人快照 */
    private String counterparty;

    /** 经手人快照 */
    private String operatorName;

    /** 单据日期 yyyy-MM-dd */
    private String orderDate;

    /** 流水生成时间 yyyy-MM-dd HH:mm:ss（倒序依据） */
    private String createdAt;

    private String warehouseId;

    private String warehouseName;
}
