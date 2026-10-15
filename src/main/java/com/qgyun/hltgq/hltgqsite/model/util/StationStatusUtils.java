package com.qgyun.hltgq.hltgqsite.model.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 测站在线状态与断联判定（唯一实现点）。
 * <p>历史上流量/闸门/墒情/雨量/水质五个监测服务各抄一份相同的 MQTT 站点清单、断联阈值与判定方法。
 * <p>口径与前端 isStaleTm 标红规则一致：MQTT 站 30 分钟、RabbitMQ 站 70 分钟无报文判离线；
 * 采集时间为空或在未来（时钟偏差）均视为在线；毫秒级严格大于阈值（向下取整会差一分钟误判）。
 */
public final class StationStatusUtils {

    /** MQTT 站点固定清单（站点名匹配，其余为 RabbitMQ）：与前端 isStaleTm 标红规则一致 */
    private static final Set<String> MQTT_STATION_NAMES = new HashSet<>(Arrays.asList(
            "南山寺节制闸", "渠首进水闸", "渠首电站防洪闸", "双庙湖节制闸"
    ));

    /** MQTT 站断联阈值：报文 10 分钟一次，30 分钟无更新判离线 */
    private static final long MQTT_STALE_MINUTES = 30;
    /** RabbitMQ 站断联阈值：报文 1 小时一次，70 分钟无更新判离线 */
    private static final long RBT_STALE_MINUTES = 70;

    /**
     * 断联判定（与前端 isStaleTm 规则一致）：MQTT 站 30 分钟、RabbitMQ 站 70 分钟无更新判离线；
     * 无时间值（null）视为在线（不判离线）；时间在未来（时钟偏差）也视为在线
     */
    public static boolean isStale(LocalDateTime tm, String stnm) {
        if (tm == null) return false;
        long threshold = MQTT_STATION_NAMES.contains(stnm) ? MQTT_STALE_MINUTES : RBT_STALE_MINUTES;
        // 毫秒级严格大于，与前端 Date.now() - t > staleMs 语义完全一致（toMinutes 向下取整会导致边界差一分钟误判）
        return Duration.between(tm, LocalDateTime.now()).toMillis() > threshold * 60_000L;
    }

    /** zebpsu 站点状态：#1# 在线、#2# 离线；兼容 "#1#"/"1"/"#1" 格式，null 或未知值默认在线 */
    public static boolean isZebpsuOnline(String zebpsu) {
        if (zebpsu == null) return true;
        return !"2".equals(zebpsu.trim().replace("#", ""));
    }
}
