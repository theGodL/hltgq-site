package com.qgyun.hltgq.hltgqsite.decision.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 网络设备监控总览（/network-device/summary 响应）。
 * <p>统计对象是「设备」（设备台账），与「站点」（站点档案）不是一个概念。
 * <p>顶部汇总卡（在线/离线/告警）+ 固定 7 类设备列（水位/雨量/流量/闸门/视频/墒情/水质）。
 * <p>告警：该设备存在未关闭告警（告警表 status #1#/#2#/#3#）即计 1 台，与大屏 unhandledAlarmCount
 * 同源（同表同过滤），大屏算条数、本接口算台数；无数据源的项（故障）返回 null，前端不展示。
 */
@Data
public class NetworkDeviceVO {

    /** 区域名 */
    private String regionName;

    /** 设备总数（设备台账全部行；= Σcategories[].total + uncategorized，业主核对可分类相加） */
    private int total;

    /**
     * 未分类设备数：设备台账未维护 type（或 type 不含 7 类编码）的设备，不计入任何分类；
     * 当前为北干渠/南干渠/汪元节制闸闸孔、建设支渠、待接入等台账缺类型的设备，
     * 台账补上类型后会自动归入对应分类。
     */
    private int uncategorized;

    /** 顶部汇总：在线/离线必有值；告警为告警设备台数；故障无数据源，恒为 null（前端不展示） */
    private Summary summary;

    /** 分类列表：固定 7 项、顺序固定，空库仍返回 7 项全 0 结构 */
    private List<Category> categories;

    /** 汇总项 */
    @Data
    public static class Summary {
        private CountPercent online;
        private CountPercent offline;
        /** 告警设备台数（存在未关闭告警的设备，与大屏 unhandledAlarmCount 同源、单位不同） */
        private CountPercent alarm;
        /** 故障：告警表无故障维度、设备台账无故障标识，暂无数据源 → null（前端据此隐藏该项，避免假 0 误导） */
        private CountPercent fault;
    }

    /** 计数 + 百分比（percent 保留两位小数 HALF_UP，与 /dashboard/overview 同一规则；分母 0 → 0.00） */
    @Data
    public static class CountPercent {
        private int count;
        private BigDecimal percent;
    }

    /** 设备分类 */
    @Data
    public static class Category {
        /** 分类键（waterLevel/rainfall/flow/gate/video/soil/quality，前端 ICONS 图标键） */
        private String key;
        /** 分类名 */
        private String name;
        /** 分类色块色值（前端直接使用） */
        private String color;
        /** 图标键（与 key 相同，前端 ICONS[icon]） */
        private String icon;
        /** 该分类设备数（同一台设备只归入一个分类：多类型取首个命中分类的编码） */
        private int total;
        /** 分类内状态计数；alarm 为该分类内告警设备台数；fault 无数据源恒为 null（前端不展示） */
        private Counts counts;
        /** 该分类全量设备列表 */
        private List<Device> devices;
    }

    /** 分类内状态计数（online + offline = 该分类设备总数；alarm 与二者正交，可同时计入） */
    @Data
    public static class Counts {
        private int online;
        private int offline;
        /** 该分类内存在未关闭告警的设备台数 */
        private int alarm;
        /** 故障：无数据源 → null（前端不展示） */
        private Integer fault;
    }

    /** 设备项 */
    @Data
    public static class Device {
        /** 设备 ID（唯一） */
        private String id;
        /** 设备名称（格式「站点名+闸孔号#」或「站点名+设备类型#」） */
        private String name;
        /** 状态：仅 online / offline（设备 status 优先，为空回退所属站点 zebpsu） */
        private String status;
        /** 类型编码（多值 | 分割，仅后端聚合用，不出现在响应中） */
        @com.fasterxml.jackson.annotation.JsonIgnore
        private String type;
        /** 状态来源：device = 设备表 status，site = 设备 status 为空回退站点档案 zebpsu（仅后端日志核对用，不出现在响应中） */
        @com.fasterxml.jackson.annotation.JsonIgnore
        private String statusFrom;
    }
}
