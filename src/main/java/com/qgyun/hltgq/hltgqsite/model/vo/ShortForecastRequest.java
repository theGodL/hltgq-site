package com.qgyun.hltgq.hltgqsite.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 短期来水预测提交入参（/water-forecast/short），小时尺度新契约（模型 /forecast V3）。
 * <p>起调水位不再由用户输入：后端自动取花凉亭坝上最新水位；
 * 逐小时降雨未传时后端自动拉取气象 16 天逐小时数据（缺失小时填 0）。
 */
@Data
public class ShortForecastRequest {

    /** 方案名称（可选，默认自动生成 "短期预报_<开始时间>_<小时数>小时"） */
    private String schemeName;

    /** 预报开始时间 YYYY-MM-DD HH:mm（必填，模型 start，与气象逐小时数据对齐） */
    private String start;

    /** 预报结束时间 YYYY-MM-DD HH:mm（必填，模型 end；窗口含端点，跨度 1~384 小时） */
    private String end;

    /** 是否启用发电下泄（默认 false） */
    private Boolean enablePower;

    /** 是否启用泄洪隧洞（默认 false） */
    private Boolean enableTunnel;

    /** 是否启用溢洪道（默认 false） */
    private Boolean enableSpillway;

    /** 逐小时降雨量(mm)（可选：不传则自动取气象数据；传则长度必须等于 start→end 逐小时步数） */
    private List<Double> rainfall;
}
