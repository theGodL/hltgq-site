package com.qgyun.hltgq.hltgqsite.model.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 流量换算工具：水量（万方）→ 流量（m³/s）。
 * <p>公式：rate = volume * 10000 / 86400（1万方=10000m³，1天=86400秒），保留 2 位小数。
 */
public final class FlowRateUtils {

    private FlowRateUtils() {
    }

    /** 万方 → m³/s，null 返回 null，结果保留 2 位小数 */
    public static Double volumeToRate(Double volumeWanfang) {
        if (volumeWanfang == null) {
            return null;
        }
        return BigDecimal.valueOf(volumeWanfang)
                .multiply(BigDecimal.valueOf(10000))
                .divide(BigDecimal.valueOf(86400), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
