package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 水位监测-灌区：每个站点最新一条水位数据
 */
@Data
public class IrrigationWaterLevelVO {

    /** 站点编号 (STCD) */
    private String stcd;

    /** 站点名称 */
    private String stnm;

    /** 站点ID */
    private String id;

    /** 监测日期 */
    private LocalDateTime tm;

    /** 水位值 (m) */
    private BigDecimal z;

    /** 1h水位涨幅 (cm) */
    private BigDecimal rise1h;

    /** 电压 (V)，取关联电压表 t_auto_hltgq_water_vol_info 最新值 */
    private BigDecimal vol;

    /** 在线状态：以站点表 zebpsu 状态判断（#1# 在线 / #2# 离线）。水位站均为库上站点，不按采集时间断联 */
    private Boolean isOnline;
}
