package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 阈值保存请求（新增与编辑共用；编辑时 site / type 由后端以库中行为准，不随请求变更）。
 */
@Data
public class ThresholdSaveVO {

    /** 站点档案主键（新增必填；编辑忽略） */
    private String site;

    /** 阈值类型编码 #N#（新增必填；编辑忽略） */
    private String type;

    /** 告警方向编码 #1#/#2#（为空时按类型默认方向落库） */
    private String alarmDir;

    /** 警戒值（必填，> 0） */
    private BigDecimal threshold;

    /** 描述（可空，≤500 字） */
    private String description;
}
