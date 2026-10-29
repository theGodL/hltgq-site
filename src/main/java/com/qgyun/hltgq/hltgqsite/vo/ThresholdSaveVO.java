package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 阈值保存请求（新增与编辑共用；编辑时 site / type / indicator 由后端以库中行为准，不随请求变更）。
 */
@Data
public class ThresholdSaveVO {

    /** 站点档案主键（新增必填；编辑忽略） */
    private String site;

    /** 阈值类型编码 #N#（新增必填；编辑忽略） */
    private String type;

    /**
     * 监测指标编码（编码为监测数据表字段名，取值域由 /threshold/meta 下发）：
     * 水质/墒情必填；水位可选 general（通用水位，落库为空）/ gate_z（闸门上下游，统一设置上游+下游）；
     * 兼容旧参数 up_z/down_z（按 gate_z 处理）；雨量/流量/开度忽略。
     */
    private String indicator;

    /** 告警方向编码 #1#/#2#（为空时按类型默认方向落库；闸门上下游两侧共用同一方向） */
    private String alarmDir;

    /** 警戒值（上游/闸前；非闸门上下游记录必填且 > 0，闸门记录可与 thresholdDown 至少填一项） */
    private BigDecimal threshold;

    /**
     * 下游警戒值（闸后，仅闸门上下游记录使用）：
     * 与 {@link #threshold} 至少填一项；留空的一侧不落库、不告警。
     */
    private BigDecimal thresholdDown;

    /** 描述（可空，≤500 字） */
    private String description;
}
