package com.qgyun.hltgq.hltgqsite.wateruse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * 用水总结 · 灌季窗口配置（第一版，业主 2026-09-11 确认）。
 * <p>夏灌 05-01 ~ 08-31（泡田栽插 → 分蘖拔节、用水高峰）；
 * 秋灌 09-01 ~ 10-31（抽穗灌浆 → 成熟停水）。
 * <p>窗口按 MM-dd 配置，逐自然年生成（如 2026 夏灌 = 2026-05-01 ~ 2026-08-31）；
 * 非灌期月份（11 月 ~ 次年 4 月）的记录不落入灌季维度，年/月维度不受影响。
 */
@Component
public class WaterUseSeasonConfig {

    /** 夏灌起（MM-dd） */
    @Value("${wateruse.season.summer-start:05-01}")
    private String summerStart;

    /** 夏灌止（MM-dd） */
    @Value("${wateruse.season.summer-end:08-31}")
    private String summerEnd;

    /** 秋灌起（MM-dd） */
    @Value("${wateruse.season.autumn-start:09-01}")
    private String autumnStart;

    /** 秋灌止（MM-dd） */
    @Value("${wateruse.season.autumn-end:10-31}")
    private String autumnEnd;

    /** 灌季窗口 */
    public static final class SeasonWindow {

        /** 机器可读键（如 2026-summer） */
        public final String key;

        /** 展示标签（如 2026夏灌） */
        public final String label;

        /** 窗口起（含） */
        public final LocalDate start;

        /** 窗口止（含） */
        public final LocalDate end;

        private SeasonWindow(String key, String label, LocalDate start, LocalDate end) {
            this.key = key;
            this.label = label;
            this.start = start;
            this.end = end;
        }
    }

    /** 某自然年的灌季窗口（顺序：夏灌 → 秋灌） */
    public List<SeasonWindow> windowsOf(int year) {
        return Arrays.asList(
                build(year, "summer", "夏灌", summerStart, summerEnd),
                build(year, "autumn", "秋灌", autumnStart, autumnEnd));
    }

    private SeasonWindow build(int year, String code, String name, String startMd, String endMd) {
        LocalDate start = parse(year, startMd, "wateruse.season." + code + "-start");
        LocalDate end = parse(year, endMd, "wateruse.season." + code + "-end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("灌季配置错误：" + "wateruse.season." + code
                    + "-end（" + endMd + "）早于 -start（" + startMd + "）");
        }
        return new SeasonWindow(year + "-" + code, year + name, start, end);
    }

    private LocalDate parse(int year, String monthDay, String property) {
        try {
            String[] parts = monthDay.trim().split("-");
            return LocalDate.of(year, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("灌季配置错误：" + property + " 日期格式应为 MM-dd，当前=" + monthDay);
        }
    }
}
