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

    /** 站点经度（站点表 bviiio_x） */
    private BigDecimal lon;

    /** 站点纬度（站点表 bviiio_y） */
    private BigDecimal lat;

    /** 监测日期（最新数据时间） */
    private LocalDateTime tm;

    /** 当前降雨量 DRP（mm）— 水文日累计（8:00 ~ 当前） */
    private BigDecimal drp;

    /** 累计雨量 DYP（mm）— RTU安装至今的总累计，永不归零 */
    private BigDecimal dyp;

    /** 1h 降雨量（mm）= 当前DYP - 1h前DYP */
    private BigDecimal rain1h;

    /** 3h 降雨量（mm）= 当前DYP - 3h前DYP */
    private BigDecimal rain3h;

    /** 6h 降雨量（mm）= 当前DYP - 6h前DYP */
    private BigDecimal rain6h;
}
