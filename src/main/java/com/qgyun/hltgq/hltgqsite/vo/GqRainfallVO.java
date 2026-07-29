package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 灌区雨量监测 VO
 * 每站点最新一条雨量数据，含 1h/3h/6h 时段增量
 */
@Data
public class GqRainfallVO {

    /** 站点编号 */
    private String stcd;

    /** 站点ID */
    private String id;

    /** 站点名称 */
    private String stnm;

    /** 监测日期（最新数据时间） */
    private LocalDateTime tm;

    /** 时段降水量 DRP（mm） */
    private BigDecimal drp;

    /** 日降雨量 DYP（mm） */
    private BigDecimal dyp;

    /** 1h 降雨量（mm）= 当前DRP - 1h前DRP */
    private BigDecimal rain1h;

    /** 3h 降雨量（mm）= 当前DRP - 3h前DRP */
    private BigDecimal rain3h;

    /** 6h 降雨量（mm）= 当前DRP - 6h前DRP */
    private BigDecimal rain6h;
}
