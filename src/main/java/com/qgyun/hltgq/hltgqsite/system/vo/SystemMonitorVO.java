package com.qgyun.hltgq.hltgqsite.system.vo;

import com.qgyun.hltgq.hltgqsite.system.entity.SystemFaultRecord;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统资源监控 VO（接口 /system-monitor/*，页面 static/system-monitor.html）。
 * <p>容量类字段统一给字节数，单位换算与展示文案由前端处理（后端只给权威数据）。
 */
public class SystemMonitorVO {

    /** 概览：资源快照 + 数据库表空间（一次请求取全，页面按固定间隔轮询） */
    @Data
    public static class Overview {
        private Resource resource;
        /** 多主机列表（本应用所在服务器为 current，其余来自各服务器采集脚本上报） */
        private List<HostStatus> hosts;
        private Database database;
        /** 越限告警阈值（页面按同口径标色，避免前端硬编码与后端不一致） */
        private Thresholds thresholds;
    }

    /** 越限告警阈值（百分比） */
    @Data
    public static class Thresholds {
        private Double cpu;
        private Double mem;
        private Double swap;
        private Double disk;
        private Double tablespace;
    }

    /** 服务器资源快照（宿主机视角优先；未挂载 /host 时为容器视角，scope 标注口径） */
    @Data
    public static class Resource {
        /** host 宿主机 / container 容器 */
        private String scope;
        private String hostname;
        private String osName;
        /** CPU 核数与使用率(%) */
        private Integer cpus;
        private Double cpuPercent;
        /** 系统负载 1/5/15 分钟 */
        private Double load1;
        private Double load5;
        private Double load15;
        /** 内存：总量/已用/可用（字节）与使用率(%) */
        private Long memTotal;
        private Long memUsed;
        private Long memAvailable;
        private Double memPercent;
        /** 交换区：总量/已用（字节）与使用率(%)（未启用交换区时 total=0、percent=null） */
        private Long swapTotal;
        private Long swapUsed;
        private Double swapPercent;
        /** 容器内存（cgroup 限额口径；宿主进程正常运行时也应关注 1C 限额是否吃紧，读不到为 null） */
        private Long containerMemLimit;
        private Long containerMemUsed;
        private Double containerMemPercent;
        /** 磁盘分区 */
        private List<Disk> disks;
        /** 采集时间 */
        private LocalDateTime sampleTime;
    }

    /** 磁盘分区使用情况 */
    @Data
    public static class Disk {
        /** 挂载点（如 /、/service） */
        private String mount;
        /** 总容量/已用/可用（字节）与使用率(%)（口径对齐 df：used/(used+avail)） */
        private Long total;
        private Long used;
        private Long avail;
        private Double percent;
    }

    /** 单个服务器状态（多主机视图：本机实时快照 / 远端最近一次上报） */
    @Data
    public static class HostStatus {
        /** 主机名（唯一键） */
        private String hostname;
        /** 主机别名（如“中间件服务器”，上报脚本可选携带；空时展示口径由前端决定） */
        private String alias;
        /** 是否本应用所在服务器 */
        private boolean current;
        /** 在线：最近上报未超过离线判定时长（本机恒 true） */
        private boolean online;
        /** 最近上报/采集时间 */
        private LocalDateTime lastReportTime;
        /** 资源快照（远端离线时为最后一次上报值） */
        private Resource resource;
    }

    /** 数据库表空间（KingbaseES：pg_database_size / pg_tablespace_size，查询结果内存缓存） */
    @Data
    public static class Database {
        private String dbName;
        /** 当前库大小（字节） */
        private Long dbSizeBytes;
        /** 容量基线（字节；未配置为 null，相关使用率一并返回 null 由前端显示 --） */
        private Long capacityBytes;
        /** 库大小 / 容量基线(%) */
        private Double dbPercent;
        private List<Tablespace> tablespaces;
        /** 查询时间（有缓存，非实时值） */
        private LocalDateTime queryTime;
    }

    /** 单个表空间 */
    @Data
    public static class Tablespace {
        private String name;
        /** 已用大小（字节） */
        private Long sizeBytes;
        private Long capacityBytes;
        private Double percent;
    }

    /** 故障记录分页 */
    @Data
    public static class FaultPage {
        private long total;
        private List<SystemFaultRecord> records;
    }
}
