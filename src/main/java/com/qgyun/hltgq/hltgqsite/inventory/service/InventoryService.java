package com.qgyun.hltgq.hltgqsite.inventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.inventory.mapper.InventoryMapper;
import com.qgyun.hltgq.hltgqsite.inventory.vo.InventoryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DictUtils;
import com.qgyun.hltgq.hltgqsite.model.util.JsonFieldUtils;
import com.qgyun.hltgq.hltgqsite.model.util.ValueConvertUtils;

/**
 * 设备库存查询服务：枚举编码翻译（中文）+ 数值文本转换 + obompq 明细 JSON 防御解析。
 * <p>库存数量/安全库存列以文本存储（样例 '30'/'5'），按文本取出后转数值，空/非法恒 null；
 * obompq 为稀疏 JSON 数组（键存在才有值），缺键恒 null 不补造；
 * 过滤参数支持中文与编码双写法（等价），未知值原样下传（命中空结果）。
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    /** 物资类别编码 → 中文（jsnowd） */
    private static final Map<String, String> CATEGORY_LABELS = new LinkedHashMap<>();
    /** 库存状态编码 → 中文（mojktw） */
    private static final Map<String, String> STOCK_STATUS_LABELS = new LinkedHashMap<>();

    static {
        CATEGORY_LABELS.put("#1#", "应急发电机");
        CATEGORY_LABELS.put("#2#", "水位计");
        CATEGORY_LABELS.put("#3#", "流量仪");
        CATEGORY_LABELS.put("#4#", "雨量仪");

        STOCK_STATUS_LABELS.put("#1#", "安全库存");
        STOCK_STATUS_LABELS.put("#2#", "库存预警");
    }

    @Autowired
    private InventoryMapper mapper;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 库存列表（按创建时间倒序，裸 List，无分页）。
     *
     * @param departmentId 所属部门 ID（czdwpj = t_apaas_uc_org.id），可选
     * @param stockStatus  库存状态：中文（安全库存/库存预警）或编码（#1#/#2#），可选
     */
    public List<InventoryVO> list(String departmentId, String stockStatus) {
        List<InventoryVO> rows = mapper.selectInventoryList(CheckUtils.trim(departmentId),
                DictUtils.resolveCode(STOCK_STATUS_LABELS, stockStatus));
        for (InventoryVO row : rows) {
            row.setCategory(DictUtils.label(CATEGORY_LABELS, row.getCategoryCode()));
            row.setStockStatus(DictUtils.label(STOCK_STATUS_LABELS, row.getStockStatusCode()));
            row.setStockQty(ValueConvertUtils.decimalOf(row.getStockQtyText()));
            row.setSafetyStock(ValueConvertUtils.decimalOf(row.getSafetyStockText()));
            row.setDetails(parseDetails(row.getDetailsJson()));
            // 内部转换字段置空（@JsonIgnore 本就不出 JSON，置空释放字符串引用）
            row.setStockQtyText(null);
            row.setSafetyStockText(null);
            row.setDetailsJson(null);
        }
        return rows;
    }

    /** obompq JSON 数组 → 明细行；空值/非法 JSON/非数组均返回空列表（不抛 5xx） */
    private List<InventoryVO.Detail> parseDetails(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return Collections.emptyList();
            }
            List<InventoryVO.Detail> details = new ArrayList<>(root.size());
            for (JsonNode node : root) {
                InventoryVO.Detail detail = new InventoryVO.Detail();
                detail.setCode(JsonFieldUtils.textOf(node, "tgmziv"));
                detail.setName(JsonFieldUtils.textOf(node, "lmtibt"));
                detail.setBrand(JsonFieldUtils.textOf(node, "ihcepa"));
                detail.setUnit(JsonFieldUtils.textOf(node, "lwlpnf"));
                detail.setInboundQty(JsonFieldUtils.decimalOf(node, "jynpfu"));
                detail.setOutboundQty(JsonFieldUtils.decimalOf(node, "uxsxkn"));
                detail.setOutboundTotalQty(JsonFieldUtils.decimalOf(node, "dsanix"));
                detail.setQuantity(resolveQuantity(node, detail));
                details.add(detail);
            }
            return details;
        } catch (Exception e) {
            log.warn("库存明细 JSON 解析失败（obompq）: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 明细数量：优先 tzqqhz；缺省按平台公式「入库数量 − 出库数量」计算
     * （《数据表结构汇总.md》定稿：tzqqhz = jynpfu − uxsxkn，uxsxkn 为隐藏字段默认 0，缺失按 0 计）。
     */
    private BigDecimal resolveQuantity(JsonNode node, InventoryVO.Detail detail) {
        BigDecimal quantity = JsonFieldUtils.decimalOf(node, "tzqqhz");
        if (quantity != null) {
            return quantity;
        }
        if (detail.getInboundQty() != null) {
            BigDecimal outbound = detail.getOutboundQty() != null ? detail.getOutboundQty() : BigDecimal.ZERO;
            return detail.getInboundQty().subtract(outbound);
        }
        return null;
    }
}
