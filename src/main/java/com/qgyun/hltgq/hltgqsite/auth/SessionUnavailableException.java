package com.qgyun.hltgq.hltgqsite.auth;

/**
 * 会话服务不可用（Redis 异常）：鉴权场景禁止降级放行，快速失败
 */
public class SessionUnavailableException extends AuthException {

    public SessionUnavailableException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
