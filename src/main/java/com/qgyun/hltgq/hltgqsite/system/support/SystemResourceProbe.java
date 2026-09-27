package com.qgyun.hltgq.hltgqsite.system.support;

import com.qgyun.hltgq.hltgqsite.system.vo.SystemMonitorVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务器资源采集器（CPU/内存/交换区/磁盘，页面 system-monitor.html）。
 * <p>双视角自动降级：部署挂载 {@code -v /:/host:ro} 时读宿主机 /proc 与挂载点（scope=host，
 * 反映整台服务器）；未挂载时退回容器内 /proc（scope=container，CPU/内存/交换区仍为宿主内核视角、
 * 磁盘仅容器可见分区）。判定依据：{@code hostRoot + "/proc/meminfo"} 是否存在。
 * <p>CPU 用 /proc/stat 两次采样差分计算（精度稳定），首次不可差分时用 MXBean 兜底；
 * 采集全程只读、异常自吞，任一文件读不到返回 null 由前端显示 --。
 */
@Component
public class SystemResourceProbe {

    private static final Logger log = LoggerFactory.getLogger(SystemResourceProbe.class);

    /** 真实块设备文件系统（宿主机锚点过滤，排除 tmpfs/proc 等噪声挂载） */
    private static final Set<String> REAL_FS = new HashSet<>(
            Arrays.asList("ext2", "ext3", "ext4", "xfs", "btrfs"));

    /** cgroup 无限制判定（v1 无限制时 limit 为极大值；v2 为 "max" 字符串已解析为 null） */
    private static final long UNLIMITED_BYTES = 1L << 50;

    /** 宿主机根挂载路径（部署脚本加 -v /:/host:ro；置空则强制容器视角） */
    @Value("${system.monitor.host-root:/host}")
    private String hostRoot;

    /** 是否宿主机视角（初始化时一次性探测） */
    private volatile boolean hostMode = false;

    /** 宿主机挂载表不可读告警仅打一次（避免页面轮询刷屏） */
    private final AtomicBoolean hostMountsWarned = new AtomicBoolean(false);

    /** CPU 差分采样状态（[total, idle] 快照 + 时间戳 + 上次结果） */
    private final Object cpuLock = new Object();
    private long[] lastCpuStat;
    private long lastCpuStatAt;
    private Double lastCpuPercent;

    @PostConstruct
    public void init() {
        String root = hostRoot == null ? "" : hostRoot.trim();
        while (root.length() > 1 && root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        hostRoot = root;
        hostMode = !root.isEmpty() && new File(root + "/proc/meminfo").isFile();
        // 预热一次 CPU 差分基线，保证首个请求即可算出使用率
        long[] initStat = readCpuStat();
        if (initStat != null) {
            lastCpuStat = initStat;
            lastCpuStatAt = System.currentTimeMillis();
        }
        log.info("[system-monitor] resource probe init: hostRoot='{}', scope={}", hostRoot,
                hostMode ? "host" : "container");
    }

    /** 采集一次完整资源快照 */
    public SystemMonitorVO.Resource snapshot() {
        SystemMonitorVO.Resource r = new SystemMonitorVO.Resource();
        r.setScope(hostMode ? "host" : "container");
        r.setHostname(hostName());
        r.setOsName(readOsName());
        r.setSampleTime(LocalDateTime.now());

        long[] stat = readCpuStat();
        r.setCpus(stat != null && stat[2] > 0 ? (int) stat[2] : Runtime.getRuntime().availableProcessors());
        r.setCpuPercent(cpuPercent(stat));

        double[] load = readLoadAvg();
        if (load != null) {
            r.setLoad1(round1(load[0]));
            r.setLoad5(round1(load[1]));
            r.setLoad15(round1(load[2]));
        }

        fillMemory(r);
        fillContainerMemory(r);
        r.setDisks(readDisks());
        return r;
    }

    /* ============================ CPU ============================ */

    /**
     * /proc/stat 差分计算 CPU 使用率（与上次采样间隔 >=500ms 才重算；
     * 无法差分时用 MXBean 兜底；两者都取不到返回 null）。
     */
    private Double cpuPercent(long[] cur) {
        synchronized (cpuLock) {
            Double result = lastCpuPercent;
            long now = System.currentTimeMillis();
            if (cur != null && lastCpuStat != null && now - lastCpuStatAt >= 500) {
                long dt = cur[0] - lastCpuStat[0];
                long di = cur[1] - lastCpuStat[1];
                if (dt > 0 && di >= 0) {
                    result = round1(clamp((1.0 - (double) di / dt) * 100.0));
                }
                lastCpuStat = cur;
                lastCpuStatAt = now;
            } else if (cur != null && lastCpuStat == null) {
                lastCpuStat = cur;
                lastCpuStatAt = now;
            }
            if (result == null) {
                result = mxCpuPercent();
            }
            lastCpuPercent = result;
            return result;
        }
    }

    /** MXBean 系统 CPU 使用率（JDK8 com.sun.management；不可用返回 null） */
    private Double mxCpuPercent() {
        try {
            java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean) {
                double v = ((com.sun.management.OperatingSystemMXBean) os).getSystemCpuLoad();
                if (v >= 0) {
                    return round1(v * 100.0);
                }
            }
        } catch (Throwable ignored) {
            // MXBean 不可用时由前端显示 --
        }
        return null;
    }

    /** 读 /proc/stat 汇总行：返回 [total, idle+iowait, 逻辑核数]；读不到返回 null */
    private long[] readCpuStat() {
        return parseCpuStat(readLines(procRoot() + "/stat"));
    }

    /** 解析 /proc/stat 文本：返回 [total, idle+iowait, 逻辑核数]；无汇总行返回 null */
    private long[] parseCpuStat(List<String> lines) {
        long[] agg = null;
        int cpus = 0;
        for (String line : lines) {
            if (!line.startsWith("cpu")) {
                continue;
            }
            if (line.length() > 3 && Character.isDigit(line.charAt(3))) {
                cpus++;
                continue;
            }
            if (agg == null) {
                String[] f = line.trim().split("\\s+");
                if (f.length < 9) {
                    continue;
                }
                long total = 0;
                for (int i = 1; i <= 8; i++) {
                    total += parseLong(f[i]);
                }
                // idle(4) + iowait(5)
                long idle = parseLong(f[4]) + parseLong(f[5]);
                agg = new long[]{total, idle, 0};
            }
        }
        if (agg == null) {
            return null;
        }
        agg[2] = cpus;
        return agg;
    }

    /** 系统负载 1/5/15 分钟（/proc/loadavg） */
    private double[] readLoadAvg() {
        return parseLoadAvg(readLines(procRoot() + "/loadavg"));
    }

    /** 解析 /proc/loadavg 文本前三列；行缺失或非法返回 null */
    private double[] parseLoadAvg(List<String> lines) {
        if (lines.isEmpty()) {
            return null;
        }
        String[] f = lines.get(0).trim().split("\\s+");
        if (f.length < 3) {
            return null;
        }
        try {
            return new double[]{Double.parseDouble(f[0]), Double.parseDouble(f[1]), Double.parseDouble(f[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /* ============================ 内存 / 交换区 ============================ */

    /** 内存与交换区：/proc/meminfo（容器内共享内核，天然为宿主机口径） */
    private void fillMemory(SystemMonitorVO.Resource r) {
        fillMemoryFrom(r, readMemInfoKb());
    }

    /** 按解析好的 meminfo 键值填充内存/交换区（本机采集与远端上报共用同一口径） */
    private void fillMemoryFrom(SystemMonitorVO.Resource r, Map<String, Long> kb) {
        if (kb.isEmpty()) {
            return;
        }
        long total = kb(kb, "MemTotal");
        if (total <= 0) {
            return;
        }
        long available = kb(kb, "MemAvailable");
        if (available <= 0) {
            available = kb(kb, "MemFree") + kb(kb, "Buffers") + kb(kb, "Cached");
        }
        long used = Math.max(0, total - available);
        r.setMemTotal(total);
        r.setMemUsed(used);
        r.setMemAvailable(available);
        r.setMemPercent(round1(used * 100.0 / total));

        long swapTotal = kb(kb, "SwapTotal");
        long swapFree = kb(kb, "SwapFree");
        long swapUsed = Math.max(0, swapTotal - swapFree);
        r.setSwapTotal(swapTotal);
        r.setSwapUsed(swapUsed);
        r.setSwapPercent(swapTotal > 0 ? round1(swapUsed * 100.0 / swapTotal) : null);
    }

    /**
     * 容器内存（cgroup 限额口径，读容器自身 /sys/fs/cgroup，不看 /host）：
     * v1 memory.limit_in_bytes/usage_in_bytes，v2 memory.max/current；无限制或不可读则不设置。
     */
    private void fillContainerMemory(SystemMonitorVO.Resource r) {
        Long limit = readLongFile("/sys/fs/cgroup/memory/memory.limit_in_bytes");
        Long usage = readLongFile("/sys/fs/cgroup/memory/memory.usage_in_bytes");
        if (limit == null || limit <= 0) {
            limit = readLongFile("/sys/fs/cgroup/memory.max");
            usage = readLongFile("/sys/fs/cgroup/memory.current");
        }
        if (limit != null && limit > 0 && limit < UNLIMITED_BYTES && usage != null && usage >= 0) {
            r.setContainerMemLimit(limit);
            r.setContainerMemUsed(usage);
            r.setContainerMemPercent(round1(usage * 100.0 / limit));
        }
    }

    /** 解析 /proc/meminfo 为「键 → kB 值」 */
    private Map<String, Long> readMemInfoKb() {
        return parseMemInfoKb(readLines(procRoot() + "/meminfo"));
    }

    private Map<String, Long> parseMemInfoKb(List<String> lines) {
        Map<String, Long> m = new HashMap<>();
        for (String line : lines) {
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String val = line.substring(idx + 1).trim();
            int sp = val.indexOf(' ');
            if (sp > 0) {
                val = val.substring(0, sp);
            }
            Long v = parseLongOrNull(val);
            if (v != null) {
                m.put(key, v);
            }
        }
        return m;
    }

    private long kb(Map<String, Long> m, String key) {
        Long v = m.get(key);
        return v == null ? 0L : v * 1024L;
    }

    /* ============================ 磁盘 ============================ */

    /**
     * 磁盘分区使用率：宿主机视角解析 {@code /host/proc/1/mounts}（宿主机 init 的挂载表，真实块
     * 设备挂载点，同设备去重），容器视角解析容器自身 mounts（overlay 根 + 已挂载卷）。
     * 使用率口径对齐 df：used/(used+avail)。
     */
    private List<SystemMonitorVO.Disk> readDisks() {
        List<SystemMonitorVO.Disk> out = new ArrayList<>();
        Set<String> seenDevice = new HashSet<>();
        // /proc/mounts 与 /proc/self/mounts 按读者 mount namespace 动态生成（容器内读 /host/proc/mounts
        // 得到的仍是容器自己的挂载表），只有 /proc/<pid>/mounts 固定返回指定进程（宿主机 init）的挂载表
        List<String> mounts = hostMode
                ? readLines(hostRoot + "/proc/1/mounts")
                : readLines("/proc/mounts");
        if (hostMode && mounts.isEmpty() && hostMountsWarned.compareAndSet(false, true)) {
            log.warn("[system-monitor] host mounts (/host/proc/1/mounts) unreadable, disk list stays empty");
        }
        for (String line : mounts) {
            String[] f = line.split("\\s+");
            if (f.length < 3) {
                continue;
            }
            String dev = unescapeMount(f[0]);
            String mp = unescapeMount(f[1]);
            String fs = f[2];
            boolean real = dev.startsWith("/dev/") && REAL_FS.contains(fs);
            boolean overlayRoot = !hostMode && "overlay".equals(fs) && "/".equals(mp);
            if (!real && !overlayRoot) {
                continue;
            }
            if (real && !seenDevice.add(dev)) {
                continue;
            }
            File dir = new File((hostMode ? hostRoot : "") + mp);
            long total;
            long free;
            long avail;
            try {
                total = dir.getTotalSpace();
                free = dir.getFreeSpace();
                avail = dir.getUsableSpace();
            } catch (Exception e) {
                continue;
            }
            if (total <= 0) {
                continue;
            }
            long used = Math.max(0, total - free);
            double percent = (used + avail) > 0 ? round1(used * 100.0 / (used + avail)) : 0d;
            SystemMonitorVO.Disk d = new SystemMonitorVO.Disk();
            d.setMount(mp);
            d.setTotal(total);
            d.setUsed(used);
            d.setAvail(avail);
            d.setPercent(percent);
            out.add(d);
        }
        out.sort(Comparator.comparing(SystemMonitorVO.Disk::getMount));
        return out;
    }

    /* ============================ 主机信息 ============================ */

    /** 主机名（宿主机视角读 /host/etc/hostname；容器视角为容器自身 hostname；供资源快照与故障记录 source 共用） */
    public String hostName() {
        // 宿主机视角：/proc/sys/kernel/hostname 按读者 UTS namespace 生成（返回容器名），
        // 改读宿主机 root fs 的 /etc/hostname（普通文件，bind 挂载可见）
        List<String> lines = hostMode
                ? readLines(hostRoot + "/etc/hostname")
                : readLines("/proc/sys/kernel/hostname");
        if (!lines.isEmpty() && !lines.get(0).trim().isEmpty()) {
            return lines.get(0).trim();
        }
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** 操作系统名称（/etc/os-release PRETTY_NAME；宿主机视角读 /host/etc/os-release） */
    private String readOsName() {
        return parseOsName(readLines((hostMode ? hostRoot : "") + "/etc/os-release"));
    }

    /** 解析 os-release 文本的 PRETTY_NAME；取不到回退 JVM os.name */
    private String parseOsName(List<String> lines) {
        for (String line : lines) {
            if (line.startsWith("PRETTY_NAME=")) {
                String v = line.substring("PRETTY_NAME=".length()).trim();
                if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                    v = v.substring(1, v.length() - 1);
                }
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return System.getProperty("os.name", "");
    }

    /* ============================ 远端上报解析 ============================ */

    /**
     * 解析服务器采集脚本上报的原始文本段（###KEY### 分段，见 sysmon-collect.sh）为资源快照。
     * <p>CPU 取 STAT/STAT2 两段差分（脚本间隔约 1 秒采样两次）；磁盘取 DF 段
     * （df -B1 -T -P 输出，按真实块设备过滤，使用率口径与 df 对齐）；HOSTNAME/OSRELEASE 直接取文本。
     * 任一数据缺失仅对应字段为空，不抛异常。
     */
    public SystemMonitorVO.Resource parseResource(Map<String, List<String>> sections) {
        SystemMonitorVO.Resource r = new SystemMonitorVO.Resource();
        r.setScope("host");
        r.setSampleTime(LocalDateTime.now());

        String host = firstLine(sections, "HOSTNAME");
        r.setHostname(host == null ? "unknown" : host);
        r.setOsName(parseOsName(lines(sections, "OSRELEASE")));

        long[] stat1 = parseCpuStat(lines(sections, "STAT"));
        long[] stat2 = parseCpuStat(lines(sections, "STAT2"));
        long[] ref = stat2 != null ? stat2 : stat1;
        r.setCpus(ref != null && ref[2] > 0 ? (int) ref[2] : null);
        r.setCpuPercent(diffCpuPercent(stat1, stat2));

        double[] load = parseLoadAvg(lines(sections, "LOADAVG"));
        if (load != null) {
            r.setLoad1(round1(load[0]));
            r.setLoad5(round1(load[1]));
            r.setLoad15(round1(load[2]));
        }
        fillMemoryFrom(r, parseMemInfoKb(lines(sections, "MEMINFO")));
        r.setDisks(parseDisksFromDf(lines(sections, "DF")));
        return r;
    }

    /** 两段 /proc/stat 差分算 CPU 使用率（跨段无效时返回 null 由前端显示 --） */
    private Double diffCpuPercent(long[] a, long[] b) {
        if (a == null || b == null) {
            return null;
        }
        long dt = b[0] - a[0];
        long di = b[1] - a[1];
        if (dt <= 0 || di < 0) {
            return null;
        }
        return round1(clamp((1.0 - (double) di / dt) * 100.0));
    }

    /**
     * 解析 {@code df -B1 -T -P} 输出（列：Filesystem Type 1B-blocks Used Available Capacity Mounted on）：
     * 真实块设备 + 文件系统白名单，同设备去重；使用率 used/(used+avail) 与 df 口径一致。
     */
    private List<SystemMonitorVO.Disk> parseDisksFromDf(List<String> lines) {
        List<SystemMonitorVO.Disk> out = new ArrayList<>();
        Set<String> seenDevice = new HashSet<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("Filesystem")) {
                continue;
            }
            String[] f = t.split("\\s+", 7);
            if (f.length < 7) {
                continue;
            }
            String dev = unescapeMount(f[0]);
            if (!dev.startsWith("/dev/") || !REAL_FS.contains(f[1]) || !seenDevice.add(dev)) {
                continue;
            }
            Long total = parseLongOrNull(f[2]);
            Long used = parseLongOrNull(f[3]);
            Long avail = parseLongOrNull(f[4]);
            if (total == null || used == null || avail == null || total <= 0) {
                continue;
            }
            SystemMonitorVO.Disk d = new SystemMonitorVO.Disk();
            d.setMount(unescapeMount(f[6]));
            d.setTotal(total);
            d.setUsed(used);
            d.setAvail(avail);
            d.setPercent((used + avail) > 0 ? round1(used * 100.0 / (used + avail)) : 0d);
            out.add(d);
        }
        out.sort(Comparator.comparing(SystemMonitorVO.Disk::getMount));
        return out;
    }

    private List<String> lines(Map<String, List<String>> sections, String key) {
        List<String> v = sections == null ? null : sections.get(key);
        return v == null ? Collections.emptyList() : v;
    }

    /** 取分段中的首个非空行（HOSTNAME 等单行段用） */
    private String firstLine(Map<String, List<String>> sections, String key) {
        for (String line : lines(sections, key)) {
            String t = line.trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return null;
    }

    /* ============================ 文件与数值工具 ============================ */

    /** /proc 根：宿主机视角为 {hostRoot}/proc，容器视角为 /proc */
    private String procRoot() {
        return hostMode ? hostRoot + "/proc" : "/proc";
    }

    private List<String> readLines(String path) {
        try {
            File f = new File(path);
            if (!f.isFile()) {
                return Collections.emptyList();
            }
            return Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Long readLongFile(String path) {
        List<String> lines = readLines(path);
        if (lines.isEmpty()) {
            return null;
        }
        return parseLongOrNull(lines.get(0).trim());
    }

    /** mounts 转义还原（\\040 空格 / \\011 制表符 / \\134 反斜杠） */
    private String unescapeMount(String s) {
        return s.replace("\\040", " ").replace("\\011", "\t").replace("\\134", "\\");
    }

    private Long parseLongOrNull(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private long parseLong(String s) {
        Long v = parseLongOrNull(s);
        return v == null ? 0L : v;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private double clamp(double v) {
        return Math.max(0d, Math.min(100d, v));
    }
}
