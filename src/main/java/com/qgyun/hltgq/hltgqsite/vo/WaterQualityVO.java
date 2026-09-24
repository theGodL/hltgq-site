package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 水质监测 VO（首页每站点最新一条 + 历史数据分页共用，七项指标）
 * <p>数值哨兵约定（与墒情一致）：-999（设备不存在）后端转 null 不返回；
 * -9991（设备异常）保留透传由前端展示 '--'。
 * <p>指标口径（2026-09-24 实测）：CODMN 高锰酸盐指数为报文连续指标（全部有值）；
 * CODCR 多数为 0（低于检出限，合法值照实返回，仅少量有值）；
 * BOD5 间歇上报（多数为 null，有值透传）；WT 水温取自 pcp_info（时间列 spt 与 nmisp tm 同组对齐）。
 */
@Data
public class WaterQualityVO {

    /** 测站编码（无编码站点为 null，前端留空显示） */
    private String stcd;

    /** 站点标识（skey = COALESCE(stcd, site)，查询/筛选回传用，不用于展示） */
    private String site;

    /** 站点档案主键 UUID（阈值行关联用，仅 monitoring/trend 填充；无档案关联时为 null） */
    private String siteId;

    /** 站点名称 */
    private String stnm;

    /** 站点经度（站点表 bviiio_x），档案缺失为 null */
    private BigDecimal lon;

    /** 站点纬度（站点表 bviiio_y），档案缺失为 null */
    private BigDecimal lat;

    /** 监测时间 */
    private LocalDateTime tm;

    /** 氨氮 (mg/L) */
    private BigDecimal nh3n;

    /** CODMN 高锰酸盐指数 (mg/L)，报文连续指标（全部有值） */
    private BigDecimal codmn;

    /** CODCR 化学需氧量 (mg/L)，0 = 低于检出限（实测多数为 0，照实返回） */
    private BigDecimal codcr;

    /** BOD 五日生化需氧量 (mg/L)，间歇上报（未上报时为 null） */
    private BigDecimal bod5;

    /** TP 总磷 (mg/L) */
    private BigDecimal tp;

    /** TN 总氮 (mg/L) */
    private BigDecimal tn;

    /** DO 溶解氧 (mg/L) */
    private BigDecimal dox;

    /** WT 水温 (℃)，取自 pcp_info（时间列 spt 与 nmisp tm 同组对齐），无对应组为 null */
    private BigDecimal wt;

    /** 该站水质阈值行（t_auto_hltgq_water_threshold，type 含 #8#），仅 monitoring 填充，无配置为空列表 */
    private List<WaterThresholdVO> thresholds;
}
