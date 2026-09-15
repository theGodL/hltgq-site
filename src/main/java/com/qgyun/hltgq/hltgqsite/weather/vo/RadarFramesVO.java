package com.qgyun.hltgq.hltgqsite.weather.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 雷达帧列表（方案 §4.2 ① `GET /weather/radar/frames`）。
 * <p>上游不可达且无可用缓存时返回 <b>空 frames</b>（`frames=[]`，接口不报错），
 * 前端据此不叠加图层、仅展示底图；`maxZoom` / `color` 在任何情况下都返回，
 * 保证前端图层配置不因降级而失效。
 */
@Data
public class RadarFramesVO {

    /** 帧列表生成时间（北京时间，来自上游 generated 的 epoch 秒换算） */
    private String generated;

    /** 帧列表（时间升序，约 13 帧、10 分钟粒度）；降级时为空数组 */
    private List<RadarFrameVO> frames = new ArrayList<>();

    /** 最大缩放级（上游免费层硬限制 7）：前端据此限制图层 maxZoom，禁止请求 z 大于该值 */
    private int maxZoom;

    /** 色板编号（固定 4 = Universal Blue，上游仅保留该色板） */
    private int color;

    /** 降级占位：空帧列表 + 配置下的图层参数（前端不叠图层，其余功能不受影响） */
    public static RadarFramesVO degraded(int maxZoom, int color) {
        RadarFramesVO vo = new RadarFramesVO();
        vo.setMaxZoom(maxZoom);
        vo.setColor(color);
        return vo;
    }
}
