package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 水库水情-水情简报 VO（对齐前端 RES_META.brief）
 * <p>覆盖三个水情站：周家河、花凉亭坝下、花凉亭坝上。
 */
@Data
public class WaterBriefVO {

    /** 站点编号 */
    private String stcd;

    /** 站点名称 */
    private String stnm;

    /** 警戒水位 (m)，站点固定参考值（阈值表 threshold） */
    private BigDecimal wrz;

    /** 昨日 8 点水位 (m) */
    private BigDecimal y8;

    /** 昨日 20 点水位 (m) */
    private BigDecimal y20;

    /** 今天 8 点水位 (m) */
    private BigDecimal t8;

    /** 水势：涨 / 落 / 平 / 无涨落信息 */
    private String wptn;

    /** 与昨日 8 点比 (m) = t8 - y8，可正可负 */
    private BigDecimal cmp;

    /** 流量 (m³/s)，取今天 8 点记录 Q 字段 */
    private BigDecimal q;

    /** 蓄水量（百万 m³）。当前无对应数据源，恒为 null，前端显示 '-' */
    private BigDecimal w;

    /** 当年最高水位 (m)（当年 1 月 1 日以来） */
    private BigDecimal maxz;

    /** 当年最高水位出现时间 */
    private LocalDateTime maxTm;

    /** 设防水位 (m)，站点固定参考值（阈值表 guarantee） */
    private BigDecimal dsflz;
}
