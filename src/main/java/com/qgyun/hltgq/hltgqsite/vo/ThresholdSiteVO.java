package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

/**
 * 阈值设置的站点候选（按阈值类型取站点档案中支持该类型的站点）。
 */
@Data
public class ThresholdSiteVO {

    /** 站点档案主键（阈值行 site 落库值） */
    private String siteId;

    /** 站点名称（站点档案 zzkaec） */
    private String name;

    /** 站点编号（站点档案 iofhpi） */
    private String siteCode;

    /** 该站点在该类型下是否已配置阈值（界面用于提示"已配置，请直接编辑"） */
    private Boolean configured;
}
