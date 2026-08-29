package com.qgyun.hltgq.hltgqsite.weather.controller;

import com.qgyun.hltgq.hltgqsite.weather.service.WeatherService;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherCardVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherListItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 天气数据接口（Open-Meteo 代理）
 * <p>实时天气卡片 /weather/current，逐小时天气列表 /weather/hourly。
 * <p>坐标由前端地图传入（WGS-84，与天地图一致，无需坐标转换），站点名称 location 可选，
 * 缺省取 weather.default-location 配置。参数越界 400（全局 IllegalArgumentException 处理），
 * 超限 429（ResponseStatusException），上游失败降级不抛 5xx。
 */
@RestController
@RequestMapping("/weather")
public class WeatherController {

    @Autowired
    private WeatherService weatherService;

    /**
     * 实时天气卡片
     *
     * @param lon      经度（-180~180）
     * @param lat      纬度（-90~90）
     * @param location 站点名称（可选，缺省取配置默认值）
     */
    @GetMapping("/current")
    public WeatherCardVO current(@RequestParam double lon,
                                 @RequestParam double lat,
                                 @RequestParam(required = false) String location) {
        validateCoordinate(lon, lat);
        return weatherService.currentWeather(lon, lat, location);
    }

    /**
     * 逐小时天气列表：时间倒序、id 重新编号
     *
     * @param startDate 开始日期 yyyy-MM-dd（可选）
     * @param endDate   结束日期 yyyy-MM-dd（可选）
     */
    @GetMapping("/hourly")
    public List<WeatherListItemVO> hourly(@RequestParam double lon,
                                          @RequestParam double lat,
                                          @RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate,
                                          @RequestParam(required = false) String location) {
        validateCoordinate(lon, lat);
        return weatherService.hourlyWeather(lon, lat, startDate, endDate, location);
    }

    private void validateCoordinate(double lon, double lat) {
        if (lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("lon 超出范围 [-180,180]: " + lon);
        }
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("lat 超出范围 [-90,90]: " + lat);
        }
    }
}
