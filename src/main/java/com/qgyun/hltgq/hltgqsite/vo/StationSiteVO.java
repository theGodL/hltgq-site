package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

/**
 * 站点/测站统一视图（雨量/水位/闸门通用）
 */
@Data
public class StationSiteVO {

    /** 站点编号（雨量/水位=STCD，闸门=site UUID） */
    private String code;

    /** 站点名称 */
    private String name;

    /**
     * 站点管理主键（站点档案表 id）：站点排序配置的站点标识；
     * 档案表中无对应站点时为空，该站不参与排序（保持默认顺序）
     */
    private String siteId;
}
