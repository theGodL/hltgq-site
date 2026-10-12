package com.qgyun.hltgq.hltgqsite.inventoryorder.vo;

import lombok.Data;

/**
 * 单据明细级导出扁平行（P1 导出）：一行 = 单据 × 物资明细，含单据头字段。
 * <p>后端返回 JSON 列表（裸 List，无分页，按条件全量），前端取全量后自行生成 Excel。
 */
@Data
public class OrderExportRowVO {

    private String orderNo;

    /** 来源编码：RK 入库单 / CK 出库单（Service 翻译 sourceName 后前端只取中文列） */
    private String source;

    /** 单据类型中文：入库单 / 出库单（Service 翻译） */
    private String sourceName;

    /** 业务类型编码（按 source 解释） */
    private String bizType;

    /** 业务类型中文（Service 按来源翻译） */
    private String bizTypeName;

    /** 单据日期 yyyy-MM-dd */
    private String orderDate;

    private String warehouseName;

    private String departmentName;

    /** 供应商/来源单位（RK） 或 领用单位/工程名称（CK） */
    private String counterparty;

    private String operatorName;

    private String remark;

    private String materialCode;

    private String materialName;

    private String materialBrand;

    private String materialSpec;

    private String materialUnit;

    private Integer quantity;
}
