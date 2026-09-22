package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.util.List;

/**
 * 站点排序保存入参（siteIds 即保存后的展示顺序；scope 指定范围时只重排本范围所在分块）
 */
@Data
public class StationSortSaveVO {

    /** 监测类型：waterLevel(水位) / rainfall(雨量，含水库站点) / flow(流量) / gate(闸门) / moisture(墒情) */
    private String type;

    /** 排序后的站点管理主键列表（按展示顺序，前端拖拽或序号调整后的结果） */
    private List<String> siteIds;

    /** 站点范围（可选）：reservoir 花凉亭水库站点 / gq 灌区站点；空值或 all 表示整表重排 */
    private String scope;
}
