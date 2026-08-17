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

    /** 当前降雨量（mm）— 当前水文日累计（DYP 增量：最新DYP - 服务器当前水文日 8 点起点前基线DYP）。
     *  基线随服务器当前时刻滑动，与 dailyDyp（昨日雨量）口径错开，避免最新报文滞后时两值重合 */
    private BigDecimal drp;

    /** 累计雨量 DYP（mm）— RTU安装至今的总累计，永不归零 */
    private BigDecimal dyp;

    /** 1h 降雨量（mm）= 当前DYP - 1h前DYP */
    private BigDecimal rain1h;

    /** 3h 降雨量（mm）= 当前DYP - 3h前DYP */
    private BigDecimal rain3h;

    /** 6h 降雨量（mm）= 当前DYP - 6h前DYP */
    private BigDecimal rain6h;

    /** 昨日雨量（mm）— 最近一个完整水文日的累计雨量（DYP 正向增量）：
     *  当前时间 < 8 点 → 前日 08:00 ~ 昨日 08:00；当前时间 >= 8 点 → 昨日 08:00 ~ 今日 08:00 */
    private BigDecimal dailyDyp;

    /** 电压 (V)，取关联电压表 t_auto_hltgq_water_vol_info 最新值 */
    private BigDecimal vol;
}
