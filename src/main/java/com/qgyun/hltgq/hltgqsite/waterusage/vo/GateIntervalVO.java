package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 闸口区间流量取数结果（t_auto_hltgq_water_wt_nfo）：
 * 区间累计 = ttf（窗口 [start, end] 内末行）− prevTtf（起点前最近一条 ttf 非空行），
 * 起点前无基准按 0；口径与流量监测/闸门监测/灌溉用水一致。
 */
@Data
public class GateIntervalVO {

    /** 站点标识（COALESCE(stcd, site)） */
    private String site;

    /** 站点名称 */
    private String stnm;

    /** 窗口内末行时间 */
    private LocalDateTime tm;

    /** 窗口内末行总累计流量（m³） */
    private BigDecimal ttf;

    /** 起点前基准总累计流量（m³，可为 null） */
    private BigDecimal prevTtf;
}
