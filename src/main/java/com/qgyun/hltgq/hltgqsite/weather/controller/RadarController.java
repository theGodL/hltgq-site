package com.qgyun.hltgq.hltgqsite.weather.controller;

import com.qgyun.hltgq.hltgqsite.weather.service.RadarService;
import com.qgyun.hltgq.hltgqsite.weather.vo.RadarFramesVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

/**
 * 雷达图接口（RainViewer 代理，方案 §4.2）。
 * <p>帧列表 `/weather/radar/frames`（时间轴数据，约 13 帧、过去 2 小时）；
 * 瓦片代理 `/weather/radar/tile/{z}/{x}/{y}?frame={path}`（天地图叠加，仅 z≤7）。
 * <p><b>降级口径</b>：帧列表上游失败返回 stale 或空 frames（不报错）；瓦片任一环节失败返回 404
 * （空白瓦片，不阻塞其余瓦片），任何情况都不抛 5xx。
 * <p>入参非法（z 越界 / frame 格式不符 / 坐标越界）由全局异常处理转 400。
 */
@RestController
@RequestMapping("/weather/radar")
public class RadarController {

    @Autowired
    private RadarService radarService;

    /**
     * 雷达帧列表（方案 §4.2 ①）
     * <p>返回 `maxZoom`（=7，前端据此限制图层缩放级，禁止请求 z≥8）与每帧的 `tileUrlTemplate`
     * （`frame` 已替换为实际 path，前端只需让地图引擎替换 `{z}/{x}/{y}`）。
     */
    @GetMapping("/frames")
    public RadarFramesVO frames() {
        return radarService.frames();
    }

    /**
     * 雷达瓦片代理（方案 §4.2 ②）：后端统一加 LRU 缓存、上游令牌桶、并发信号量与终端 IP 限流。
     *
     * @param z     缩放级 0~7（越界 400；上游 z≥8 返回占位图，后端主动拦截）
     * @param x     瓦片列号（0 ~ 2^z-1，越界 400）
     * @param y     瓦片行号（0 ~ 2^z-1，越界 400）
     * @param frame 帧标识，取 `/weather/radar/frames` 的 `frames[].path`（格式不符 400）
     * @return PNG 瓦片；降级（上游限流/超时/失败）返回 404
     */
    @GetMapping("/tile/{z}/{x}/{y}")
    public ResponseEntity<byte[]> tile(@PathVariable int z,
                                       @PathVariable int x,
                                       @PathVariable int y,
                                       @RequestParam String frame,
                                       HttpServletRequest request) {
        byte[] data = radarService.tile(z, x, y, frame, clientIp(request));
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        // 帧 path 不可变，同一 URL 内容恒定：允许浏览器缓存 30 分钟（与后端 LRU TTL 同口径），
        // 减少重复拖动产生的请求
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(30, TimeUnit.MINUTES).cachePublic())
                .body(data);
    }

    /**
     * 终端 IP 提取：`X-Forwarded-For` 首段 → `X-Real-IP` → `remoteAddr`。
     * <p>仅用于终端限流计数（方案 §4.4），不用于鉴权判定——伪造该头只会把自己的配额算到别人头上，
     * 无法突破"按 IP 限流"的整体保护。
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
