package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

/**
 * 站点排序行（排序抽屉列表：序号 + 站点名称）
 */
@Data
public class StationSortVO {

    /** 站点管理主键（排序配置的站点标识，保存时按此回传；不在站点档案内时为 null） */
    private String siteId;

    /** 站点名称 */
    private String name;

    /** 当前展示位次（从 1 开始，按排序配置与默认顺序合成） */
    private Integer sortNo;

    /** 是否已保存过排序配置（false = 该站按默认顺序展示） */
    private Boolean configured;

    /** 是否可参与排序（站点档案表内登记的站点才可排序） */
    private Boolean sortable;
}
