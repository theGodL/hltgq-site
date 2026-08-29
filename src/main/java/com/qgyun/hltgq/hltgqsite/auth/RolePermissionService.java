package com.qgyun.hltgq.hltgqsite.auth;

import com.qgyun.hltgq.hltgqsite.mapper.RoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 角色权限服务：判定当前用户是否为系统管理员（敏感操作权限）。
 * <p>判定链路（按方案文档「外挂服务可行链路」）：
 * <ol>
 *   <li>本地缓存 5 分钟（避免每请求访问 Redis/库）；</li>
 *   <li>Redis 角色缓存（平台维护，TTL 30m）：
 *       LRANGE qx.auth.hltgq.user.{userId} 取 roleId 列表，
 *       逐个 HGET qx.auth.hltgq.role.{roleId} 的 code 字段比对 hltgq_default_admin；</li>
 *   <li>Redis 未命中/异常 → 直连库查角色指派关系兜底（RoleMapper），保证判定正确性。</li>
 * </ol>
 * <p>角色缓存 key 前缀可配置（{@code auth.role-cache-key-prefix}），生产实测如带环境前缀（如 dev_）时调整；
 * 因有查库兜底，前缀不匹配不影响判定正确性，仅影响性能。
 */
@Service
public class RolePermissionService {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionService.class);

    /** 系统管理员角色编码：{corpCode}_default_admin（hltgq 场景） */
    private static final String ADMIN_ROLE_CODE = "hltgq_default_admin";

    /** 角色缓存中"无角色"占位值 */
    private static final String NO_ROLE_PLACEHOLDER = "0";

    /** 判定结果本地缓存 TTL（毫秒） */
    private static final long LOCAL_CACHE_TTL_MS = 5 * 60 * 1000L;

    /** 角色缓存 key 前缀（如 qx.auth.hltgq.），可配置 */
    @Value("${auth.role-cache-key-prefix:qx.auth.hltgq.}")
    private String roleCacheKeyPrefix;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RoleMapper roleMapper;

    /** userId → (判定结果, 过期时间戳) */
    private final ConcurrentHashMap<String, CachedEntry> localCache = new ConcurrentHashMap<>();

    /**
     * 判定用户是否为系统管理员（拥有 hltgq_default_admin 角色）
     *
     * @param userId 用户主键（t_apaas_uc_user.id）
     * @return true = 系统管理员
     */
    public boolean isSystemAdmin(String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        CachedEntry cached = localCache.get(userId);
        if (cached != null && cached.expireAt > System.currentTimeMillis()) {
            return cached.admin;
        }
        boolean admin = resolveAdmin(userId);
        localCache.put(userId, new CachedEntry(admin, System.currentTimeMillis() + LOCAL_CACHE_TTL_MS));
        log.info("角色判定 userId={}, isAdmin={}", userId, admin);
        return admin;
    }

    /**
     * 真实判定：Redis 角色缓存优先（命中即信任，含"无角色"占位），未命中/异常走查库兜底。
     */
    private boolean resolveAdmin(String userId) {
        try {
            List<String> roleIds = redisTemplate.opsForList().range(roleCacheKeyPrefix + "user." + userId, 0, -1);
            if (roleIds != null && !roleIds.isEmpty()) {
                // Redis 命中（空角色列表存 "0" 占位）：遍历角色详情比对 code
                for (String roleId : roleIds) {
                    if (roleId == null || roleId.isEmpty() || NO_ROLE_PLACEHOLDER.equals(roleId)) {
                        continue;
                    }
                    Object codeValue = redisTemplate.opsForHash().get(roleCacheKeyPrefix + "role." + roleId, "code");
                    if (codeValue != null && ADMIN_ROLE_CODE.equals(stripQuotes(String.valueOf(codeValue)))) {
                        return true;
                    }
                }
                return false;
            }
            // Redis 未命中 → 查库兜底
            return roleMapper.existsAdminRole(userId) > 0;
        } catch (Exception e) {
            log.warn("Redis 角色缓存不可用，降级查库判定 userId={}：{}", userId, e.getMessage());
            return roleMapper.existsAdminRole(userId) > 0;
        }
    }

    /**
     * 剥离 JSON 序列化遗留的首尾双引号（平台 Redis 值可能带引号包裹）。
     */
    private String stripQuotes(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * 本地缓存条目
     */
    private static class CachedEntry {
        final boolean admin;
        final long expireAt;

        CachedEntry(boolean admin, long expireAt) {
            this.admin = admin;
            this.expireAt = expireAt;
        }
    }
}
