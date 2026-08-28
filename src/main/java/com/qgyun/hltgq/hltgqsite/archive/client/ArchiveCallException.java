package com.qgyun.hltgq.hltgqsite.archive.client;

/**
 * 浩微档案系统调用异常：rc 非 200、网络不可达、响应解析失败等。
 */
public class ArchiveCallException extends RuntimeException {

    private final String rc;

    public ArchiveCallException(String rc, String message) {
        super(message);
        this.rc = rc;
    }

    public String getRc() {
        return rc;
    }
}
