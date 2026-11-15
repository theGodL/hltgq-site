package com.qgyun.hltgq.hltgqsite.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.system.entity.SystemResourceSample;
import com.qgyun.hltgq.hltgqsite.system.mapper.SystemMonitorMapper;
import com.qgyun.hltgq.hltgqsite.system.support.SystemResourceProbe;
import com.qgyun.hltgq.hltgqsite.system.vo.SystemMonitorVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 系统监控服务（接口 /system-monitor/*，页面 system-monitor.html）。
 * <p>读侧：资源快照实时采集（轻量：/proc 读文件 + statvfs）；数据库表空间查询重量级
 * （pg_tablespace_size 扫目录），内存缓存 {@code system.monitor.db.cache-minutes} 分钟，
 * 查询失败时回退上次缓存值。表空间“使用率”分母 = {@code system.monitor.db.capacity-gb}
 * 配置的数据盘容量基线（KingbaseES 库内取不到磁盘容量）；未配置（0）时使用率返回 null，前端显示 --。
 * <p>多主机：本机实时快照 + 远端服务器采集脚本上报（POST /system-monitor/report，
 * ###KEY### 分段协议见仓库根目录 sysmon-collect.sh）；远端主机内存缓存，每次上报落表并越限检测，
 * 概览以 hosts 列表下发（离线主机保留最后快照，重启后远端列表随下次上报自然恢复）。
 * <p>写侧（采集/记录逻辑与查询聚合同模块内聚，不另建定时任务类）：
 * ① 启动记录：服务启动时落一条系统故障（warn，供监控报警子系统识别重启）；
 * ② 采样：固定间隔（默认 5 分钟）采集快照落表（“监控信息保存”），并做越限检测
 * （CPU/内存/交换区/磁盘/表空间 vs 阈值，越限记系统故障，同键由 {@link FaultRecordService} 抑制窗口去重）；
 * ③ 清理：每日清理保留期外的采样记录（故障记录属审计数据，不清理）。
 * <p>写侧所有动作异常自吞：任一环节失败仅告警，绝不阻断应用启动与后续调度
 * （历史教训：ApplicationRunner 抛异常曾导致全站 502）。
 */
@Service
public class SystemMonitorService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemMonitorService.class);

    private static final String FAULT_TYPE_RESOURCE = "资源越限";

    @Autowired
    private SystemResourceProbe probe;

    @Autowired
    private SystemMonitorMapper mapper;

    @Autowired
    private FaultRecordService faultRecordService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShortIdGenerator shortIdGenerator;

    /** 表空间查询缓存（分钟） */
    @Value("${system.monitor.db.cache-minutes:5}")
    private int cacheMinutes;

    /** 数据库数据盘容量基线（GB；0/负 = 未配置） */
    @Value("${system.monitor.db.capacity-gb:0}")
    private double capacityGb;

    /** 越限告警阈值（页面按同口径标色，与采样任务共用同一份配置） */
    @Value("${system.monitor.threshold.cpu-percent:90}")
    private double cpuThreshold;

    @Value("${system.monitor.threshold.mem-percent:90}")
    private double memThreshold;

    @Value("${system.monitor.threshold.swap-percent:80}")
    private double swapThreshold;

    @Value("${system.monitor.threshold.disk-percent:85}")
    private double diskThreshold;

    @Value("${system.monitor.threshold.tablespace-percent:85}")
    private double tablespaceThreshold;

    /** 采样保留天数（每日清理一次） */
    @Value("${system.monitor.sample.retain-days:30}")
    private int retainDays;

    /** 本机别名（监控页展示名与越限故障标签；远端各机别名来自各自脚本 ALIAS 段） */
    @Value("${system.monitor.local-alias:应用服务器2}")
    private String localAlias;

    /** 表空间缓存（volatile 发布；过期后先返回旧值、由后台线程刷新，接口不阻塞在重量级查询上） */
    private volatile SystemMonitorVO.Database cachedDb;
    private volatile long cachedDbAt;

    /** 表空间后台刷新互斥（同一时刻只有一个任务去撞重量级查询，失败由退避控制重试节奏） */
    private final AtomicBoolean dbRefreshing = new AtomicBoolean(false);

    /** 表空间刷新失败后的重试退避（毫秒）：需长于单次查询超时（60s），
     *  否则超时型失败在退避期满后立即重试，等于持续占用一个连接反复扫目录 */
    private static final long DB_RETRY_BACKOFF_MS = 5 * 60_000L;

    /** 表空间快照的额外陈旧容忍（毫秒）：正常刷新为秒级，超过「缓存周期 + 本宽限」视为刷新卡住 */
    private static final long DB_STALE_GRACE_MS = 2 * 60_000L;

    private volatile long dbRetryAfter;

    /** 表空间最近一次刷新是否失败（失败期间缓存保留旧值，越限判定需跳过，避免重复记告警） */
    private volatile boolean dbRefreshFailed = false;

    /** 上次清理日期（调度线程单线程执行，无需原子保证） */
    private volatile LocalDate lastCleanupDay;

    /** 远端主机最近上报缓存（hostname → 状态；重启后随下次上报自然恢复） */
    private final Map<String, SystemMonitorVO.HostStatus> remoteHosts = new ConcurrentHashMap<>();

    /** 主机离线判定时长（秒）：最近上报超过该时长视为离线（上报周期 1 分钟） */
    @Value("${system.monitor.report.offline-seconds:300}")
    private int reportOfflineSeconds;

    /** 同一主机最小上报间隔（秒）：高频重复上报直接忽略，防脚本误配 */
    @Value("${system.monitor.report.min-interval-seconds:3}")
    private int reportMinIntervalSeconds;

    /** 概览：资源快照 + 多主机列表 + 表空间（+ 阈值口径），页面按固定间隔轮询本接口 */
    public SystemMonitorVO.Overview overview() {
        SystemMonitorVO.Overview o = new SystemMonitorVO.Overview();
        SystemMonitorVO.Resource local = probe.snapshot();
        o.setResource(local);
        o.setHosts(hosts(local));
        o.setDatabase(database());
        o.setThresholds(thresholds());
        return o;
    }

    /**
     * 主机列表：本机（current）+ 远端上报缓存；在线状态按最近上报时间动态计算
     * （离线主机保留最后快照），本机固定在前、其余按别名/主机名字母序。
     */
    private List<SystemMonitorVO.HostStatus> hosts(SystemMonitorVO.Resource local) {
        List<SystemMonitorVO.HostStatus> out = new ArrayList<>();
        if (local != null) {
            SystemMonitorVO.HostStatus self = new SystemMonitorVO.HostStatus();
            self.setHostname(local.getHostname());
            self.setAlias(localAlias);
            self.setCurrent(true);
            self.setOnline(true);
            self.setLastReportTime(local.getSampleTime());
            self.setResource(local);
            out.add(self);
        }
        long onlineLimit = System.currentTimeMillis() - reportOfflineSeconds * 1000L;
        for (SystemMonitorVO.HostStatus h : remoteHosts.values()) {
            h.setOnline(h.getLastReportTime() != null
                    && h.getLastReportTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() >= onlineLimit);
            out.add(h);
        }
        out.sort(Comparator.comparing((SystemMonitorVO.HostStatus h) -> !h.isCurrent())
                .thenComparing(h -> h.getAlias() == null || h.getAlias().isEmpty() ? h.getHostname() : h.getAlias()));
        return out;
    }

    /**
     * 数据库表空间（缓存 + 后台刷新，请求线程恒定毫秒级返回）。
     * <p>表空间查询是重量级操作（pg_database_size / pg_tablespace_size 需遍历数据目录，
     * 库大时单次耗时数十秒），因此请求线程永不执行该查询：无缓存时返回 null 并触发后台加载，
     * 缓存过期时先返回旧值再后台刷新。
     * 历史问题：缓存过期瞬间首个请求同步查询并持锁，页面轮询请求全部排在锁上，
     * 表现为刚打开页面时主机切换下拉框与资源卡片长时间空白，刷新若干次才恢复。
     */
    public SystemMonitorVO.Database database() {
        SystemMonitorVO.Database cached = cachedDb;
        long now = System.currentTimeMillis();
        if (cached == null) {
            // 无缓存：只触发后台加载，首个实例由下一次轮询/采样周期带走
            if (now >= dbRetryAfter) {
                refreshDatabaseAsync();
            }
            return cachedDb;
        }
        if (now - cachedDbAt >= cacheTtlMs() && now >= dbRetryAfter) {
            refreshDatabaseAsync();
        }
        return cached;
    }

    /** 表空间缓存有效期（毫秒）：配置写 0/负数时按 1 分钟兜底，避免每个轮询周期都触发扫目录 */
    private long cacheTtlMs() {
        return Math.max(1, cacheMinutes) * 60_000L;
    }

    /**
     * 表空间快照是否可用于越限判定与落库。
     * <p>两种陈旧情形都要跳过：① 最近一次刷新失败——{@code cachedDb} 保留最后一次成功的数值，
     * 若照常判定，只要旧值仍越限就会按故障抑制窗口重复记同一条告警；
     * ② 刷新任务卡住（查询超时未生效、连接半开）——此时失败标志还没置位，
     * 用「缓存周期 + 宽限」兜底识别，避免长期拿陈旧值判定与落库。恢复后自动继续。
     */
    private boolean databaseFresh() {
        if (dbRefreshFailed || cachedDb == null) {
            return false;
        }
        return System.currentTimeMillis() - cachedDbAt <= cacheTtlMs() + DB_STALE_GRACE_MS;
    }

    /**
     * 后台刷新表空间缓存（单任务，绝不占用请求线程）。
     * <p>失败只记退避、不改 {@code cachedDbAt}：保留「最后一次成功时间」语义，
     * 采样落库与页面「统计时间」据此能看出数据是否陈旧。
     */
    private void refreshDatabaseAsync() {
        if (!dbRefreshing.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                cachedDb = loadDatabase();
                cachedDbAt = System.currentTimeMillis();
                dbRefreshFailed = false;
            } catch (Exception e) {
                dbRetryAfter = System.currentTimeMillis() + DB_RETRY_BACKOFF_MS;
                dbRefreshFailed = true;
                log.warn("[system-monitor] tablespace refresh failed: {}", e.getMessage());
            } finally {
                dbRefreshing.set(false);
            }
        }, "system-monitor-db-refresh");
        try {
            t.setDaemon(true);
            t.start();
        } catch (Throwable ex) {
            // 启动线程失败：按刷新失败处理（同样跳过越限判定与采样落库，避免用旧值）；
            // 状态写完再释放互斥，保证后续抢到互斥的任务不会与本处的状态写入交叉
            dbRetryAfter = System.currentTimeMillis() + DB_RETRY_BACKOFF_MS;
            dbRefreshFailed = true;
            log.warn("[system-monitor] start tablespace refresh thread failed: {}", ex.toString());
            dbRefreshing.set(false);
        }
    }

    private SystemMonitorVO.Database loadDatabase() {
        Map<String, Object> info = mapper.selectDbInfo();
        List<Map<String, Object>> rows = mapper.selectTablespaceSize();

        long capacity = capacityGb > 0 ? (long) (capacityGb * 1024L * 1024L * 1024L) : 0L;
        Long capacityBytes = capacity > 0 ? capacity : null;

        SystemMonitorVO.Database db = new SystemMonitorVO.Database();
        db.setDbName(stringOf(info.get("db_name")));
        Long dbSize = longOf(info.get("db_size"));
        db.setDbSizeBytes(dbSize);
        db.setCapacityBytes(capacityBytes);
        db.setDbPercent(percentOf(dbSize, capacity));

        List<SystemMonitorVO.Tablespace> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            SystemMonitorVO.Tablespace ts = new SystemMonitorVO.Tablespace();
            ts.setName(stringOf(row.get("spc_name")));
            Long size = longOf(row.get("size_bytes"));
            ts.setSizeBytes(size);
            ts.setCapacityBytes(capacityBytes);
            ts.setPercent(percentOf(size, capacity));
            list.add(ts);
        }
        db.setTablespaces(list);
        db.setQueryTime(LocalDateTime.now());
        return db;
    }

    private SystemMonitorVO.Thresholds thresholds() {
        SystemMonitorVO.Thresholds t = new SystemMonitorVO.Thresholds();
        t.setCpu(cpuThreshold);
        t.setMem(memThreshold);
        t.setSwap(swapThreshold);
        t.setDisk(diskThreshold);
        t.setTablespace(tablespaceThreshold);
        return t;
    }

    /* ============================ 写侧：启动记录 / 采样 / 越限检测 / 清理 ============================ */

    /** 服务启动记录（warn 级系统故障，供监控报警子系统识别重启） */
    @Override
    public void run(ApplicationArguments args) {
        try {
            faultRecordService.recordSystem("服务启动", hostname(), FaultRecordService.LEVEL_WARN,
                    "服务启动：java=" + System.getProperty("java.version")
                            + ", pid=" + ManagementFactory.getRuntimeMXBean().getName());
        } catch (Exception e) {
            log.warn("[system-monitor] startup record failed: {}", e.getMessage());
        }
        // 预热表空间缓存：异步执行，避免监控页首次打开时同步查询导致页面长时间空白
        refreshDatabaseAsync();
    }

    /**
     * 定时采样：采集快照 → 落表（保存）→ 越限检测（反馈）→ 每日清理。
     * <p>首次延迟与固定间隔均可配置，默认启动 60s 后首采、间隔 5 分钟。
     */
    @Scheduled(initialDelayString = "${system.monitor.sample.initial-delay-ms:60000}",
            fixedDelayString = "${system.monitor.sample.delay-ms:300000}")
    public void sample() {
        SystemMonitorVO.Overview overview;
        try {
            overview = overview();
        } catch (Exception e) {
            log.warn("[system-monitor] sample failed: {}", e.getMessage());
            return;
        }
        // 同一轮采样共用一份新鲜度判定：刷新失败期间既不落表空间、也不做表空间越限判定
        boolean dbFresh = databaseFresh();
        try {
            saveSample(overview, dbFresh);
        } catch (Exception e) {
            // 落库失败与故障记录共用同一张库表，此处只告警避免二次失败
            log.warn("[system-monitor] sample persist failed: {}", e.getMessage());
        }
        try {
            checkThresholds(overview, dbFresh);
        } catch (Exception e) {
            log.warn("[system-monitor] threshold check failed: {}", e.getMessage());
        }
        try {
            cleanup();
        } catch (Exception e) {
            log.warn("[system-monitor] sample cleanup failed: {}", e.getMessage());
        }
    }

    /**
     * 采样落表（本机完整快照：资源 + 表空间）。
     * <p>表空间快照仅在来自成功刷新时落库：刷新失败或快照过期期间缓存保留上一次成功的数值，
     * 若照常写入，落库数据会与真实采集状态不一致，也无法区分「采集失败」与「数值未变」。
     */
    private void saveSample(SystemMonitorVO.Overview o, boolean dbFresh) throws Exception {
        persistSample(o.getResource(), dbFresh ? o.getDatabase() : null);
    }

    /** 采样落表：磁盘/表空间明细存 JSON 文本列（id 由短 ID 生成器产出，与平台 22 位短 ID 规范一致） */
    private SystemResourceSample persistSample(SystemMonitorVO.Resource r, SystemMonitorVO.Database db) throws Exception {
        SystemResourceSample s = new SystemResourceSample();
        LocalDateTime now = LocalDateTime.now();
        s.setId(shortIdGenerator.nextUUID(s));
        s.setSampleTime(r != null && r.getSampleTime() != null ? r.getSampleTime() : now);
        if (r != null) {
            s.setScope(r.getScope());
            s.setHostname(r.getHostname());
            s.setCpuPercent(r.getCpuPercent());
            s.setLoad1(r.getLoad1());
            s.setLoad5(r.getLoad5());
            s.setLoad15(r.getLoad15());
            s.setMemTotal(r.getMemTotal());
            s.setMemUsed(r.getMemUsed());
            s.setMemPercent(r.getMemPercent());
            s.setSwapTotal(r.getSwapTotal());
            s.setSwapUsed(r.getSwapUsed());
            s.setSwapPercent(r.getSwapPercent());
            if (r.getDisks() != null) {
                s.setDiskJson(objectMapper.writeValueAsString(r.getDisks()));
            }
        }
        if (db != null && db.getTablespaces() != null) {
            s.setDbJson(objectMapper.writeValueAsString(db.getTablespaces()));
        }
        s.setCorpCode("hltgq");
        s.setCreatedBy("system-monitor");
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        mapper.insertSample(s);
        return s;
    }

    /**
     * 越限检测（本机）：资源项 + 数据库表空间，越限记一条系统故障（desc 含实际值与阈值）。
     * <p>表空间部分仅在快照新鲜时判定，刷新失败或快照过期期间跳过（防陈旧值重复告警）。
     */
    private void checkThresholds(SystemMonitorVO.Overview o, boolean dbFresh) {
        SystemMonitorVO.Resource r = o.getResource();
        SystemMonitorVO.Thresholds t = o.getThresholds();
        if (r == null || t == null) {
            return;
        }
        checkHostThresholds(r, localAlias == null || localAlias.isEmpty() ? hostname() : localAlias);
        SystemMonitorVO.Database db = o.getDatabase();
        if (db != null && db.getTablespaces() != null && dbFresh) {
            for (SystemMonitorVO.Tablespace ts : db.getTablespaces()) {
                if (exceed(ts.getPercent(), t.getTablespace())) {
                    record("表空间 " + ts.getName(), String.format("表空间 %s 使用率 %s%% 超过阈值 %s%%（已用 %s / 基线 %s）",
                            ts.getName(), ts.getPercent(), t.getTablespace(), fmtBytes(ts.getSizeBytes()), fmtBytes(ts.getCapacityBytes())));
                }
            }
        }
    }

    /** 单主机资源项越限检测（CPU/内存/交换区/磁盘；source 带 @主机标签区分多主机） */
    private void checkHostThresholds(SystemMonitorVO.Resource r, String label) {
        if (exceed(r.getCpuPercent(), cpuThreshold)) {
            record("CPU@" + label, String.format("CPU 使用率 %s%% 超过阈值 %s%%", r.getCpuPercent(), cpuThreshold));
        }
        if (exceed(r.getMemPercent(), memThreshold)) {
            record("内存@" + label, String.format("内存使用率 %s%% 超过阈值 %s%%（已用 %s / 总计 %s）",
                    r.getMemPercent(), memThreshold, fmtBytes(r.getMemUsed()), fmtBytes(r.getMemTotal())));
        }
        if (exceed(r.getSwapPercent(), swapThreshold)) {
            record("交换区@" + label, String.format("交换区使用率 %s%% 超过阈值 %s%%", r.getSwapPercent(), swapThreshold));
        }
        List<SystemMonitorVO.Disk> disks = r.getDisks();
        if (disks != null) {
            for (SystemMonitorVO.Disk d : disks) {
                if (exceed(d.getPercent(), diskThreshold)) {
                    record("磁盘 " + d.getMount() + "@" + label, String.format(
                            "磁盘 %s 使用率 %s%% 超过阈值 %s%%（已用 %s / 可用 %s）",
                            d.getMount(), d.getPercent(), diskThreshold, fmtBytes(d.getUsed()), fmtBytes(d.getAvail())));
                }
            }
        }
    }

    /** 记录一条资源越限系统故障（warn 级） */
    private void record(String source, String desc) {
        faultRecordService.recordSystem(FAULT_TYPE_RESOURCE, source, FaultRecordService.LEVEL_WARN, desc);
    }

    /** 每日清理一次保留期外的采样记录 */
    private void cleanup() {
        LocalDate today = LocalDate.now();
        if (today.equals(lastCleanupDay)) {
            return;
        }
        lastCleanupDay = today;
        int n = mapper.deleteSamplesBefore(LocalDateTime.now().minusDays(retainDays));
        if (n > 0) {
            log.info("[system-monitor] sample cleanup: {} rows older than {} days removed", n, retainDays);
        }
    }

    /* ============================ 远端上报（多主机） ============================ */

    /**
     * 接收远端服务器采集上报：分段解析 → 内存缓存 → 采样落表 → 越限检测。
     * <p>协议：###KEY### 占位行分段（KEY 为大写字母/数字/下划线），段内容为原始命令输出
     * （见仓库根目录 sysmon-collect.sh）；鉴权与来源限制在 Controller 前置完成。
     * 同一主机高频重复上报（小于最小间隔）直接返回上次状态，不重复落库与检测；
     * 本机（平台所在服务器）的上报直接拒绝（本地已有实时采集，防主机列表重复）。
     *
     * @return 该主机最新状态（落库/检测失败不阻断返回，仅告警）
     * @throws IllegalArgumentException 报文缺少有效 HOSTNAME 段，或上报主机为平台本机
     */
    public SystemMonitorVO.HostStatus acceptReport(String body) {
        Map<String, List<String>> sections = parseSections(body);
        String hostname = firstNonBlank(sections, "HOSTNAME");
        if (hostname == null || hostname.length() > 128) {
            throw new IllegalArgumentException("报文缺少有效 HOSTNAME 段");
        }
        if (hostname.equalsIgnoreCase(hostname())) {
            // 防误配：平台所在主机由本地实时采集，脚本上报会造成主机列表重复（如应用服务器2）
            log.info("[system-monitor] report from local host ignored: {}", hostname);
            throw new IllegalArgumentException("本机由平台实时采集，无需部署采集脚本：" + hostname);
        }
        SystemMonitorVO.HostStatus prev = remoteHosts.get(hostname);
        if (prev != null && prev.getLastReportTime() != null
                && System.currentTimeMillis() - prev.getLastReportTime()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                < reportMinIntervalSeconds * 1000L) {
            return prev;
        }

        SystemMonitorVO.Resource r = probe.parseResource(sections);
        String alias = firstNonBlank(sections, "ALIAS");

        SystemMonitorVO.HostStatus h = new SystemMonitorVO.HostStatus();
        h.setHostname(hostname);
        h.setAlias(alias);
        h.setCurrent(false);
        h.setOnline(true);
        h.setLastReportTime(r.getSampleTime());
        h.setResource(r);
        remoteHosts.put(hostname, h);

        try {
            persistSample(r, null);
        } catch (Exception e) {
            log.warn("[system-monitor] remote sample persist failed: host={}, {}", hostname, e.getMessage());
        }
        try {
            checkHostThresholds(r, alias == null || alias.isEmpty() ? hostname : alias);
        } catch (Exception e) {
            log.warn("[system-monitor] remote threshold check failed: host={}, {}", hostname, e.getMessage());
        }
        log.info("[system-monitor] report accepted: host={}, alias={}", hostname, alias == null ? "-" : alias);
        return h;
    }

    /** 解析 ###KEY### 分段报文为「段名 → 行列表」（段外行丢弃；段名非法时丢弃该段） */
    private Map<String, List<String>> parseSections(String body) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String current = null;
        for (String raw : body.split("\n")) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.startsWith("###") && line.endsWith("###") && line.length() > 6) {
                String key = line.substring(3, line.length() - 3).trim();
                current = key.matches("[A-Z0-9_]{1,32}") ? key : null;
                if (current != null && !sections.containsKey(current)) {
                    sections.put(current, new ArrayList<>());
                }
            } else if (current != null) {
                sections.get(current).add(line);
            }
        }
        return sections;
    }

    /** 取分段的第一个非空行（ALIAS/HOSTNAME 等单行段用） */
    private String firstNonBlank(Map<String, List<String>> sections, String key) {
        List<String> lines = sections.get(key);
        if (lines == null) {
            return null;
        }
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return null;
    }

    /* ============================ 工具 ============================ */

    /** 使用率 = 已用 / 基线容量（基线未配置或已用为空时返回 null） */
    private Double percentOf(Long size, long capacity) {
        if (size == null || capacity <= 0) {
            return null;
        }
        return Math.round(size * 1000.0 / capacity) / 10.0;
    }

    private boolean exceed(Double value, Double threshold) {
        return value != null && threshold != null && value > threshold;
    }

    private String hostname() {
        // 与资源快照同源：宿主机视角返回宿主机名（容器 hostname 为十六进制容器 ID，且随重新部署变化）
        try {
            String name = probe.hostName();
            if (name != null && !name.trim().isEmpty()) {
                return name;
            }
        } catch (Exception ignored) {
            // 降级到进程视角主机名
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            String env = System.getenv("HOSTNAME");
            return env == null ? "unknown" : env;
        }
    }

    /** 字节数转可读文本（描述文案用；页面展示换算由前端自行处理） */
    private String fmtBytes(Long bytes) {
        if (bytes == null) {
            return "--";
        }
        double v = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        int i = 0;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format("%.1f%s", v, units[i]);
    }

    private String stringOf(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Long longOf(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
