package com.qgyun.hltgq.hltgqsite.auth;

/**
 * 未登录 / 会话过期
 */
public class UnauthorizedException extends AuthException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
