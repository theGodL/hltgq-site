package com.qgyun.hltgq.hltgqsite.weather.service;

import com.qgyun.hltgq.hltgqsite.weather.support.WeatherConvertUtils;
import com.qgyun.hltgq.hltgqsite.weather.vo.RainTrendDayVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.RainTrendVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherDailyListVO;
import com.qgyun.hltgq.hltgqsite.weather.vo.WeatherDailyVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * N4 雨情趋势预判服务（方案 §6）。
 * <p><b>实时读 N1 日级数据计算，不落独立缓存</b>（评审 A1）：与 `/weather/daily` 永远一致，
 * 降级状态（`degradedSegments` / `exactDays`）也完全继承——独立缓存会产生"daily 已刷新而雨情仍用旧值"的不一致窗口。
 * <p>职责边界：本项目 `weather` 包保持无 Mapper/DAO 分层，<b>实测段由前端组合现有雨量接口绘制</b>，
 * 后端只产出预报段（方案 §6.1）。
 */
@Service
public class RainTrendService {

    private static final Logger log = LoggerFactory.getLogger(RainTrendService.class);

    /** 国标 GB/T 28592 日雨量分级的起报下限（mm）：低于该值为"无雨"，等级为 null */
    private static final double RAIN_LEVEL_MIN = 0.1D;

    @Autowired
    private WeatherDailyService dailyService;

    /**
     * 雨情趋势（对外入口）。
     *
     * @param days 预报天数，与 `/weather/daily` 同口径（1~40 归一）
     */
    public RainTrendVO rainTrend(double lon, double lat, Integer days, String location) {
        WeatherDailyListVO daily = dailyService.daily(lon, lat, days, location);

        RainTrendVO vo = new RainTrendVO();
        vo.setLocation(daily.getLocation());
        vo.setUpdatedAt(daily.getUpdatedAt());
        vo.setSource(daily.getSource());
        vo.setDegradedSegments(daily.getDegradedSegments());

        List<WeatherDailyVO> source = daily.getList();
        List<RainTrendDayVO> list = new ArrayList<>(source.size());

        double total = 0D;
        double cumulative = 0D;
        int rainyDays = 0;
        int exactDays = 0;
        Double maxRainfall = null;
        String maxDate = null;

        int dryRunLength = 0;
        String dryRunStart = null;
        int dryDays = 0;
        String dryDaysStart = null;

        for (WeatherDailyVO item : source) {
            Double rainfall = item.getRainfall();
            if (rainfall != null) {
                total += rainfall;
                cumulative += rainfall;
                if (rainfall >= RAIN_LEVEL_MIN) {
                    rainyDays++;
                }
                if (maxRainfall == null || rainfall > maxRainfall) {
                    maxRainfall = rainfall;
                    maxDate = item.getDate();
                }
            }

            // 连续无雨段：null（成员全缺）视为"未知"并打断连续段，避免把数据缺失夸大为干旱
            if (rainfall != null && rainfall < RAIN_LEVEL_MIN) {
                if (dryRunLength == 0) {
                    dryRunStart = item.getDate();
                }
                dryRunLength++;
                if (dryRunLength > dryDays) {
                    dryDays = dryRunLength;
                    dryDaysStart = dryRunStart;
                }
            } else {
                dryRunLength = 0;
                dryRunStart = null;
            }

            if (WeatherDailyService.SEGMENT_EXACT.equals(item.getAccuracy())) {
                exactDays++;
            }

            RainTrendDayVO day = new RainTrendDayVO();
            day.setDate(item.getDate());
            day.setRainfall(rainfall);
            day.setRainProbability(item.getRainProbability());
            day.setCumulative(WeatherConvertUtils.truncateRainfall(cumulative));
            day.setRainLevel(rainLevel(rainfall));
            day.setAccuracy(item.getAccuracy());
            list.add(day);
        }

        // 全为 0（含无数据）时挑峰结果无意义，按方案返回 null
        if (maxRainfall == null || maxRainfall <= 0D) {
            maxRainfall = null;
            maxDate = null;
        } else {
            maxRainfall = WeatherConvertUtils.truncateRainfall(maxRainfall);
        }

        vo.setTotalRainfall(WeatherConvertUtils.truncateRainfall(total));
        vo.setMaxDailyRainfall(maxRainfall);
        vo.setMaxDailyDate(maxDate);
        vo.setRainyDays(rainyDays);
        // dryDays = 0 时返回 null（评审 C3）
        vo.setDryDays(dryDays);
        vo.setDryDaysStart(dryDays == 0 ? null : dryDaysStart);
        // exactDays 由 list 统计（与 N1 同口径：本次返回 list 中 accuracy=exact 的实际天数）
        vo.setExactDays(exactDays);
        vo.setList(list);

        log.info("雨情趋势计算完成: 站点={} 天数={} 累计雨量={}mm 有雨天数={} 最长无雨={}天 来源={} 缺失段={}",
                vo.getLocation(), list.size(), vo.getTotalRainfall(), rainyDays, dryDays,
                vo.getSource(), vo.getDegradedSegments());
        return vo;
    }

    /**
     * 国标 GB/T 28592 日雨量分级（方案 §6.2）。
     * <p>&lt; 0.1 mm 为 null；小雨 0.1~9.9 / 中雨 10~24.9 / 大雨 25~49.9 /
     * 暴雨 50~99.9 / 大暴雨 100~249.9 / 特大暴雨 ≥250。
     */
    private String rainLevel(Double rainfall) {
        if (rainfall == null || rainfall < RAIN_LEVEL_MIN) {
            return null;
        }
        if (rainfall < 10D) {
            return "小雨";
        }
        if (rainfall < 25D) {
            return "中雨";
        }
        if (rainfall < 50D) {
            return "大雨";
        }
        if (rainfall < 100D) {
            return "暴雨";
        }
        if (rainfall < 250D) {
            return "大暴雨";
        }
        return "特大暴雨";
    }
}
