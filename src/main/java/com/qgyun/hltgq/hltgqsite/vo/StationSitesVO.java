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

    /** 取水量监测单位 */
    private List<StationSiteVO> waterIntake;
}
