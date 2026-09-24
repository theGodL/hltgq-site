package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 站点/测站统一视图（雨量/水位/闸门通用）
 */
@Data
public class StationSiteVO {

    /**
     * 站点标识（业务表原值，沿用既有契约不变）：雨量 / 水位为站点编号 STCD；
     * 闸门 / 流量 / 墒情业务表的 site 列为站点管理主键（与 siteId 同值）。
     * 需要「站点编号」时取 {@link #stcd}
     */
    private String code;

    /** 站点编号（站点档案表 iofhpi，如 9000000001 / 3206400001 / QSJSZ / NSS）；档案无对应记录时为 null */
    private String stcd;

    /** 站点名称 */
    private String name;

    /**
     * 站点管理主键（站点档案表 id）：站点排序配置的站点标识，也是各监测接口按站点查询可用的标识；
     * 档案表中无对应站点时为空，该站不参与排序（保持默认顺序）
     */
    private String siteId;

    /** 站点经度（站点档案表 bviiio_x），档案无对应记录时为 null */
    private BigDecimal lon;

    /** 站点纬度（站点档案表 bviiio_y），档案无对应记录时为 null */
    private BigDecimal lat;
}
