package com.qgyun.hltgq.hltgqsite.model.vo;

import lombok.Data;

/**
 * 水量损失预测提交入参（/water-forecast/loss）。
 * <p>参数从所选历史方案 request_json 提取复现：
 * mode=short 时 sourceRecordId 为短期来水方案；mode=long 时为中长期来水方案。
 */
@Data
public class LossSubmitRequest {

    /** 模式：short（短期逐日蒸发）/ long（中长期旬蒸发） */
    private String mode;

    /** 参数来源方案ID（必传）：短期=短期来水方案，长期=中长期来水方案 */
    private String sourceRecordId;

    /** 方案名称（可选，默认自动生成 "损失预测_<模式>_<源方案名>"） */
    private String schemeName;
}
