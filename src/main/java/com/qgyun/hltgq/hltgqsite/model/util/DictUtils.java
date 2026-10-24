package com.qgyun.hltgq.hltgqsite.model.util;

import java.util.Map;

/**
 * 平台字典（编码 ↔ 中文）双向换算（唯一实现点）。
 * <p>历史上工单、巡检排班、站点档案、物资、库存单据等模块各抄一份同名的
 * {@code label()/translate()}（编码→中文）与 {@code resolveCode()}（过滤值→编码），
 * 实现逐字相同，仅「是否已是编码」的判定一处按 {@code #} 前缀、一处按字典 key 命中。
 * <p>本类判定取两者并集：值以 {@code #} 开头或已是字典 key 时原样下传，否则按中文名反查编码；
 * 对现存各字典（key 均为 {@code #N#} 形态）与原任一实现行为一致。
 */
public final class DictUtils {

    /** 平台字典编码前缀（形如 #1#） */
    private static final char CODE_PREFIX = '#';

    private DictUtils() {
    }

    /** 编码 → 中文（null/空串/未知编码原样返回） */
    public static String label(Map<String, String> labels, String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        return labels.getOrDefault(code, code);
    }

    /** 编码 → 中文（{编码, 名称} 成对数组形态；null/未知编码原样返回） */
    public static String label(String[][] mapping, String code) {
        if (code == null) {
            return null;
        }
        for (String[] pair : mapping) {
            if (pair[0].equals(code)) {
                return pair[1];
            }
        }
        return code;
    }

    /**
     * 多选编码（「|」分隔）→ 中文顿号拼接：空段跳过，未知段原样保留；入参空值原样返回。
     */
    public static String labelMulti(Map<String, String> labels, String codes) {
        if (codes == null || codes.isEmpty()) {
            return codes;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : codes.split("\\|")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(label(labels, token));
        }
        return sb.toString();
    }

    /** 过滤值归一为编码：空 → null；编码（{@code #...#} 或字典 key）原样；中文 → 编码；未知原样下传 */
    public static String resolveCode(Map<String, String> labels, String value) {
        String v = CheckUtils.trim(value);
        if (v == null || v.charAt(0) == CODE_PREFIX || labels.containsKey(v)) {
            return v;
        }
        for (Map.Entry<String, String> e : labels.entrySet()) {
            if (e.getValue().equals(v)) {
                return e.getKey();
            }
        }
        return v;
    }

    /** 编码 → 中文（null → 空串；未知编码原样返回）——展示兜底形态，与 {@link #label} 的 null 传递语义有别 */
    public static String nameOf(Map<String, String> dict, String code) {
        return dict.getOrDefault(code == null ? "" : code, code == null ? "" : code);
    }

    /** 水势代码 → 中文：4/涨 → 涨；5/落 → 落；6/平 → 平；其余/空 → 无涨落信息 */
    public static String mapWptn(String wptn) {
        if (wptn == null || wptn.isEmpty()) {
            return "无涨落信息";
        }
        switch (wptn.trim()) {
            case "4":
            case "涨":
                return "涨";
            case "5":
            case "落":
                return "落";
            case "6":
            case "平":
                return "平";
            default:
                return "无涨落信息";
        }
    }
}
