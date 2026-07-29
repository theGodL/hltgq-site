package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 水库水位-河道水情 VO（单条记录）
 * 字段：监测时间、警戒水位、保证水位、水位、水势
 */
@Data
public class RiverRegimeVO {

    /** 站点编号 */
    private String stcd;

    /** 站点名称 */
    private String stnm;

    /** 监测时间 */
    private LocalDateTime tm;

    /** 警戒水位 (m)，站点固定参考值 */
    private BigDecimal warningLevel;

    /** 保证水位 (m)，站点固定参考值 */
    private BigDecimal guaranteedLevel;

    /** 水位 (m) */
    private BigDecimal z;

    /** 水势：无涨落信息 / 涨 / 落 / 平 */
    private String wptn;
}
