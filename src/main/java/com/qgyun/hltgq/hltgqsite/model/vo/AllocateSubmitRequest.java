package com.qgyun.hltgq.hltgqsite.model.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 水资源配置提交入参（/water-allocation 与 /water-allocation/upload）。
 * <p>auto 模式：参数从所选方案提取（scenario←中长期方案、保证率/渠系系数←需水方案，
 * 损失方案仅归档追溯），调 /allocate(mode=auto, save_excel=true)。
 * <p>manual 模式：rows 为配水基础数据收集表解析结果（36 旬），调 /allocate(mode=manual)。
 */
@Data
public class AllocateSubmitRequest {

    /** 模式：auto（调用已有结果）/ manual（手动输入） */
    private String mode;

    /** 方案名称（可选，默认自动生成） */
    private String schemeName;

    /** 中长期来水方案ID（auto 模式必传），对应 t_auto_hltgq_water_long_predict_record 表记录 */
    private String longPredictRecordId;

    /** 需水预测方案ID（auto 模式必传） */
    private String demandRecordId;

    /** 水量损失方案ID（auto 模式可选，仅归档追溯） */
    private String lossRecordId;

    /** 塘坝档位（auto 模式，页面暂无输入，后端默认 "多年平均"） */
    private String pondOption;

    /** 初始库水位(m)（默认 75.0） */
    private Double startLevel;

    /** 汛限水位(m)（默认 80.0） */
    private Double floodLimitLevel;

    /** 模型限制最高水位(m)（默认 82.8） */
    private Double maxLevel;

    /** 手动输入逐旬数据行（manual 模式，上传解析或 JSON 直传） */
    private List<Map<String, Object>> rows;

    /** 是否使用输入中的弃水列（manual 模式，默认 false 按调度规则自动算弃水） */
    private Boolean useManualSpill;
}
