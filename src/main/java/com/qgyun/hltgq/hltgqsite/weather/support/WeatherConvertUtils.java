package com.qgyun.hltgq.hltgqsite.weather.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 天气数据公共转换：WMO 码 / 风向 / 蒲福风级 / 雨量 2 位截断 / EC46 三维度天气码推导。
 * <p>新旧服务共用（评审要求：抽公共转换，避免 daily 与 current/hourly 口径漂移）。
 */
public final class WeatherConvertUtils {

    private WeatherConvertUtils() {
    }

    /** WMO 天气代码 → 中文描述（全量 28 项） */
    private static final Map<Integer, String> WEATHER_CODE_MAP = new HashMap<>();

    static {
        WEATHER_CODE_MAP.put(0, "晴朗");
        WEATHER_CODE_MAP.put(1, "主要晴朗");
        WEATHER_CODE_MAP.put(2, "多云");
        WEATHER_CODE_MAP.put(3, "阴天");
        WEATHER_CODE_MAP.put(45, "雾");
        WEATHER_CODE_MAP.put(48, "雾凇");
        WEATHER_CODE_MAP.put(51, "毛毛雨");
        WEATHER_CODE_MAP.put(53, "中度毛毛雨");
        WEATHER_CODE_MAP.put(55, "强毛毛雨");
        WEATHER_CODE_MAP.put(56, "冻雨");
        WEATHER_CODE_MAP.put(57, "强冻雨");
        WEATHER_CODE_MAP.put(61, "小雨");
        WEATHER_CODE_MAP.put(63, "中雨");
        WEATHER_CODE_MAP.put(65, "大雨");
        WEATHER_CODE_MAP.put(66, "冻雨");
        WEATHER_CODE_MAP.put(67, "强冻雨");
        WEATHER_CODE_MAP.put(71, "小雪");
        WEATHER_CODE_MAP.put(73, "中雪");
        WEATHER_CODE_MAP.put(75, "大雪");
        WEATHER_CODE_MAP.put(77, "雪粒");
        WEATHER_CODE_MAP.put(80, "阵雨");
        WEATHER_CODE_MAP.put(81, "中度阵雨");
        WEATHER_CODE_MAP.put(82, "强阵雨");
        WEATHER_CODE_MAP.put(85, "阵雪");
        WEATHER_CODE_MAP.put(86, "强阵雪");
        WEATHER_CODE_MAP.put(95, "雷雨");
        WEATHER_CODE_MAP.put(96, "雷雨伴冰雹");
        WEATHER_CODE_MAP.put(99, "强雷雨伴冰雹");
    }

    /** WMO 码 → 中文（未收录返回"未知"，与现有 current/hourly 行为一致） */
    public static String translateWeatherCode(int code) {
        return WEATHER_CODE_MAP.getOrDefault(code, "未知");
    }

    /** 16 方位风向 */
    private static final String[] WIND_DIRECTIONS = {"北", "北东北", "东北", "东东北", "东", "东东南", "东南", "南东南",
            "南", "南西南", "西南", "西西南", "西", "西西北", "西北", "北西北"};

    /** 角度 → 16 方位中文（含"风"字后缀） */
    public static String translateWindDirection(int degree) {
        int normalized = ((degree % 360) + 360) % 360;
        int index = (int) Math.round(normalized / 22.5) % 16;
        return WIND_DIRECTIONS[index] + "风";
    }

    /** 风速(km/h) → 蒲福风级 0~12 */
    public static Integer calculateWindLevel(double speedKmh) {
        if (speedKmh < 1) return 0;
        if (speedKmh < 6) return 1;
        if (speedKmh < 12) return 2;
        if (speedKmh < 20) return 3;
        if (speedKmh < 29) return 4;
        if (speedKmh < 39) return 5;
        if (speedKmh < 50) return 6;
        if (speedKmh < 62) return 7;
        if (speedKmh < 75) return 8;
        if (speedKmh < 89) return 9;
        if (speedKmh < 103) return 10;
        if (speedKmh < 118) return 11;
        return 12;
    }

    /**
     * 雨量 2 位小数截断（业主口径：不四舍五入）
     */
    public static double truncateRainfall(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.DOWN).doubleValue();
    }

    /**
     * EC46 三维度天气码推导（评审 B1：概率 + 雨量 + 云量）。
     * <p>概率 ≥ 70%：按雨量分中雨(63)/小雨(61)；≥ 40% → 阵雨(80)；
     * 否则按云量：&lt;20% 晴朗(0) / 20~60% 多云(2) / &gt;60% 阴天(3)。
     * <p>云量缺失时退化为「多云(2)」，避免无依据地宣称晴朗。
     */
    public static int deriveWeatherCode(Integer rainProbability, Double rainfall, Double cloudCover) {
        int probability = rainProbability == null ? 0 : rainProbability;
        double rain = rainfall == null ? 0.0 : rainfall;
        if (probability >= 70) {
            if (rain >= 25) return 65;
            if (rain >= 10) return 63;
            return 61;
        }
        if (probability >= 40) return 80;
        if (cloudCover == null) return 2;
        if (cloudCover < 20) return 0;
        if (cloudCover <= 60) return 2;
        return 3;
    }
}
