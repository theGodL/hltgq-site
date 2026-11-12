package com.qgyun.hltgq.hltgqsite.sms.vo;

import lombok.Data;

/**
 * 单条短信发送结果（一次号码调用）。
 * <p>success=true 表示阿里云返回 Code=OK（已受理）；false 时 code/message 落库供排查。
 * <p>网络异常（超时/连接失败）code 为 NETWORK_ERROR，由发送服务按重试策略处理；
 * 业务错误（isv.xxx，如频控/参数非法）不重试。
 */
@Data
public class SmsSendResult {

    private boolean success;

    /** 阿里云返回 Code（OK / isv.xxx / NETWORK_ERROR） */
    private String code;

    /** 阿里云返回 Message 或异常摘要 */
    private String message;

    /** 发送回执 ID（BizId） */
    private String bizId;

    /** 阿里云请求 ID */
    private String requestId;

    public static SmsSendResult ok(String bizId, String requestId) {
        SmsSendResult result = new SmsSendResult();
        result.setSuccess(true);
        result.setCode("OK");
        result.setMessage("OK");
        result.setBizId(bizId);
        result.setRequestId(requestId);
        return result;
    }

    public static SmsSendResult fail(String code, String message) {
        SmsSendResult result = new SmsSendResult();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /** 网络类异常（可重试） */
    public boolean isRetryable() {
        return !success && "NETWORK_ERROR".equals(code);
    }
}
