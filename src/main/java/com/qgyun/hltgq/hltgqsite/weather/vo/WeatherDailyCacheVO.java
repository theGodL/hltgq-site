package com.qgyun.hltgq.hltgqsite.weather.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 日级预报<b>按段缓存</b>结构（方案 §3.4 规则二；复审 A6）。
 * <p><b>无顶层过期时钟</b>——新鲜度按段独立计时（{@code expireAt = 该段 pulledAt + TTL}），
 * 避免「补 B 段后沿用 A 段旧时钟导致新数据提前过期」与「重置顶层时钟使 A 段被续命」两种坏结果。
 * <p>该结构仅存于 Redis（`weather:daily:{lon},{lat}`），不作为对外契约。
 */
@Data
public class WeatherDailyCacheVO {

    /** 结构最近一次更新时间（任一成功写回时刷新，仅作观测用） */
    private String updatedAt;

    /** 段名 → 段数据；段名取值 exact / extended */
    private Map<String, Segment> segments = new LinkedHashMap<>();

    /** 单段：段内独立新鲜度 */
    @Data
    public static class Segment {

        /** 该段拉取时刻（ISO，便于日志/排查） */
        private String pulledAt;

        /** 该段拉取时刻（epoch 毫秒，用于旧值上限判定） */
        private long pulledAtMs;

        /** 该段新鲜期截止（epoch 毫秒）：pulledAtMs + TTL(+抖动) */
        private long expireAt;

        /** 该段逐日列表（升序，段内不含跨段裁剪） */
        private List<WeatherDailyVO> list = new ArrayList<>();

        /** 是否新鲜 */
        public boolean fresh(long nowMs) {
            return nowMs < expireAt;
        }
    }
}
