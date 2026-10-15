package com.qgyun.hltgq.hltgqsite.model.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接口应答组装（唯一实现点）：分页参数收敛、分页结构、操作成功应答。
 * <p>分页口径全站一致：{@code total/page/size/pages/records}；
 * 各模块 XxxSupport 保留门面并委托本类，不再各写一份。
 */
public final class ApiResultUtils {

    private ApiResultUtils() {
    }

    /** 页码收敛（1 ~ 100000） */
    public static int clampPage(int page) {
        return Math.min(100_000, Math.max(1, page));
    }

    /** 页大小收敛（1 ~ 100） */
    public static int clampSize(int size) {
        return Math.min(100, Math.max(1, size));
    }

    /** 分页结果（total/page/size/pages/records） */
    public static Map<String, Object> pageResult(long total, int page, int size, List<?> records) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("page", page);
        out.put("size", size);
        out.put("pages", size <= 0 ? 0 : (total + size - 1) / size);
        out.put("records", records);
        return out;
    }

    /** 操作成功应答（{"success": true}，与全站写接口口径一致） */
    public static Map<String, Object> ok() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        return out;
    }
}
