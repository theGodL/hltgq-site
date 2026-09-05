package com.qgyun.hltgq.hltgqsite.external.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 三维系统对接接口（/external）响应 VO 集合。
 * <p>契约见 src/main/resources/三维系统对接接口.md：面向第三方大屏/数字孪生，
 * 与页面接口隔离，字段名以对接契约为准。
 */
public final class ExternalVO {

    private ExternalVO() {
    }

    /** 闸门类型数量：固定 4 类，count 暂恒 0（类型分类数据待三方收集补录） */
    @Data
    public static class GateTypeCount {
        private List<GateTypeItem> items;
    }

    @Data
    public static class GateTypeItem {
        /** 类型名：节制闸/分水口/退水闸/倒虹吸 */
        private String type;
        /** 数量 */
        private Integer count;
    }

    /** 渠首进水闸实时数据：闸前/闸后水位 + 流量 + 管理单位 */
    @Data
    public static class IntakeGate {
        /** 站点标识 */
        private String stcd;
        /** 站点名称（固定：渠首进水闸） */
        private String stnm;
        /** 闸前（上游）水位 m，2 位小数（截断补零），无数据 null */
        private BigDecimal upZ;
        /** 闸后（下游）水位 m，2 位小数（截断补零），无数据 null */
        private BigDecimal downZ;
        /** 瞬时流量 m³/s，3 位小数（截断），无数据 null */
        private BigDecimal q;
        /** 数据时间 yyyy-MM-dd HH:mm:ss（取水位/流量较新者） */
        private String tm;
        /** 管理单位（固定：花凉亭灌区） */
        private String managementUnit;
    }

    /** 巡检汇总：累计巡检次数/计划数/完成数/维养预算/维养成本 */
    @Data
    public static class PatrolSummary {
        /** 累计巡检次数（巡检记录已提交，排除草稿，全量） */
        private Long patrolCount;
        /** 巡检计划总数 */
        private Long scheduleCount;
        /** 完成巡检数（计划状态=已完成） */
        private Long finishedCount;
        /** 维养预算（万元），暂恒 0（数据源待确认） */
        private Integer maintenanceBudget;
        /** 维养成本（万元），暂恒 0（数据源待确认） */
        private Integer maintenanceCost;
    }

    /** 巡检/问题逐日趋势（三数组等长，无记录补 0） */
    @Data
    public static class DailyTrend {
        /** 横轴逐日标签 yyyy-MM-dd（含首尾） */
        private List<String> dates;
        /** 逐日巡检次数（已提交记录按巡检时间取日期聚合） */
        private List<Long> patrol;
        /** 逐日问题数量（问题按发现时间取日期聚合，全状态） */
        private List<Long> issue;
    }

    /** 问题状态统计（两组口径同源：问题处理 / 应急响应） */
    @Data
    public static class IssueStats {
        /** 已处理 = 已关闭 */
        private Long handled;
        /** 未整改 = 处理中 + 已转工单 */
        private Long unrectified;
        /** 突发事件 = 问题总数（全状态） */
        private Long emergencyTotal;
        /** 已解除响应 = 已处理问题数（= handled） */
        private Long resolved;
        /** 未解除响应 = 处理中 + 已转工单（= unrectified） */
        private Long unresolved;
    }

    /** 视频监测按管理所聚合（总数/在线/离线） */
    @Data
    public static class VideoItem {
        /** 管理所名称（安装位置按「-」截取首段，空归「未知」） */
        private String org;
        /** 视频设备总数 */
        private Long total;
        /** 在线数 */
        private Long online;
        /** 离线数（total - online） */
        private Long offline;
    }
}
