package com.qgyun.hltgq.hltgqsite.stats.client;

/**
 * hltgq-device 数据统计服务调用异常（device 视频巡检统计接口仅内网可达，由 site 网关转发）。
 * <p>全局处理见 {@link com.qgyun.hltgq.hltgqsite.controller.GlobalExceptionHandler} → HTTP 502。
 */
public class DeviceStatsCallException extends RuntimeException {

    public DeviceStatsCallException(String message) {
        super(message);
    }

    public DeviceStatsCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
