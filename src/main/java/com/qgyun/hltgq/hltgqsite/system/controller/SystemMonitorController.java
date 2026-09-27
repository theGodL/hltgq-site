package com.qgyun.hltgq.hltgqsite.system.controller;

import com.qgyun.hltgq.hltgqsite.system.service.FaultRecordService;
import com.qgyun.hltgq.hltgqsite.system.service.SystemMonitorService;
import com.qgyun.hltgq.hltgqsite.system.vo.SystemMonitorVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统资源监控接口（页面 static/system-monitor.html）。
 * <p>查询接口按需求（“数据值班人员利用该功能可方便查询”）不设管理员写保护；
 * 写侧动作（采样落表、越限告警）由 {@link SystemMonitorService} 后台完成。
 * <p>例外：{@code /report} 为服务器采集脚本上报入口（无登录会话，auth.white-list 放行，
 * X-Sysmon-Token 鉴权 + 可选来源前缀限制 + 请求体大小限制）。
 */
@RestController
@RequestMapping("/system-monitor")
public class SystemMonitorController {

    private static final Logger log = LoggerFactory.getLogger(SystemMonitorController.class);

    /** 上报报文大小上限（字符，约 512KB；/proc/stat 与 df 输出远小于此） */
    private static final int MAX_REPORT_CHARS = 512 * 1024;

    @Autowired
    private SystemMonitorService systemMonitorService;

    @Autowired
    private FaultRecordService faultRecordService;

    /** 上报鉴权 token（未配置时上报接口停用；部署时由环境变量 SYSTEM_MONITOR_REPORT_TOKEN 注入） */
    @Value("${system.monitor.report.token:}")
    private String reportToken;

    /** 上报来源 IP 前缀白名单（逗号分隔，空 = 不限制；如 10.68.18.） */
    @Value("${system.monitor.report.allow-cidr:}")
    private String reportAllowCidr;

    /**
     * 概览：服务器资源快照（CPU/内存/交换区/磁盘）+ 数据库表空间 + 告警阈值口径。
     * <p>页面按固定间隔轮询本接口实现实时刷新。
     */
    @GetMapping("/overview")
    public SystemMonitorVO.Overview overview() {
        return systemMonitorService.overview();
    }

    /**
     * 故障记录分页。
     *
     * @param category  类别：soft 软件故障 / system 系统故障（缺省 soft）
     * @param level     级别：error / warn（可选）
     * @param startDate 起始日期 yyyy-MM-dd（可选，含当天）
     * @param endDate   截止日期 yyyy-MM-dd（可选，含当天）
     * @param page      页码（从 1 起）
     * @param size      每页条数（1~100）
     */
    @GetMapping("/faults")
    public SystemMonitorVO.FaultPage faults(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return faultRecordService.query(category, level, startDate, endDate, page, size);
    }

    /**
     * 服务器资源上报（各服务器 cron 采集脚本调用，见仓库根目录 sysmon-collect.sh）。
     * <p>鉴权：X-Sysmon-Token 头与 {@code system.monitor.report.token} 一致（未配置时接口停用）；
     * {@code system.monitor.report.allow-cidr} 配置后仅允许来源 IP 前缀匹配的上报。
     * 请求体为 ###KEY### 分段的原始命令输出（UTF-8 文本）。
     */
    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> report(HttpServletRequest request) {
        if (reportToken == null || reportToken.trim().isEmpty()) {
            return reply(HttpStatus.SERVICE_UNAVAILABLE, "上报接口未启用（未配置 token）");
        }
        String token = request.getHeader("X-Sysmon-Token");
        if (token == null || !MessageDigest.isEqual(
                token.trim().getBytes(StandardCharsets.UTF_8),
                reportToken.trim().getBytes(StandardCharsets.UTF_8))) {
            log.warn("上报鉴权失败：{} 来源 {}", request.getMethod(), request.getRemoteAddr());
            return reply(HttpStatus.FORBIDDEN, "鉴权失败");
        }
        if (!originAllowed(request.getRemoteAddr())) {
            log.warn("上报来源被拒：{}", request.getRemoteAddr());
            return reply(HttpStatus.FORBIDDEN, "来源不允许");
        }
        try {
            SystemMonitorVO.HostStatus h = systemMonitorService.acceptReport(readBody(request));
            Map<String, Object> ok = new HashMap<>();
            ok.put("ok", true);
            ok.put("hostname", h.getHostname());
            return ResponseEntity.ok(ok);
        } catch (IllegalArgumentException e) {
            return reply(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            return reply(HttpStatus.BAD_REQUEST, "读取请求体失败");
        } catch (Exception e) {
            log.error("上报处理失败：{}", e.getMessage());
            return reply(HttpStatus.INTERNAL_SERVER_ERROR, "上报处理失败");
        }
    }

    /** 请求体读取（强制 UTF-8、限长防滥用） */
    private String readBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int total = 0;
        try (Reader reader = new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8)) {
            int n;
            while ((n = reader.read(buf)) != -1) {
                total += n;
                if (total > MAX_REPORT_CHARS) {
                    throw new IllegalArgumentException("上报数据过大");
                }
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    private boolean originAllowed(String addr) {
        if (reportAllowCidr == null || reportAllowCidr.trim().isEmpty() || addr == null) {
            return true;
        }
        for (String prefix : reportAllowCidr.split(",")) {
            String p = prefix.trim();
            if (!p.isEmpty() && addr.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<Map<String, Object>> reply(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("ok", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
