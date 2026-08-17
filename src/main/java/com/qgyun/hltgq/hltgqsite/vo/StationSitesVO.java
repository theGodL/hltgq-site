package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.util.List;

/**
 * 全量站点分类视图
 */
@Data
public class StationSitesVO {

    /** 雨量监测站点 */
    private List<StationSiteVO> rainfall;

    /** 水位监测站点 */
    private List<StationSiteVO> waterLevel;

    /** 闸门监测站点 */
    private List<StationSiteVO> gate;

    /** 流量监测站点 */
    private List<StationSiteVO> flow;

    /** 墒情监测站点 */
    private List<StationSiteVO> moisture;
}
