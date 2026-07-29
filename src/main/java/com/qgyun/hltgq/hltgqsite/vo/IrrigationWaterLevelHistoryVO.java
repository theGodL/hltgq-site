package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 灌区水位历史数据 VO（单条记录）
 * 小时维度：监测时间 + 水位值(m) + 1h涨幅(cm)
 */
@Data
public class IrrigationWaterLevelHistoryVO {

    /** 监测时间 "yyyy-MM-dd HH:00" */
    private String hour;

    /** 水位值 (m) */
    private BigDecimal waterLevel;

    /** 1h 水位涨幅 (cm)，正值=上涨，负值=下降。首小时为 null */
    private BigDecimal change;
}
