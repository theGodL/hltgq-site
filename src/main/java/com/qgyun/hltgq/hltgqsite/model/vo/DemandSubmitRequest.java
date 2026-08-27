package com.qgyun.hltgq.hltgqsite.model.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 需水预测提交入参（/water-forecast/demand 与 /water-forecast/demand/upload）。
 * <p>tableRows 为需水基础数据（模板上传解析或拓扑图输入），列：
 * 片区/分灌区/支渠/灌区面积/种植作物种类。
 * 模型接口表格入参改造未完成前，tableRows 仅随 request_json 归档，不传给模型。
 */
@Data
public class DemandSubmitRequest {

    /** 方案名称（可选，默认自动生成 "需水预测_<保证率>"） */
    private String schemeName;

    /** 保证率：50 / 75 / 90 / "多年平均"（字符串或数字均可，默认 90） */
    private String guaranteeRate;

    /** 渠系水利用系数（0~1，默认 0.589） */
    private Double canalEff;

    /** 目标总毛需水量(万方)，null 表示不约束 */
    private Double targetTotal;

    /** 需水基础数据行（模板解析结果或拓扑图输入），仅归档 */
    private List<Map<String, Object>> tableRows;
}
