package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

/**
 * 站点/测站统一视图（雨量/水位/闸门/取水量通用）
 */
@Data
public class StationSiteVO {

    /** 站点编号（雨量/水位=STCD，闸门=site UUID，取水量=WIUST_CD） */
    private String code;

    /** 站点名称 */
    private String name;
}
