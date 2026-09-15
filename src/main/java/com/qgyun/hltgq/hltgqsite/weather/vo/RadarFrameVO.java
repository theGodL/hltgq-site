package com.qgyun.hltgq.hltgqsite.weather.vo;

import lombok.Data;

/**
 * 雷达帧（方案 §4.2 ① `/weather/radar/frames` 的 `frames[]` 元素）。
 * <p>上游 RainViewer 每 10 分钟滚动一帧、仅保留过去约 2 小时（nowcast 已于 2026-01-01 全球停止）。
 */
@Data
public class RadarFrameVO {

    /** 帧时间（北京时间，如 "2026-09-14T13:00:00"，来自上游 epoch 秒换算） */
    private String time;

    /** 帧时间戳（epoch 秒，上游原值） */
    private long timestamp;

    /** 帧标识（= 上游 path，如 "/v2/radar/4f4ae5d6985d"）；瓦片接口的 `frame` 参数只接受该值 */
    private String path;

    /** 帧类型：`past`（实况回放）；上游 nowcast 空数组，恢复时会自动带出 `nowcast` */
    private String type;

    /**
     * 该帧的完整瓦片模板（`frame` 已替换为实际 path，前端只处理 `{z}/{x}/{y}`）。
     * <p>返回<b>相对路径</b>（不带前导 `/`）：页面与接口同源部署在 `/hltgq-site` 前缀下时，
     * 相对路径按当前页面解析即为正确地址，避免把上下文前缀硬编码进后端响应。
     */
    private String tileUrlTemplate;
}
