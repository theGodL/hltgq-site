package com.qgyun.hltgq.hltgqsite.emergencynotice;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 应急通知（t_auto_hltgq_bcmxdv_lmqaew）字典与导航提示口径。
 * <p>编码取自低代码平台选项组，存储值为 {@code #选项 value#}：
 * <ul>
 *   <li>响应类别 vqpwue：防汛 {@code #1#} / 抗旱 {@code #vtsx#} / 其他 {@code #aivs#}；</li>
 *   <li>通知事项 pxkzdz：启动响应 {@code #1#} / 升级响应 {@code #ifde#} / 降级响应 {@code #sjib#} /
 *       终止响应 {@code #doga#} / 补充工作要求 {@code #tckv#}；</li>
 *   <li>市级响应等级 sycosi：Ⅰ级 {@code #1#} / Ⅱ级 {@code #ggdd#} / Ⅲ级 {@code #mytc#} / Ⅳ级 {@code #bylw#}；</li>
 *   <li>通知状态 cgkpil：草稿 {@code #1#} / 已发布 {@code #ucpd#} / 已终止 {@code #mevt#}。</li>
 * </ul>
 * <p>导航提示返回「已发布」和「已终止」的通知（草稿不返回），由 `status` 标明该条是已发布还是已终止；
 * 「已终止」与通知事项「终止响应」都算终止类提示，只返回近 {@link #RELEASE_WINDOW_DAYS} 天的。
 */
public final class EmergencyNoticeDict {

    /** 通知状态：草稿（不参与导航提示） */
    public static final String STATUS_DRAFT = "#1#";

    /** 通知状态：已发布 */
    public static final String STATUS_PUBLISHED = "#ucpd#";

    /** 通知状态：已终止 */
    public static final String STATUS_TERMINATED = "#mevt#";

    /** 通知事项：启动响应 */
    public static final String ITEM_START = "#1#";

    /** 通知事项：升级响应 */
    public static final String ITEM_UPGRADE = "#ifde#";

    /** 通知事项：降级响应 */
    public static final String ITEM_DOWNGRADE = "#sjib#";

    /** 通知事项：终止响应（导航提示为「响应解除」） */
    public static final String ITEM_TERMINATE = "#doga#";

    /** 通知事项：补充工作要求（不改变响应状态，等级沿用最近一次启动/升级/降级） */
    public static final String ITEM_SUPPLEMENT = "#tckv#";

    /** 通知状态名：已发布（同样用作提示类型） */
    public static final String TIP_PUBLISHED = "published";

    /** 通知状态名：已终止（同样用作提示类型） */
    public static final String TIP_TERMINATED = "terminated";

    /** 提示类型：无提示 */
    public static final String TIP_NONE = "none";

    /** 终止类提示保留天数：状态「已终止」或事项「终止响应」的提示只保留近这么多天 */
    public static final int RELEASE_WINDOW_DAYS = 3;

    /** 状态显示名：已发布 */
    public static final String STATUS_NAME_PUBLISHED = "已发布";

    /** 状态显示名：已终止 */
    public static final String STATUS_NAME_TERMINATED = "已终止";

    /** 应急通知所在应用编码（表名 t_auto_hltgq_bcmxdv_lmqaew 的中段） */
    public static final String APP_CODE = "bcmxdv";

    /** 应急通知表单编码（前端据此跳转「应急管理-应急通知」） */
    public static final String PAGE_CODE = "lmqaew";

    private static final Map<String, String> CATEGORY_LABELS;
    private static final Map<String, String> ITEM_LABELS;
    private static final Map<String, String> LEVEL_LABELS;

    static {
        Map<String, String> category = new LinkedHashMap<>();
        category.put("#1#", "防汛");
        category.put("#vtsx#", "抗旱");
        category.put("#aivs#", "其他");
        CATEGORY_LABELS = Collections.unmodifiableMap(category);

        Map<String, String> item = new LinkedHashMap<>();
        item.put(ITEM_START, "启动响应");
        item.put(ITEM_UPGRADE, "升级响应");
        item.put(ITEM_DOWNGRADE, "降级响应");
        item.put(ITEM_TERMINATE, "终止响应");
        item.put(ITEM_SUPPLEMENT, "补充工作要求");
        ITEM_LABELS = Collections.unmodifiableMap(item);

        Map<String, String> level = new LinkedHashMap<>();
        level.put("#1#", "Ⅰ级");
        level.put("#ggdd#", "Ⅱ级");
        level.put("#mytc#", "Ⅲ级");
        level.put("#bylw#", "Ⅳ级");
        LEVEL_LABELS = Collections.unmodifiableMap(level);
    }

    private EmergencyNoticeDict() {
    }

    /** 响应类别显示名（未知编码原样返回） */
    public static String categoryLabel(String code) {
        return labelOf(CATEGORY_LABELS, code);
    }

    /** 通知事项显示名（未知编码原样返回） */
    public static String itemLabel(String code) {
        return labelOf(ITEM_LABELS, code);
    }

    /** 市级响应等级显示名（未填写返回空串） */
    public static String levelLabel(String code) {
        String value = trimToNull(code);
        if (value == null) {
            return "";
        }
        String label = LEVEL_LABELS.get(value);
        return label == null ? value : label;
    }

    /** 是否「终止响应」（该类别响应已解除） */
    public static boolean isTerminate(String itemCode) {
        return ITEM_TERMINATE.equals(trimToNull(itemCode));
    }

    /** 是否终止类通知：状态为「已终止」或事项为「终止响应」 */
    public static boolean isTerminated(String statusCode, String itemCode) {
        return STATUS_TERMINATED.equals(trimToNull(statusCode)) || isTerminate(itemCode);
    }

    /** 去首尾空格，空串归一为 null */
    public static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private static String labelOf(Map<String, String> labels, String code) {
        String value = trimToNull(code);
        if (value == null) {
            return "";
        }
        String label = labels.get(value);
        return label == null ? value : label;
    }
}
