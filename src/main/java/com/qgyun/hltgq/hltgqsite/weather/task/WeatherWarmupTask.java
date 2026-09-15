package com.qgyun.hltgq.hltgqsite.weather.task;

import com.qgyun.hltgq.hltgqsite.weather.service.TyphoonService;
import com.qgyun.hltgq.hltgqsite.weather.service.WeatherDailyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 天气数据预热任务（P3a，方案 §10.1）。
 * <p>只预热<b>日级预报</b>与<b>台风</b>两项；雷达帧列表/瓦片预热属 P3b（依赖 P2 的雷达接口与瓦片代理）。
 * <p>预热的意义：日级首屏 miss 需并行拉两段（1~2 s）；预热把上游调用挪到后台，
 * 用户请求直接命中缓存。段级/接口级缓存判定了新鲜度，<b>新鲜时预热不产生上游调用</b>，可安全高频调度。
 * <p>节流：日级每 3 小时（与段 TTL 3 h 对齐，错开 30 分钟触发）；台风每 30 分钟（与缓存 TTL 对齐）。
 * <p>开关 {@code weather.warmup.enabled} 默认 <b>关闭</b>；坐标留空时取唯一来源 {@code weather.site-lon/lat}
 * （复审 C8：避免坐标两处维护），仅多站点预热时才填 {@code weather.warmup.locations}。
 */
@Component
public class WeatherWarmupTask {

    private static final Logger log = LoggerFactory.getLogger(WeatherWarmupTask.class);

    @Autowired
    private WeatherDailyService weatherDailyService;

    @Autowired
    private TyphoonService typhoonService;

    /** 预热总开关（默认关闭，P3a 上线后按需开启） */
    @Value("${weather.warmup.enabled:false}")
    private boolean enabled;

    /** 多站点预热坐标：`lon,lat` 多项以分号分隔；留空则使用灌区参考中心点 */
    @Value("${weather.warmup.locations:}")
    private String locations;

    /** 灌区参考中心经度（唯一来源，方案 §5.3） */
    @Value("${weather.site-lon:116.359678}")
    private double siteLon;

    /** 灌区参考中心纬度 */
    @Value("${weather.site-lat:30.378607}")
    private double siteLat;

    /**
     * 日级预报预热：每 3 小时一轮（默认 01:30 / 04:30 / … / 22:30），与段 TTL 对齐。
     * <p>段齐备且新鲜时全程无上游调用；有缺失段则同步补拉，有 stale 段则由读取流程按需后台刷新。
     */
    @Scheduled(cron = "${weather.warmup.daily-cron:0 30 1,4,7,10,13,16,19,22 * * ?}")
    public void warmDaily() {
        if (!enabled) {
            return;
        }
        List<double[]> points = resolvePoints();
        for (double[] point : points) {
            weatherDailyService.warmUp(point[0], point[1]);
        }
    }

    /**
     * 台风预热：每 30 分钟一轮（默认每小时 :10 与 :40），与列表/详情缓存 TTL 对齐。
     * <p>非台风季为一次列表请求（结果为空也会写缓存）；活跃期顺带拉取详情（列表项本身也需要最新位置）。
     */
    @Scheduled(cron = "${weather.warmup.typhoon-cron:0 10,40 * * * ?}")
    public void warmTyphoon() {
        if (!enabled) {
            return;
        }
        typhoonService.warmUp();
    }

    /** 解析预热坐标：留空 → 灌区参考中心点（唯一来源） */
    private List<double[]> resolvePoints() {
        List<double[]> points = new ArrayList<>();
        if (StringUtils.hasText(locations)) {
            for (String item : locations.split(";")) {
                String text = item.trim();
                if (text.isEmpty()) {
                    continue;
                }
                String[] pair = text.split(",");
                if (pair.length != 2) {
                    log.warn("weather.warmup.locations 项格式非法（应为 lon,lat）：{}", text);
                    continue;
                }
                try {
                    points.add(new double[]{Double.parseDouble(pair[0].trim()), Double.parseDouble(pair[1].trim())});
                } catch (NumberFormatException e) {
                    log.warn("weather.warmup.locations 项无法解析：{}", text);
                }
            }
        }
        if (points.isEmpty()) {
            points.add(new double[]{siteLon, siteLat});
        }
        return points;
    }
}
