package com.qgyun.hltgq.hltgqsite.auth;

/**
 * 当前请求用户上下文持有器（ThreadLocal）
 * <p>由 AuthInterceptor 在 preHandle 写入、afterCompletion 清理；
 * 业务代码通过 {@link #currentUser()} 获取，接口被放行或开关关闭时为 null。
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext user) {
        HOLDER.set(user);
    }

    /**
     * 当前请求登录人，未经过拦截器（白名单/开关关闭）时为 null
     */
    public static UserContext currentUser() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
