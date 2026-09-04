package com.qgyun.hltgq.hltgqsite.model.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 需水预测提交入参（/water-forecast/demand 与 /water-forecast/demand/upload）。
 * <p>tableRows 为需水基础数据（模板上传解析或拓扑图输入），列：
 * 片区/分灌区/支渠/灌区面积/种植作物种类。
 * <p>2026-09 模型接口改造完成：tableRows 直传模型 demand_table
 * （不经过文件、不改写服务器默认文件）；未传则模型沿用服务器默认需水预测表。
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

    /** 需水基础数据行（模板解析结果或拓扑图输入），直传模型 demand_table（未传则沿用服务器默认文件） */
    private List<Map<String, Object>> tableRows;
}
