package com.qgyun.hltgq.hltgqsite.model.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 累计流量/水量展示单位换算：设备上报单位 m³，客户展示统一为万m³。
 * <p>换算规则：万m³ = m³ ÷ 10000，保留 3 位小数截断（业主口径：不四舍五入，与既有 2 位截断口径一致）。
 * <p>数据库列仍保存 m³ 原值，仅在接口输出时换算；区间累计（ttf 相减）、月/年累计等计算
 * 必须先按 m³ 原值完成，换算只在最后一步进行（缩放后相减会放大误差）。
 */
public final class WaterVolumeUtils {

    /** m³ → 万m³ 换算系数 */
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    /** 万m³ 展示小数位（业主口径：3 位小数） */
    private static final int SCALE = 3;

    private WaterVolumeUtils() {
    }

    /**
     * m³ → 万m³（3 位小数截断，null 安全）。
     * <p>示例：12345678 m³ → 1234.567 万m³
     */
    public static BigDecimal m3ToWan(BigDecimal m3) {
        return m3 == null ? null : m3.divide(TEN_THOUSAND, SCALE, RoundingMode.DOWN);
    }
}
