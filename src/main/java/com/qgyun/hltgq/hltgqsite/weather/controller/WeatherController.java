package com.qgyun.hltgq.hltgqsite.weather.controller;

import com.qgyun.hltgq.hltgqsite.weather.service.RainTrendService;
import com.qgyun.hltgq.hltgqsite.weather.service.TyphoonService;
import com.qgyun.hltgq.hltgqsite.weather.service.WeatherDailyService;
import com.qgyun.hltgq.hltgqsite.weather.service.WeatherService;
import com.qgyun.hltgq.hltgqsite.weather.vo.RainTrendVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.TyphoonActiveVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.TyphoonDetailVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherCardVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherDailyListVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherListItemVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 天气数据接口（Open-Meteo 代理 + 台风观测）
 * <p>实时天气卡片 /weather/current，逐小时天气列表 /weather/hourly，
 * 40 天日级预报 /weather/daily，雨情趋势 /weather/rain-trend，
 * 活跃台风列表 /weather/typhoon/active，台风路径详情 /weather/typhoon/detail。
 * <p>坐标由前端地图传入（WGS-84，与天地图一致，无需坐标转换），站点名称 location 可选，
 * 缺省取 weather.default-location 配置。参数越界 400（全局 IllegalArgumentException 处理），
 * 超限 429（ResponseStatusException），上游失败降级不抛 5xx。
 */
@RestController
@RequestMapping("/weather")
public class WeatherController {

    @Autowired
    private WeatherService weatherService;

    @Autowired
    private WeatherDailyService weatherDailyService;

    @Autowired
    private RainTrendService rainTrendService;

    @Autowired
    private TyphoonService typhoonService;

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
     * @param startDate 开始日期 yyyy-MM-dd（可选，非法格式 400）
     * @param endDate   结束日期 yyyy-MM-dd（可选，非法格式 400）
     */
    @GetMapping("/hourly")
    public List<WeatherListItemVO> hourly(@RequestParam double lon,
                                          @RequestParam double lat,
                                          @RequestParam(required = false) String startDate,
                                          @RequestParam(required = false) String endDate,
                                          @RequestParam(required = false) String location) {
        validateCoordinate(lon, lat);
        startDate = validateDate("startDate", startDate);
        endDate = validateDate("endDate", endDate);
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

    /**
     * 日期入参校验（yyyy-MM-dd）：留空视为不筛选（返回 null），非空则必须是合法日期。
     * <p>实测发现：格式非法（如 `2026/09/15`）会被下游按字典序比较后静默筛空——
     * 调用方无法区分「窗口内确实无数据」与「参数写错」，与坐标 / 台风 ID 的 400 口径也不一致。
     *
     * @return 去首尾空白后的日期（供下游使用，避免校验通过但比较仍失配）；留空返回 null
     */
    private String validateDate(String name, String date) {
        if (date == null || date.trim().isEmpty()) {
            return null;
        }
        String value = date.trim();
        try {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new IllegalArgumentException(name + " 格式非法（须为 yyyy-MM-dd）: " + date);
        }
        return value;
    }

    /**
     * 未来 40 天日级预报（方案 §3.2）
     * <p>1~16 天为常规预报（`accuracy=exact`），17~40 天为 EC46 延伸期趋势（`accuracy=extended`）；
     * `days` 单参数即决定返回天数与上游拉取段数，`degradedSegments` 显式标记缺失段。
     *
     * @param days 返回天数（可选，默认 40，范围 1~40，&gt;40 按 40 处理）
     */
    @GetMapping("/daily")
    public WeatherDailyListVO daily(@RequestParam double lon,
                                    @RequestParam double lat,
                                    @RequestParam(required = false) Integer days,
                                    @RequestParam(required = false) String location) {
        validateCoordinate(lon, lat);
        return weatherDailyService.daily(lon, lat, days, location);
    }

    /**
     * 雨情趋势预判（方案 §6.2）
     * <p>实时读日级数据计算（不落独立缓存）：含累计雨量、最大单日雨量、有雨天数、
     * 最长连续无雨天数与国标日雨量分级，降级状态与 `/weather/daily` 完全一致。
     *
     * @param days 预报天数（可选，默认 40，与 /weather/daily 同口径）
     */
    @GetMapping("/rain-trend")
    public RainTrendVO rainTrend(@RequestParam double lon,
                                 @RequestParam double lat,
                                 @RequestParam(required = false) Integer days,
                                 @RequestParam(required = false) String location) {
        validateCoordinate(lon, lat);
        return rainTrendService.rainTrend(lon, lat, days, location);
    }

    /**
     * 活跃台风列表（方案 §5.2 ①）：非台风季返回空列表；含到灌区参考中心点的距离（仅供排序参考）
     */
    @GetMapping("/typhoon/active")
    public TyphoonActiveVO typhoonActive() {
        return typhoonService.active();
    }

    /**
     * 台风路径详情（方案 §5.2 ②）：`track` 为实况路径、`forecast` 为预报路径
     *
     * @param typhoonId 台风 ID（来自 /weather/typhoon/active，纯数字；非法入参 400）
     */
    @GetMapping("/typhoon/detail")
    public TyphoonDetailVO typhoonDetail(@RequestParam String typhoonId) {
        return typhoonService.detail(typhoonId);
    }
}
