package com.qgyun.hltgq.hltgqsite.inventorycheck.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.auth.SessionContextService;
import com.qgyun.hltgq.hltgqsite.inventorycheck.mapper.StocktakeMapper;
import com.qgyun.hltgq.hltgqsite.inventorycheck.vo.InventoryCheckExportVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.client.FileClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 设备盘点导出服务：himanc 明细 JSON 防御解析 + 附件文件服务尽力解析。
 * <p>盘点明细为稀疏 JSON 数组（键存在才有值），缺键恒 null，单位缺键补平台默认「个」；
 * 数值列按文本取出转数值（空/非法恒 null）；附件 xfeiey 为单个文件 id 时经文件服务
 * 换文件名/签名地址（增强信息，无会话/服务失败/复合引用降级为 null，不阻断主数据）。
 */
@Service
public class StocktakeExportService {

    private static final Logger log = LoggerFactory.getLogger(StocktakeExportService.class);

    /** 明细单位缺省值（表单字段默认「个」） */
    private static final String DEFAULT_UNIT = "个";

    @Autowired
    private StocktakeMapper mapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileClient fileClient;

    @Autowired
    private SessionContextService sessionContextService;

    /**
     * 盘点记录列表（按盘点日期倒序，裸 List，供前端导出 Excel）。
     *
     * @param code      编号模糊（yogpxf），可选
     * @param checkDate 盘点日期 yyyy-MM-dd（含当日），可选
     * @param request   当前请求（提取会话透传文件服务，解析附件文件名/签名地址）
     */
    public List<InventoryCheckExportVO> list(String code, LocalDate checkDate, HttpServletRequest request) {
        String dayStart = checkDate == null ? null : checkDate.toString();
        String dayEnd = checkDate == null ? null : checkDate.plusDays(1).toString();
        String sessionId = sessionContextService.extractSessionId(request);

        List<InventoryCheckExportVO> rows = mapper.selectCheckList(trimToNull(code), dayStart, dayEnd);
        for (InventoryCheckExportVO row : rows) {
            row.setCheckDate(normalizeDate(row.getCheckDateRaw()));
            row.setDetails(parseDetails(row.getDetailsJson()));
            resolveAttachment(row, sessionId);
            // 内部转换字段置空（@JsonIgnore 本就不出 JSON，置空释放字符串引用）
            row.setCheckDateRaw(null);
            row.setDetailsJson(null);
        }
        return rows;
    }

    /** himanc JSON 数组 → 明细行；空值/非法 JSON/非数组均返回空列表（不抛 5xx） */
    private List<InventoryCheckExportVO.Detail> parseDetails(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return Collections.emptyList();
            }
            List<InventoryCheckExportVO.Detail> details = new ArrayList<>(root.size());
            for (JsonNode node : root) {
                InventoryCheckExportVO.Detail detail = new InventoryCheckExportVO.Detail();
                detail.setItemCode(textOf(node, "iwckqw"));
                detail.setItemName(textOf(node, "acsoil"));
                detail.setBookQty(decimalOf(node, "ezzjcs"));
                detail.setCheckQty(decimalOf(node, "ekfgwh"));
                detail.setProfitQty(decimalOf(node, "eihdbh"));
                detail.setLossQty(decimalOf(node, "yuwdms"));
                String unit = textOf(node, "vbcddc");
                detail.setUnit(unit == null || unit.isEmpty() ? DEFAULT_UNIT : unit);
                details.add(detail);
            }
            return details;
        } catch (Exception e) {
            log.warn("盘点明细 JSON 解析失败（himanc）: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 附件解析（尽力模式）：xfeiey 为单个文件 id 时经文件服务换文件名/签名地址；
     * 引用为空 / 复合引用 / 无会话 / 服务失败均降级（id 原样返回，name/url 为 null）。
     */
    private void resolveAttachment(InventoryCheckExportVO row, String sessionId) {
        String ref = trimToNull(row.getAttachmentId());
        if (ref == null) {
            return;
        }
        if (ref.startsWith("http://") || ref.startsWith("https://")
                || ref.contains(",") || ref.startsWith("[") || ref.startsWith("{")) {
            log.warn("[设备盘点] 附件引用非单个文件 id（URL/复合格式），跳过解析: {}", ref);
            return;
        }
        if (sessionId == null) {
            log.warn("[设备盘点] 无会话，附件解析跳过 fileId={}", ref);
            return;
        }
        try {
            FileClient.FileInfo info = fileClient.getFile(ref, sessionId);
            row.setAttachmentName(info.name);
            row.setAttachmentUrl(info.url);
        } catch (Exception e) {
            log.warn("[设备盘点] 附件解析失败 fileId={}: {}", ref, e.getMessage());
        }
    }

    /** JSON 节点文本值（键缺失/空节点恒 null） */
    private String textOf(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** JSON 节点数值（文本解析失败/空恒 null） */
    private BigDecimal decimalOf(JsonNode node, String key) {
        return toDecimal(textOf(node, key));
    }

    /** 文本转数值（空串/非法恒 null） */
    private BigDecimal toDecimal(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 盘点日期规范化：时间戳形态取前 10 位（如 2026-09-01 00:00:00 → 2026-09-01），其余原样 */
    private String normalizeDate(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return raw.length() > 10 ? raw.substring(0, 10) : raw;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
