package com.qgyun.hltgq.hltgqsite.sms.vo;

import lombok.Data;

/**
 * 一次批量发送的统计（多号码）。
 */
@Data
public class SmsBatchStat {

    private int successCount;

    private int failCount;

    /** 网络异常重试次数（运维观察阿里云连通性） */
    private int retryCount;

    public static SmsBatchStat of(int successCount, int failCount, int retryCount) {
        SmsBatchStat stat = new SmsBatchStat();
        stat.setSuccessCount(successCount);
        stat.setFailCount(failCount);
        stat.setRetryCount(retryCount);
        return stat;
    }
}
