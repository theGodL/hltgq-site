package com.qgyun.hltgq.hltgqsite.inventoryorder;

import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.PickerOptionVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.qgyun.hltgq.hltgqsite.model.util.DictUtils;

/**
 * 出入库业务类型字典与单据状态口径（单一维护点，前端经 /inventory-order/meta 获取，不写死）。
 * <p>入库类型（inbound_order.inbound_type）与出库类型（outbound_order.outbound_type）为两套独立枚举：
 * 两表同值编码含义不同（如 #1# 入库=采购入库、出库=工程领用），展示与筛选一律按
 * 「来源（RK/CK）+ 编码」联合映射，禁止单凭编码判断。
 * <p>类型下拉选项的 value 采用联合键 RK#1# / CK#1#（来源前缀 + 编码），列表筛选参数 bizType 同格式。
 */
public final class InventoryTypeDict {

    /** 单据来源：入库单 */
    public static final String SOURCE_INBOUND = "RK";
    /** 单据来源：出库单 */
    public static final String SOURCE_OUTBOUND = "CK";

    /** 流水变动类型：入库 */
    public static final String CHANGE_TYPE_IN = "#1#";
    /** 流水变动类型：出库 */
    public static final String CHANGE_TYPE_OUT = "#2#";
    /** 流水变动类型：盘盈 / 盘亏（盘点单专用，设备盘点启用） */
    public static final String CHANGE_TYPE_PROFIT = "#3#";
    public static final String CHANGE_TYPE_LOSS = "#4#";

    /** 流水单据类别：入库单 */
    public static final String ORDER_KIND_INBOUND = "#1#";
    /** 流水单据类别：出库单 */
    public static final String ORDER_KIND_OUTBOUND = "#2#";
    /** 流水单据类别：盘点单（设备盘点启用） */
    public static final String ORDER_KIND_STOCKTAKE = "#3#";

    /** 入库类型编码 → 中文（inbound_type） */
    private static final Map<String, String> INBOUND_TYPES = new LinkedHashMap<>();
    /** 出库类型编码 → 中文（outbound_type） */
    private static final Map<String, String> OUTBOUND_TYPES = new LinkedHashMap<>();

    /** 库存状态编码 → 中文（tzkioe.mojktw：#1# 正常 / #2# 偏低，按设计方案口径；
     * 既有「设备库存查询」页同字典值显示为「安全库存/库存预警」，文案不同、判断一致） */
    private static final Map<String, String> STOCK_STATUS = new LinkedHashMap<>();

    /** 流水变动类型编码 → 中文（kcjl.change_type；#3# 盘盈 / #4# 盘亏由设备盘点启用） */
    private static final Map<String, String> CHANGE_TYPES = new LinkedHashMap<>();

    static {
        INBOUND_TYPES.put("#1#", "采购入库");
        INBOUND_TYPES.put("#2#", "调拨入库");
        INBOUND_TYPES.put("#3#", "退料入库");
        INBOUND_TYPES.put("#4#", "其他入库");

        OUTBOUND_TYPES.put("#1#", "工程领用");
        OUTBOUND_TYPES.put("#2#", "调拨出库");
        OUTBOUND_TYPES.put("#3#", "退料出库");
        OUTBOUND_TYPES.put("#4#", "报废出库");
        OUTBOUND_TYPES.put("#5#", "其他出库");

        STOCK_STATUS.put("#1#", "正常");
        STOCK_STATUS.put("#2#", "偏低");

        CHANGE_TYPES.put("#1#", "入库");
        CHANGE_TYPES.put("#2#", "出库");
        CHANGE_TYPES.put("#3#", "盘盈");
        CHANGE_TYPES.put("#4#", "盘亏");
    }

    private InventoryTypeDict() {
    }

    /** 入库类型名称（未知编码原样返回，空返回 ''） */
    public static String inboundTypeName(String code) {
        return DictUtils.nameOf(INBOUND_TYPES, code);
    }

    /** 出库类型名称（未知编码原样返回，空返回 ''） */
    public static String outboundTypeName(String code) {
        return DictUtils.nameOf(OUTBOUND_TYPES, code);
    }

    /** 按来源（RK/CK）联合映射业务类型名称（避免两表同值歧义） */
    public static String bizTypeName(String source, String code) {
        return SOURCE_INBOUND.equals(source) ? inboundTypeName(code) : outboundTypeName(code);
    }

    /** 单据来源名称（RK 入库单 / CK 出库单） */
    public static String sourceName(String source) {
        if (SOURCE_INBOUND.equals(source)) {
            return "入库单";
        }
        if (SOURCE_OUTBOUND.equals(source)) {
            return "出库单";
        }
        return source == null ? "" : source;
    }

    /** 流水业务类型名称（按单据类别解释编码：#1# 入库单 → 入库字典，#2# 出库单 → 出库字典，
     *  #3# 盘点单 → 恒「盘点」，biz_type 仅作快照不参与解释） */
    public static String changeBizTypeName(String orderKind, String bizType) {
        if (ORDER_KIND_INBOUND.equals(orderKind)) {
            return inboundTypeName(bizType);
        }
        if (ORDER_KIND_OUTBOUND.equals(orderKind)) {
            return outboundTypeName(bizType);
        }
        if (ORDER_KIND_STOCKTAKE.equals(orderKind)) {
            return "盘点";
        }
        return bizType == null ? "" : bizType;
    }

    /** 业务类型编码校验（按来源），非法返回 false */
    public static boolean isValidBizType(String source, String code) {
        Map<String, String> dict = SOURCE_INBOUND.equals(source) ? INBOUND_TYPES : OUTBOUND_TYPES;
        return code != null && dict.containsKey(code);
    }

    /** 库存状态名称（#1# 正常 / #2# 偏低） */
    public static String stockStatusName(String code) {
        return DictUtils.nameOf(STOCK_STATUS, code);
    }

    /** 库存状态编码归一：接受中文（正常/偏低）或编码（#1#/#2#），未知原样返回（命中空结果） */
    public static String resolveStockStatus(String value) {
        return DictUtils.resolveCode(STOCK_STATUS, value);
    }

    /** 流水变动类型名称 */
    public static String changeTypeName(String code) {
        return DictUtils.nameOf(CHANGE_TYPES, code);
    }

    /**
     * 业务类型下拉选项（两组独立枚举合并，value = RK#1# / CK#1#，group = in / out）。
     * 前端按当前 Tab 过滤 group 展示。
     */
    public static List<PickerOptionVO> typeOptions() {
        List<PickerOptionVO> options = new ArrayList<>();
        for (Map.Entry<String, String> e : INBOUND_TYPES.entrySet()) {
            options.add(option(SOURCE_INBOUND + e.getKey(), e.getValue(), "in"));
        }
        for (Map.Entry<String, String> e : OUTBOUND_TYPES.entrySet()) {
            options.add(option(SOURCE_OUTBOUND + e.getKey(), e.getValue(), "out"));
        }
        return options;
    }

    /** 解析列表筛选参数 bizType（RK#1# / CK#1#）→ {source, code}；非法返回 null（
     * 编码本身非法不报错，交由 SQL 命中空结果） */
    public static String[] parseBizType(String bizType) {
        if (bizType == null || bizType.length() < 4) {
            return null;
        }
        String source = bizType.substring(0, 2);
        if (!SOURCE_INBOUND.equals(source) && !SOURCE_OUTBOUND.equals(source)) {
            return null;
        }
        return new String[]{source, bizType.substring(2)};
    }

    private static PickerOptionVO option(String value, String label, String group) {
        PickerOptionVO vo = new PickerOptionVO();
        vo.setValue(value);
        vo.setLabel(label);
        vo.setGroup(group);
        return vo;
    }
}
