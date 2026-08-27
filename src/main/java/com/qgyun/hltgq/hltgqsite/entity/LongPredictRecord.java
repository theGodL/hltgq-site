package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 中长期来水预测方案主表（t_auto_hltgq_water_long_predict_record）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_water_long_predict_record\"")
public class LongPredictRecord extends BaseRecordEntity {

    /** 来水情景：丰 / 平 / 枯 / null */
    @TableField("\"scenario\"")
    private String scenario;

    /** 是否使用历史数据：#1#=是，#2#=否 */
    @TableField("\"use_historical\"")
    private String useHistorical;

    /** 历史年份 */
    @TableField("\"historical_year\"")
    private Double historicalYear;

    /** 是否重训练：#1#=是，#2#=否 */
    @TableField("\"retrain\"")
    private String retrain;

    /** 训练轮数 */
    @TableField("\"epochs\"")
    private Double epochs;

    /** 隐藏层神经元数 */
    @TableField("\"hidden_neurons\"")
    private Double hiddenNeurons;

    /** 学习率 */
    @TableField("\"learning_rate\"")
    private Double learningRate;

    /** 请求体归档 JSON */
    @TableField("\"request_json\"")
    private String requestJson;

    /** 全年预测水量(万方)：SUM(monthly.predict_total) */
    @TableField("\"annual_predict_volume\"")
    private Double annualPredictVolume;

    /** 最大旬水量(万方)：MAX(tenday.predict_volume) */
    @TableField("\"max_tenday_volume\"")
    private Double maxTendayVolume;

    /** 最小旬水量(万方)：MIN(tenday.predict_volume) */
    @TableField("\"min_tenday_volume\"")
    private Double minTendayVolume;

    /** NSE 指标（val_metrics，大小写兼容） */
    @TableField("\"nse\"")
    private Double nse;

    /** RMSE 指标（val_metrics，大小写兼容） */
    @TableField("\"rmse\"")
    private Double rmse;

    /** MAE 指标（val_metrics） */
    @TableField("\"mae\"")
    private Double mae;

    /** MSE 指标（val_metrics） */
    @TableField("\"mse\"")
    private Double mse;

    /** R2 决定系数（val_metrics） */
    @TableField("\"r2\"")
    private Double r2;

    /** SMAPE 指标（val_metrics） */
    @TableField("\"smape\"")
    private Double smape;
}
