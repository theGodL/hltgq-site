package com.qgyun.hltgq.hltgqsite.model.util;

import java.math.BigDecimal;

/**
 * 设备上报值哨兵语义（唯一实现点）。
 * <p>报文约定：-9991 表示设备异常（前端展示 '--' 或不参与计算）、-999 表示设备不存在（前端不展示）；
 * 历史上各监控 Service 各抄一份 {@code isMissing/isError/nullIfMissing/usable} 判定，实现逐字相同。
 * <p>本类为上述判定的唯一实现点。比较一律用 {@code compareTo}（BigDecimal 的 equals 受标度影响）。
 */
public final class DeviceValueUtils {

    /** 设备异常标记（原 -999 语义迁移为 -9991） */
    public static final BigDecimal ERROR = new BigDecimal("-9991");

    /** 设备不存在标记（报文原生 -999，前端不展示） */
    public static final BigDecimal MISSING = new BigDecimal("-999");

    private DeviceValueUtils() {
    }

    /** 设备不存在（-999） */
    public static boolean isMissing(BigDecimal value) {
        return value != null && value.compareTo(MISSING) == 0;
    }

    /** 设备异常（-9991） */
    public static boolean isError(BigDecimal value) {
        return value != null && value.compareTo(ERROR) == 0;
    }

    /** -999 → null（-9991 保留透传，由前端展示 '--'） */
    public static BigDecimal nullIfMissing(BigDecimal value) {
        return isMissing(value) ? null : value;
    }

    /** 可用值：null 与 -999/-9991 → null；其余原样 */
    public static BigDecimal usable(BigDecimal value) {
        if (value == null || isMissing(value) || isError(value)) {
            return null;
        }
        return value;
    }
}
