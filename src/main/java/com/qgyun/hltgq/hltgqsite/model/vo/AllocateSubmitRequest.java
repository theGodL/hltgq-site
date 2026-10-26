package com.qgyun.hltgq.hltgqsite.model.vo;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 水资源配置提交入参（/water-allocation）。
 * <p>2026-10-26 会议定稿：仅保留参数配置入口（manual 模式与大表上传已删除，mode 后端恒 auto）；
 * 字段名定稿 initialLevel / spillwayOpenLevel / pondScenario（旧名 startLevel / maxLevel / pondOption 已废弃，
 * 传入旧名由 submit 前置 400 拒绝，不静默忽略）。
 * <p>auto 模式：参数从所选方案提取（scenario←中长期方案、保证率/渠系系数←需水方案，
 * 损失方案年渗漏量 annual_leakage_wan_m3 一并提取），调 /allocate(mode=auto, save_excel=true)。
 */
@Data
public class AllocateSubmitRequest {

    /** 反序列化时收集的未知字段（旧字段名 startLevel / maxLevel / pondOption 校验用） */
    private Map<String, Object> unknownFields = new LinkedHashMap<>();

    @JsonAnySetter
    public void handleUnknown(String key, Object value) {
        unknownFields.put(key, value);
    }

    /** 是否携带已废弃的旧字段名（submit 前置 400 拒绝，避免用户改了起始水位却不生效） */
    public boolean hasLegacyField() {
        return unknownFields.containsKey("startLevel")
                || unknownFields.containsKey("maxLevel")
                || unknownFields.containsKey("pondOption");
    }


    /** 方案名称（可选，默认自动生成） */
    private String schemeName;

    /** 中长期来水方案ID（必传），对应 t_auto_hltgq_water_long_predict_record 表记录 */
    private String longPredictRecordId;

    /** 需水预测方案ID（必传） */
    private String demandRecordId;

    /** 水量损失方案ID（可选，仅归档追溯） */
    private String lossRecordId;

    /** 塘坝可供水量情景：50% / 75% / 90% / 多年平均（默认 "多年平均"） */
    private String pondScenario;

    /** 初始库水位(m)（默认 75.0，范围 [74,99]） */
    private Double initialLevel;

    /** 汛限水位(m)（默认 80.0，范围 [75,81]） */
    private Double floodLimitLevel;

    /** 溢洪道开启水位(m)（默认 82.8，范围 [81,99]） */
    private Double spillwayOpenLevel;

    /** 泄洪隧洞启用水位(m)（默认 81，parameters 透传 flood_control_target_level） */
    private Double floodControlTargetLevel;

    /** 死水位(m)（默认 74，parameters 透传 dead_water_level） */
    private Double deadWaterLevel;

    /** 计划年末蓄水位(m)（默认 77，parameters 透传 year_end_target_level） */
    private Double yearEndTargetLevel;

    /** 入汛时间（只读：后端固定 2027-05-01，用户改传也以此为准，parameters 透传 flood_start_date） */
    private String floodStartDate;

    /** 发电最低水位(m)（默认 75，parameters 透传 generation_min_level） */
    private Double generationMinLevel;

    /** 发电适用水位上界(m)（默认 90，parameters 透传 generation_max_level） */
    private Double generationMaxLevel;

    /** 常规发电基准流量(m³/s)（默认 30，parameters 透传 base_generation_m3s） */
    private Double baseGenerationM3s;

    /** 最大发电流量(m³/s)（默认 120，parameters 透传 generation_nameplate_m3s） */
    private Double generationNameplateM3s;

    /** 灌区全年城镇需水量(万 m³)（默认 3175，parameters 透传 urban_annual_demand） */
    private Double urbanAnnualDemand;

    /** 灌区全年农村生活需水量(万 m³)（默认 3903，parameters 透传 rural_annual_demand） */
    private Double ruralAnnualDemand;

    /** 水厂全年可供水量(万 m³)（默认 6552，parameters 透传 water_plant_annual_supply） */
    private Double waterPlantAnnualSupply;

    /** 水库向城市供水水量(万 m³)（默认 631，parameters 透传 city_supply_annual_wan_m3） */
    private Double citySupplyAnnualWanM3;
}
