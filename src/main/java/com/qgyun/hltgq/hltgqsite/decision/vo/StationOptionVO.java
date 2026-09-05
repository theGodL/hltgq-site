package com.qgyun.hltgq.hltgqsite.decision.vo;

import lombok.Data;

/**
 * 防洪页可切换候选站（GET /flood-drought/stations 响应元素）。
 * <p>stcd 即档案表 iofhpi（站点查询键，兼容编号与 site UUID），stnm 为档案表站点名称。
 */
@Data
public class StationOptionVO {

    /** 站点查询键（档案表 iofhpi） */
    private String stcd;

    /** 站点名称（档案表 zzkaec） */
    private String stnm;
}
