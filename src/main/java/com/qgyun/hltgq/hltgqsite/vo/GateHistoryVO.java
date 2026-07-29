package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 闸门监测历史数据 VO（每条记录对应一个闸孔的一条实时数据）
 */
@Data
public class GateHistoryVO {

    /** 站点名称（如"南山寺节制闸"） */
    private String stnm;

    /** 闸孔编号（如"1"、"2"） */
    private String gateNo;

    /** 设备名称（如"南山寺节制闸2#"） */
    private String deviceName;

    /** 监测时间 */
    private LocalDateTime tm;

    /** 闸门开度 (m) */
    private BigDecimal openDegree;

    /** 闸前水位 (m) — 上游水位 */
    private BigDecimal upZ;

    /** 闸后水位 (m) — 下游水位 */
    private BigDecimal downZ;
}
