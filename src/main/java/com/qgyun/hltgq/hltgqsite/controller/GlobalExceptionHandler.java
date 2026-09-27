package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.archive.client.ArchiveCallException;
import com.qgyun.hltgq.hltgqsite.auth.SessionUnavailableException;
import com.qgyun.hltgq.hltgqsite.auth.UnauthorizedException;
import com.qgyun.hltgq.hltgqsite.model.client.ModelCallException;
import com.qgyun.hltgq.hltgqsite.stats.client.DeviceStatsCallException;
import com.qgyun.hltgq.hltgqsite.stats.client.MqStatsCallException;
import com.qgyun.hltgq.hltgqsite.system.service.FaultRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理：统一参数错误与模型调用错误的 HTTP 语义。
 * <p>IllegalArgumentException → 400（请求参数错误）；
 * IllegalStateException → 502（上游服务调用失败，如三维 SSO）；
 * ModelCallException → 502（上游模型服务返回错误码或不可达）；
 * DeviceStatsCallException → 502（上游 hltgq-device 视频统计服务返回错误或不可达）；
 * UnauthorizedException → 401（未登录/会话过期）；
 * SessionUnavailableException → 503（会话服务不可用）；
 * DataIntegrityViolationException → 409（唯一约束冲突，如阈值表 uniq_hltgq_threshold_site_type_zb）。
 * <p>上游依赖异常（6 类，401/400/409 除外）同时埋点记录软件故障
 * （t_auto_hltgq_sys_fault_record，页面 system-monitor.html），"监控信息反馈给报警子系统"的数据来源之一。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private FaultRecordService faultRecordService;

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException e) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", e.getMessage() == null ? "请求参数错误" : e.getMessage());
        return result;
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> handleIllegalState(IllegalStateException e) {
        faultRecordService.recordSoft("上游服务调用失败", "upstream",
                e.getMessage() == null ? "上游服务调用失败" : e.getMessage());
        Map<String, Object> result = new HashMap<>();
        result.put("code", 502);
        result.put("message", e.getMessage() == null ? "上游服务调用失败" : e.getMessage());
        return result;
    }

    @ExceptionHandler(ModelCallException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> handleModelCall(ModelCallException e) {
        faultRecordService.recordSoft("上游服务调用失败", "model",
                "模型服务调用失败：code=" + e.getCode() + ", " + e.getMessage());
        Map<String, Object> result = new HashMap<>();
        result.put("code", e.getCode());
        result.put("message", e.getMessage());
        return result;
    }

    @ExceptionHandler(ArchiveCallException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> handleArchiveCall(ArchiveCallException e) {
        faultRecordService.recordSoft("上游服务调用失败", "archive",
                "档案服务调用失败：rc=" + e.getRc() + ", " + e.getMessage());
        Map<String, Object> result = new HashMap<>();
        result.put("rc", e.getRc());
        result.put("message", e.getMessage());
        return result;
    }

    @ExceptionHandler(MqStatsCallException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> handleMqStatsCall(MqStatsCallException e) {
        faultRecordService.recordSoft("上游服务调用失败", "mq",
                "MQ 统计服务调用失败：" + e.getMessage());
        Map<String, Object> result = new HashMap<>();
        result.put("code", 502);
        result.put("message", e.getMessage());
        return result;
    }

    @ExceptionHandler(DeviceStatsCallException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> handleDeviceStatsCall(DeviceStatsCallException e) {
        faultRecordService.recordSoft("上游服务调用失败", "device",
                "视频统计服务调用失败：" + e.getMessage());
        Map<String, Object> result = new HashMap<>();
        result.put("code", 502);
        result.put("message", e.getMessage());
        return result;
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleUnauthorized(UnauthorizedException e) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 401);
        result.put("message", e.getMessage() == null ? "未登录" : e.getMessage());
        return result;
    }

    @ExceptionHandler(SessionUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleSessionUnavailable(SessionUnavailableException e) {
        faultRecordService.recordSoft("上游服务调用失败", "auth",
                "会话服务不可用：" + (e.getMessage() == null ? "会话服务不可用" : e.getMessage()));
        Map<String, Object> result = new HashMap<>();
        result.put("code", 503);
        result.put("message", e.getMessage() == null ? "会话服务不可用" : e.getMessage());
        return result;
    }

    /**
     * 唯一约束冲突 → 409 且带引导文案。
     * <p>阈值表已建唯一索引 {@code uniq_hltgq_threshold_site_type_zb}（site + 类型归一 + 指标归一）。
     * 页面判重（LIKE 子串语义）与入库之间存在并发窗口：两个请求同时新增同一「站点+类型+指标」时，
     * 前者通过判重、后者被索引拒绝。此处把 DB 约束冲突翻译为与页面判重一致的提示，
     * 避免前端因无 message 回退为「请求失败（HTTP 500）」。
     * <p>捕获父类是为了兼容不同驱动对 SQLState 23505 的翻译（DuplicateKeyException 为子类，同样命中）。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleDataIntegrity(DataIntegrityViolationException e) {
        String detail = e.getMessage() == null ? "" : e.getMessage();
        Map<String, Object> result = new HashMap<>();
        result.put("message", detail.contains("uniq_hltgq_threshold_site_type_zb")
                ? "该站点已配置该类型（指标）阈值，请刷新列表后直接编辑"
                : "数据已存在或不满足约束，请刷新后重试");
        return result;
    }
}
