package com.qgyun.hltgq.hltgqsite.auth;

import com.qgyun.hltgq.hltgqsite.mapper.ApaasUcUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话上下文服务（通用）：从平台 Redis 会话解析当前登录人。
 * <p>会话来源：平台登录后 cookie 中的 sessionId（形如 dev_hltgq_session:xxxx），
 * 该值即 Redis Hash key，HGETALL 取用户上下文（userId、username、name、corpCode 等）。
 * <p>注意：鉴权场景 Redis 异常禁止降级放行（区别于天气缓存），抛 SessionUnavailableException 快速失败。
 */
@Service
public class SessionContextService {

    private static final Logger log = LoggerFactory.getLogger(SessionContextService.class);

    /** Header 传递会话 ID（平台服务端代理场景） */
    public static final String HEADER_SESSION_ID = "X-Session-Id";

    /** Authorization 头传递会话 ID（标准头，值形如 dev_hltgq_session:xxxx，可选带 Bearer 前缀） */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** Cookie 传递会话 ID（浏览器直连场景） */
    public static final String COOKIE_SESSION_ID = "sessionId";

    /** loginName 本地缓存 TTL（毫秒） */
    private static final long LOGIN_NAME_CACHE_TTL_MS = 5 * 60 * 1000L;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ApaasUcUserMapper apaasUcUserMapper;

    /** userId → (loginName, 过期时间戳)，避免每个请求查库 */
    private final ConcurrentHashMap<String, LoginNameEntry> loginNameCache = new ConcurrentHashMap<>();

    /**
     * 从请求提取会话 ID：优先 Header（X-Session-Id），其次 Header（Authorization），最后 Cookie（sessionId）
     *
     * @return 会话 ID，未携带返回 null
     */
    public String extractSessionId(HttpServletRequest request) {
        String headerValue = request.getHeader(HEADER_SESSION_ID);
        if (StringUtils.hasText(headerValue)) {
            return headerValue.trim();
        }
        String authValue = request.getHeader(HEADER_AUTHORIZATION);
        if (StringUtils.hasText(authValue)) {
            String trimmed = authValue.trim();
            // 兼容标准 "Bearer xxx" 前缀，剥离后剩余部分即会话键
            if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                trimmed = trimmed.substring(7).trim();
            }
            if (StringUtils.hasText(trimmed)) {
                return trimmed;
            }
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_SESSION_ID.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue().trim();
                }
            }
        }
        return null;
    }

    /**
     * 解析当前请求登录人（提取会话 ID → 解析用户上下文），供接口直接调用。
     */
    public UserContext resolveCurrentUser(HttpServletRequest request) {
        String sessionId = extractSessionId(request);
        if (sessionId == null) {
            log.warn("未登录：请求未携带会话 ID（X-Session-Id/Authorization/Cookie 均缺失）");
            throw new UnauthorizedException("未登录：缺少会话 ID");
        }
        return resolveUser(sessionId);
    }

    /**
     * 按会话 ID 严格解析用户上下文：HGETALL {sessionId}，空 Hash 视为未登录/已过期。
     * <p>登录判定以本方法为准：携带 sessionId 后还须 Redis 中存在有效会话才算登录，
     * 供拦截器校验会话有效性与 /auth/current-user 等业务接口解析身份使用。
     */
    public UserContext resolveUser(String sessionId) {
        Map<Object, Object> entries;
        try {
            entries = redisTemplate.opsForHash().entries(sessionId);
        } catch (Exception e) {
            // 会话有效性无法确认时禁止降级放行：Redis 不可达快速失败
            log.error("会话服务不可用：HGETALL {} 异常", sessionId, e);
            throw new SessionUnavailableException("会话服务不可用，请稍后重试", e);
        }
        if (entries == null || entries.isEmpty()) {
            log.warn("未登录或会话过期：session {} 无用户上下文", sessionId);
            throw new UnauthorizedException("未登录或会话已过期");
        }
        return parseUserContext(sessionId, entries);
    }

    /**
     * 解析会话 Hash 为用户上下文（仅取实际存在的字段，登录名由 resolveLoginName 查库兜底）。
     */
    private UserContext parseUserContext(String sessionId, Map<Object, Object> entries) {
        UserContext user = new UserContext();
        user.setUserId(firstOf(entries, "userId", "user_id", "id"));
        user.setCorpCode(firstOf(entries, "corpCode", "corp_code"));
        user.setSuperAdmin(firstOf(entries, "superAdmin", "super_admin"));

        log.info("会话解析完成 sessionId={}, userId={}, corpCode={}, superAdmin={}, hashKeys={}",
                sessionId, user.getUserId(), user.getCorpCode(), user.getSuperAdmin(), entries.keySet());
        return user;
    }

    /**
     * 解析登录名（t_apaas_uc_user.login_name，三维 SSO 使用）：
     * 按 userId 查库获取，结果本地缓存 5 分钟（用户量小，避免每请求查库）。
     */
    public String resolveLoginName(UserContext user) {
        if (StringUtils.hasText(user.getLoginName())) {
            return user.getLoginName();
        }
        String userId = user.getUserId();
        if (userId == null) {
            log.error("登录名解析失败：会话中无 userId，无法查询 t_apaas_uc_user");
            throw new UnauthorizedException("登录人信息不完整：缺少 userId");
        }

        LoginNameEntry cached = loginNameCache.get(userId);
        if (cached != null && cached.expireAt > System.currentTimeMillis()) {
            user.setLoginName(cached.loginName);
            return cached.loginName;
        }

        String loginName = apaasUcUserMapper.selectLoginNameById(userId);
        if (loginName == null) {
            log.error("登录名解析失败：t_apaas_uc_user 中无 userId={}（corp_code='hltgq'）的记录", userId);
            throw new UnauthorizedException("当前账号未关联企效用户，无法单点登录三维系统（userId=" + userId + "）");
        }
        loginNameCache.put(userId, new LoginNameEntry(loginName, System.currentTimeMillis() + LOGIN_NAME_CACHE_TTL_MS));
        user.setLoginName(loginName);
        return loginName;
    }

    /**
     * 按候选 key 顺序取会话 Hash 字段值（兼容字段命名变体），全部缺失返回 null。
     * <p>平台存储时对值做过 JSON 序列化（字符串值带双引号包裹），统一剥离首尾引号，
     * 避免查库条件携带引号字符导致不匹配。
     */
    private String firstOf(Map<Object, Object> entries, String... keys) {
        for (String key : keys) {
            Object value = entries.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return normalizeValue(String.valueOf(value));
            }
        }
        return null;
    }

    /**
     * 规范化 Hash 字段值：剥离 JSON 序列化遗留的首尾双引号。
     */
    private String normalizeValue(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * loginName 本地缓存条目
     */
    private static class LoginNameEntry {
        final String loginName;
        final long expireAt;

        LoginNameEntry(String loginName, long expireAt) {
            this.loginName = loginName;
            this.expireAt = expireAt;
        }
    }
}
