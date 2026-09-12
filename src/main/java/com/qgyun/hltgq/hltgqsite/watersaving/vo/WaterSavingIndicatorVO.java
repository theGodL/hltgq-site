package com.qgyun.hltgq.hltgqsite.watersaving.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 节水体系列表行（表 t_auto_hltgq_yn8cm_iiitnh）。
 * <p>枚举字段返回中文（状态）并附编码原文（权威值）；
 * 年度 ckzbjf 库中为时间戳/文本两种形态，统一规范为 YYYY 输出；
 * 明细 ijjwkn（JSON 数组）不在此接口返回。
 */
@Data
public class WaterSavingIndicatorVO {

    /** 记录主键 id */
    private String id;

    /** 指标名称（iewyyy） */
    private String name;

    /** 年度（ckzbjf 规范化为 YYYY） */
    private String year;

    /** 目标值（sklcff，百分比精度 2，原样数值） */
    private BigDecimal target;

    /** 状态翻译（zkcllb） */
    private String status;

    /** 状态编码原文（zkcllb，权威值） */
    private String statusCode;

    /** 审核意见（nschzs，未审核为 null） */
    private String reviewOpinion;

    /** 更新时间（updated_at） */
    private LocalDateTime updatedAt;

    /** 年度原始值（ckzbjf，内部转换用，不出 JSON） */
    @JsonIgnore
    private String yearRaw;
}
