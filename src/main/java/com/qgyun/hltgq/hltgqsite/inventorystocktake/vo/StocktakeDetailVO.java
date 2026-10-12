package com.qgyun.hltgq.hltgqsite.inventorystocktake.vo;

import lombok.Data;

import java.util.List;

/**
 * 盘点单详情（主表 + 明细 + 附件），入口：GET /inventory-stocktake/detail?id=。
 */
@Data
public class StocktakeDetailVO {

    private String id;

    /** 盘点单号：PDD+yyyyMMdd+4位序号 */
    private String orderNo;

    /** 盘点日期 yyyy-MM-dd */
    private String checkDate;

    private String warehouseId;

    private String warehouseName;

    private String operatorId;

    /** 盘点人姓名快照 */
    private String operatorName;

    private String remark;

    /** 创建时间 yyyy-MM-dd HH:mm:ss */
    private String createdAt;

    private List<StocktakeItemVO> items;

    private List<StocktakeAttachmentVO> attachments;
}
