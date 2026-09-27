package com.qgyun.hltgq.hltgqsite.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qgyun.hltgq.hltgqsite.system.entity.SystemFaultRecord;
import com.qgyun.hltgq.hltgqsite.system.mapper.SystemMonitorMapper;
import com.qgyun.hltgq.hltgqsite.system.vo.SystemMonitorVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 故障记录服务（t_auto_hltgq_sys_fault_record，页面 system-monitor.html）。
 * <p>写入异步（单线程有界队列，队满丢弃、写库失败只记日志）：调用方为全局异常处理链与定时采样，
 * 绝不允许反向拖慢或二次抛出异常。
 * <p>抑制窗口：同键（category + type + source）在 {@code system.monitor.fault.suppress-minutes}
 * 分钟内只记一条，防止异常风暴把表写爆。
 * <p>反馈出口（“监控信息保存或反馈给监控报警子系统”）：配置 {@code system.monitor.webhook-url}
 * 后每条落库的故障记录异步 POST JSON（未配置时整体关闭）；超时/失败只记日志，绝不阻塞业务线程。
 */
@Service
public class FaultRecordService {

    private static final Logger log = LoggerFactory.getLogger(FaultRecordService.class);

    /** 类别：软件故障 / 系统故障 */
    public static final String CATEGORY_SOFT = "soft";
    public static final String CATEGORY_SYSTEM = "system";

    /** 级别 */
    public static final String LEVEL_ERROR = "error";
    public static final String LEVEL_WARN = "warn";

    /** 描述截断长度（防御超长堆栈文本） */
    private static final int DESC_MAX = 2000;

    @Autowired
    private SystemMonitorMapper mapper;

    @Autowired
    private ObjectMapper objectMapper;

    /** 报警子系统 Webhook（空 = 关闭，默认） */
    @Value("${system.monitor.webhook-url:}")
    private String webhookUrl;

    @Value("${system.monitor.webhook-timeout-ms:3000}")
    private int webhookTimeoutMs;

    /** 同键抑制窗口（分钟） */
    @Value("${system.monitor.fault.suppress-minutes:5}")
    private int suppressMinutes;

    /** 异步落库执行器：单线程 + 有界队列（1000），队满直接丢弃（记录不值得反压业务） */
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1000), r -> {
        Thread t = new Thread(r, "sys-fault-recorder");
        t.setDaemon(true);
        return t;
    }, new ThreadPoolExecutor.DiscardPolicy());

    /** Webhook 推送执行器：与落库队列隔离，HTTP 超时不得阻塞落库；有界队列（500）队满丢弃 */
    private final ThreadPoolExecutor webhookExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(500), r -> {
        Thread t = new Thread(r, "sys-fault-webhook");
        t.setDaemon(true);
        return t;
    }, new ThreadPoolExecutor.DiscardPolicy());

    /** 抑制窗口记忆：键 → 上次记录毫秒 */
    private final ConcurrentHashMap<String, Long> lastSeen = new ConcurrentHashMap<>();

    /** 记录软件故障（GlobalExceptionHandler 上游依赖异常埋点） */
    public void recordSoft(String type, String source, String desc) {
        record(CATEGORY_SOFT, type, source, LEVEL_ERROR, desc);
    }

    /** 记录系统故障（服务重启/依赖中断/资源越限） */
    public void recordSystem(String type, String source, String level, String desc) {
        record(CATEGORY_SYSTEM, type, source, level, desc);
    }

    /** 记录一条故障（抑制窗口内同键去重；异步落库 + 异步 webhook） */
    public void record(String category, String type, String source, String level, String desc) {
        try {
            String key = category + "|" + type + "|" + (source == null ? "" : source);
            long now = System.currentTimeMillis();
            Long last = lastSeen.get(key);
            if (last != null && now - last < suppressMinutes * 60_000L) {
                return;
            }
            lastSeen.put(key, now);
            if (lastSeen.size() > 2000) {
                // 键集合无界增长防护：清理已过期的抑制记忆（低频触发）
                lastSeen.entrySet().removeIf(e -> now - e.getValue() >= suppressMinutes * 60_000L);
            }

            SystemFaultRecord rec = new SystemFaultRecord();
            rec.setCategory(category);
            rec.setFaultType(type);
            rec.setFaultSource(source);
            rec.setFaultLevel(level);
            rec.setFaultDesc(truncate(desc));
            rec.setOccurTime(LocalDateTime.now());
            rec.setCorpCode("hltgq");
            rec.setCreatedBy("system-monitor");
            rec.setCreatedAt(rec.getOccurTime());
            rec.setUpdatedAt(rec.getOccurTime());

            executor.execute(() -> {
                try {
                    mapper.insert(rec);
                } catch (Exception e) {
                    log.warn("[system-monitor] fault record insert failed: {}", e.getMessage());
                }
            });
            notifyWebhook(rec);
            log.info("[system-monitor] fault recorded: category={}, type={}, source={}, level={}, desc={}",
                    category, type, source, level, rec.getFaultDesc());
        } catch (Exception e) {
            log.warn("[system-monitor] fault record dispatch failed: {}", e.getMessage());
        }
    }

    /** 异步推送故障记录到报警子系统（URL 未配置时静默跳过） */
    private void notifyWebhook(SystemFaultRecord record) {
        final String url = webhookUrl == null ? "" : webhookUrl.trim();
        if (url.isEmpty()) {
            return;
        }
        webhookExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String body = objectMapper.writeValueAsString(record);
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(webhookTimeoutMs);
                conn.setReadTimeout(webhookTimeoutMs);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                if (code >= 300) {
                    log.warn("[system-monitor] fault webhook responded HTTP {}", code);
                }
            } catch (Exception e) {
                log.warn("[system-monitor] fault webhook failed: {}", e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /**
     * 故障记录分页查询（category 缺省按软件故障；level、startDate/endDate 可选，日期含两端）。
     */
    public SystemMonitorVO.FaultPage query(String category, String level,
                                           String startDate, String endDate, int page, int size) {
        String cat = CATEGORY_SYSTEM.equals(category) ? CATEGORY_SYSTEM : CATEGORY_SOFT;
        String lv = (level == null || level.trim().isEmpty()) ? null : level.trim();
        LocalDateTime start = parseDayStart(startDate);
        LocalDateTime end = parseDayEnd(endDate);
        // page 上界钳制：防 (p-1)*s 溢出为负使 OFFSET 非法
        int p = Math.min(100_000, Math.max(1, page));
        int s = Math.min(100, Math.max(1, size));

        SystemMonitorVO.FaultPage vo = new SystemMonitorVO.FaultPage();
        vo.setTotal(mapper.countFaults(cat, lv, start, end));
        vo.setRecords(mapper.selectFaultPage(cat, lv, start, end, s, (p - 1) * s));
        return vo;
    }

    private LocalDateTime parseDayStart(String s) {
        LocalDate d = parseDate(s);
        return d == null ? null : d.atStartOfDay();
    }

    private LocalDateTime parseDayEnd(String s) {
        LocalDate d = parseDate(s);
        return d == null ? null : d.atTime(LocalTime.MAX);
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd：" + s);
        }
    }

    private String truncate(String desc) {
        if (desc == null) {
            return null;
        }
        return desc.length() > DESC_MAX ? desc.substring(0, DESC_MAX) : desc;
    }
}
