package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 日时段水情表 VO（对齐前端 RES_META.period）
 */
@Data
public class PeriodRegimeVO {

    /** 站点编号 */
    private String stcd;

    /** 站点名称 */
    private String stnm;

    /** 时间 */
    private LocalDateTime tm;

    /** 水位 (m) */
    private BigDecimal z;

    /** 水势：落 / 涨 / 平 / 无涨落信息 */
    private String wptn;

    /** 流量 (m³/s) */
    private BigDecimal q;

    /** 测流方法 */
    private String msqmt;

    /** 测积方法 */
    private String msamt;

    /** 测速方法 */
    private String msvmt;
}
