package com.qgyun.hltgq.hltgqsite.patrol.vo;

import lombok.Data;

import java.util.List;

/**
 * 巡查范围站点（计划详情与「添加巡检记录」站点下拉共用）。
 */
@Data
public class PatrolSiteVO {

    /** 站点档案主键 */
    private String id;

    /** 站点名称（档案 zzkaec） */
    private String name;

    /** 站点编号（档案 iofhpi） */
    private String code;

    /** 在计划巡查范围中的顺序（关系 nature_order；站点下拉场景为 null） */
    private String order;
}
