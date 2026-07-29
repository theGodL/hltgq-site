package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class StationMetricsVO {

    private String stcd;

    private String stnm;

    /** 最新水位(m)，无数据为 null */
    private BigDecimal z;

    /** 水位观测时间，无数据为 null */
    private LocalDateTime riverTm;

    /** 当前降雨量 DRP（mm）— 水文日累计（8:00 ~ 当前），每日8:00归零；无数据为 null */
    private BigDecimal drp;

    /** 雨量观测日期，无数据为 null */
    private LocalDate pptnTm;

    /** water=仅水位 / rain=仅雨量 / all=两者都有 */
    private String type;
}
