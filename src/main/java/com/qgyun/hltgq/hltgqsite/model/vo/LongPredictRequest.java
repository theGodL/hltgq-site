package com.qgyun.hltgq.hltgqsite.model.vo;

import lombok.Data;

/**
 * 中长期来水预测提交入参（/water-forecast/long）。
 */
@Data
public class LongPredictRequest {

    /** 方案名称（可选，默认自动生成 "中长期预报_<情景>"） */
    private String schemeName;

    /** 来水情景：丰 / 平 / 枯 / null（原始） */
    private String scenario;

    /** 是否使用历史某年实测数据（默认 false） */
    private Boolean useHistorical;

    /** 历史年份（useHistorical=true 时生效，默认 2020） */
    private Integer historicalYear;

    /** 是否重新训练 BP 模型（默认 false，日常保持 false） */
    private Boolean retrain;

    /** 训练轮数（retrain=true 生效，默认 20000） */
    private Integer epochs;

    /** 隐藏层神经元数（retrain=true 生效，默认 200） */
    private Integer hiddenNeurons;

    /** 学习率（retrain=true 生效，默认 0.005） */
    private Double learningRate;
}
