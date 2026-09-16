package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.util.List;

/**
 * 站点排序保存入参（整表覆盖：siteIds 即保存后的展示顺序）
 */
@Data
public class StationSortSaveVO {

    /** 监测类型：waterLevel(水位) / rainfall(雨量，含水库站点) / flow(流量) / gate(闸门) / moisture(墒情) */
    private String type;

    /** 排序后的站点管理主键列表（按展示顺序，前端拖拽或序号调整后的结果） */
    private List<String> siteIds;
}
