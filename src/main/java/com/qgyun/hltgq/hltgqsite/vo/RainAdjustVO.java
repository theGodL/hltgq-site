package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 雨量补偿配置视图（监测数据删除方案 §5.1）：列表行 + 站点/设备候选。
 */
@Data
public class RainAdjustVO {

    private String id;

    /** 站点档案主键 */
    private String site;

    /** 站点名（档案 zzkaec；档案缺失时回退主键） */
    private String siteName;

    /** 站点编号（档案 iofhpi） */
    private String siteCode;

    /** 设备主键（唯一/匹配键） */
    private String device;

    /** 设备名（设备表 name；缺失时回退主键） */
    private String deviceName;

    /** 设备编号（设备表 code） */
    private String deviceCode;

    /** 站点 RTU 站号（识别辅助列，可空） */
    private String stcd;

    /** 入库补偿值 */
    private BigDecimal offsetValue;

    /** 启用开关（停用=无补偿入库） */
    private Boolean enabled;

    /** 备注 */
    private String remark;

    private LocalDateTime createdAt;

    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;

    /** 站点候选（新增表单下拉） */
    @Data
    public static class SiteOption {

        /** 站点档案主键 */
        private String id;

        /** 站点编号（档案 iofhpi；RTU 站号，可空） */
        private String stcd;

        /** 站名（档案 zzkaec） */
        private String stnm;
    }

    /** 设备候选（按站点联动下拉） */
    @Data
    public static class DeviceOption {

        private String id;

        private String name;

        /** 设备编号 */
        private String code;

        /** 监测类型编码（多值 #N# 以 | 分隔，如 #1#|#2#） */
        private String type;

        /** 监测类型中文名（服务端翻译，如 雨量） */
        private String typeName;

        /** 归属站点主键（新增校验用） */
        private String site;
    }
}
