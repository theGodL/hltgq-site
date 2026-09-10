package com.qgyun.hltgq.hltgqsite.irrigation.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 灌溉用水 · 区间取数行（内部使用，不出 JSON）：
 * 一个站点在时间窗口 [start, end] 内的末行总累计 ttf 与起点前最近一条 ttf（区间累计 = ttf − prevTtf）。
 */
@Data
public class IrrigationIntervalVO {

    /** 站点标识（COALESCE(stcd, site)：老站为编号，MQTT 站为 site UUID） */
    private String site;

    /** 站点名称 */
    private String stnm;

    /** 窗口内末行监测时间 */
    private LocalDateTime tm;

    /** 窗口内末行总累计流量(m³) */
    private BigDecimal ttf;

    /** 起点前最近一条 ttf 非空行的总累计流量(m³)，可为空（基准按 0） */
    private BigDecimal prevTtf;
}
