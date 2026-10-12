package com.qgyun.hltgq.hltgqsite.inventorystocktake.vo;

import lombok.Data;

import java.util.List;

/**
 * 盘点单列表行（P6 列表页）。
 * <p>attachments 为该单附件摘要（id/名称/大小），列表附件列展示与下载/预览入口，
 * 由 Service 在分页查询后批量装配，避免逐行查询。
 */
@Data
public class StocktakePageItemVO {

    private String id;

    private String orderNo;

    /** 盘点日期 yyyy-MM-dd */
    private String checkDate;

    private String warehouseId;

    private String warehouseName;

    private String operatorId;

    private String operatorName;

    private String remark;

    /** 创建时间 yyyy-MM-dd HH:mm:ss */
    private String createdAt;

    private List<StocktakeAttachmentVO> attachments;
}
