package com.qgyun.hltgq.hltgqsite.model.vo;

import lombok.Data;

/**
 * 墒情预测提交入参（/water-forecast/moisture），模型 /moisture。
 * <p>初始墒情后端自动取四站点（太湖毕岭/望江/宿松/怀宁麻塘湖）最新有效数据；
 * 降雨序列后端自动拉取气象逐小时数据（缺失小时填 0），无需前端传入。
 */
@Data
public class MoistureSubmitRequest {

    /** 方案名称（可选，默认自动生成 "墒情预测_<起始时间>_<天数>天"） */
    private String schemeName;

    /** 预测天数（可选，默认 16，范围 1~16；与气象逐小时预报上限一致） */
    private Integer days;
}
