package com.qgyun.hltgq.hltgqsite.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 配水调度提交入参（/water-decision）。
 * <p>2026-10-26 定稿：仅需水资源配置方案一个下拉（必传）；
 * 需水方案不再传，后端沿配置方案归档的 demand_record_id 链式追溯 canal_eff（取不到兜底 0.589）。
 * source=所选配置方案 mode，调 /decision(tens=null 全 18 旬, save_excel=true)。
 */
@Data
public class DecisionSubmitRequest {

    /** 水资源配置方案ID（必传） */
    private String allocateRecordId;

    /** 仅计算指定旬标签（可选，null=全部 18 个灌溉旬） */
    private List<String> tens;

    /** 方案名称（可选，默认自动生成 "配水调度_<配置方案名>"） */
    private String schemeName;
}
