package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.archive.client.ArchiveCallException;
import com.qgyun.hltgq.hltgqsite.auth.SessionUnavailableException;
import com.qgyun.hltgq.hltgqsite.auth.UnauthorizedException;
import com.qgyun.hltgq.hltgqsite.model.client.ModelCallException;
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
 * UnauthorizedException → 401（未登录/会话过期）；
 * SessionUnavailableException → 503（会话服务不可用）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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
        Map<String, Object> result = new HashMap<>();
        result.put("code", 502);
        result.put("message", e.getMessage() == null ? "上游服务调用失败" : e.getMessage());
        return result;
    }

    @ExceptionHandler(ModelCallException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> handleModelCall(ModelCallException e) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", e.getCode());
        result.put("message", e.getMessage());
        return result;
    }

    @ExceptionHandler(ArchiveCallException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> handleArchiveCall(ArchiveCallException e) {
        Map<String, Object> result = new HashMap<>();
        result.put("rc", e.getRc());
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
        Map<String, Object> result = new HashMap<>();
        result.put("code", 503);
        result.put("message", e.getMessage() == null ? "会话服务不可用" : e.getMessage());
        return result;
    }
}
