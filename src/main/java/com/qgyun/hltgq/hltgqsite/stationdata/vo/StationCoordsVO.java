package com.qgyun.hltgq.hltgqsite.stationdata.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 站点经纬度（/station-data/coords 响应元素）。
 * <p>数据来源：站点档案表 t_auto_hltgq_5nw74_vnqqef 坐标拆分列 bviiio_x/bviiio_y；
 * 未填坐标的站点 lon/lat 为 null；传入 id 未命中（不存在/非本企业）不返回该条。
 */
@Data
public class StationCoordsVO {

    /** 站点档案主键（其他业务表 site 值） */
    private String id;

    /** 经度（bviiio_x；未填为 null） */
    private BigDecimal lon;

    /** 纬度（bviiio_y；未填为 null） */
    private BigDecimal lat;
}
