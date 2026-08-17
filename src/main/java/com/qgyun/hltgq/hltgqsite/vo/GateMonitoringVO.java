package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 闸门监测 VO（每个闸站一条，包含该站所有闸孔的最新数据）
 *
 * <p>闸前水位/闸后水位取各闸孔中最新一条记录的值（同一时刻各孔水位应一致）。
 */
@Data
public class GateMonitoringVO {

    /** 站点 UUID（对应 t_auto_hltgq_water_gate.site） */
    private String siteId;

    /** 站点名称 */
    private String siteName;

    /** 监测时间（取该站各闸孔中最新者） */
    private LocalDateTime tm;

    /** 闸前水位 (m) — 上游水位 */
    private BigDecimal upZ;

    /** 闸后水位 (m) — 下游水位 */
    private BigDecimal downZ;

    /** 流量 (m³/s)，取关联流量表 t_auto_hltgq_water_wt_nfo 最新值 */
    private BigDecimal q;

    /** 电压 (V)，取关联电压表 t_auto_hltgq_water_vol_info 最新值 */
    private BigDecimal vol;

    /** 站点经度（站点表 bviiio_x） */
    private BigDecimal lon;

    /** 站点纬度（站点表 bviiio_y） */
    private BigDecimal lat;

    /** 各闸孔数据（按闸孔号排序） */
    private List<GateHoleData> holes;
}
