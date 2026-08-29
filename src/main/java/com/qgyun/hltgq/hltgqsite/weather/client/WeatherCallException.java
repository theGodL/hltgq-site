package com.qgyun.hltgq.hltgqsite.weather.client;

/**
 * Open-Meteo 调用异常：HTTP 非 2xx、网络不可达、超时或响应解析失败。
 * <p>天气为非关键旁路数据，WeatherService 捕获后降级（卡片 default、列表空数组），
 * 不向上抛 5xx，保证大屏页面常驻可用。
 */
public class WeatherCallException extends RuntimeException {

    public WeatherCallException(String message) {
        super(message);
    }

    public WeatherCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
