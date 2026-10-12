package com.qgyun.hltgq.hltgqsite.inventoryorder.vo;

import lombok.Data;

/**
 * 单据列表行（P1：入库单 UNION ALL 出库单）。
 * <p>source 区分来源（RK 入库 / CK 出库），业务类型展示一律按 source + bizType 联合映射；
 * counterparty 为两表往来字段合并列（入库=source_unit 供应商/来源，出库=receive_unit 领用单位）。
 */
@Data
public class OrderPageItemVO {

    /** 来源：RK 入库单 / CK 出库单 */
    private String source;

    private String id;

    private String orderNo;

    /** 业务类型编码（按 source 解释） */
    private String bizType;

    /** 业务类型中文（Service 按 source + bizType 翻译） */
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
}
