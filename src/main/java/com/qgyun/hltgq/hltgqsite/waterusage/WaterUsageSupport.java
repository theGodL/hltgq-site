package com.qgyun.hltgq.hltgqsite.waterusage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.model.util.ApiResultUtils;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.OperatorUtils;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.PlanRefVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 灌区用水（用水户/定额/计划）共用支撑：文本归一、分页、审计操作人、数值口径。
 * <ul>
 *   <li>单位口径（设计方案 2.4）：存储 m³（2 位小数）、亩（2 位）、水价 元/m³（3 位）；
 *       万m³（1 位）仅用于提示文案换算，库内外均为 m³；</li>
 *   <li>测算水量 = 灌溉面积 × 定额标准（HALF_UP 保留 2 位）；</li>
 *   <li>分页结果口径与全站一致：total/page/size/pages/records（委托 ApiResultUtils）。</li>
 * </ul>
 */
public final class WaterUsageSupport {

    private static final Logger log = LoggerFactory.getLogger(WaterUsageSupport.class);

    /** 明细行数防御上限（用水计划） */
    public static final int MAX_ITEMS = 500;

    /** 万m³ 换算基数（仅展示/提示用） */
    public static final BigDecimal WAN = new BigDecimal("10000");

    /** 流量设备不存在哨兵值（ttf/ytf） */
    public static final BigDecimal DEVICE_MISSING = new BigDecimal("-999");

    /** 流量设备异常哨兵值（ttf/ytf） */
    public static final BigDecimal DEVICE_ERROR = new BigDecimal("-9991");

    /** 日期时间格式（对外展示口径） */
    private static final DateTimeFormatter DT_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final DateTimeFormatter DT_SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private WaterUsageSupport() {
    }

    /** 去首尾空白，空串按 null 处理（委托 CheckUtils） */
    public static String trim(String value) {
        return CheckUtils.trim(value);
    }

    /** 必填文本（trim 后为空报 400；委托 CheckUtils） */
    public static String requireText(String value, String message) {
        return CheckUtils.requireText(value, message);
    }

    /** 长度上限校验（列宽约束前置拦截；null 放过；委托 CheckUtils） */
    public static String maxLength(String value, int max, String label) {
        return CheckUtils.maxLength(value, max, label);
    }

    /** 页码收敛（1 ~ 100000；委托 ApiResultUtils） */
    public static int clampPage(int page) {
        return ApiResultUtils.clampPage(page);
    }

    /** 页大小收敛（1 ~ 100；委托 ApiResultUtils） */
    public static int clampSize(int size) {
        return ApiResultUtils.clampSize(size);
    }

    /** 分页结果（全站口径 total/page/size/pages/records；委托 ApiResultUtils） */
    public static Map<String, Object> pageResult(long total, int page, int size, List<?> records) {
        return ApiResultUtils.pageResult(total, page, size, records);
    }

    /**
     * Tab 角标统计（countByStatus 行集 → 英文码数量 + total）：
     * row 形如 {status: '#N#', total: N}；keys 为预置英文码（未出现保持 0，未知状态只计入 total）。
     */
    public static Map<String, Object> statusStats(List<Map<String, Object>> counts,
                                                  Function<String, String> codeOf, String... keys) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : keys) {
            out.put(key, 0L);
        }
        out.put("total", 0L);
        for (Map<String, Object> row : counts) {
            String status = row.get("status") == null ? "" : String.valueOf(row.get("status"));
            long total = row.get("total") == null ? 0L : ((Number) row.get("total")).longValue();
            String code = codeOf.apply(status);
            if (out.containsKey(code)) {
                out.put(code, total);
            }
            out.put("total", ((Number) out.get("total")).longValue() + total);
        }
        return out;
    }

    /** 当前操作人（会话缺失时用配置兜底；审计列 NOT NULL；委托 OperatorUtils） */
    public static String currentOperatorId(String fallbackOperator) {
        return OperatorUtils.currentOperatorId(fallbackOperator);
    }

    /** 必填正数（>0，小数位不超过 maxScale；label 如「水价」「计划水量」；委托 CheckUtils） */
    public static BigDecimal requirePositive(BigDecimal value, String label, int maxScale) {
        return CheckUtils.requirePositive(value, label, maxScale);
    }

    /** 选填正数（null 放过；填写则 >0 且小数位不超过 maxScale；委托 CheckUtils） */
    public static BigDecimal optionalPositive(BigDecimal value, String label, int maxScale) {
        return CheckUtils.optionalPositive(value, label, maxScale);
    }

    /** 测算水量 = 灌溉面积(亩) × 定额标准(m³/亩)，HALF_UP 保留 2 位（存储口径 m³） */
    public static BigDecimal calcWater(BigDecimal area, BigDecimal quotaValue) {
        return area.multiply(quotaValue).setScale(2, RoundingMode.HALF_UP);
    }

    /** m³ → 万m³ 文案（1 位小数，用于校验警告；库内外数据一律 m³） */
    public static String formatWan(BigDecimal m3) {
        if (m3 == null) {
            return "0.0";
        }
        return m3.divide(WAN, 1, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 万m³ 录入值 → m³ 存储值（×10000，HALF_UP 2 位）。四页面接口对外数量均为万m³（≤2 位小数），
     * 库内与全部校验/计量/金额计算一律 m³。
     */
    public static BigDecimal toM3(BigDecimal wan) {
        if (wan == null) {
            return null;
        }
        return wan.multiply(WAN).setScale(2, RoundingMode.HALF_UP);
    }

    /** m³ 存储值 → 万m³ 对外输出值（÷10000，HALF_UP 4 位；前端展示按自身口径取位） */
    public static BigDecimal toWan(BigDecimal m3) {
        if (m3 == null) {
            return null;
        }
        return m3.divide(WAN, 4, RoundingMode.HALF_UP);
    }

    /** 数值归一（HALF_UP 保留 2 位；用于水量/金额落库与快照） */
    public static BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** 必填正数（万m³ 录入，≤2 位小数），返回 m³ 存储值（>0 校验在万m³ 口径上完成） */
    public static BigDecimal requirePositiveWan(BigDecimal wan, String label) {
        BigDecimal value = requirePositive(wan, label, 2);
        return toM3(value);
    }

    /** 流量累计可用值：null 与哨兵值 -999（设备不存在）/ -9991（设备异常）视为缺失 */
    public static BigDecimal usableFlow(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(DEVICE_MISSING) == 0 || value.compareTo(DEVICE_ERROR) == 0) {
            return null;
        }
        return value;
    }

    /**
     * 解析日期时间文本：兼容 'YYYY-MM-DD HH:mm[:ss]' 与 'YYYY-MM-DDTHH:mm[:ss]'（datetime-local 原样提交），
     * 纯日期按当天 0 点；为空/非法报 400。
     */
    public static LocalDateTime parseDateTime(String text, String label) {
        String value = requireText(text, label + "不能为空");
        String normalized = value.replace('T', ' ').trim();
        try {
            if (normalized.length() == 16) {
                return LocalDateTime.parse(normalized, DT_MINUTE);
            }
            if (normalized.length() == 19) {
                return LocalDateTime.parse(normalized, DT_SECOND);
            }
            if (normalized.length() == 10) {
                return LocalDate.parse(normalized).atStartOfDay();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "格式不正确（应为 YYYY-MM-DD HH:mm）");
        }
        throw new IllegalArgumentException(label + "格式不正确（应为 YYYY-MM-DD HH:mm）");
    }

    /** 解析日期文本（YYYY-MM-DD），为空/非法报 400 */
    public static LocalDate parseDate(String text, String label) {
        String value = requireText(text, label + "不能为空");
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "格式不正确（应为 YYYY-MM-DD）");
        }
    }

    /** 操作成功应答（{"success": true}；委托 ApiResultUtils） */
    public static Map<String, Object> ok() {
        return ApiResultUtils.ok();
    }

    /**
     * 编号唯一索引冲突重试（Controller 层兜底并发生成撞号）：
     * 命中指定唯一索引且未达 3 次时重试（每次重试由 Service 新事务重新取号），其余冲突原样抛出。
     */
    public static <T> T retryOnNoConflict(String ukName, String label, Supplier<T> action, Logger callerLog) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (DataIntegrityViolationException e) {
                String detail = e.getMessage() == null ? "" : e.getMessage();
                if (attempt >= 3 || !detail.contains(ukName)) {
                    throw e;
                }
                callerLog.warn("[灌区用水] {}编号冲突，第 {} 次重试", label, attempt);
            }
        }
    }

    /**
     * 引用分流（用水户/定额删除保护两级规则）：
     * 编制中（#1#）→ pending（二次确认后连带清理）；其余状态 → blocked（禁止删除）。
     */
    public static Map<String, Object> splitPlanRefs(List<PlanRefVO> refs) {
        List<PlanRefVO> blocked = new ArrayList<>();
        List<PlanRefVO> pending = new ArrayList<>();
        if (refs != null) {
            for (PlanRefVO ref : refs) {
                if (WaterUsageDict.PLAN_STATUS_DRAFT.equals(ref.getStatus())) {
                    pending.add(ref);
                } else {
                    blocked.add(ref);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("blocked", blocked);
        out.put("pending", pending);
        return out;
    }

    /** 引用明细行数合计（删除确认文案用） */
    public static int sumItemCount(List<PlanRefVO> refs) {
        int total = 0;
        if (refs != null) {
            for (PlanRefVO ref : refs) {
                total += ref.getItemCount() == null ? 0 : ref.getItemCount();
            }
        }
        return total;
    }

    /**
     * 计划引用两级删除保护的裁决（用水户/定额删除共用，label 如「该用水户」）：
     * blocked 非空（被非编制中计划引用）→ 报错禁止；
     * pending 非空（被编制中计划引用）→ force=false 报错提示二次确认，force=true 返回待清理计划 id 列表（空 = 无待清理）。
     */
    public static List<String> resolvePlanRefsDelete(Map<String, Object> refs, boolean force, String label) {
        @SuppressWarnings("unchecked")
        List<PlanRefVO> blocked = (List<PlanRefVO>) refs.get("blocked");
        @SuppressWarnings("unchecked")
        List<PlanRefVO> pending = (List<PlanRefVO>) refs.get("pending");
        if (!blocked.isEmpty()) {
            throw new IllegalArgumentException(label + "已被 " + blocked.size()
                    + " 个非编制中计划引用（已生效 / 已归档 / 已作废），不可删除");
        }
        List<String> planIds = new ArrayList<>();
        if (!pending.isEmpty()) {
            if (!force) {
                throw new IllegalArgumentException(label + "被 " + pending.size()
                        + " 个编制中计划引用（明细 " + sumItemCount(pending)
                        + " 行），删除后将从这些计划中移除对应明细并重算总水量，请确认");
            }
            for (PlanRefVO ref : pending) {
                planIds.add(ref.getPlanId());
            }
        }
        return planIds;
    }

    /**
     * 解析用水户闸口配置 JSON（gate_config 列）：
     * <pre>
     * 累加 {"mode":"SUM","gates":[{"id":"…","name":"…"},…]}
     * 差额 {"mode":"DIFF","main":{…},"deduct":[{…},…]}
     * </pre>
     * 结构非法/缺失返回 null（调用方按业务兜底：方案闸口校验不通过、账单计量按无闸口组处理）。
     */
    public static GateConfig parseGateConfig(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String mode = node.path("mode").asText("");
        List<String> allIds = new ArrayList<>();
        List<String> deductIds = new ArrayList<>();
        List<String> names = new ArrayList<>();
        if (WaterUsageDict.GATE_MODE_SUM.equals(mode)) {
            JsonNode gates = node.get("gates");
            if (gates == null || !gates.isArray() || gates.size() == 0) {
                return null;
            }
            for (JsonNode gate : gates) {
                String id = trim(gate.path("id").asText(null));
                if (id == null) {
                    return null;
                }
                allIds.add(id);
                names.add(gate.path("name").asText(""));
            }
            return new GateConfig(mode, null, deductIds, allIds, String.join("、", names));
        }
        if (WaterUsageDict.GATE_MODE_DIFF.equals(mode)) {
            JsonNode main = node.get("main");
            String mainId = main == null ? null : trim(main.path("id").asText(null));
            if (mainId == null) {
                return null;
            }
            JsonNode deduct = node.get("deduct");
            if (deduct == null || !deduct.isArray() || deduct.size() == 0) {
                return null;
            }
            allIds.add(mainId);
            List<String> deductNames = new ArrayList<>();
            for (JsonNode gate : deduct) {
                String id = trim(gate.path("id").asText(null));
                if (id == null) {
                    return null;
                }
                deductIds.add(id);
                allIds.add(id);
                deductNames.add(gate.path("name").asText(""));
            }
            String text = main.path("name").asText("") + " − " + String.join("、", deductNames);
            return new GateConfig(mode, mainId, deductIds, allIds, text);
        }
        return null;
    }

    /**
     * 解析用水户闸口配置 JSON 原文（gate_config 列）为结构化配置。
     * <p>空串/结构非法返回 null（调用方按业务兜底：方案闸口校验不通过、账单计量按无闸口组处理）。
     */
    public static GateConfig gateConfigOf(String rawJson, ObjectMapper objectMapper) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return null;
        }
        try {
            return parseGateConfig(objectMapper.readTree(rawJson));
        } catch (Exception e) {
            log.warn("[灌区用水] 闸口配置解析失败：{}", e.getMessage());
            return null;
        }
    }

    /** 用水户闸口配置解析产物（只读）：计量符号判定与展示文案的唯一来源 */
    public static final class GateConfig {
        /** SUM 指定闸口累加 / DIFF 闸口差额计量 */
        public final String mode;

        /** DIFF 主闸口 id（SUM 为 null） */
        public final String mainId;

        /** 扣减闸口 id 列表（SUM 为空列表） */
        public final List<String> deductIds;

        /** 全部闸口 id（SUM = gates；DIFF = main + deduct） */
        public final List<String> allIds;

        /** 展示文案（累加「闸A、闸B」；差额「主闸 − 扣A、扣B」） */
        public final String gateText;

        private GateConfig(String mode, String mainId, List<String> deductIds,
                           List<String> allIds, String gateText) {
            this.mode = mode;
            this.mainId = mainId;
            this.deductIds = deductIds;
            this.allIds = allIds;
            this.gateText = gateText;
        }

        /** 该闸口在差额策略下是否为扣减角色（记账符号取负） */
        public boolean isDeduct(String gateId) {
            return gateId != null && deductIds.contains(gateId);
        }

        /** 该闸口是否属于本配置（编制方案 / 上报指定闸口的准入校验） */
        public boolean contains(String gateId) {
            return gateId != null && allIds.contains(gateId);
        }
    }
}
