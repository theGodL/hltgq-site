package com.qgyun.hltgq.hltgqsite.model.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 短期来水预测·自定义出库提交入参（/water-forecast/short/custom，模型 /forecast/custom V3）。
 * <p>与常规版共用 start/end/rainfall/起调水位（后端自动取坝上最新水位）；
 * qOut 为逐小时总出库流量（必填、非负、与逐小时步数等长）。
 * <p>自定义出库不使用发电/泄洪隧洞/溢洪道开关与机组台数（模型侧不接受这三个参数），
 * 出库过程完全由 qOut 给定，模型不按当时水位对应的泄流能力限流。
 */
@Data
public class ShortForecastCustomRequest {

    /** 方案名称（可选，默认自动生成 "短期预报_自定义出库_<开始时间>_<小时数>小时"） */
    private String schemeName;

    /** 预报开始时间 YYYY-MM-DD HH:mm（必填，模型 start） */
    private String start;

    /** 预报结束时间 YYYY-MM-DD HH:mm（必填，模型 end；窗口含端点，跨度 1~384 小时） */
    private String end;

    /** 逐小时降雨量(mm)（可选：不传则自动取气象数据；传则长度必须等于 start→end 逐小时步数） */
    private List<Double> rainfall;

    /**
     * 逐小时总出库流量(m3/s)（必填，模型 q_out：非负有限数，长度必须等于逐小时步数，含首尾）。
     * <p>对外字段名固定为 qOut：Jackson 把 getter getQOut 推导成 qout（前两位大写按 legacy 命名转小写），
     * 不显式声明 JsonProperty 时传 qOut 会收不到值，故此处显式指定，并兼容 q_out 与 qout 两种写法。
     */
    @JsonProperty("qOut")
    @JsonAlias({"q_out", "qout"})
    private List<Double> qOut;
}
