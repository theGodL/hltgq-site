package com.qgyun.hltgq.hltgqsite.model.vo;

import lombok.Data;

/**
 * 水量损失预测提交入参（/water-forecast/loss）。
 * <p>2026-10-26 会议定稿：参数来源方案ID（sourceRecordId）已删除，不再从历史方案复现参数；
 * 入参改为 年渗漏量（annualLeakageWanM3，默认 156）+ 情景（scenario，丰/平/枯/推荐值，可选）；
 * 仅支持 long（短期损失已下线），后端调 /loss(mode=long)。
 */
@Data
public class LossSubmitRequest {

    /** 模式：long（中长期）；可选，默认 long，传其他值会被拒绝 */
    private String mode;

    /** 情景：丰 / 平 / 枯 / 推荐值；可选，null=模型默认 */
    private String scenario;

    /** 年渗漏量(万方)：可选，默认 156 */
    private Double annualLeakageWanM3;

    /** 方案名称（可选，默认自动生成 "损失预测_中长期"） */
    private String schemeName;
}
