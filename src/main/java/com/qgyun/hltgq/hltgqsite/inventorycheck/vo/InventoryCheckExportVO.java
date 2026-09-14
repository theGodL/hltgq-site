package com.qgyun.hltgq.hltgqsite.inventorycheck.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 设备盘点导出行（/inventory-check/export 响应）。
 * <p>数据来源：盘点表 t_auto_hltgq_knc3g_apsypa。
 * 盘点明细为 himanc JSON 数组的稀疏对象（键存在才有值），缺键恒 null；
 * 单位 vbcddc 缺键按平台默认补「个」；附件 xfeiey 为文件管理记录 id（单选），
 * 文件名/签名地址经文件服务尽力解析（失败/null 不影响主数据）。
 */
@Data
public class InventoryCheckExportVO {

    /** 记录 ID（主键） */
    private String id;

    /** 编号（yogpxf） */
    private String code;

    /** 盘点日期（khntiy，统一 yyyy-MM-dd 输出） */
    private String checkDate;

    /** 盘点明细（himanc JSON 数组解析；空/非法 JSON 返回空数组） */
    private List<Detail> details;

    /** 备注（ymiwpu） */
    private String remark;

    /** 附件文件 id（xfeiey 原文；留空为 null） */
    private String attachmentId;

    /** 附件文件名（文件服务解析；无会话/服务失败/复合引用时 null） */
    private String attachmentName;

    /** 附件签名地址（文件服务解析；同 attachmentName 条件为 null） */
    private String attachmentUrl;

    /** 盘点日期原始值（khntiy，内部规范化用，不出 JSON） */
    @JsonIgnore
    private String checkDateRaw;

    /** 盘点明细 JSON 原文（himanc，内部解析用，不出 JSON） */
    @JsonIgnore
    private String detailsJson;

    /**
     * 盘点明细行（himanc JSON 元素，稀疏对象：键存在才有值）。
     * <p>键与表单子字段一一对应（iwckqw/acsoil/ezzjcs/ekfgwh/eihdbh/yuwdms/vbcddc），
     * 数值列按文本取出转数值（空/非法恒 null），单位缺键补平台默认「个」。
     */
    @Data
    public static class Detail {

        /** 物资编号（iwckqw） */
        private String itemCode;

        /** 物资名称（acsoil） */
        private String itemName;

        /** 当前账面库存（ezzjcs，文本转数值） */
        private BigDecimal bookQty;

        /** 本次盘点数量（ekfgwh，文本转数值） */
        private BigDecimal checkQty;

        /** 盘盈数量（eihdbh，文本转数值） */
        private BigDecimal profitQty;

        /** 盘亏数量（yuwdms，文本转数值） */
        private BigDecimal lossQty;

        /** 单位（vbcddc；缺键补平台默认「个」） */
        private String unit;
    }
}
