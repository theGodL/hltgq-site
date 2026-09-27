package com.qgyun.hltgq.hltgqsite.stationdetail.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 站点详情-设备信息：设备列表行（表 t_auto_hltgq_water_device）+ 设备绑定子结构。
 * <p>列表行：实时数据按设备监测类型（type 编码）取对应监测表最新值，由 Service 按
 * 闸门→水位→雨量→流量→墒情→水质→视频 的优先级取第一个可匹配类型；
 * 数值型走 value（BigDecimal 序列化保留尾零），文本型（视频巡检结果）走 textValue。
 */
@Data
public class DeviceVO {

    /** 设备主键 id */
    private String id;

    /** 设备编码（设备表 code，如 1000231$1$0$10） */
    private String code;

    /** 设备名称（设备表 name，如「南山寺节制闸1#」） */
    private String name;

    /** 设备类型（type 翻译文本，多类型「、」分隔，如「闸门、水位」） */
    private String type;

    /** 设备类型编码原文（type，如 #4#|#1#，权威值） */
    private String typeCodes;

    /** 所属闸口（由设备名解析：站点名前缀去除后剩余部分去 #，如「1」；无闸孔信息为 null） */
    private String gate;

    /** 设备安装位置（设备表 wlcvig，如「渠首管理所-东面」；台账现状仅视频设备有值，其余为 null） */
    private String location;

    /** 设备状态（status 翻译：#1# 在线、#2# 离线） */
    private String status;

    /** 设备状态编码原文（status） */
    private String statusCode;

    /** 实时数据指标名（如「当前开度」「瞬时流量」，无实时数据为 null） */
    private String metric;

    /** 实时数据数值（-999/-9991 异常值已过滤，无有效值为 null） */
    private BigDecimal value;

    /** 实时数据文本值（视频巡检结果：正常/检出故障/检测异常；其他类型为 null） */
    private String textValue;

    /** 实时数据单位（如 m、mm、m³/s、%，文本型为 null） */
    private String unit;

    /**
     * 绑定弹窗候选设备行（管理员）：全库搜索含当前归属站点，本站设备标记 current 由前端禁选
     */
    @Data
    public static class BindCandidate {

        /** 设备主键 id */
        private String id;

        /** 设备名称（设备表 name） */
        private String name;

        /** 设备编码（设备表 code） */
        private String code;

        /** 设备类型（type 翻译文本，多类型「、」分隔） */
        private String type;

        /** 设备类型编码原文（type，如 #4#|#1#，权威值） */
        private String typeCodes;

        /** 当前归属站点 id（设备表 site；无归属为 null） */
        private String siteId;

        /** 当前归属站点名称（站点档案 zzkaec；档案缺失为 null） */
        private String siteName;

        /** 是否已属于目标站（true=已在本站，前端禁选） */
        private Boolean current;

        /** 是否禁止选择（true=闸孔设备按名称归属其他站或站级计量同类冲突，前端禁选并展示 tip 原因） */
        private Boolean blocked;

        /** 绑定提示（禁选原因 / 闸孔归属存疑提醒；无提示为 null） */
        private String tip;
    }

    /**
     * 设备换绑结果（管理员）：供前台提示「已从 X 站转移到 Y 站」
     */
    @Data
    public static class BindResult {

        /** 设备主键 id */
        private String deviceId;

        /** 设备名称 */
        private String deviceName;

        /** 原归属站点 id（首次绑定为 null） */
        private String oldSiteId;

        /** 原归属站点名称（首次绑定或档案缺失为 null） */
        private String oldSiteName;

        /** 新归属站点 id（本站） */
        private String newSiteId;

        /** 新归属站点名称（本站） */
        private String newSiteName;

        /** 绑定结果警示（闸孔名称无法解析归属站等放行提示；无警示为 null） */
        private String warning;
    }
}
