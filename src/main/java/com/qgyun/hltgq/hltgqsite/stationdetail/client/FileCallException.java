package com.qgyun.hltgq.hltgqsite.stationdetail.client;

/**
 * 企效文件服务调用异常：success 非 true、网络不可达、响应解析失败等。
 */
public class FileCallException extends RuntimeException {

    public FileCallException(String message) {
        super(message);
    }

    public FileCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
