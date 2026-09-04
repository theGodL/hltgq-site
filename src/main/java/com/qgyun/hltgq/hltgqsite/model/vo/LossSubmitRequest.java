package com.qgyun.hltgq.hltgqsite.model.vo;

import lombok.Data;

/**
 * 水量损失预测提交入参（/water-forecast/loss）。
 * <p>2026-09 会议定稿：短期损失已下线，仅支持 long（中长期旬蒸发）。
 * <p>参数从所选中长期来水方案 request_json 提取复现，sourceRecordId 为中长期方案ID。
 */
@Data
public class LossSubmitRequest {

    /** 模式：long（中长期旬蒸发）；short 已下线，传 short 会被拒绝 */
    private String mode;

    /** 参数来源方案ID（必传）：中长期来水方案 */
    private String sourceRecordId;

    /** 方案名称（可选，默认自动生成 "损失预测_中长期_<源方案名>"） */
    private String schemeName;
}
