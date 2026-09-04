package com.qgyun.hltgq.hltgqsite.model.service;

import com.qgyun.hltgq.hltgqsite.decision.mapper.FloodDroughtMapper;
import com.qgyun.hltgq.hltgqsite.weather.service.WeatherService;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherListItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型计算用气象逐小时降雨辅助：坐标解析 + 逐小时降雨对齐填充。
 * <p>短期来水预测、墒情预测共用同一口径：配置坐标优先，未配置回退雨量站经纬度；
 * 天气不可用/缺小时一律填 0（气象为非关键旁路数据）。
 */
@Service
public class ModelWeatherRainService {

    private static final Logger log = LoggerFactory.getLogger(ModelWeatherRainService.class);

    /** 模型窗口时间格式（yyyy-MM-dd HH:mm） */
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WeatherService weatherService;
    private final FloodDroughtMapper floodDroughtMapper;
    private final String weatherLon;
    private final String weatherLat;

    public ModelWeatherRainService(WeatherService weatherService,
                                   FloodDroughtMapper floodDroughtMapper,
                                   @Value("${flood-drought.weather-lon:}") String weatherLon,
                                   @Value("${flood-drought.weather-lat:}") String weatherLat) {
        this.weatherService = weatherService;
        this.floodDroughtMapper = floodDroughtMapper;
        this.weatherLon = trimToNull(weatherLon);
        this.weatherLat = trimToNull(weatherLat);
    }

    /**
     * 拉取 start 起 steps 个小时的逐小时降雨序列（缺失小时填 0，降雨保留 1 位小数）。
     *
     * @return 长度=steps 的逐小时降雨序列（mm）
     */
    public List<Double> loadHourlyRain(LocalDateTime start, int steps) {
        List<Double> result = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            result.add(0.0);
        }
        Double[] coord = resolveCoord();
        if (coord == null) {
            log.warn("气象逐小时降雨：天气坐标未配置且雨量站无经纬度，按 0 处理");
            return result;
        }
        try {
            List<WeatherListItemVO> hours = weatherService.hourlyWeather(coord[0], coord[1], null, null, null);
            Map<LocalDateTime, Double> byHour = new HashMap<>();
            for (WeatherListItemVO h : hours) {
                if (h.getDate() == null || h.getHour() == null || h.getRainfall() == null) {
                    continue;
                }
                try {
                    byHour.put(LocalDateTime.parse(h.getDate() + " " + h.getHour(), HOUR_FMT), h.getRainfall());
                } catch (Exception ignored) {
                    // 日期/小时格式异常的行跳过
                }
            }
            for (int i = 0; i < steps; i++) {
                Double v = byHour.get(start.plusHours(i));
                if (v != null) {
                    result.set(i, Math.round(v * 10.0) / 10.0);
                }
            }
        } catch (Exception e) {
            log.warn("气象逐小时降雨获取失败，按 0 处理：{}", e.getMessage());
        }
        return result;
    }

    /** 天气坐标：配置优先，未配置回退雨量站经纬度；均无则 null。 */
    private Double[] resolveCoord() {
        Double lon = parseDouble(weatherLon);
        Double lat = parseDouble(weatherLat);
        if (lon != null && lat != null) {
            return new Double[]{lon, lat};
        }
        String rainStcd = floodDroughtMapper.selectStationByType("#2#");
        if (rainStcd != null) {
            FloodDroughtMapper.SiteCoord coord = floodDroughtMapper.selectStationCoord(rainStcd);
            if (coord != null && coord.getLon() != null && coord.getLat() != null) {
                return new Double[]{coord.getLon(), coord.getLat()};
            }
        }
        return null;
    }

    private static Double parseDouble(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
