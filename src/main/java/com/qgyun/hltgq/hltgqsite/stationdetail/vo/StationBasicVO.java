package com.qgyun.hltgq.hltgqsite.stationdetail.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 站点详情-基础信息聚合结果（一次请求渲染「基础信息」整页）。
 * <p>数据来源：站点档案表 t_auto_hltgq_5nw74_vnqqef + 电压表 t_auto_hltgq_water_vol_info
 * + 设备表 t_auto_hltgq_water_device（闸口计数/视频通道）。
 * <p>档案表无对应字段的页面项（所属渠系/负责人/电话/建成/投运时间/站点简介/额定电压/丢包率等）
 * 恒返回 null，前端显示占位符，后端不补造数据。
 */
@Data
public class StationBasicVO {

    /** 站点编号（档案表 iofhpi） */
    private String code;

    /** 站点名称（档案表 zzkaec） */
    private String name;

    /** 站点类型（epjutj 翻译文本，多类型「、」分隔，如「水位、雨量」） */
    private String type;

    /** 站点类型编码原文（epjutj，如 #1#|#2#，权威值） */
    private String typeCodes;

    /** 所属管理单位（档案表 ahieto 自关联本表取管理单位名称 zzkaec；无上级或上级记录不存在为 null） */
    private String org;

    /** 所属渠系（档案表无此字段，恒 null） */
    private String canal;

    /** 闸口数量（设备表 type 含 #4# 闸门的设备数） */
    private Integer gates;

    /** 主要功能（档案表无此字段，恒 null） */
    private String func;

    /** 站点位置（档案表 mivbcz 值同站名，不取，恒 null） */
    private String loc;

    /** 详细地址（档案表无此字段，恒 null，loc 即位置） */
    private String addr;

    /** 经纬度文本「经度, 纬度」（档案表 bviiio_x/bviiio_y，缺任一为 null，由 Service 拼接） */
    private String lnglat;

    /** 站点经度（档案表 bviiio_x） */
    private BigDecimal lon;

    /** 站点纬度（档案表 bviiio_y） */
    private BigDecimal lat;

    /** 运行状态（zebpsu 翻译：#1# 在线、#2# 离线） */
    private String runStatus;

    /** 运行状态编码原文（zebpsu，权威值） */
    private String runStatusCode;

    /** 运行状态是否正常（zebpsu=#1#） */
    private Boolean runStatusOk;

    /** 负责人（档案表无此字段，恒 null） */
    private String owner;

    /** 联系电话（档案表无此字段，恒 null） */
    private String phone;

    /** 建成时间（档案表无此字段，恒 null） */
    private String built;

    /** 投入运行时间（档案表无此字段，恒 null） */
    private String online;

    /** 运行年限（档案表无建成时间，恒 null） */
    private String years;

    /** 站点简介（档案表无此字段，恒 null） */
    private String intro;

    /** 当前电压 V（电压表 vol_info 最新 vol） */
    private BigDecimal volt;

    /** 额定电压（无数据源，恒 null） */
    private String ratedVolt;

    /** 电压偏差（无额定电压不可算，恒 null） */
    private String voltDev;

    /** 当前电流 A（电压表电流列库中未确认，恒 null） */
    private BigDecimal amp;

    /** 供电状态（档案表 waljdn 翻译：#1# 已接通市电、#2# 未接通市电，未知编码原样返回） */
    private String powerStatus;

    /** 是否接通市电编码原文（档案表 waljdn，如 #1#，权威值） */
    private String mainsPowerCode;

    /** 供电是否正常（waljdn=#1# 已接通市电） */
    private Boolean powerOk;

    /** 通信方式（档案表 bhsqxd 文本，如「4G无线」，原样返回） */
    private String comm;

    /** 信号强度（电压表信号列库中未确认，恒 null） */
    private BigDecimal signal;

    /** 最近通信时间（电压表最新 tm） */
    private LocalDateTime commTime;

    /** 通信延迟 ms（无数据源，恒 null） */
    private Long latency;

    /** 丢包率（无数据源，恒 null） */
    private String loss;

    /** 网络状态（zebpsu：#1# 在线、#2# 离线） */
    private String netStatus;

    /** 网络是否正常（zebpsu=#1#） */
    private Boolean netOk;

    /** 视频通道列表（设备表 type 含 #5# 视频） */
    private List<VideoChannel> videos;

    /**
     * 视频通道行（设备表行投影）
     */
    @Data
    public static class VideoChannel {

        /** 通道名称（设备表 name，如「xx站点1号摄像机」） */
        private String channel;

        /** 当前状态（status 翻译：#1# 在线、#2# 离线） */
        private String status;

        /** 状态编码原文（#1#/#2#，权威值） */
        private String statusCode;
    }
}
