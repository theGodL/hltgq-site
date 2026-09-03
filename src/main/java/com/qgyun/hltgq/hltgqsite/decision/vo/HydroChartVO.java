package com.qgyun.hltgq.hltgqsite.decision.vo;

import lombok.Data;

import java.util.List;

/**
 * 水文关系预测分析图数据（/flood-drought/hydro/{id} 响应）。
 * <p>柱状：实际/预测降雨量；曲线：实测/预测水位、实测/预测流量。
 * <p>null 语义：无数据不渲染（柱状无柱/折线断线），不补 0。
 * <p>分界点（下标 split-1）水位/流量两序列同时有值，保证折线视觉连续。
 */
@Data
public class HydroChartVO {

    /** 横轴逐日标签（等长于下面所有数组，自然日含首尾） */
    private List<String> dates;

    /** 实测天数（下标 < split 为实测段）；可为 0（整个区间都在未来） */
    private int split;

    /** 实际降雨量（mm，1 位小数）；预测段置 null */
    private List<Double> rainObs;

    /** 预测降雨量（mm）；实测段置 null */
    private List<Double> rainPred;

    /** 实测水位（m，3 位小数）；预测段置 null，分界点有值 */
    private List<Double> levelObs;

    /** 预测水位（m）；实测段（除分界点）置 null */
    private List<Double> levelPred;

    /** 实测流量（m³/s，3 位小数）；预测段置 null，分界点有值 */
    private List<Double> flowObs;

    /** 预测流量（m³/s）；实测段（除分界点）置 null */
    private List<Double> flowPred;

    /** 预测截止日期（yyyy-MM-dd）；无预测时为 null */
    private String forecastEndDate;
}
