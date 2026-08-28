package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.archive.client.ArchiveCallException;
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
 * ModelCallException → 502（上游模型服务返回错误码或不可达）。
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
}
