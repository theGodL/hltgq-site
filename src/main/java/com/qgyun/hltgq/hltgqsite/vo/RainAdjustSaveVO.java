package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 雨量补偿配置保存请求（新增/编辑共用）。
 * <p>新增：site/device/offsetValue 必填；编辑：仅 offsetValue/enabled/remark 生效——
 * site/device/stcd 为匹配键与识别列，锁定不可改（换设备=新增新配置后停用/删除旧配置）。
 */
@Data
public class RainAdjustSaveVO {

    /** 站点档案主键（新增必填；与设备联动） */
    private String site;

    /** 设备主键（新增必填；全局唯一，已配置过的设备须走编辑） */
    private String device;

    /** 入库补偿值（必填，可正可负，如 -34；0=无补偿） */
    private BigDecimal offsetValue;

    /** 启用开关（可选，默认 true） */
    private Boolean enabled;

    /** 备注（可选，≤255 字） */
    private String remark;
}
