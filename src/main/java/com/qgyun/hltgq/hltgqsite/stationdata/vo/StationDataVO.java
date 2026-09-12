package com.qgyun.hltgq.hltgqsite.stationdata.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 站点数据查询列表行（站点档案表 t_auto_hltgq_5nw74_vnqqef）。
 * <p>枚举字段返回中文（监测类型/站点状态/监测方法）并附编码原文（权威值）；
 * 监测类型/监测方法为多选，库中以 | 分隔，译文以「、」拼接；
 * 核心指标（ccnhtm/ijzsby）类型未确认，原样文本返回。
 */
@Data
public class StationDataVO {

    /** 站点档案主键 id（其他业务表 site 值） */
    private String id;

    /** 站点名称（zzkaec） */
    private String name;

    /** 站点编号（iofhpi） */
    private String code;

    /** 站点位置（mivbcz） */
    private String location;

    /** 坐标定位「经度, 纬度」（bviiio_x/bviiio_y 拼接，缺一为 null） */
    private String lnglat;

    /** 站点经度（bviiio_x） */
    private BigDecimal lon;

    /** 站点纬度（bviiio_y） */
    private BigDecimal lat;

    /** 监测类型翻译（epjutj，多值「、」分隔，如「水位站、视频站」） */
    private String type;

    /** 监测类型编码原文（epjutj，如 #1#|#5#，权威值） */
    private String typeCodes;

    /** 站点状态翻译（zebpsu：#1# 在线、#2# 离线） */
    private String status;

    /** 站点状态编码原文（zebpsu，权威值） */
    private String statusCode;

    /** 核心指标-水位（ccnhtm，原样文本） */
    private String waterIndicator;

    /** 核心指标-雨量（ijzsby，原样文本） */
    private String rainIndicator;

    /** 监测方法翻译（nxtggq，多值「、」分隔） */
    private String method;

    /** 监测方法编码原文（nxtggq，如 #1#|#4#，权威值） */
    private String methodCodes;

    /** 负责人（lhwhuc） */
    private String owner;

    /** 联系电话（cbitue） */
    private String phone;
}
