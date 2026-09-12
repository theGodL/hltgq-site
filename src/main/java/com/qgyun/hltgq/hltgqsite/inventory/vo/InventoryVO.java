package com.qgyun.hltgq.hltgqsite.inventory.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 设备库存行（/inventory/list 响应）。
 * <p>数据来源：库存表 t_auto_hltgq_knc3g_tzkioe + 部门表 t_apaas_uc_org（czdwpj 解析部门名称）。
 * <p>枚举字段返回中文（category/stockStatus）并附编码原文（categoryCode/stockStatusCode，权威值）；
 * 库存明细为 obompq JSON 数组的稀疏对象（键存在才有值），缺键恒 null，不补造数据。
 */
@Data
public class InventoryVO {

    /** 记录 ID（主键） */
    private String id;

    /** 所属部门 ID（czdwpj，t_apaas_uc_org.id） */
    private String departmentId;

    /** 所属部门名称（t_apaas_uc_org.name；部门记录缺失时为 null） */
    private String departmentName;

    /** 物资类别（jsnowd 翻译：#1# 应急发电机、#2# 水位计、#3# 流量仪、#4# 雨量仪；未知编码原样返回） */
    private String category;

    /** 物资类别编码原文（jsnowd，权威值） */
    private String categoryCode;

    /** 库存数量（ejgxlq，文本转数值；空/非法恒 null） */
    private BigDecimal stockQty;

    /** 安全库存（vhvhja，文本转数值；空/非法恒 null） */
    private BigDecimal safetyStock;

    /** 单位（kxhsks，如「个」；空值恒 null） */
    private String unit;

    /** 库存状态（mojktw 翻译：#1# 安全库存、#2# 库存预警；未知编码原样返回） */
    private String stockStatus;

    /** 库存状态编码原文（mojktw，权威值） */
    private String stockStatusCode;

    /** 库存明细（obompq JSON 数组解析；空/非法 JSON 返回空数组） */
    private List<Detail> details;

    /** 库存数量原文（ejgxlq 列值，内部转换用，不出 JSON） */
    @JsonIgnore
    private String stockQtyText;

    /** 安全库存原文（vhvhja 列值，内部转换用，不出 JSON） */
    @JsonIgnore
    private String safetyStockText;

    /** 库存明细 JSON 原文（obompq，内部解析用，不出 JSON） */
    @JsonIgnore
    private String detailsJson;

    /**
     * 库存明细行（obompq JSON 元素，稀疏对象：键存在才有值）。
     * <p>键与表单子字段一一对应（tgmziv/lmtibt/ihcepa/lwlpnf/tzqqhz/jynpfu/uxsxkn/dsanix），
     * 真实样例仅出现部分键（tzqqhz/lwlpnf/uxsxkn 未出现），解析按缺键防御。
     */
    @Data
    public static class Detail {

        /** 编号（tgmziv） */
        private String code;

        /** 物资名称（lmtibt） */
        private String name;

        /** 品牌型号（ihcepa） */
        private String brand;

        /** 数量（tzqqhz；缺省按公式「入库数量 − 出库数量」计算，uxsxkn 隐藏字段缺失按 0 计） */
        private BigDecimal quantity;

        /** 单位（lwlpnf；样例未出现恒 null） */
        private String unit;

        /** 入库数量（jynpfu） */
        private BigDecimal inboundQty;

        /** 出库数量（uxsxkn；样例未出现恒 null） */
        private BigDecimal outboundQty;

        /** 累计出库数量（dsanix） */
        private BigDecimal outboundTotalQty;
    }
}
