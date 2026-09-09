package com.qgyun.hltgq.hltgqsite.stats.client;

/**
 * hltgq-mq 数据统计服务调用异常（mq 内部统计接口仅内网可达，由 site 网关转发）。
 * <p>全局处理见 {@link com.qgyun.hltgq.hltgqsite.controller.GlobalExceptionHandler} → HTTP 502。
 */
public class MqStatsCallException extends RuntimeException {

    public MqStatsCallException(String message) {
        super(message);
    }

    public MqStatsCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
