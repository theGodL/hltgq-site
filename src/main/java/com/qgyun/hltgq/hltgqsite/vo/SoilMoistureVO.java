package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 墒情监测 VO（首页每站点最新一条 + 历史数据分页共用）
 * 含水量数值为 -999 时表示设备异常（前端展示 '--'），后端透传不拦截
 */
@Data
public class SoilMoistureVO {

    /** 测站编码（无编码站点为 null，前端留空显示） */
    private String stcd;

    /** 站点标识（stcd 或 site UUID，无编号站点查询/筛选用，不用于展示） */
    private String site;

    /** 站点名称 */
    private String stnm;

    /** 监测时间 */
    private LocalDateTime tm;

    /** 10 厘米土壤含水量 */
    private BigDecimal mten;

    /** 20 厘米土壤含水量 */
    private BigDecimal mtwenty;

    /** 30 厘米土壤含水量 */
    private BigDecimal mthirty;

    /** 40 厘米土壤含水量 */
    private BigDecimal mforty;

    /** 50 厘米土壤含水量 */
    private BigDecimal mfifty;

    /** 60 厘米土壤含水量 */
    private BigDecimal msixty;

    /** 80 厘米土壤含水量 */
    private BigDecimal meighty;

    /** 100 厘米土壤含水量 */
    private BigDecimal mhundred;

    /** 电压 (V)，取关联电压表 t_auto_hltgq_water_vol_info 最新值（仅 monitoring 返回，history 为 null） */
    private BigDecimal vol;
}
