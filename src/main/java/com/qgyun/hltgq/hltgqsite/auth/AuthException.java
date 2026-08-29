package com.qgyun.hltgq.hltgqsite.auth;

/**
 * 登录鉴权异常基类
 * <p>未登录（session 不存在/已过期）→ 401 + 跳转登录页；
 * 会话服务不可用（Redis 故障）→ 503，禁止降级放行。
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
