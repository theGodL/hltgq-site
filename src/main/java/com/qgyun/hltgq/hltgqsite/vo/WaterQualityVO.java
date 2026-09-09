package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 水质监测 VO（首页每站点最新一条 + 历史数据分页共用）
 * <p>数值哨兵约定（与墒情一致）：-999（设备不存在）后端转 null 不返回；
 * -9991（设备异常）保留透传由前端展示 '--'。
 * <p>BOD5 为实验室指标设备不上报恒为 null（前端展示 '--'）；
 * CODCR=0.0 按"低于检出限"理解照实返回（0 值合法入库）。
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

    /** 监测时间 */
    private LocalDateTime tm;

    /** 氨氮 (mg/L) */
    private BigDecimal nh3n;

    /** COD 化学需氧量 (mg/L)，0 = 低于检出限 */
    private BigDecimal codcr;

    /** BOD 五日生化需氧量 (mg/L)，设备未上报恒为 null */
    private BigDecimal bod5;

    /** TP 总磷 (mg/L) */
    private BigDecimal tp;

    /** TN 总氮 (mg/L) */
    private BigDecimal tn;

    /** DO 溶解氧 (mg/L) */
    private BigDecimal dox;

    /** 该站水质阈值行（t_auto_hltgq_water_threshold，type 含 #8#），仅 monitoring 填充，无配置为空列表 */
    private List<WaterThresholdVO> thresholds;
}
