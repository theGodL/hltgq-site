package com.qgyun.hltgq.hltgqsite.decision.vo;

import lombok.Data;

import java.util.List;

/**
 * 网络设备监控总览（/network-device/summary 响应）。
 * <p>顶部 4 汇总卡（在线/离线/告警/故障）+ 固定 7 类设备列（水位/雨量/流量/闸门/视频/墒情/水质）。
 */
@Data
public class NetworkDeviceVO {

    /** 区域名 */
    private String regionName;

    /** 设备去重总数（多类型设备只计 1 次，故 total ≠ Σcategories[].total） */
    private int total;

    /** 顶部汇总：固定 4 项（告警/故障暂不支持恒 0） */
    private Summary summary;

    /** 分类列表：固定 7 项、顺序固定，空库仍返回 7 项全 0 结构 */
    private List<Category> categories;

    /** 汇总项 */
    @Data
    public static class Summary {
        private CountPercent online;
        private CountPercent offline;
        private CountPercent alarm;
        private CountPercent fault;
    }

    /** 计数 + 百分比（percent 已四舍五入取整） */
    @Data
    public static class CountPercent {
        private int count;
        private int percent;
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
        /** 该分类设备数（多类型设备可同时计入多个分类） */
        private int total;
        /** 分类内状态计数（告警/故障恒 0） */
        private Counts counts;
        /** 该分类全量设备列表 */
        private List<Device> devices;
    }

    /** 分类内状态计数 */
    @Data
    public static class Counts {
        private int online;
        private int offline;
        private int alarm;
        private int fault;
    }

    /** 设备项 */
    @Data
    public static class Device {
        /** 设备 ID（唯一） */
        private String id;
        /** 设备名称（格式「站点名+闸孔号#」或「站点名+设备类型#」） */
        private String name;
        /** 状态：仅 online / offline */
        private String status;
        /** 类型编码（多值 | 分割，仅后端聚合用，不出现在响应中） */
        @com.fasterxml.jackson.annotation.JsonIgnore
        private String type;
    }
}
