package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 水库水位-水库水情 VO（单条记录）
 * 字段：站点编号、站名、时间、汛限水位、正常蓄水位、水位、水势、入库流量、出库流量
 */
@Data
public class ReservoirRegimeVO {

    /** 站点编号 */
    private String stcd;

    /** 站点名称 */
    private String stnm;

    /** 监测时间 */
    private LocalDateTime tm;

    /** 汛限水位 (m)（字段名沿用 warningLevel 保持接口兼容，水库口径术语为汛限水位） */
    private BigDecimal warningLevel;

    /** 正常蓄水位 (m)（字段名沿用 guaranteedLevel 保持接口兼容，水库口径术语为正常蓄水位） */
    private BigDecimal guaranteedLevel;

    /** 水位值 (m) */
    private BigDecimal z;

    /** 水势：无涨落信息 / 涨 / 落 / 平 */
    private String wptn;

    /** 入库流量 (m³/s) */
    private BigDecimal inq;

    /** 出库流量 (m³/s) */
    private BigDecimal otq;
}
